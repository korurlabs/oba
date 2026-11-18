package com.korur.oba.apps.kaya.account.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractIngestIT;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountControllerIT extends AbstractIngestIT {

    private void useAccount(Machine machine, String accountId) {
        jdbcTemplate.update("UPDATE kaya.machines SET account_id = ?::uuid WHERE id = ?::uuid", accountId, machine.machineId());
    }

    private void snapshot(Tenant tenant, Machine machine, String accountId, double fiveHour, Instant capturedAt) {
        jdbcTemplate.update("""
                INSERT INTO kaya.machine_usage_snapshots
                    (organization_id, machine_id, account_id, session_id, five_hour_used_percentage, five_hour_resets_at,
                     captured_at, received_at)
                VALUES (?, ?::uuid, ?::uuid, 'sess', ?, now() + interval '2 hours', ?::timestamptz, ?::timestamptz)
                """, tenant.organization().getId(), machine.machineId(), accountId, fiveHour, capturedAt.toString(),
                capturedAt.toString());
    }

    @Nested
    class ListAccounts {

        @Test
        void member_readsShapeOrderAndUsersOfEveryAccount() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Person bo = signIn(tenant, "bo");
            String zed = insertAccount(tenant, ada.userId(), "CLAUDE", "zed@claude.example", null, null);
            String team = insertAccount(tenant, ada.userId(), "CLAUDE", "amy@claude.example", "org-uuid-1", "Acme");
            String personal = insertAccount(tenant, ada.userId(), "CLAUDE", "amy@claude.example", null, null);
            linkAccount(tenant, team, bo.userId());

            mockMvc.perform(in(tenant, get("/kaya/accounts"), jwtOf(tenant.member().getUser()), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(3))
                    // same email: a personal account (null organization name) sorts after the named one
                    .andExpect(jsonPath("$.data[0].id").value(team))
                    .andExpect(jsonPath("$.data[1].id").value(personal))
                    .andExpect(jsonPath("$.data[2].id").value(zed))
                    .andExpect(jsonPath("$.data[0].provider").value("CLAUDE"))
                    .andExpect(jsonPath("$.data[0].accountEmail").value("amy@claude.example"))
                    .andExpect(jsonPath("$.data[0].accountOrganizationUuid").value("org-uuid-1"))
                    .andExpect(jsonPath("$.data[0].accountOrganizationName").value("Acme"))
                    .andExpect(jsonPath("$.data[0].lastSeenAt").exists())
                    .andExpect(jsonPath("$.data[0].users.length()").value(2))
                    .andExpect(jsonPath("$.data[0].users[0].source").value("RECORDER"))
                    .andExpect(jsonPath("$.data[1].accountOrganizationUuid").doesNotExist())
                    .andExpect(jsonPath("$.data[1].users.length()").value(1))
                    .andExpect(jsonPath("$.data[1].users[0].userId").value(ada.userId()))
                    .andExpect(jsonPath("$.data[0].usage.sharedWith.length()").value(0));
        }

        @Test
        void organizationWithoutAccounts_returnsEmptyList() throws Exception {
            Tenant tenant = newTenant();

            mockMvc.perform(in(tenant, get("/kaya/accounts"), jwtOf(tenant.member().getUser()), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void userIdFilter_keepsLinkedAccountsAndEachStillListsAllItsUsers() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Person bo = signIn(tenant, "bo");
            String shared = insertAccount(tenant, ada.userId(), "CLAUDE", "shared@claude.example", null, null);
            linkAccount(tenant, shared, bo.userId());
            String adas = insertAccount(tenant, ada.userId(), "CLAUDE", "ada@claude.example", null, null);
            insertAccount(tenant, bo.userId(), "CLAUDE", "bo@claude.example", null, null);

            mockMvc.perform(in(tenant, get("/kaya/accounts").param("userId", ada.userId()),
                            jwtOf(tenant.member().getUser()), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(adas))
                    .andExpect(jsonPath("$.data[1].id").value(shared))
                    .andExpect(jsonPath("$.data[1].users.length()").value(2));
        }

        @Test
        void malformedUserId_returnsValidationError() throws Exception {
            Tenant tenant = newTenant();

            mockMvc.perform(in(tenant, get("/kaya/accounts").param("userId", "not-a-uuid"),
                            jwtOf(tenant.member().getUser()), null))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        }

        @Test
        void machineCountsAndUsage_followTheMachinesLatestAccount() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            String account = insertAccount(tenant, ada.userId(), "CLAUDE", "ada@claude.example", null, null);
            String idle = insertAccount(tenant, ada.userId(), "CLAUDE", "idle@claude.example", null, null);
            Machine laptop = registerMachine(tenant, ada, "laptop");
            Machine desktop = registerMachine(tenant, ada, "desktop");
            Machine old = registerMachine(tenant, ada, "old");
            useAccount(laptop, account);
            useAccount(desktop, account);
            useAccount(old, account);
            jdbcTemplate.update("UPDATE kaya.machines SET status = 'RETIRED' WHERE id = ?::uuid", old.machineId());
            snapshot(tenant, laptop, account, 10.5, Instant.now().minusSeconds(600));
            snapshot(tenant, desktop, account, 42.25, Instant.now().minusSeconds(60));

            mockMvc.perform(in(tenant, get("/kaya/accounts"), jwtOf(tenant.member().getUser()), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.id=='" + account + "')].machines").value(3))
                    .andExpect(jsonPath("$.data[?(@.id=='" + account + "')].activeMachines").value(2))
                    .andExpect(jsonPath("$.data[?(@.id=='" + account + "')].usage.state").value("KNOWN"))
                    .andExpect(jsonPath("$.data[?(@.id=='" + account + "')].usage.reportedByMachineName").value("desktop"))
                    .andExpect(jsonPath("$.data[?(@.id=='" + account + "')].usage.fiveHour.usedPercentage").value(42.25))
                    .andExpect(jsonPath("$.data[?(@.id=='" + idle + "')].machines").value(0))
                    .andExpect(jsonPath("$.data[?(@.id=='" + idle + "')].activeMachines").value(0))
                    .andExpect(jsonPath("$.data[?(@.id=='" + idle + "')].usage.state").value("NO_SNAPSHOT"));
        }

        @Test
        void accountsOfAnotherOrganization_areNeverListed() throws Exception {
            Tenant tenant = newTenant();
            Tenant foreign = newTenant();
            Person eve = signIn(foreign, "eve");
            insertAccount(foreign, eve.userId(), "CLAUDE", "eve@claude.example", null, null);

            mockMvc.perform(in(tenant, get("/kaya/accounts"), jwtOf(tenant.member().getUser()), null))
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        void withoutOrganizationHeader_returnsBadRequest() throws Exception {
            Tenant tenant = newTenant();

            mockMvc.perform(get("/kaya/accounts").with(jwtOf(tenant.member().getUser())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("GEN_API_007"));
        }

        @Test
        void nonMemberOfTheOrganization_returnsForbidden() throws Exception {
            Tenant tenant = newTenant();
            Tenant foreign = newTenant();

            mockMvc.perform(in(tenant, get("/kaya/accounts"), jwtOf(foreign.owner().getUser()), null))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ORG_SEC_001"));
        }

        @Test
        void withoutJwt_returnsUnauthorized() throws Exception {
            Tenant tenant = newTenant();

            mockMvc.perform(get("/kaya/accounts").header("X-Organization-ID", tenant.id()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class ReadAccount {

        @Test
        void member_readsTheAccountWithUsersCountsAndTimestamps() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Person bo = signIn(tenant, "bo");
            String account = insertAccount(tenant, ada.userId(), "CLAUDE", "ada@claude.example", "org-uuid-1", "Acme");
            linkAccount(tenant, account, bo.userId());
            Machine laptop = registerMachine(tenant, ada, "laptop");
            useAccount(laptop, account);

            mockMvc.perform(in(tenant, get("/kaya/accounts/" + account), jwtOf(tenant.member().getUser()), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(account))
                    .andExpect(jsonPath("$.data.provider").value("CLAUDE"))
                    .andExpect(jsonPath("$.data.accountEmail").value("ada@claude.example"))
                    .andExpect(jsonPath("$.data.accountOrganizationName").value("Acme"))
                    .andExpect(jsonPath("$.data.users.length()").value(2))
                    .andExpect(jsonPath("$.data.machines").value(1))
                    .andExpect(jsonPath("$.data.activeMachines").value(1))
                    .andExpect(jsonPath("$.data.usage.state").value("NO_SNAPSHOT"))
                    .andExpect(jsonPath("$.data.createdAt").exists())
                    .andExpect(jsonPath("$.data.updatedAt").exists());
        }

        @Test
        void unknownId_returnsNotFound() throws Exception {
            Tenant tenant = newTenant();

            mockMvc.perform(in(tenant, get("/kaya/accounts/" + UUID.randomUUID()), jwtOf(tenant.member().getUser()), null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        }

        @Test
        void accountOfAnotherOrganization_returnsNotFound() throws Exception {
            Tenant tenant = newTenant();
            Tenant foreign = newTenant();
            Person eve = signIn(foreign, "eve");
            String foreignAccount = insertAccount(foreign, eve.userId(), "CLAUDE", "eve@claude.example", null, null);

            mockMvc.perform(in(tenant, get("/kaya/accounts/" + foreignAccount), jwtOf(tenant.member().getUser()), null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        }

        @Test
        void withoutOrganizationHeader_returnsBadRequest() throws Exception {
            Tenant tenant = newTenant();

            mockMvc.perform(get("/kaya/accounts/" + UUID.randomUUID()).with(jwtOf(tenant.member().getUser())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("GEN_API_007"));
        }

        @Test
        void nonMemberOfTheOrganization_returnsForbidden() throws Exception {
            Tenant tenant = newTenant();
            Tenant foreign = newTenant();

            mockMvc.perform(in(tenant, get("/kaya/accounts/" + UUID.randomUUID()), jwtOf(foreign.owner().getUser()), null))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ORG_SEC_001"));
        }
    }
}

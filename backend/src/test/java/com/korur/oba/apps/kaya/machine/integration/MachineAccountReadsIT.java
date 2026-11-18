package com.korur.oba.apps.kaya.machine.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 3. Java SDK
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.machine.dto.response.MachineAccountSummaryResponse;
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineUsageResponse;
import com.korur.oba.apps.kaya.machine.enums.MachineUsageState;
import com.korur.oba.apps.kaya.machine.service.MachineQueryService;
import com.korur.oba.apps.kaya.machine.service.MachineUsageQueryService;
import com.korur.oba.apps.kaya.support.AbstractIngestIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MachineAccountReadsIT extends AbstractIngestIT {

    @Autowired
    private MachineQueryService machineQueryService;
    @Autowired
    private MachineUsageQueryService machineUsageQueryService;

    private void useAccount(Machine machine, String accountId) {
        jdbcTemplate.update("UPDATE kaya.machines SET account_id = ?::uuid WHERE id = ?::uuid", accountId, machine.machineId());
    }

    private void snapshot(Tenant tenant, Machine machine, String accountId, double fiveHour, Instant resetsAt,
            Instant capturedAt) {
        jdbcTemplate.update("""
                INSERT INTO kaya.machine_usage_snapshots
                    (organization_id, machine_id, account_id, session_id, five_hour_used_percentage, five_hour_resets_at,
                     captured_at, received_at)
                VALUES (?, ?::uuid, ?::uuid, 'sess', ?, ?::timestamptz, ?::timestamptz, ?::timestamptz)
                """, tenant.organization().getId(), machine.machineId(), accountId, fiveHour, resetsAt.toString(),
                capturedAt.toString(), capturedAt.toString());
    }

    @Nested
    class GetAccountSummaries {

        @Test
        void machinesOnAccounts_countsAllAndActiveAndOmitsAccountsWithoutMachines() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            UUID first = UUID.fromString(insertAccount(tenant, ada.userId(), "CLAUDE", "a@claude.example", null, null));
            UUID second = UUID.fromString(insertAccount(tenant, ada.userId(), "CLAUDE", "b@claude.example", null, null));
            UUID empty = UUID.fromString(insertAccount(tenant, ada.userId(), "CLAUDE", "c@claude.example", null, null));
            Machine one = registerMachine(tenant, ada, "one");
            Machine two = registerMachine(tenant, ada, "two");
            Machine three = registerMachine(tenant, ada, "three");
            useAccount(one, first.toString());
            useAccount(two, first.toString());
            useAccount(three, second.toString());
            jdbcTemplate.update("UPDATE kaya.machines SET status = 'RETIRED' WHERE id = ?::uuid", two.machineId());

            Map<UUID, MachineAccountSummaryResponse> summaries = machineQueryService.getAccountSummaries(
                    tenant.organization().getId(), List.of(first, second, empty));

            assertEquals(2L, summaries.get(first).machines());
            assertEquals(1L, summaries.get(first).activeMachines());
            assertEquals(1L, summaries.get(second).machines());
            assertEquals(1L, summaries.get(second).activeMachines());
            assertFalse(summaries.containsKey(empty));
        }

        @Test
        void noAccountIds_returnsEmptyMap() {
            assertTrue(machineQueryService.getAccountSummaries(UUID.randomUUID(), List.of()).isEmpty());
        }

        @Test
        void accountOfAnotherOrganization_countsNothing() throws Exception {
            Tenant tenant = newTenant();
            Tenant foreign = newTenant();
            Person eve = signIn(foreign, "eve");
            UUID account = UUID.fromString(insertAccount(foreign, eve.userId(), "CLAUDE", "e@claude.example", null, null));
            useAccount(registerMachine(foreign, eve, "eves"), account.toString());

            assertTrue(machineQueryService.getAccountSummaries(tenant.organization().getId(), List.of(account)).isEmpty());
        }
    }

    @Nested
    class UsageForAccounts {

        @Test
        void machinesReportingOneAccount_returnTheNewestSnapshotWithoutSharedMachines() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            UUID account = UUID.fromString(insertAccount(tenant, ada.userId(), "CLAUDE", "a@claude.example", null, null));
            Machine older = registerMachine(tenant, ada, "older");
            Machine newer = registerMachine(tenant, ada, "newer");
            useAccount(older, account.toString());
            useAccount(newer, account.toString());
            Instant now = Instant.now();
            snapshot(tenant, older, account.toString(), 11, now.plusSeconds(3600), now.minusSeconds(900));
            snapshot(tenant, newer, account.toString(), 33.5, now.plusSeconds(3600), now.minusSeconds(30));

            MachineUsageResponse usage = machineUsageQueryService
                    .usageForAccounts(tenant.organization().getId(), List.of(account), now).get(account);

            assertEquals(MachineUsageState.KNOWN, usage.state());
            assertEquals(newer.machineId(), usage.reportedByMachineId().toString());
            assertEquals(0, usage.fiveHour().usedPercentage().compareTo(new BigDecimal("33.5")));
            assertFalse(usage.fiveHour().reset());
            assertTrue(usage.sharedWith().isEmpty());
        }

        @Test
        void windowPastItsReset_isShownResetWithoutValue() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            UUID account = UUID.fromString(insertAccount(tenant, ada.userId(), "CLAUDE", "a@claude.example", null, null));
            Machine machine = registerMachine(tenant, ada, "box");
            useAccount(machine, account.toString());
            Instant now = Instant.now();
            snapshot(tenant, machine, account.toString(), 80, now.minusSeconds(60), now.minusSeconds(3600));

            MachineUsageResponse usage = machineUsageQueryService
                    .usageForAccounts(tenant.organization().getId(), List.of(account), now).get(account);

            assertTrue(usage.fiveHour().reset());
            assertNull(usage.fiveHour().usedPercentage());
        }

        @Test
        void accountWithoutSnapshot_isNoSnapshot() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            UUID account = UUID.fromString(insertAccount(tenant, ada.userId(), "CLAUDE", "a@claude.example", null, null));

            MachineUsageResponse usage = machineUsageQueryService
                    .usageForAccounts(tenant.organization().getId(), List.of(account), Instant.now()).get(account);

            assertEquals(MachineUsageState.NO_SNAPSHOT, usage.state());
        }

        @Test
        void noAccountIds_returnsEmptyMap() {
            assertTrue(machineUsageQueryService.usageForAccounts(UUID.randomUUID(), List.of(), Instant.now()).isEmpty());
        }
    }

    @Nested
    class ListMachinesByAccount {

        @Test
        void accountIdFilter_keepsOnlyMachinesWhoseLatestAccountIsThatAccount() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            String account = insertAccount(tenant, ada.userId(), "CLAUDE", "a@claude.example", null, null);
            String other = insertAccount(tenant, ada.userId(), "CLAUDE", "b@claude.example", null, null);
            Machine on = registerMachine(tenant, ada, "on");
            Machine elsewhere = registerMachine(tenant, ada, "elsewhere");
            registerMachine(tenant, ada, "none");
            useAccount(on, account);
            useAccount(elsewhere, other);

            mockMvc.perform(in(tenant, get("/kaya/machines").param("accountId", account), jwtOf(tenant.member().getUser()), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(on.machineId()));
        }

        @Test
        void accountIdOfAnotherOrganization_returnsNoMachines() throws Exception {
            Tenant tenant = newTenant();
            Tenant foreign = newTenant();
            Person eve = signIn(foreign, "eve");
            String account = insertAccount(foreign, eve.userId(), "CLAUDE", "e@claude.example", null, null);
            useAccount(registerMachine(foreign, eve, "eves"), account);
            registerMachine(tenant, signIn(tenant, "ada"), "adas");

            mockMvc.perform(in(tenant, get("/kaya/machines").param("accountId", account), jwtOf(tenant.member().getUser()), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }
}

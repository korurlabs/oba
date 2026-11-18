package com.korur.oba.apps.kaya.machine.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.machine.service.MachineTokenQueryService;
import com.korur.oba.apps.kaya.support.AbstractTenantIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MachineControllerIT extends AbstractTenantIT {

    @Autowired
    private MachineTokenQueryService machineTokenQueryService;

    private MvcResult createMachine(Tenant tenant, Person person, String machineName) throws Exception {
        return mockMvc.perform(in(tenant, post("/kaya/machines"), person.jwt(), "{\"machineName\":\"" + machineName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static String sha256Hex(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void assertTokenRefused(String token) {
        UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                () -> machineTokenQueryService.authenticate(token));
        assertEquals("KAY_SEC_003", exception.getErrorCode());
    }

    @Nested
    class CreateMachine {

        @Test
        void ownUser_returnsTokenOnceAndStoresOnlyItsHash() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");

            MvcResult created = createMachine(tenant, ada, "laptop");

            String token = read(created, "$.data.token");
            String machineId = read(created, "$.data.machine.id");
            assertTrue(token.matches("^kyt_[A-Za-z0-9_-]{43}$"), "token format: " + token);
            assertEquals(47, token.length());
            assertEquals(token.substring(0, 12), read(created, "$.data.machine.token.tokenPrefix"));
            assertEquals(ada.userId(), read(created, "$.data.machine.userId"));
            assertEquals("ACTIVE", read(created, "$.data.machine.status"));
            assertEquals(sha256Hex(token), jdbcTemplate.queryForObject(
                    "SELECT token_hash FROM kaya.machine_tokens WHERE machine_id = ?::uuid", String.class, machineId));
            assertEquals(0L, count("""
                    SELECT count(*) FROM kaya.machine_tokens
                    WHERE machine_id = ?::uuid AND (token_hash = ? OR token_prefix = ?)
                    """, machineId, token, token));
            assertEquals(0L, count("""
                    SELECT count(*) FROM kaya.user_audit_entries WHERE user_id = ?::uuid AND details::text LIKE ?
                    """, ada.userId(), "%" + token + "%"));
            mockMvc.perform(in(tenant, get("/kaya/machines/" + machineId), ada.jwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").exists())
                    .andExpect(jsonPath("$.data.token.tokenHash").doesNotExist())
                    .andExpect(jsonPath("$.data.token.token").doesNotExist());
        }

        @Test
        void ownUser_tokenAuthenticatesAsMachineAndExpiresInAYear() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");

            MvcResult created = createMachine(tenant, ada, "laptop");

            MachinePrincipal principal = machineTokenQueryService.authenticate(read(created, "$.data.token"));
            assertEquals(read(created, "$.data.machine.id"), principal.machineId().toString());
            assertEquals(ada.user().getId(), principal.userId());
            assertEquals(tenant.id(), principal.organizationId().toString());
            assertEquals("laptop", principal.machineName());
            Instant expiresAt = Instant.parse(read(created, "$.data.machine.token.expiresAt"));
            long days = Duration.between(Instant.now(), expiresAt).toDays();
            assertTrue(days >= 364 && days <= 365, "expires in " + days + " days");
        }

        @Test
        void ownUser_writesOneMachineCreatedEntryByTheUser() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");

            MvcResult created = createMachine(tenant, ada, "laptop");

            assertEquals(1L, count("""
                    SELECT count(*) FROM kaya.user_audit_entries
                    WHERE user_id = ?::uuid AND action = 'MACHINE_CREATED' AND actor_kind = 'USER' AND actor_id = ?
                      AND details ->> 'machineId' = ? AND details ->> 'machineName' = 'laptop'
                      AND details ->> 'reactivated' = 'false'
                    """, ada.userId(), ada.user().getId(), read(created, "$.data.machine.id")));
        }

        @Test
        void activeMachineWithSameMachineName_throwsConflictAndLeavesNoSecondToken() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            MvcResult first = createMachine(tenant, ada, "laptop");

            mockMvc.perform(in(tenant, post("/kaya/machines"), ada.jwt(), "{\"machineName\":\"laptop\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KAY_VAL_006"))
                    .andExpect(jsonPath("$.error.errorValues.machineId").value(read(first, "$.data.machine.id")));

            assertEquals(1L, count("SELECT count(*) FROM kaya.machines WHERE user_id = ?::uuid", ada.userId()));
            assertEquals(1L, count("SELECT count(*) FROM kaya.machine_tokens WHERE user_id = ?::uuid", ada.userId()));
            assertEquals(1L, auditCount(ada.userId(), "MACHINE_CREATED"));
        }

        @Test
        void retiredMachineWithSameMachineName_isReactivatedWithNewToken() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            MvcResult first = createMachine(tenant, ada, "laptop");
            String machineId = read(first, "$.data.machine.id");
            mockMvc.perform(in(tenant, patch("/kaya/machines/" + machineId), ada.jwt(), "{\"status\":\"RETIRED\"}"))
                    .andExpect(status().isOk());

            MvcResult again = createMachine(tenant, ada, "laptop");

            assertEquals(machineId, read(again, "$.data.machine.id"));
            assertEquals("ACTIVE", read(again, "$.data.machine.status"));
            assertNotEquals(read(first, "$.data.token"), read(again, "$.data.token"));
            assertEquals(machineId, machineTokenQueryService.authenticate(read(again, "$.data.token")).machineId().toString());
            assertEquals(1L, count("""
                    SELECT count(*) FROM kaya.user_audit_entries
                    WHERE user_id = ?::uuid AND action = 'MACHINE_CREATED' AND details ->> 'reactivated' = 'true'
                    """, ada.userId()));
            assertEquals(1L, count("SELECT count(*) FROM kaya.machines WHERE user_id = ?::uuid", ada.userId()));
        }

        @Test
        void blankMachineName_returnsValidationError() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");

            mockMvc.perform(in(tenant, post("/kaya/machines"), ada.jwt(), "{\"machineName\":\" \"}"))
                    .andExpect(status().isUnprocessableEntity());
            mockMvc.perform(in(tenant, post("/kaya/machines"), ada.jwt(), "{\"machineName\":\"" + "m".repeat(256) + "\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void memberWhoNeverSignedIn_createsTheMachineForOwnUser() throws Exception {
            Tenant tenant = newTenant();

            mockMvc.perform(in(tenant, post("/kaya/machines"), jwtOf(tenant.member().getUser()), "{\"machineName\":\"laptop\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.machine.userId").value(tenant.member().getUser().getId().toString()));
        }

        @Test
        void adminForAnotherUser_createsMachineAndRecordsAdminAsActor() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");

            mockMvc.perform(in(tenant, post("/kaya/machines"), jwtOf(tenant.admin().getUser()),
                            "{\"machineName\":\"build-box\",\"userId\":\"" + ada.userId() + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.machine.userId").value(ada.userId()));

            assertEquals(1L, count("""
                    SELECT count(*) FROM kaya.user_audit_entries
                    WHERE user_id = ?::uuid AND action = 'MACHINE_CREATED' AND actor_id = ?
                    """, ada.userId(), tenant.admin().getUser().getId()));
        }

        @Test
        void memberForAnotherUser_returnsForbiddenAndCreatesNothing() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Person bob = signIn(tenant, "bob");

            mockMvc.perform(in(tenant, post("/kaya/machines"), bob.jwt(),
                            "{\"machineName\":\"x\",\"userId\":\"" + ada.userId() + "\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("GEN_SEC_003"));

            assertEquals(0L, count("SELECT count(*) FROM kaya.machines WHERE user_id = ?::uuid", ada.userId()));
        }

        @Test
        void userOfAnotherOrganization_returnsNotFound() throws Exception {
            Tenant tenant = newTenant();
            Tenant foreign = newTenant();
            Person stranger = signIn(foreign, "eve");

            mockMvc.perform(in(tenant, post("/kaya/machines"), jwtOf(tenant.admin().getUser()),
                            "{\"machineName\":\"x\",\"userId\":\"" + stranger.userId() + "\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Authenticate {

        @Test
        void malformedOrUnknownToken_isRefused() throws Exception {
            assertTokenRefused(null);
            assertTokenRefused("");
            assertTokenRefused("garbage");
            assertTokenRefused("kyt_" + "A".repeat(42));
            assertTokenRefused("xyz_" + "A".repeat(43));
            assertTokenRefused("kyt_" + "A".repeat(43));
        }

        @Test
        void tokenPresentedWithChangedCase_isRefused() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            String token = read(createMachine(tenant, ada, "laptop"), "$.data.token");

            assertTokenRefused("kyt_" + token.substring(4).toUpperCase().replace('-', 'A'));
        }

        @Test
        void expiredToken_isRefused() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            MvcResult created = createMachine(tenant, ada, "laptop");
            jdbcTemplate.update("UPDATE kaya.machine_tokens SET expires_at = now() - interval '1 second' WHERE machine_id = ?::uuid",
                    read(created, "$.data.machine.id"));

            assertTokenRefused(read(created, "$.data.token"));
        }

        @Test
        void tokenOfRetiredMachine_isRefusedEvenWhenNotRevoked() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            MvcResult created = createMachine(tenant, ada, "laptop");
            jdbcTemplate.update("UPDATE kaya.machines SET status = 'RETIRED' WHERE id = ?::uuid",
                    read(created, "$.data.machine.id"));

            assertTokenRefused(read(created, "$.data.token"));
        }
    }

    @Nested
    class RotateToken {

        @Test
        void self_replacesTokenKeepsOneLiveTokenAndRecordsRotation() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            MvcResult created = createMachine(tenant, ada, "laptop");
            String machineId = read(created, "$.data.machine.id");
            String oldToken = read(created, "$.data.token");

            MvcResult rotated = mockMvc.perform(in(tenant, post("/kaya/machines/" + machineId + "/token"), ada.jwt(), null))
                    .andExpect(status().isCreated())
                    .andReturn();

            String newToken = read(rotated, "$.data.token");
            assertTrue(newToken.matches("^kyt_[A-Za-z0-9_-]{43}$"));
            assertNotEquals(oldToken, newToken);
            assertEquals(newToken.substring(0, 12), read(rotated, "$.data.tokenPrefix"));
            assertTokenRefused(oldToken);
            assertEquals(machineId, machineTokenQueryService.authenticate(newToken).machineId().toString());
            assertEquals(1L, count("SELECT count(*) FROM kaya.machine_tokens WHERE machine_id = ?::uuid AND revoked_at IS NULL", machineId));
            assertEquals(2L, count("SELECT count(*) FROM kaya.machine_tokens WHERE machine_id = ?::uuid", machineId));
            assertEquals(sha256Hex(newToken), jdbcTemplate.queryForObject(
                    "SELECT token_hash FROM kaya.machine_tokens WHERE id = ?::uuid", String.class, read(rotated, "$.data.tokenId")));
            assertEquals(1L, count("""
                    SELECT count(*) FROM kaya.user_audit_entries
                    WHERE user_id = ?::uuid AND action = 'TOKEN_ROTATED' AND details ->> 'tokenId' = ?
                      AND details ->> 'revokedTokenId' IS NOT NULL
                    """, ada.userId(), read(rotated, "$.data.tokenId")));
        }

        @Test
        void selfWhenNoTokenIsLive_recordsTokenIssued() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            String machineId = read(createMachine(tenant, ada, "laptop"), "$.data.machine.id");
            mockMvc.perform(in(tenant, delete("/kaya/machines/" + machineId + "/token"), ada.jwt(), null))
                    .andExpect(status().isOk());

            mockMvc.perform(in(tenant, post("/kaya/machines/" + machineId + "/token"), ada.jwt(), null))
                    .andExpect(status().isCreated());

            assertEquals(1L, auditCount(ada.userId(), "TOKEN_ISSUED"));
            assertEquals(0L, auditCount(ada.userId(), "TOKEN_ROTATED"));
        }

        @Test
        void adminOrOtherMember_returnsForbiddenAndKeepsToken() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Person bob = signIn(tenant, "bob");
            MvcResult created = createMachine(tenant, ada, "laptop");
            String machineId = read(created, "$.data.machine.id");

            mockMvc.perform(in(tenant, post("/kaya/machines/" + machineId + "/token"), jwtOf(tenant.admin().getUser()), null))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("GEN_SEC_003"));
            mockMvc.perform(in(tenant, post("/kaya/machines/" + machineId + "/token"), jwtOf(tenant.owner().getUser()), null))
                    .andExpect(status().isForbidden());
            mockMvc.perform(in(tenant, post("/kaya/machines/" + machineId + "/token"), bob.jwt(), null))
                    .andExpect(status().isForbidden());

            assertEquals(machineId, machineTokenQueryService.authenticate(read(created, "$.data.token")).machineId().toString());
            assertEquals(0L, auditCount(ada.userId(), "TOKEN_ROTATED"));
        }

        @Test
        void retiredMachine_returnsConflict() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            String machineId = read(createMachine(tenant, ada, "laptop"), "$.data.machine.id");
            mockMvc.perform(in(tenant, patch("/kaya/machines/" + machineId), ada.jwt(), "{\"status\":\"RETIRED\"}"))
                    .andExpect(status().isOk());

            mockMvc.perform(in(tenant, post("/kaya/machines/" + machineId + "/token"), ada.jwt(), null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KAY_VAL_011"));
        }

        @Test
        void unknownOrForeignMachine_returnsNotFound() throws Exception {
            Tenant tenant = newTenant();
            Tenant foreign = newTenant();
            Person ada = signIn(tenant, "ada");
            Person eve = signIn(foreign, "eve");
            String foreignMachine = read(createMachine(foreign, eve, "laptop"), "$.data.machine.id");

            mockMvc.perform(in(tenant, post("/kaya/machines/" + UUID.randomUUID() + "/token"), ada.jwt(), null))
                    .andExpect(status().isNotFound());
            mockMvc.perform(in(tenant, post("/kaya/machines/" + foreignMachine + "/token"), ada.jwt(), null))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class RevokeToken {

        @Test
        void adminOrSelf_revokesTokenAndRecordsEntry() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            MvcResult first = createMachine(tenant, ada, "laptop");
            MvcResult second = createMachine(tenant, ada, "desktop");

            mockMvc.perform(in(tenant, delete("/kaya/machines/" + read(first, "$.data.machine.id") + "/token"),
                            jwtOf(tenant.admin().getUser()), null))
                    .andExpect(status().isOk());
            mockMvc.perform(in(tenant, delete("/kaya/machines/" + read(second, "$.data.machine.id") + "/token"), ada.jwt(), null))
                    .andExpect(status().isOk());

            assertTokenRefused(read(first, "$.data.token"));
            assertTokenRefused(read(second, "$.data.token"));
            assertEquals(2L, auditCount(ada.userId(), "TOKEN_REVOKED"));
            mockMvc.perform(in(tenant, get("/kaya/machines/" + read(first, "$.data.machine.id")), ada.jwt(), null))
                    .andExpect(jsonPath("$.data.token").doesNotExist())
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }

        @Test
        void otherMember_returnsForbiddenAndKeepsToken() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            MvcResult created = createMachine(tenant, ada, "laptop");

            mockMvc.perform(in(tenant, delete("/kaya/machines/" + read(created, "$.data.machine.id") + "/token"),
                            jwtOf(tenant.member().getUser()), null))
                    .andExpect(status().isForbidden());

            machineTokenQueryService.authenticate(read(created, "$.data.token"));
        }

        @Test
        void noLiveToken_changesNothingAndWritesNoEntry() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            String machineId = read(createMachine(tenant, ada, "laptop"), "$.data.machine.id");
            mockMvc.perform(in(tenant, delete("/kaya/machines/" + machineId + "/token"), ada.jwt(), null))
                    .andExpect(status().isOk());

            mockMvc.perform(in(tenant, delete("/kaya/machines/" + machineId + "/token"), ada.jwt(), null))
                    .andExpect(status().isOk());

            assertEquals(1L, auditCount(ada.userId(), "TOKEN_REVOKED"));
        }
    }

    @Nested
    class RetireMachine {

        @Test
        void self_retiresMachineRevokesTokenAndRecordsEntry() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            MvcResult created = createMachine(tenant, ada, "laptop");
            String machineId = read(created, "$.data.machine.id");

            mockMvc.perform(in(tenant, patch("/kaya/machines/" + machineId), ada.jwt(), "{\"status\":\"RETIRED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("RETIRED"))
                    .andExpect(jsonPath("$.data.token").doesNotExist());

            assertTokenRefused(read(created, "$.data.token"));
            assertEquals(1L, count("""
                    SELECT count(*) FROM kaya.user_audit_entries
                    WHERE user_id = ?::uuid AND action = 'MACHINE_RETIRED' AND details ->> 'revokedTokenId' IS NOT NULL
                    """, ada.userId()));
        }

        @Test
        void alreadyRetired_returnsMachineWithoutSecondEntry() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            String machineId = read(createMachine(tenant, ada, "laptop"), "$.data.machine.id");
            mockMvc.perform(in(tenant, patch("/kaya/machines/" + machineId), ada.jwt(), "{\"status\":\"RETIRED\"}"))
                    .andExpect(status().isOk());

            mockMvc.perform(in(tenant, patch("/kaya/machines/" + machineId), jwtOf(tenant.admin().getUser()), "{\"status\":\"RETIRED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("RETIRED"));

            assertEquals(1L, auditCount(ada.userId(), "MACHINE_RETIRED"));
        }

        @Test
        void statusActiveOrOtherMember_isRefused() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            String machineId = read(createMachine(tenant, ada, "laptop"), "$.data.machine.id");

            mockMvc.perform(in(tenant, patch("/kaya/machines/" + machineId), ada.jwt(), "{\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isUnprocessableEntity());
            mockMvc.perform(in(tenant, patch("/kaya/machines/" + machineId), jwtOf(tenant.member().getUser()),
                            "{\"status\":\"RETIRED\"}"))
                    .andExpect(status().isForbidden());

            assertEquals(0L, auditCount(ada.userId(), "MACHINE_RETIRED"));
        }
    }

    @Nested
    class ReadMachines {

        @Test
        void list_filtersByUserAndStatusInsideTheOrganization() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Person bob = signIn(tenant, "bob");
            createMachine(tenant, ada, "laptop");
            String retired = read(createMachine(tenant, ada, "old"), "$.data.machine.id");
            createMachine(tenant, bob, "bobs");
            mockMvc.perform(in(tenant, patch("/kaya/machines/" + retired), ada.jwt(), "{\"status\":\"RETIRED\"}"))
                    .andExpect(status().isOk());

            mockMvc.perform(in(tenant, get("/kaya/machines"), jwtOf(tenant.member().getUser()), null))
                    .andExpect(jsonPath("$.data.length()").value(3));
            mockMvc.perform(in(tenant, get("/kaya/machines").param("userId", ada.userId()), jwtOf(tenant.member().getUser()), null))
                    .andExpect(jsonPath("$.data.length()").value(2));
            mockMvc.perform(in(tenant, get("/kaya/machines").param("userId", ada.userId()).param("status", "RETIRED"),
                            jwtOf(tenant.member().getUser()), null))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(retired));
        }

        @Test
        void list_neverShowsMachinesOfAnotherOrganization() throws Exception {
            Tenant tenant = newTenant();
            Tenant foreign = newTenant();
            Person eve = signIn(foreign, "eve");
            createMachine(foreign, eve, "laptop");

            mockMvc.perform(in(tenant, get("/kaya/machines"), jwtOf(tenant.member().getUser()), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }



        @Test
        void detail_unknownOrForeignMachine_returnsNotFound() throws Exception {
            Tenant tenant = newTenant();
            Tenant foreign = newTenant();
            Person eve = signIn(foreign, "eve");
            String foreignMachine = read(createMachine(foreign, eve, "laptop"), "$.data.machine.id");

            mockMvc.perform(in(tenant, get("/kaya/machines/" + foreignMachine), jwtOf(tenant.member().getUser()), null))
                    .andExpect(status().isNotFound());
            mockMvc.perform(in(tenant, get("/kaya/machines/" + UUID.randomUUID()), jwtOf(tenant.member().getUser()), null))
                    .andExpect(status().isNotFound());
        }
    }
}

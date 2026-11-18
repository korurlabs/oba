package com.korur.oba.apps.kaya.core.security.filter;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// 3. Java SDK
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractIngestIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MachineTokenAuthenticationFilterIT extends AbstractIngestIT {

    private static final int LIMIT_BYTES = 8 * 1024 * 1024;

    private Map<String, Object> validBatch(Machine machine) {
        return batch(machine, sessionId(), remote(), List.of(event(1, "SessionStart")));
    }

    private Instant lastUsedAt(Machine machine) {
        List<Timestamp> rows = jdbcTemplate.queryForList(
                "SELECT last_used_at FROM kaya.machine_tokens WHERE machine_id = ?::uuid", Timestamp.class, machine.machineId());
        return rows.get(0) == null ? null : rows.get(0).toInstant();
    }

    private Machine newMachine() throws Exception {
        Tenant tenant = newTenant();
        return registerMachine(tenant, signIn(tenant, "ada"), "laptop");
    }

    @Nested
    class ValidToken {

        @Test
        void validToken_acceptsTheBatchAndMarksTheTokenUsed() throws Exception {
            Machine machine = newMachine();
            assertNull(lastUsedAt(machine));

            send(machine.token(), validBatch(machine))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.acknowledgedSeq").value(1));

            assertNotNull(lastUsedAt(machine));
        }

        @Test
        void validToken_withSurroundingWhitespace_isAccepted() throws Exception {
            Machine machine = newMachine();

            mockMvc.perform(post("/kaya/ingest/batches")
                            .header("Authorization", "Bearer   " + machine.token() + "  ")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validBatch(machine))))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class RefusedToken {

        private void assertRefused(ResultActions result) throws Exception {
            result.andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("KAY_SEC_003"));
        }

        @Test
        void missingAuthorizationHeader_returns401() throws Exception {
            Machine machine = newMachine();

            assertRefused(mockMvc.perform(post("/kaya/ingest/batches")
                    .contentType(MediaType.APPLICATION_JSON).content(json(validBatch(machine)))));
        }

        @Test
        void nonBearerScheme_returns401() throws Exception {
            Machine machine = newMachine();

            assertRefused(mockMvc.perform(post("/kaya/ingest/batches")
                    .header("Authorization", "Basic " + machine.token())
                    .contentType(MediaType.APPLICATION_JSON).content(json(validBatch(machine)))));
        }

        @Test
        void unknownToken_returns401() throws Exception {
            Machine machine = newMachine();

            assertRefused(send("kyt_" + "A".repeat(43), validBatch(machine)));
        }

        @Test
        void keycloakJwtWithoutMachineToken_returns401() throws Exception {
            Machine machine = newMachine();

            assertRefused(mockMvc.perform(post("/kaya/ingest/batches").with(machine.owner().jwt())
                    .contentType(MediaType.APPLICATION_JSON).content(json(validBatch(machine)))));
        }

        @Test
        void revokedToken_returns401AndDoesNotMarkItUsed() throws Exception {
            Machine machine = newMachine();
            jdbcTemplate.update("UPDATE kaya.machine_tokens SET revoked_at = now() WHERE machine_id = ?::uuid", machine.machineId());

            assertRefused(send(machine.token(), validBatch(machine)));

            assertNull(lastUsedAt(machine));
        }

        @Test
        void expiredToken_returns401() throws Exception {
            Machine machine = newMachine();
            jdbcTemplate.update("UPDATE kaya.machine_tokens SET expires_at = now() - interval '1 second' WHERE machine_id = ?::uuid",
                    machine.machineId());

            assertRefused(send(machine.token(), validBatch(machine)));
        }

        @Test
        void tokenOfRetiredMachine_returns401() throws Exception {
            Machine machine = newMachine();
            jdbcTemplate.update("UPDATE kaya.machines SET status = 'RETIRED' WHERE id = ?::uuid", machine.machineId());

            assertRefused(send(machine.token(), validBatch(machine)));
        }

        @Test
        void refusedToken_storesNothing() throws Exception {
            Machine machine = newMachine();
            String session = sessionId();
            jdbcTemplate.update("UPDATE kaya.machine_tokens SET revoked_at = now() WHERE machine_id = ?::uuid", machine.machineId());

            send(machine.token(), batch(machine, session, remote(), List.of(event(1, "SessionStart"))))
                    .andExpect(status().isUnauthorized());

            assertEquals(0L, sessionCount(session));
        }
    }

    @Nested
    class LastUsedAt {

        @Test
        void tokenUsedSecondsAgo_keepsItsLastUsedAt() throws Exception {
            Machine machine = newMachine();
            send(machine.token(), validBatch(machine)).andExpect(status().isOk());
            jdbcTemplate.update("UPDATE kaya.machine_tokens SET last_used_at = now() - interval '10 seconds' WHERE machine_id = ?::uuid",
                    machine.machineId());
            Instant before = lastUsedAt(machine);

            send(machine.token(), validBatch(machine)).andExpect(status().isOk());

            assertEquals(before, lastUsedAt(machine));
        }

        @Test
        void tokenUsedMoreThanAMinuteAgo_movesItsLastUsedAt() throws Exception {
            Machine machine = newMachine();
            send(machine.token(), validBatch(machine)).andExpect(status().isOk());
            jdbcTemplate.update("UPDATE kaya.machine_tokens SET last_used_at = now() - interval '2 minutes' WHERE machine_id = ?::uuid",
                    machine.machineId());
            Instant before = lastUsedAt(machine);

            send(machine.token(), validBatch(machine)).andExpect(status().isOk());

            assertTrue(lastUsedAt(machine).isAfter(before.plusSeconds(60)));
        }
    }

    @Nested
    class BodyLimit {

        @Test
        void bodyOverFourMebibytes_returns413WithTheLimitAndStillMarksTheTokenUsed() throws Exception {
            Machine machine = newMachine();

            mockMvc.perform(post("/kaya/ingest/batches")
                            .header("Authorization", "Bearer " + machine.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new byte[LIMIT_BYTES + 1]))
                    .andExpect(status().isPayloadTooLarge())
                    .andExpect(jsonPath("$.error.code").value("KAY_VAL_012"))
                    .andExpect(jsonPath("$.error.errorValues.limitBytes").value(LIMIT_BYTES));

            assertNotNull(lastUsedAt(machine));
        }

        @Test
        void bodyOfExactlyFourMebibytes_isNotRefusedForItsSize() throws Exception {
            Machine machine = newMachine();

            mockMvc.perform(post("/kaya/ingest/batches")
                            .header("Authorization", "Bearer " + machine.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new byte[LIMIT_BYTES]))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void oversizedBodyWithoutToken_returns401NotTheBodyError() throws Exception {
            mockMvc.perform(post("/kaya/ingest/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new byte[LIMIT_BYTES + 1]))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("KAY_SEC_003"));
        }
    }
}

package com.korur.oba.apps.kaya.ingest.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

// 3. Java SDK
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractIngestIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IngestUsageIT extends AbstractIngestIT {

    private Map<String, Object> usageBatch(Machine machine, Map<String, Object> usage) {
        Map<String, Object> batch = batch(machine, sessionId(), remote(), List.of());
        batch.put("usage", usage);
        return batch;
    }

    private static long epoch(Instant instant) {
        return instant.getEpochSecond();
    }

    private ResultActions usageOf(Tenant tenant, Person reader, Machine machine) throws Exception {
        return mockMvc.perform(in(tenant, get("/kaya/machines/" + machine.machineId()), reader.jwt(), null))
                .andExpect(status().isOk());
    }

    private Map<String, Object> snapshotRow(Machine machine) {
        return jdbcTemplate.queryForMap("""
                SELECT five_hour_used_percentage, seven_day_used_percentage, captured_at, received_at, session_id,
                       account_id
                FROM kaya.machine_usage_snapshots WHERE machine_id = ?::uuid
                """, machine.machineId());
    }

    @Nested
    class Snapshots {

        @Test
        void usageBatch_storesOneSnapshotPerMachineWithTwoDecimals() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Instant now = Instant.now();

            send(machine.token(), usageBatch(machine, usage(now, 23.456, epoch(now) + 3600, 41, epoch(now) + 86_400)))
                    .andExpect(status().isOk());

            Map<String, Object> row = snapshotRow(machine);
            assertEquals(new BigDecimal("23.46"), row.get("five_hour_used_percentage"));
            assertEquals(new BigDecimal("41.00"), row.get("seven_day_used_percentage"));
        }

        @Test
        void newerSnapshot_replacesTheStoredOne() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Instant now = Instant.now();

            send(machine.token(), usageBatch(machine, usage(now.minusSeconds(600), 10, epoch(now) + 3600, 20, epoch(now) + 86_400)))
                    .andExpect(status().isOk());
            send(machine.token(), usageBatch(machine, usage(now, 30, epoch(now) + 3600, 40, epoch(now) + 86_400)))
                    .andExpect(status().isOk());

            assertEquals(1L, count("SELECT count(*) FROM kaya.machine_usage_snapshots WHERE machine_id = ?::uuid", machine.machineId()));
            assertEquals(new BigDecimal("30.00"), snapshotRow(machine).get("five_hour_used_percentage"));
        }

        @Test
        void olderSnapshot_doesNotOverwriteANewerOne() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Instant now = Instant.now();

            send(machine.token(), usageBatch(machine, usage(now, 30, epoch(now) + 3600, 40, epoch(now) + 86_400)))
                    .andExpect(status().isOk());
            send(machine.token(), usageBatch(machine, usage(now.minusSeconds(3600), 5, epoch(now) + 3600, 6, epoch(now) + 86_400)))
                    .andExpect(status().isOk());

            Map<String, Object> row = snapshotRow(machine);
            assertEquals(new BigDecimal("30.00"), row.get("five_hour_used_percentage"));
            assertEquals(new BigDecimal("40.00"), row.get("seven_day_used_percentage"));
        }

        @Test
        void captureTimeInTheFuture_isClampedToTheReceiveTime() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Instant now = Instant.now();

            send(machine.token(), usageBatch(machine, usage(now.plus(Duration.ofDays(3)), 30, epoch(now) + 3600, 40,
                    epoch(now) + 86_400))).andExpect(status().isOk());

            Map<String, Object> row = snapshotRow(machine);
            Instant captured = ((Timestamp) row.get("captured_at")).toInstant();
            Instant received = ((Timestamp) row.get("received_at")).toInstant();
            assertFalse(captured.isAfter(received), "captured " + captured + " received " + received);
            assertTrue(captured.isBefore(now.plus(Duration.ofMinutes(5))));
        }

        @Test
        void usageWithOnlyTheFiveHourWindow_storesNoSevenDayValue() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Instant now = Instant.now();
            Map<String, Object> usage = Map.of("capturedAt", now.toString(),
                    "fiveHour", Map.of("usedPercentage", 12, "resetsAt", epoch(now) + 3600));

            send(machine.token(), usageBatch(machine, usage)).andExpect(status().isOk());

            Map<String, Object> row = snapshotRow(machine);
            assertEquals(new BigDecimal("12.00"), row.get("five_hour_used_percentage"));
            assertEquals(null, row.get("seven_day_used_percentage"));
        }

        @Test
        void usageWithoutAnyWindow_storesNothing() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");

            send(machine.token(), usageBatch(machine, Map.of("capturedAt", Instant.now().toString())))
                    .andExpect(status().isOk());

            assertEquals(0L, count("SELECT count(*) FROM kaya.machine_usage_snapshots WHERE machine_id = ?::uuid", machine.machineId()));
        }

        @Test
        void snapshot_keepsTheSessionAndTheAccountItWasReportedWith() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            String session = sessionId();
            Instant now = Instant.now();
            Map<String, Object> batch = batch(machine, session, remote(), List.of());
            batch.put("usage", usage(now, 10, epoch(now) + 3600, 20, epoch(now) + 86_400));
            section(batch, "machine").put("account", account("ada.claude@" + tenant.domain()));

            send(machine.token(), batch).andExpect(status().isOk());

            Map<String, Object> row = snapshotRow(machine);
            assertEquals(session, row.get("session_id"));
            assertEquals(1L, count("SELECT count(*) FROM kaya.accounts WHERE id = ?::uuid AND account_email = ?",
                    row.get("account_id").toString(), "ada.claude@" + tenant.domain()));
        }
    }

    @Nested
    class OutOfRange {

        private void assertRefused(Machine machine, Map<String, Object> usage, String window, String field) throws Exception {
            send(machine.token(), usageBatch(machine, usage))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("KAY_VAL_009"))
                    .andExpect(jsonPath("$.error.errorValues.window").value(window))
                    .andExpect(jsonPath("$.error.errorValues." + field).exists());
            assertEquals(0L, count("SELECT count(*) FROM kaya.machine_usage_snapshots WHERE machine_id = ?::uuid", machine.machineId()));
        }

        @Test
        void fiveHourPercentageOver100_returns400() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Instant now = Instant.now();

            assertRefused(machine, usage(now, 100.01, epoch(now) + 3600, 10, epoch(now) + 3600), "fiveHour", "usedPercentage");
        }

        @Test
        void sevenDayNegativePercentage_returns400() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Instant now = Instant.now();

            assertRefused(machine, usage(now, 10, epoch(now) + 3600, -1, epoch(now) + 3600), "sevenDay", "usedPercentage");
        }

        @Test
        void resetTimeNineDaysAway_returns400() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Instant now = Instant.now();

            assertRefused(machine, usage(now, 10, epoch(now) + 9 * 86_400, 10, epoch(now) + 3600), "fiveHour", "resetsAt");
        }

        @Test
        void resetTimeNineDaysInThePast_returns400() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Instant now = Instant.now();

            assertRefused(machine, usage(now, 10, epoch(now) + 3600, 10, epoch(now) - 9 * 86_400), "sevenDay", "resetsAt");
        }

        @Test
        void bounds_zeroAndOneHundredPercentAndSevenDaysAhead_areAccepted() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Instant now = Instant.now();

            send(machine.token(), usageBatch(machine, usage(now, 0, epoch(now) + 3600, 100, epoch(now) + 7 * 86_400)))
                    .andExpect(status().isOk());
        }

        @Test
        void refusedUsage_rollsBackTheEventsOfTheSameBatch() throws Exception {
            Tenant tenant = newTenant();
            Machine machine = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            String session = sessionId();
            Instant now = Instant.now();
            Map<String, Object> batch = batch(machine, session, remote(), events(1, 3));
            batch.put("usage", usage(now, 250, epoch(now) + 3600, 10, epoch(now) + 3600));

            send(machine.token(), batch).andExpect(status().isBadRequest());

            assertEquals(0L, sessionCount(session));
            assertEquals(0L, eventCount(session));
        }
    }

    @Nested
    class UsageFor {

        @Test
        void machineThatNeverReported_hasNoAccount() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Machine machine = registerMachine(tenant, ada, "laptop");

            usageOf(tenant, ada, machine)
                    .andExpect(jsonPath("$.data.usage.state").value("NO_ACCOUNT"))
                    .andExpect(jsonPath("$.data.usage.fiveHour").doesNotExist())
                    .andExpect(jsonPath("$.data.usage.sevenDay").doesNotExist());
        }

        @Test
        void machineWithAnAccountButNoSnapshot_hasNoSnapshot() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Machine machine = registerMachine(tenant, ada, "laptop");
            Map<String, Object> batch = batch(machine, sessionId(), remote(), events(1, 1));
            section(batch, "machine").put("account", account("ada.claude@" + tenant.domain()));
            send(machine.token(), batch).andExpect(status().isOk());

            usageOf(tenant, ada, machine)
                    .andExpect(jsonPath("$.data.usage.state").value("NO_SNAPSHOT"))
                    .andExpect(jsonPath("$.data.usage.fiveHour").doesNotExist());
        }

        @Test
        void machineWithoutAnAccountButWithItsOwnSnapshot_isKnown() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Machine machine = registerMachine(tenant, ada, "laptop");
            Instant now = Instant.now();
            send(machine.token(), usageBatch(machine, usage(now, 23.5, epoch(now) + 3600, 41, epoch(now) + 86_400)))
                    .andExpect(status().isOk());

            usageOf(tenant, ada, machine)
                    .andExpect(jsonPath("$.data.usage.state").value("KNOWN"))
                    .andExpect(jsonPath("$.data.usage.fiveHour.usedPercentage").value(23.5))
                    .andExpect(jsonPath("$.data.usage.fiveHour.reset").value(false))
                    .andExpect(jsonPath("$.data.usage.sevenDay.usedPercentage").value(41.0))
                    .andExpect(jsonPath("$.data.usage.reportedByMachineName").value("laptop"));
        }

        @Test
        void twoMachinesOfOneAccount_bothShowTheNewestSnapshotAndListEachOtherAsSharing() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Machine laptop = registerMachine(tenant, ada, "laptop");
            Machine desktop = registerMachine(tenant, ada, "desktop");
            Instant now = Instant.now();
            String claude = "ada.claude@" + tenant.domain();
            for (Object[] report : new Object[][] {{laptop, now.minusSeconds(600), 20.0}, {desktop, now.minusSeconds(60), 50.0}}) {
                Machine machine = (Machine) report[0];
                Map<String, Object> batch = usageBatch(machine, usage((Instant) report[1], (Double) report[2],
                        epoch(now) + 3600, 60, epoch(now) + 86_400));
                section(batch, "machine").put("account", account(claude));
                send(machine.token(), batch).andExpect(status().isOk());
            }

            usageOf(tenant, ada, laptop)
                    .andExpect(jsonPath("$.data.usage.state").value("KNOWN"))
                    .andExpect(jsonPath("$.data.usage.fiveHour.usedPercentage").value(50.0))
                    .andExpect(jsonPath("$.data.usage.reportedByMachineName").value("desktop"))
                    .andExpect(jsonPath("$.data.usage.sharedWith.length()").value(1))
                    .andExpect(jsonPath("$.data.usage.sharedWith[0].machineName").value("desktop"));
            usageOf(tenant, ada, desktop)
                    .andExpect(jsonPath("$.data.usage.fiveHour.usedPercentage").value(50.0))
                    .andExpect(jsonPath("$.data.usage.sharedWith[0].machineName").value("laptop"));
        }

        @Test
        void accountSharedWithARetiredMachine_doesNotListIt() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Machine laptop = registerMachine(tenant, ada, "laptop");
            Machine desktop = registerMachine(tenant, ada, "desktop");
            Instant now = Instant.now();
            for (Machine machine : List.of(laptop, desktop)) {
                Map<String, Object> batch = usageBatch(machine, usage(now, 20, epoch(now) + 3600, 60, epoch(now) + 86_400));
                section(batch, "machine").put("account", account("ada.claude@" + tenant.domain()));
                send(machine.token(), batch).andExpect(status().isOk());
            }
            mockMvc.perform(in(tenant, patch("/kaya/machines/" + desktop.machineId()), ada.jwt(), "{\"status\":\"RETIRED\"}"))
                    .andExpect(status().isOk());

            usageOf(tenant, ada, laptop).andExpect(jsonPath("$.data.usage.sharedWith.length()").value(0));
        }

        @Test
        void windowWhoseResetTimePassed_isShownResetWithoutAPercentage() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Machine machine = registerMachine(tenant, ada, "laptop");
            Instant now = Instant.now();
            Instant captured = now.minus(Duration.ofHours(2));
            send(machine.token(), usageBatch(machine, usage(captured, 80, epoch(now) - 3600, 41, epoch(now) + 86_400)))
                    .andExpect(status().isOk());

            usageOf(tenant, ada, machine)
                    .andExpect(jsonPath("$.data.usage.state").value("KNOWN"))
                    .andExpect(jsonPath("$.data.usage.fiveHour.reset").value(true))
                    .andExpect(jsonPath("$.data.usage.fiveHour.usedPercentage").doesNotExist())
                    .andExpect(jsonPath("$.data.usage.sevenDay.reset").value(false))
                    .andExpect(jsonPath("$.data.usage.sevenDay.usedPercentage").value(41.0));
        }

        @Test
        void machineList_carriesTheUsageOfEveryMachine() throws Exception {
            Tenant tenant = newTenant();
            Person ada = signIn(tenant, "ada");
            Machine reporting = registerMachine(tenant, ada, "laptop");
            registerMachine(tenant, ada, "desktop");
            Instant now = Instant.now();
            send(reporting.token(), usageBatch(reporting, usage(now, 20, epoch(now) + 3600, 60, epoch(now) + 86_400)))
                    .andExpect(status().isOk());

            mockMvc.perform(in(tenant, get("/kaya/machines").param("userId", ada.userId()), ada.jwt(), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[?(@.machineName=='laptop')].usage.state").value("KNOWN"))
                    .andExpect(jsonPath("$.data[?(@.machineName=='desktop')].usage.state").value("NO_ACCOUNT"));
        }

        @Test
        void usageOfAnotherOrganization_isNeverShown() throws Exception {
            Tenant first = newTenant();
            Tenant second = newTenant();
            Person inFirst = signIn(first, "ada");
            Machine reporting = registerMachine(first, inFirst, "laptop");
            Person inSecond = signIn(second, "bo");
            Machine other = registerMachine(second, inSecond, "laptop");
            Instant now = Instant.now();
            send(reporting.token(), usageBatch(reporting, usage(now, 77, epoch(now) + 3600, 88, epoch(now) + 86_400)))
                    .andExpect(status().isOk());

            usageOf(second, inSecond, other).andExpect(jsonPath("$.data.usage.state").value("NO_ACCOUNT"));
        }
    }
}

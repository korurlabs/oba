package com.korur.oba.apps.kazan.cyberscore.controller.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.apps.kazan.cyberscore.dto.derived.CyberscoreCalculation;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreCheck;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreCheckRepository;
import com.korur.oba.apps.kazan.cyberscore.service.impl.CyberscoreCalculateServiceImpl;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * The cyberscore and check endpoints against the Flyway-migrated database: an admin's writes keep
 * the stored score equal to the formula over the committed checks, also when two admins change
 * outcomes at the same moment (the row lock orders them), a name taken by a concurrent create
 * answers 409 and never 500, a check of another organization reads as not found, and a member only
 * reads.
 */
class CyberscoreControllerIT extends AbstractTenantIT {

    private static final String CHECKS = "/kazan/cyberscore/checks";
    private static final String SCORE = "/kazan/cyberscore";

    @Autowired
    private CyberscoreCheckRepository checkRepository;

    private static String body(String name, int weight, String category) {
        return "{\"name\":\"%s\",\"expected\":\"expected\",\"weight\":%d,\"category\":%s}"
                .formatted(name, weight, category == null ? "null" : "\"" + category + "\"");
    }

    private String createCheck(Tenant tenant, User caller, String name, int weight, String category) throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post(CHECKS), jwtOf(caller), body(name, weight, category)))
                .andExpect(status().isCreated()).andReturn();
        return read(result, "$.data.id");
    }

    private MockHttpServletRequestBuilder setOutcome(Tenant tenant, User caller, String checkId, String outcome) {
        return in(tenant, put(CHECKS + "/" + checkId + "/outcome"), jwtOf(caller), "{\"outcome\":\"" + outcome + "\"}");
    }

    private CyberscoreCalculation formulaOverCommittedChecks(Tenant tenant) {
        List<CyberscoreCheck> checks = checkRepository.findByOrganizationIdOrderByCategoryAscSortOrderAscNameAsc(
                tenant.organization().getId());
        return new CyberscoreCalculateServiceImpl().calculate(checks);
    }

    private long snapshotCount(Tenant tenant) {
        return count("SELECT count(*) FROM kazan.cyberscore_snapshots WHERE organization_id = ?",
                tenant.organization().getId());
    }

    @Test
    void getCyberscore_organizationWithoutChecks_isUnknownAndWritesNoRow() throws Exception {
        Tenant tenant = newTenant();

        mockMvc.perform(in(tenant, get(SCORE), jwtOf(tenant.member().getUser()), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scoreLevel").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.scorePercentage").value(0))
                .andExpect(jsonPath("$.data.scorableCheckCount").value(0));

        assertEquals(0, count("SELECT count(*) FROM kazan.cyberscores WHERE organization_id = ?",
                tenant.organization().getId()));
    }

    @Test
    void adminFlow_createOutcomeWeightDelete_keepsScoreBreakdownAndTrendInStep() throws Exception {
        Tenant tenant = newTenant();
        User admin = tenant.admin().getUser();
        String first = createCheck(tenant, admin, "first", 10, "DEVICES");
        String second = createCheck(tenant, admin, "second", 30, "DEVICES");

        mockMvc.perform(in(tenant, get(SCORE), jwtOf(tenant.member().getUser()), null))
                .andExpect(jsonPath("$.data.scorePercentage").value(0))
                .andExpect(jsonPath("$.data.scoreLevel").value("CRITICAL"))
                .andExpect(jsonPath("$.data.scorableCheckCount").value(2));

        mockMvc.perform(setOutcome(tenant, admin, first, "PASS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));
        mockMvc.perform(setOutcome(tenant, admin, second, "PARTIAL")).andExpect(status().isOk());

        // (10 + 15) of 40 is 62.5 and rounds up to 63.
        mockMvc.perform(in(tenant, get(SCORE), jwtOf(tenant.member().getUser()), null))
                .andExpect(jsonPath("$.data.scorePercentage").value(63))
                .andExpect(jsonPath("$.data.scoreLevel").value("LOW"))
                .andExpect(jsonPath("$.data.breakdownByCategory.DEVICES").value(63));

        mockMvc.perform(in(tenant, put(CHECKS + "/" + first), jwtOf(admin), body("first", 30, "DEVICES")))
                .andExpect(status().isOk());
        // (30 + 15) of 60 is 75.
        mockMvc.perform(in(tenant, get(SCORE), jwtOf(admin), null))
                .andExpect(jsonPath("$.data.scorePercentage").value(75));

        mockMvc.perform(in(tenant, delete(CHECKS + "/" + second), jwtOf(admin), null)).andExpect(status().isOk());
        mockMvc.perform(in(tenant, get(SCORE), jwtOf(admin), null))
                .andExpect(jsonPath("$.data.scorePercentage").value(100))
                .andExpect(jsonPath("$.data.scorableCheckCount").value(1));

        mockMvc.perform(in(tenant, get(SCORE + "/trend?days=7"), jwtOf(admin), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].scorePercentage").value(100));
        assertEquals(formulaOverCommittedChecks(tenant).scorePercentage(), 100);
    }

    @Test
    void textOnlyAndStatusOnlyEdits_writeNoSnapshot() throws Exception {
        Tenant tenant = newTenant();
        User admin = tenant.admin().getUser();
        String id = createCheck(tenant, admin, "check", 10, "DEVICES");
        mockMvc.perform(setOutcome(tenant, admin, id, "PASS")).andExpect(status().isOk());
        long before = snapshotCount(tenant);

        mockMvc.perform(in(tenant, put(CHECKS + "/" + id), jwtOf(admin), body("renamed", 10, "DEVICES")))
                .andExpect(status().isOk());
        mockMvc.perform(in(tenant, put(CHECKS + "/" + id + "/status"), jwtOf(admin), "{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        mockMvc.perform(setOutcome(tenant, admin, id, "PASS")).andExpect(status().isOk());

        assertEquals(before, snapshotCount(tenant));
    }

    @Test
    void createCheck_nameTaken_answers409WithTheNameCode() throws Exception {
        Tenant tenant = newTenant();
        User admin = tenant.admin().getUser();
        createCheck(tenant, admin, "taken", 10, null);

        mockMvc.perform(in(tenant, post(CHECKS), jwtOf(admin), body("taken", 10, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAZ_STA_020"));
    }

    @Test
    void createCheck_sameNameInAnotherOrganization_isAllowed() throws Exception {
        Tenant tenantA = newTenant();
        Tenant tenantB = newTenant();

        createCheck(tenantA, tenantA.admin().getUser(), "shared name", 10, null);
        createCheck(tenantB, tenantB.admin().getUser(), "shared name", 10, null);
    }

    @Test
    void createCheck_invalidBody_answers422() throws Exception {
        Tenant tenant = newTenant();

        mockMvc.perform(in(tenant, post(CHECKS), jwtOf(tenant.admin().getUser()), body("n", 101, null)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void checkOfAnotherOrganization_readsAsNotFoundOnEveryWrite() throws Exception {
        Tenant tenantA = newTenant();
        Tenant tenantB = newTenant();
        String idOfA = createCheck(tenantA, tenantA.admin().getUser(), "private", 10, "DEVICES");
        User adminB = tenantB.admin().getUser();

        mockMvc.perform(in(tenantB, put(CHECKS + "/" + idOfA), jwtOf(adminB), body("x", 10, null)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("KAZ_NOT_020"));
        mockMvc.perform(setOutcome(tenantB, adminB, idOfA, "PASS")).andExpect(status().isNotFound());
        mockMvc.perform(in(tenantB, put(CHECKS + "/" + idOfA + "/status"), jwtOf(adminB), "{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(in(tenantB, delete(CHECKS + "/" + idOfA), jwtOf(adminB), null))
                .andExpect(status().isNotFound());
        mockMvc.perform(in(tenantB, get(CHECKS), jwtOf(adminB), null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));

        assertEquals(1, count("SELECT count(*) FROM kazan.cyberscore_checks WHERE id = ?::uuid", idOfA));
    }

    @Test
    void member_readsButCannotWrite() throws Exception {
        Tenant tenant = newTenant();
        String id = createCheck(tenant, tenant.admin().getUser(), "check", 10, null);
        User member = tenant.member().getUser();

        mockMvc.perform(in(tenant, get(CHECKS), jwtOf(member), null)).andExpect(status().isOk());
        mockMvc.perform(in(tenant, get(SCORE), jwtOf(member), null)).andExpect(status().isOk());
        mockMvc.perform(in(tenant, post(CHECKS), jwtOf(member), body("new", 10, null)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ORG_SEC_003"));
        mockMvc.perform(setOutcome(tenant, member, id, "PASS")).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, delete(CHECKS + "/" + id), jwtOf(member), null)).andExpect(status().isForbidden());
    }

    @Test
    void guest_canNeitherReadNorWrite() throws Exception {
        Tenant tenant = newTenant();
        User guest = createActiveMember(tenant.organization(), tenant.domain(), OrganizationMemberRole.GUEST).getUser();

        mockMvc.perform(in(tenant, get(SCORE), jwtOf(guest), null))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ORG_SEC_003"));
        mockMvc.perform(in(tenant, get(SCORE + "/trend"), jwtOf(guest), null))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ORG_SEC_003"));
        mockMvc.perform(in(tenant, get(CHECKS), jwtOf(guest), null))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ORG_SEC_003"));
        mockMvc.perform(in(tenant, post(CHECKS), jwtOf(guest), body("new", 10, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCyberscoreTrend_daysNotANumber_isRejected() throws Exception {
        Tenant tenant = newTenant();

        mockMvc.perform(in(tenant, get(SCORE + "/trend?days=week"), jwtOf(tenant.member().getUser()), null))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listChecks_unknownStatusFilter_isRejectedAsInvalid() throws Exception {
        Tenant tenant = newTenant();

        mockMvc.perform(in(tenant, get(CHECKS + "?status=NOPE"), jwtOf(tenant.admin().getUser()), null))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listChecks_statusFilter_returnsOnlyMatchingChecksInDisplayOrder() throws Exception {
        Tenant tenant = newTenant();
        User admin = tenant.admin().getUser();
        createCheck(tenant, admin, "no category", 1, null);
        String done = createCheck(tenant, admin, "done check", 1, "COMPLIANCE");
        createCheck(tenant, admin, "identity check", 1, "IDENTITY");
        mockMvc.perform(setOutcome(tenant, admin, done, "PASS")).andExpect(status().isOk());

        mockMvc.perform(in(tenant, get(CHECKS), jwtOf(admin), null))
                .andExpect(jsonPath("$.data[0].name").value("identity check"))
                .andExpect(jsonPath("$.data[1].name").value("done check"))
                .andExpect(jsonPath("$.data[2].name").value("no category"));
        mockMvc.perform(in(tenant, get(CHECKS + "?status=DONE"), jwtOf(admin), null))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("done check"));
    }

    @Test
    void concurrentOutcomeChanges_endWithARowEqualToTheFormula() throws Exception {
        Tenant tenant = newTenant();
        User admin = tenant.admin().getUser();
        String[] outcomes = {"PASS", "PARTIAL", "FAIL", "PASS", "NO_LICENSE", "PARTIAL"};
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < outcomes.length; index++) {
            ids.add(createCheck(tenant, admin, "check " + index, 7 + index * 11, index % 2 == 0 ? "DEVICES" : "COMPLIANCE"));
        }

        List<Callable<Integer>> writes = new ArrayList<>();
        for (int index = 0; index < outcomes.length; index++) {
            String id = ids.get(index);
            String outcome = outcomes[index];
            writes.add(() -> mockMvc.perform(setOutcome(tenant, admin, id, outcome)).andReturn().getResponse().getStatus());
        }

        for (int status : runTogether(writes)) {
            assertEquals(200, status);
        }

        CyberscoreCalculation expected = formulaOverCommittedChecks(tenant);
        mockMvc.perform(in(tenant, get(SCORE), jwtOf(admin), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scorePercentage").value(expected.scorePercentage()))
                .andExpect(jsonPath("$.data.scoreLevel").value(expected.scoreLevel().name()));
    }

    @Test
    void concurrentCreatesOfOneName_answerOneCreatedAndTheRest409() throws Exception {
        Tenant tenant = newTenant();
        User admin = tenant.admin().getUser();

        List<Callable<Integer>> writes = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            writes.add(() -> mockMvc.perform(in(tenant, post(CHECKS), jwtOf(admin), body("contested", 10, null)))
                    .andReturn().getResponse().getStatus());
        }

        List<Integer> statuses = runTogether(writes);

        assertEquals(1, statuses.stream().filter(status -> status == 201).count(), statuses.toString());
        assertEquals(5, statuses.stream().filter(status -> status == 409).count(), statuses.toString());
        assertEquals(1, count("SELECT count(*) FROM kazan.cyberscore_checks WHERE organization_id = ? AND name = 'contested'",
                tenant.organization().getId()));
        assertTrue(formulaOverCommittedChecks(tenant).scorable());
    }

    /** Starts every call at the same moment and returns the results in call order. */
    private static <T> List<T> runTogether(List<Callable<T>> calls) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(calls.size());
        CountDownLatch ready = new CountDownLatch(calls.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> call : calls) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call();
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS));
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}

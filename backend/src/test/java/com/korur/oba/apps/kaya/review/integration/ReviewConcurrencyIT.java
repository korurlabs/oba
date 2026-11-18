package com.korur.oba.apps.kaya.review.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// 4. Internal
import com.korur.oba.apps.kaya.release.service.ReleaseService;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reviews that run at the same time. Two approvals of different packs of one repository: the
 * release signed last must deliver both approved versions, or delivery would undo the approval
 * that committed first. An approval and a rejection of one version: exactly one decides it, and
 * the other is refused against the state the first committed. Each test holds a lock until both
 * reviews wait on it, so the two transactions overlap every time.
 */
class ReviewConcurrencyIT extends AbstractVersionIT {

    private static final String RULE = "---\npaths: \"src/**\"\n---\n";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private ReleaseService releaseService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Repository repository;
    private UUID first;
    private UUID second;

    @BeforeEach
    void setUp() throws Exception {
        initTenant();
        repository = saveRepository(tenant.organization());
        first = packWith("first-" + uniqueSuffix().substring(0, 8), ".agents/rules/first.md", RULE + "First\n");
        second = packWith("second-" + uniqueSuffix().substring(0, 8), ".agents/rules/second.md", RULE + "Second\n");
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(first, second))).andExpect(status().isOk());
    }

    /** A draft of the owner on the pack's current version, with one rule changed, submitted for review. */
    private UUID inReview(UUID pack, String path, String content) throws Exception {
        MvcResult created = mockMvc.perform(in(tenant, post("/kaya/drafts"), owner, draftBody(pack)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID draft = UUID.fromString(read(created, "$.data.id"));
        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner,
                        JSON.writeValueAsString(Map.of("path", path, "content", content))))
                .andExpect(status().isOk());
        mockMvc.perform(in(tenant, post("/kaya/changes/" + draft + "/submit"), owner,
                        "{\"reason\":\"Sharpen the rule of this pack\"}"))
                .andExpect(status().isOk());
        return draft;
    }

    private int approve(UUID version) throws Exception {
        return mockMvc.perform(in(tenant, post("/kaya/changes/" + version + "/reviews"), admin, "{\"decision\":\"APPROVE\"}"))
                .andReturn().getResponse().getStatus();
    }

    private int reject(UUID version) throws Exception {
        return mockMvc.perform(in(tenant, post("/kaya/changes/" + version + "/reviews"), admin,
                        "{\"decision\":\"REJECT\",\"comment\":\"Keep the old wording\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void review_approvalsOfTwoPacksOfOneRepositoryAtOnce_latestReleaseDeliversBoth() throws Exception {
        UUID firstChange = inReview(first, ".agents/rules/first.md", RULE + "First, sharper\n");
        UUID secondChange = inReview(second, ".agents/rules/second.md", RULE + "Second, sharper\n");

        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            CountDownLatch held = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Future<?> holder = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                releaseService.lockReleases(repository.getId());
                held.countDown();
                awaitOrFail(release);
            }));
            assertTrue(held.await(30, TimeUnit.SECONDS), "the test holds the release lock");
            Future<Integer> firstApproval = pool.submit(() -> approve(firstChange));
            Future<Integer> secondApproval = pool.submit(() -> approve(secondChange));
            awaitLockWaiters(2);
            release.countDown();
            holder.get(30, TimeUnit.SECONDS);

            assertEquals(200, firstApproval.get(60, TimeUnit.SECONDS));
            assertEquals(200, secondApproval.get(60, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(List.of(1, 2, 3), jdbcTemplate.queryForList(
                "SELECT number FROM kaya.releases WHERE repository_id = ? ORDER BY number", Integer.class, repository.getId()));
        List<UUID> delivered = jdbcTemplate.queryForList("""
                SELECT CAST(entry ->> 'versionId' AS uuid) FROM kaya.releases r,
                       jsonb_array_elements(r.manifest) AS entry
                WHERE r.repository_id = ? AND r.number = 3 ORDER BY entry ->> 'path'
                """, UUID.class, repository.getId());
        assertEquals(List.of(firstChange, secondChange), delivered, "the latest release delivers both approved versions");
    }

    @Test
    void review_approvalAndRejectionOfOneVersionAtOnce_onlyOneDecidesAndTheOtherIsRefused() throws Exception {
        UUID before = currentVersionOf(first);
        UUID change = inReview(first, ".agents/rules/first.md", RULE + "First, sharper\n");
        long releasesBefore = count("SELECT count(*) FROM kaya.releases WHERE repository_id = ?", repository.getId());

        ExecutorService pool = Executors.newFixedThreadPool(3);
        int approval;
        int rejection;
        try {
            CountDownLatch held = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Future<?> holder = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                jdbcTemplate.queryForObject("SELECT id FROM kaya.versions WHERE id = ? FOR UPDATE", UUID.class, change);
                held.countDown();
                awaitOrFail(release);
            }));
            assertTrue(held.await(30, TimeUnit.SECONDS), "the test holds the version row lock");
            Future<Integer> approveCall = pool.submit(() -> approve(change));
            Future<Integer> rejectCall = pool.submit(() -> reject(change));
            awaitLockWaiters(2);
            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            approval = approveCall.get(60, TimeUnit.SECONDS);
            rejection = rejectCall.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        String outcome = "approve answered " + approval + ", reject answered " + rejection;
        assertNotEquals(approval, rejection, "exactly one of the two reviews decides the version; " + outcome);
        assertEquals(List.of(200, 409), List.of(approval, rejection).stream().sorted().toList(),
                "the review that waited is refused against the committed decision; " + outcome);
        String status = jdbcTemplate.queryForObject("SELECT status FROM kaya.versions WHERE id = ?", String.class, change);
        long releasesAfter = count("SELECT count(*) FROM kaya.releases WHERE repository_id = ?", repository.getId());
        if (approval == 200) {
            assertEquals("APPROVED", status);
            assertEquals(change, currentVersionOf(first), "the approved version is the pack's current version");
            assertEquals(releasesBefore + 1, releasesAfter, "the approval released the repository once");
        } else {
            assertEquals("DRAFT", status);
            assertEquals(before, currentVersionOf(first), "a rejected version never becomes current");
            assertEquals(releasesBefore, releasesAfter, "a rejection releases nothing");
        }
    }

    private static void awaitOrFail(CountDownLatch latch) {
        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the lock");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Waits until the given number of sessions of this database wait for a lock. */
    private void awaitLockWaiters(int waiters) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (count("""
                SELECT count(*) FROM pg_stat_activity
                WHERE wait_event_type = 'Lock' AND datname = current_database()
                """) < waiters) {
            assertTrue(System.nanoTime() < deadline, "waiting for " + waiters + " lock waiters");
            Thread.sleep(20);
        }
    }
}

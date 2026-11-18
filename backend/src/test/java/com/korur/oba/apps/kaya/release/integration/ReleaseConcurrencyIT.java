package com.korur.oba.apps.kaya.release.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 2. Third-party

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
import com.korur.oba.apps.kaya.release.service.ReleaseService;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.version.repository.VersionRepository;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Releases of one repository are numbered 1, 2, 3 without gaps or repeats even when pack changes
 * and approvals arrive together. A repository without a release has no row to lock, so the numbering
 * is serialised by an advisory lock; these tests race real transactions to prove it.
 */
class ReleaseConcurrencyIT extends AbstractVersionIT {

    private static final int PACKS = 6;

    @Autowired
    private ReleaseService releaseService;
    @Autowired
    private VersionRepository versionRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private List<UUID> packs;

    @BeforeEach
    void setUp() throws Exception {
        initTenant();
        packs = new ArrayList<>();
        for (int index = 0; index < PACKS; index++) {
            packs.add(packWith("pack-" + index, ".agents/rules/rule-" + index + ".md", "---\npaths: \"src/**\"\n---\nRule " + index + "\n"));
        }
    }

    private int putStatus(Repository repository, UUID... ids) throws Exception {
        return mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(ids))).andReturn().getResponse().getStatus();
    }

    private List<Integer> numbers(Repository repository) {
        return jdbcTemplate.queryForList("SELECT number FROM kaya.releases WHERE repository_id = ? ORDER BY number",
                Integer.class, repository.getId());
    }

    private static List<Integer> upTo(int last) {
        List<Integer> expected = new ArrayList<>();
        for (int number = 1; number <= last; number++) {
            expected.add(number);
        }
        return expected;
    }

    private <T> List<T> race(List<Callable<T>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void replacePacks_differentListsAtOnce_numberTheReleasesWithoutGapsOrRepeats() throws Exception {
        Repository repository = saveRepository(tenant.organization());
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int index = 0; index < PACKS; index++) {
            UUID pack = packs.get(index);
            calls.add(() -> putStatus(repository, pack));
        }

        List<Integer> statuses = race(calls);

        assertTrue(statuses.stream().allMatch(status -> status == 200), "statuses " + statuses);
        assertEquals(upTo(PACKS), numbers(repository));
        UUID finalPack = jdbcTemplate.queryForObject("SELECT pack_id FROM kaya.repository_packs WHERE repository_id = ?", UUID.class,
                repository.getId());
        assertEquals(1, count("SELECT count(*) FROM kaya.repository_packs WHERE repository_id = ?", repository.getId()));
        assertEquals(1, count("SELECT jsonb_array_length(manifest) FROM kaya.releases WHERE repository_id = ? AND number = ?",
                repository.getId(), PACKS), "the newest release holds the final pack " + finalPack);
    }

    @Test
    void replacePacks_sameListAtOnce_createsExactlyOneRelease() throws Exception {
        Repository repository = saveRepository(tenant.organization());
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int index = 0; index < PACKS; index++) {
            calls.add(() -> putStatus(repository, packs.get(0), packs.get(1)));
        }

        List<Integer> statuses = race(calls);

        assertTrue(statuses.stream().allMatch(status -> status == 200), "statuses " + statuses);
        assertEquals(List.of(1), numbers(repository));
    }

    @Test
    void replacePacks_onSeveralRepositories_numbersEachRepositoryOnItsOwn() throws Exception {
        Repository first = saveRepository(tenant.organization());
        Repository second = saveRepository(tenant.organization());
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            UUID pack = packs.get(index);
            calls.add(() -> putStatus(first, pack));
            calls.add(() -> putStatus(second, pack, packs.get(5)));
        }

        List<Integer> statuses = race(calls);

        assertTrue(statuses.stream().allMatch(status -> status == 200), "statuses " + statuses);
        assertEquals(upTo(4), numbers(first));
        assertEquals(upTo(4), numbers(second));
    }

    @Test
    void releaseVersion_racingPackChangesOnComposingRepositories_neverDeadlocksOrRepeatsANumber() throws Exception {
        Repository first = saveRepository(tenant.organization());
        Repository second = saveRepository(tenant.organization());
        UUID shared = packs.get(0);
        assertEquals(200, putStatus(first, shared));
        assertEquals(200, putStatus(second, shared));
        UUID next = approvedVersion(shared, 2);
        insertFile(tenant.organization(), next, ".agents/rules/rule-0.md", "---\npaths: \"src/**\"\n---\nRule 0 v2\n");

        List<Callable<Object>> calls = new ArrayList<>();
        calls.add(() -> new TransactionTemplate(transactionManager).execute(transaction ->
                releaseService.releaseVersion(tenant.organization().getId(), versionRepository.findById(next).orElseThrow())));
        calls.add(() -> putStatus(second, shared, packs.get(1)));
        calls.add(() -> putStatus(first, shared, packs.get(2)));
        List<Object> results = race(calls);

        assertEquals(200, results.get(1));
        assertEquals(200, results.get(2));
        assertEquals(upTo(3), numbers(first));
        assertEquals(upTo(3), numbers(second));
    }
}

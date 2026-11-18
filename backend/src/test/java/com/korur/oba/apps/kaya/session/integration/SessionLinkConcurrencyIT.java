package com.korur.oba.apps.kaya.session.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractSessionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Two members adding the same manual link at the same moment. The session has one link per kind and
 * reference, so the second insert meets the unique constraint; the member must get the link or a
 * clean 409, never a 500.
 */
class SessionLinkConcurrencyIT extends AbstractSessionIT {

    private static final int ROUNDS = 8;
    private static final int PARALLEL_ADDS = 6;

    private int addLink(Tenant tenant, Person person, String session, String reference) throws Exception {
        return mockMvc.perform(in(tenant, post("/kaya/sessions/" + session + "/links"), person.jwt(),
                        "{\"kind\":\"KAZAN_TICKET\",\"reference\":\"" + reference + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void sameManualLinkAddedAtOnce_answersCreatedOrConflictNeverServerError() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Person cy = signIn(tenant, "cy");
        Machine laptop = registerMachine(tenant, ada, "laptop");
        String session = ingestSession(laptop, remote(), 2);
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_ADDS);
        List<Integer> statuses = new ArrayList<>();
        try {
            for (int round = 1; round <= ROUNDS; round++) {
                String reference = "RACE-" + round;
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> adds = new ArrayList<>();
                for (int add = 0; add < PARALLEL_ADDS; add++) {
                    Person person = add % 2 == 0 ? ada : cy;
                    Callable<Integer> call = () -> {
                        start.await();
                        return addLink(tenant, person, session, reference);
                    };
                    adds.add(pool.submit(call));
                }
                start.countDown();
                for (Future<Integer> add : adds) {
                    statuses.add(awaitStatus(add));
                }
                assertEquals(1L, count("""
                        SELECT count(*) FROM kaya.session_links l JOIN kaya.sessions c ON c.id = l.session_id
                        WHERE c.session_id = ? AND l.reference = ?
                        """, session, reference), "one link row for " + reference);
            }
        } finally {
            pool.shutdownNow();
        }

        assertTrue(statuses.stream().allMatch(status -> status == 201 || status == 409), "statuses " + statuses);
        assertTrue(statuses.contains(201), "at least one add succeeds: " + statuses);
    }

    private static int awaitStatus(Future<Integer> add) throws InterruptedException, ExecutionException, TimeoutException {
        return add.get(30, TimeUnit.SECONDS);
    }
}

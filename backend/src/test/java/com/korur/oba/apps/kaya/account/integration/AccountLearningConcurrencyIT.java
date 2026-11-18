package com.korur.oba.apps.kaya.account.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.apps.kaya.account.enums.AccountProvider;
import com.korur.oba.apps.kaya.account.service.AccountService;
import com.korur.oba.apps.kaya.support.AbstractIngestIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Several users learn the same new account at the same moment. The unique key of the account
 * lets exactly one create it; a loser either links to the committed account or is refused with the
 * concurrent-learning conflict, and never ends up with a duplicate account or a half-made link.
 */
class AccountLearningConcurrencyIT extends AbstractIngestIT {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int USERS = 6;

    @Autowired
    private AccountService accountService;

    @Test
    void learnAccount_sixUsersLearnTheSameNewAccountAtOnce_oneAccountAndEverySuccessLinked() throws Exception {
        Tenant tenant = newTenant();
        String email = "team@claude.example";
        List<Machine> machines = new ArrayList<>();
        for (int i = 0; i < USERS; i++) {
            machines.add(registerMachine(tenant, signIn(tenant, "p" + i), "box-" + i));
        }
        CyclicBarrier barrier = new CyclicBarrier(USERS);
        ExecutorService executor = Executors.newFixedThreadPool(USERS);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (Machine machine : machines) {
                Callable<Boolean> task = () -> {
                    barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return accountService.learnAccount(tenant.organization().getId(),
                            machine.owner().user().getId(), UUID.fromString(machine.machineId()),
                            AccountProvider.CLAUDE, email, null, "Acme", null, null, null) != null;
                };
                futures.add(executor.submit(task));
            }
            long linked = 0;
            for (Future<Boolean> future : futures) {
                try {
                    assertTrue(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                    linked++;
                } catch (ExecutionException e) {
                    ConflictException conflict = (ConflictException) e.getCause();
                    assertEquals("KAY_VAL_016", conflict.getErrorCode());
                }
            }

            assertEquals(1L, count("SELECT count(*) FROM kaya.accounts WHERE organization_id = ? AND account_email = ?",
                    tenant.organization().getId(), email));
            assertEquals(linked, count("""
                    SELECT count(*) FROM kaya.account_users l JOIN kaya.accounts a ON a.id = l.account_id
                    WHERE a.organization_id = ? AND a.account_email = ?
                    """, tenant.organization().getId(), email));
            assertTrue(linked >= 1, "at least the winner links the account");
        } finally {
            executor.shutdownNow();
        }
    }
}

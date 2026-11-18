package com.korur.oba.apps.kaya.account.integration;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractIngestIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What happens to an account and the rows that point at it when a user stops using it.
 */
class AccountLifecycleIT extends AbstractIngestIT {

    @Nested
    class Learned {

        @Test
        void twoUsersOnOneAccount_showOneAccountWithBothUsersAsClaude() throws Exception {
            Tenant tenant = newTenant();
            Machine ada = registerMachine(tenant, signIn(tenant, "ada"), "laptop");
            Machine bo = registerMachine(tenant, signIn(tenant, "bo"), "desktop");
            String email = "team@claude.example";

            send(ada.token(), withAccount(ada, email)).andExpect(status().isOk());
            send(bo.token(), withAccount(bo, email)).andExpect(status().isOk());

            mockMvc.perform(in(tenant, get("/kaya/accounts"), jwtOf(tenant.member().getUser()), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].provider").value("CLAUDE"))
                    .andExpect(jsonPath("$.data[0].users.length()").value(2))
                    .andExpect(jsonPath("$.data[0].machines").value(2))
                    .andExpect(jsonPath("$.data[0].activeMachines").value(2));
        }

        private Map<String, Object> withAccount(Machine machine, String email) {
            Map<String, Object> batch = batch(machine, sessionId(), remote(), events(1, 1));
            section(batch, "machine").put("account", account(email));
            return batch;
        }
    }

}

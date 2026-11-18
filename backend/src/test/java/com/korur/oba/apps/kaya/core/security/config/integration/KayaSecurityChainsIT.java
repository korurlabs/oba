package com.korur.oba.apps.kaya.core.security.config.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractIngestIT;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The three filter chains of the Oba API side by side: the Alertmanager webhook chain, the Kaya
 * machine-token chain on /kaya/ingest and /kaya/delivery, and the JWT chain for everything else.
 * A Keycloak JWT is refused on the machine-token paths with the machine code, and a machine token is
 * refused on every JWT path and on the webhook path.
 */
class KayaSecurityChainsIT extends AbstractIngestIT {

    private static final String MACHINE_REFUSED = "KAY_SEC_003";
    private static final String JWT_REFUSED = "GEN_SEC_002";

    private Machine newMachine() throws Exception {
        Tenant tenant = newTenant();
        return registerMachine(tenant, signIn(tenant, "ada"), "laptop");
    }

    private static void assertMachineChainRefusal(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(MACHINE_REFUSED));
    }

    @Test
    void ingest_machineToken_isAcceptedByTheMachineChain() throws Exception {
        Machine machine = newMachine();

        send(machine.token(), batch(machine, sessionId(), remote(), List.of(event(1, "SessionStart"))))
                .andExpect(status().isOk());
    }

    @Test
    void ingest_keycloakJwt_isRefusedWithTheMachineCode() throws Exception {
        Machine machine = newMachine();

        assertMachineChainRefusal(mockMvc.perform(post("/kaya/ingest/batches").with(machine.owner().jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(batch(machine, sessionId(), remote(), List.of(event(1, "SessionStart")))))));
    }

    @Test
    void delivery_keycloakJwt_isRefusedWithTheMachineCode() throws Exception {
        Machine machine = newMachine();

        assertMachineChainRefusal(mockMvc.perform(get("/kaya/delivery/current").with(machine.owner().jwt())));
    }

    @Test
    void delivery_withoutCredentials_isRefusedWithTheMachineCode() throws Exception {
        assertMachineChainRefusal(mockMvc.perform(get("/kaya/delivery/current")));
    }

    @Test
    void jwtPath_machineToken_isRefusedByTheJwtChain() throws Exception {
        Machine machine = newMachine();

        mockMvc.perform(get("/kaya/sessions").header("Authorization", "Bearer " + machine.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(JWT_REFUSED));
    }

    @Test
    void jwtPathOutsideKaya_machineToken_isRefusedByTheJwtChain() throws Exception {
        Machine machine = newMachine();

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + machine.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(JWT_REFUSED));
    }

    @Test
    void machineRegistration_machineToken_isRefusedByTheJwtChain() throws Exception {
        Machine machine = newMachine();

        mockMvc.perform(post("/kaya/machines").header("Authorization", "Bearer " + machine.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"machineName\":\"other\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(JWT_REFUSED));
    }

    @Test
    void alertmanagerWebhook_machineToken_isRefusedByTheWebhookChain() throws Exception {
        Machine machine = newMachine();

        String body = mockMvc.perform(post("/kaizen/integrations/alertmanager")
                        .header("Authorization", "Bearer " + machine.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertNotEquals(true, body.contains(MACHINE_REFUSED), "the webhook chain must not answer with the machine code");
    }

    @Test
    void alertmanagerWebhook_keycloakJwt_isRefusedByTheWebhookChain() throws Exception {
        mockMvc.perform(post("/kaizen/integrations/alertmanager").header("Authorization", "Bearer aaa.bbb.ccc")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}

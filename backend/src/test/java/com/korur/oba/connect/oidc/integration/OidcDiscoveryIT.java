package com.korur.oba.connect.oidc.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

// 4. Internal
import com.korur.oba.support.ObaIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two issuer documents on a deployment that has no issuer: both stay anonymous, and both
 * answer 404 with the JSON envelope and OID_NOT_001, never a 401 or an empty document.
 */
class OidcDiscoveryIT extends ObaIntegrationTest {

    @Test
    void discovery_noIssuer_anonymousAnswers404WithCode() throws Exception {
        MvcResult result = mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isNotFound()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("OID_NOT_001");
    }

    @Test
    void jwks_noIssuer_anonymousAnswers404WithCode() throws Exception {
        MvcResult result = mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isNotFound()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("OID_NOT_001");
    }
}

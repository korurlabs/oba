package com.korur.oba.shrine.azureconnection.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two issuer documents of a deployment with an issuer, read anonymously through the real
 * security chain: raw JSON, not the envelope, with the cache header and no private key material.
 */
class AzureIssuerDiscoveryIT extends AbstractAzureConnectionIT {

    @Test
    void discovery_anonymous_answersRawDocumentWithCacheHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk()).andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(body, "$.issuer")).isEqualTo(ISSUER_URL);
        assertThat(JsonPath.<String>read(body, "$.jwks_uri")).isEqualTo(ISSUER_URL + "/.well-known/jwks.json");
        assertThat(JsonPath.<String>read(body, "$.id_token_signing_alg_values_supported[0]")).isEqualTo("RS256");
        assertThat(body).doesNotContain("\"data\"");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("max-age=3600").contains("public");
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void jwks_anonymous_answersPublicKeyOnlyWithCacheHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk()).andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(body, "$.keys[0].kid")).isEqualTo(SIGNING_KEY_ID);
        assertThat(JsonPath.<String>read(body, "$.keys[0].kty")).isEqualTo("RSA");
        assertThat(JsonPath.<String>read(body, "$.keys[0].alg")).isEqualTo("RS256");
        assertThat(body).doesNotContain("\"d\"").doesNotContain("\"p\"").doesNotContain("\"q\"");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("max-age=3600");
    }
}

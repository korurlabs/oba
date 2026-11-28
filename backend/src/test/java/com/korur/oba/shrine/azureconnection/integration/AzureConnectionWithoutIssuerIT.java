package com.korur.oba.shrine.azureconnection.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A deployment without an OpenID Connect issuer cannot create a connection, and what exists stays
 * readable, with no issuer in the setup values.
 */
class AzureConnectionWithoutIssuerIT extends AbstractTenantIT {

    @Test
    void create_noIssuerConfigured_answers404AndStoresNothing() throws Exception {
        Tenant tenant = newTenant();

        MvcResult result = mockMvc.perform(in(tenant, post("/azure/connections"), jwtOf(tenant.admin().getUser()),
                        "{\"name\":\"Production\",\"tenantId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("AZR_NOT_002");
        assertThat(count("SELECT count(*) FROM shrine.azure_connections WHERE organization_id = ?",
                tenant.organization().getId())).isZero();
    }

    @Test
    void list_noIssuerConfigured_answers200WithEmptyList() throws Exception {
        Tenant tenant = newTenant();

        MvcResult result = mockMvc.perform(in(tenant, get("/azure/connections"), jwtOf(tenant.admin().getUser()), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"data\":[]");
    }
}

package com.korur.oba.shrine.azureconnection.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.dto.AzureSubscriptionPayload;
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.helper.AzureResourceManagerErrorTranslator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the verify endpoint end to end against the migrated schema: the connection is loaded
 * with its tenant and client id, the stubbed Resource Manager answer is stored as the check
 * result, and a refused token exchange is stored as an error code and never as Azure's message.
 */
class AzureConnectionVerifyIT extends AbstractAzureConnectionIT {

    private Tenant tenant;
    private RequestPostProcessor admin;
    private UUID tenantId;
    private UUID clientId;
    private String connectionId;

    @BeforeEach
    void setUpConnection() throws Exception {
        tenant = newTenant();
        admin = jwtOf(tenant.admin().getUser());
        tenantId = UUID.randomUUID();
        clientId = UUID.randomUUID();
        connectionId = createConnection(tenant, admin, "Verify " + uniqueSuffix(), tenantId, clientId);
    }

    private MvcResult verify(String id) throws Exception {
        return mockMvc.perform(in(tenant, post(CONNECTIONS_PATH + "/" + id + "/verify"), admin, null))
                .andExpect(status().isOk()).andReturn();
    }

    @Test
    void verify_subscriptionsReadable_connectsAndStoresThem() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(azureResourceManagerClient.listSubscriptions(any(AzureConnection.class))).thenReturn(List.of(
                new AzureSubscriptionPayload(second, "Zeta", "Enabled"),
                new AzureSubscriptionPayload(first, "Alpha", "Enabled")));

        MvcResult result = verify(connectionId);

        assertThat(read(result, "$.data.status")).isEqualTo("CONNECTED");
        assertThat(read(result, "$.data.lastError")).isEqualTo("null");
        assertThat(read(result, "$.data.lastVerifiedAt")).isNotEqualTo("null");
        assertThat(read(result, "$.data.subscriptions[0].subscriptionId")).isEqualTo(first.toString());
        assertThat(read(result, "$.data.subscriptions[1].displayName")).isEqualTo("Zeta");
        assertThat(count("SELECT count(*) FROM shrine.azure_connection_subscriptions WHERE connection_id = ?::uuid",
                connectionId)).isEqualTo(2L);
    }

    @Test
    void verify_clientCalledWithTheTenantAndClientIdOfTheRow() throws Exception {
        when(azureResourceManagerClient.listSubscriptions(any(AzureConnection.class))).thenAnswer(invocation -> {
            AzureConnection connection = invocation.getArgument(0);
            assertThat(connection.getTenantId()).isEqualTo(tenantId);
            assertThat(connection.getClientId()).isEqualTo(clientId);
            assertThat(connection.getSubject()).isEqualTo("oba:organization:" + tenant.id() + ":connection:" + connectionId);
            return List.of(new AzureSubscriptionPayload(UUID.randomUUID(), "Only", "Enabled"));
        });

        MvcResult result = verify(connectionId);

        assertThat(read(result, "$.data.status")).isEqualTo("CONNECTED");
    }

    @Test
    void verify_secondCheckReplacesTheSubscriptions() throws Exception {
        UUID old = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        when(azureResourceManagerClient.listSubscriptions(any(AzureConnection.class)))
                .thenReturn(List.of(new AzureSubscriptionPayload(old, "Old", "Enabled")))
                .thenReturn(List.of(new AzureSubscriptionPayload(current, "Current", "Enabled")));

        verify(connectionId);
        MvcResult result = verify(connectionId);

        assertThat(read(result, "$.data.subscriptions[0].subscriptionId")).isEqualTo(current.toString());
        assertThat(count("SELECT count(*) FROM shrine.azure_connection_subscriptions WHERE connection_id = ?::uuid",
                connectionId)).isEqualTo(1L);
    }

    @Test
    void verify_noSubscriptionReadable_keepsPendingWithNoSubscriptionError() throws Exception {
        when(azureResourceManagerClient.listSubscriptions(any(AzureConnection.class))).thenReturn(List.of());

        MvcResult result = verify(connectionId);

        assertThat(read(result, "$.data.status")).isEqualTo("PENDING");
        assertThat(read(result, "$.data.lastError")).isEqualTo("AZR_STA_005");
    }

    @Test
    void verify_connectedThenNoSubscriptionReadable_becomesFailing() throws Exception {
        when(azureResourceManagerClient.listSubscriptions(any(AzureConnection.class)))
                .thenReturn(List.of(new AzureSubscriptionPayload(UUID.randomUUID(), "Alpha", "Enabled")))
                .thenReturn(List.of());

        verify(connectionId);
        MvcResult result = verify(connectionId);

        assertThat(read(result, "$.data.status")).isEqualTo("FAILING");
        assertThat(read(result, "$.data.lastError")).isEqualTo("AZR_STA_005");
    }

    @Test
    void verify_tokenExchangeRefusedWithAadstsCode_storesCodeWithoutAzureMessage() throws Exception {
        when(azureResourceManagerClient.listSubscriptions(any(AzureConnection.class))).thenThrow(
                AzureResourceManagerErrorTranslator.fromTokenExchange(new RuntimeException(
                        "AADSTS700213: No matching federated identity record found for presented assertion subject "
                                + "'secret-subject'")));

        MvcResult result = verify(connectionId);

        assertThat(read(result, "$.data.lastError")).isEqualTo("AZR_UPS_009:AADSTS700213");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("No matching federated identity");
        assertThat(count("SELECT count(*) FROM shrine.azure_connections WHERE id = ?::uuid AND last_error = ?",
                connectionId, "AZR_UPS_009:AADSTS700213")).isEqualTo(1L);
    }

    @Test
    void verify_azureUnreachable_storesUnreachableCode() throws Exception {
        when(azureResourceManagerClient.listSubscriptions(any(AzureConnection.class)))
                .thenThrow(AzureResourceManagerErrorTranslator.unreachable());

        MvcResult result = verify(connectionId);

        assertThat(read(result, "$.data.lastError")).isEqualTo("AZR_UPS_005");
    }

    @Test
    void verify_withoutClientId_answers422AndReadsNoAzure() throws Exception {
        String withoutClient = createConnection(tenant, admin, "No client " + uniqueSuffix(), UUID.randomUUID(), null);

        MvcResult result = mockMvc.perform(in(tenant, post(CONNECTIONS_PATH + "/" + withoutClient + "/verify"), admin, null))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("AZR_VAL_001");
        verifyNoInteractions(azureResourceManagerClient);
    }

    @Test
    void verify_unknownConnection_answers404() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant,
                        post(CONNECTIONS_PATH + "/" + UUID.randomUUID() + "/verify"), admin, null))
                .andExpect(status().isNotFound()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("AZR_NOT_001");
    }
}

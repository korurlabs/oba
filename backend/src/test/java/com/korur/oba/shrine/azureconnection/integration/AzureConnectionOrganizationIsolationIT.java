package com.korur.oba.shrine.azureconnection.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against the migrated PostgreSQL schema and the real security chain, that the connections
 * of an organization and the subscriptions listed under them are closed to every other
 * organization: an ADMIN who sits in two organizations reaches a connection only with the header
 * of the organization that owns it, and every other header answers 404 without its data.
 */
class AzureConnectionOrganizationIsolationIT extends AbstractAzureConnectionIT {

    private static final String NOT_FOUND_CODE = "AZR_NOT_001";

    private Tenant tenantA;
    private Tenant tenantB;
    private User user;
    private RequestPostProcessor jwt;
    private String connectionName;
    private String connectionId;
    private UUID subscriptionId;

    @BeforeEach
    void setUpTenantsAndConnectionOfA() throws Exception {
        tenantA = newTenant();
        tenantB = newTenant();
        user = tenantA.admin().getUser();
        jwt = jwtOf(user);
        // ADMIN in both, so the role never explains a refusal and only the organization does.
        createMember(tenantB.organization(), user, OrganizationMemberRole.ADMIN, OrganizationMemberStatus.ACTIVE);

        connectionName = "Isolated " + uniqueSuffix();
        connectionId = createConnection(tenantA, jwt, connectionName, UUID.randomUUID(), UUID.randomUUID());
        subscriptionId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO shrine.azure_connection_subscriptions "
                + "(subscription_id, display_name, state, connection_id) VALUES (?, 'Secret subscription', 'Enabled', ?::uuid)",
                subscriptionId, connectionId);
    }

    private void assertAnswersNotFound(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isNotFound()).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains(NOT_FOUND_CODE)
                .doesNotContain(connectionName)
                .doesNotContain(subscriptionId.toString());
    }

    @Test
    void getWithHeaderOfAnotherOrganization_answers404WithoutData() throws Exception {
        assertAnswersNotFound(in(tenantB, get(CONNECTIONS_PATH + "/" + connectionId), jwt, null));
    }

    @Test
    void putWithHeaderOfAnotherOrganization_answers404AndChangesNothing() throws Exception {
        assertAnswersNotFound(in(tenantB, put(CONNECTIONS_PATH + "/" + connectionId), jwt,
                connectionBody("Hijacked", UUID.randomUUID(), UUID.randomUUID())));

        assertThat(count("SELECT count(*) FROM shrine.azure_connections WHERE id = ?::uuid AND name = ?",
                connectionId, connectionName)).isEqualTo(1L);
    }

    @Test
    void deleteWithHeaderOfAnotherOrganization_answers404AndDeletesNothing() throws Exception {
        assertAnswersNotFound(in(tenantB, delete(CONNECTIONS_PATH + "/" + connectionId), jwt, null));

        assertThat(count("SELECT count(*) FROM shrine.azure_connections WHERE id = ?::uuid", connectionId))
                .isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM shrine.azure_connection_subscriptions WHERE connection_id = ?::uuid",
                connectionId)).isEqualTo(1L);
    }

    @Test
    void verifyWithHeaderOfAnotherOrganization_answers404AndReadsNoAzure() throws Exception {
        assertAnswersNotFound(in(tenantB, post(CONNECTIONS_PATH + "/" + connectionId + "/verify"), jwt, null));

        verifyNoInteractions(azureResourceManagerClient);
    }

    @Test
    void listWithHeaderOfAnotherOrganization_showsNothingOfTheConnectionOfA() throws Exception {
        MvcResult other = mockMvc.perform(in(tenantB, get(CONNECTIONS_PATH), jwt, null))
                .andExpect(status().isOk()).andReturn();
        MvcResult own = mockMvc.perform(in(tenantA, get(CONNECTIONS_PATH), jwt, null))
                .andExpect(status().isOk()).andReturn();

        assertThat(other.getResponse().getContentAsString())
                .doesNotContain(connectionId).doesNotContain(connectionName).doesNotContain(subscriptionId.toString());
        assertThat(own.getResponse().getContentAsString())
                .contains(connectionId).contains(connectionName).contains(subscriptionId.toString());
    }

    @Test
    void getWithHeaderOfTheOwningOrganization_sameUser_answers200WithData() throws Exception {
        MvcResult result = mockMvc.perform(in(tenantA, get(CONNECTIONS_PATH + "/" + connectionId), jwt, null))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(result, "$.data.name")).isEqualTo(connectionName);
        assertThat(read(result, "$.data.subscriptions[0].subscriptionId")).isEqualTo(subscriptionId.toString());
    }

    @Test
    void createWithSameNameAndIdentityInAnotherOrganization_succeeds() throws Exception {
        String id = createConnection(tenantB, jwt, connectionName, UUID.randomUUID(), UUID.randomUUID());

        assertThat(id).isNotEqualTo(connectionId);
        assertThat(count("SELECT count(*) FROM shrine.azure_connections WHERE id = ?::uuid AND organization_id = ?",
                id, tenantB.organization().getId())).isEqualTo(1L);
    }
}

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
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the access matrix of the six connection endpoints through the real security chain: a
 * GUEST and a MEMBER are refused with ORG_SEC_003 on every endpoint and change nothing, an ADMIN
 * and an OWNER read and write, and a caller outside the organization is refused with ORG_SEC_001.
 */
class AzureConnectionAccessIT extends AbstractAzureConnectionIT {

    private static final String INSUFFICIENT_ROLE_CODE = "ORG_SEC_003";
    private static final String NOT_A_MEMBER_CODE = "ORG_SEC_001";

    private Tenant tenant;
    private RequestPostProcessor guest;
    private RequestPostProcessor member;
    private RequestPostProcessor admin;
    private RequestPostProcessor owner;
    private String connectionId;
    private String connectionName;

    @BeforeEach
    void setUpTenantWithOneConnection() throws Exception {
        tenant = newTenant();
        OrganizationMember guestMember = createActiveMember(tenant.organization(), tenant.domain(),
                OrganizationMemberRole.GUEST);
        guest = jwtOf(guestMember.getUser());
        member = jwtOf(tenant.member().getUser());
        admin = jwtOf(tenant.admin().getUser());
        owner = jwtOf(tenant.owner().getUser());
        connectionName = "Access " + uniqueSuffix();
        connectionId = createConnection(tenant, admin, connectionName, UUID.randomUUID(), UUID.randomUUID());
    }

    private MockHttpServletRequestBuilder[] allEndpointsAs(RequestPostProcessor jwt) {
        String body = connectionBody("Other " + uniqueSuffix(), UUID.randomUUID(), UUID.randomUUID());
        String path = CONNECTIONS_PATH + "/" + connectionId;
        return new MockHttpServletRequestBuilder[] {
                in(tenant, get(CONNECTIONS_PATH), jwt, null),
                in(tenant, get(path), jwt, null),
                in(tenant, post(CONNECTIONS_PATH), jwt, body),
                in(tenant, put(path), jwt, body),
                in(tenant, delete(path), jwt, null),
                in(tenant, post(path + "/verify"), jwt, null)
        };
    }

    private void assertEveryEndpointRefusedWith(RequestPostProcessor jwt, String code) throws Exception {
        for (MockHttpServletRequestBuilder request : allEndpointsAs(jwt)) {
            MvcResult result = mockMvc.perform(request).andExpect(status().isForbidden()).andReturn();
            assertThat(result.getResponse().getContentAsString()).contains(code);
        }
    }

    @Test
    void everyEndpoint_asGuest_answers403WithInsufficientRole() throws Exception {
        assertEveryEndpointRefusedWith(guest, INSUFFICIENT_ROLE_CODE);
    }

    @Test
    void everyEndpoint_asMember_answers403WithInsufficientRole() throws Exception {
        assertEveryEndpointRefusedWith(member, INSUFFICIENT_ROLE_CODE);
    }

    @Test
    void everyEndpoint_asUserOutsideTheOrganization_answers403WithNotAMember() throws Exception {
        assertEveryEndpointRefusedWith(jwtOf(createUser()), NOT_A_MEMBER_CODE);
    }

    @Test
    void writesAsMember_changeNothing() throws Exception {
        for (MockHttpServletRequestBuilder request : allEndpointsAs(member)) {
            mockMvc.perform(request).andExpect(status().isForbidden());
        }

        assertThat(count("SELECT count(*) FROM shrine.azure_connections WHERE organization_id = ?",
                tenant.organization().getId())).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM shrine.azure_connections WHERE id = ?::uuid AND name = ?",
                connectionId, connectionName)).isEqualTo(1L);
    }

    @Test
    void list_asAdmin_returnsTheConnection() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, get(CONNECTIONS_PATH), admin, null))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(result, "$.data[0].id")).isEqualTo(connectionId);
        assertThat(read(result, "$.data[0].status")).isEqualTo("PENDING");
    }

    @Test
    void list_asOwner_returnsTheConnection() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, get(CONNECTIONS_PATH), owner, null))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(result, "$.data[0].name")).isEqualTo(connectionName);
    }

    @Test
    void create_asAdmin_returns201WithSubjectOfOrganizationAndConnection() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post(CONNECTIONS_PATH), admin,
                        connectionBody("Created " + uniqueSuffix(), UUID.randomUUID(), null)))
                .andExpect(status().isCreated()).andReturn();

        String id = read(result, "$.data.id");
        assertThat(read(result, "$.data.setup.subject"))
                .isEqualTo("oba:organization:" + tenant.id() + ":connection:" + id);
        assertThat(read(result, "$.data.setup.issuer")).isEqualTo(ISSUER_URL);
        assertThat(read(result, "$.data.setup.audience")).isEqualTo("api://AzureADTokenExchange");
        assertThat(count("SELECT count(*) FROM shrine.azure_connections WHERE id = ?::uuid AND created_by_id = ?",
                id, tenant.admin().getUser().getId())).isEqualTo(1L);
    }

    @Test
    void update_asOwner_renamesTheConnection() throws Exception {
        String renamed = "Renamed " + uniqueSuffix();

        mockMvc.perform(in(tenant, put(CONNECTIONS_PATH + "/" + connectionId), owner,
                        connectionBody(renamed, UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM shrine.azure_connections WHERE id = ?::uuid AND name = ?",
                connectionId, renamed)).isEqualTo(1L);
    }

    @Test
    void delete_asAdmin_removesTheConnection() throws Exception {
        mockMvc.perform(in(tenant, delete(CONNECTIONS_PATH + "/" + connectionId), admin, null))
                .andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM shrine.azure_connections WHERE id = ?::uuid", connectionId)).isZero();
    }

    @Test
    void create_duplicateName_answers409() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post(CONNECTIONS_PATH), admin,
                        connectionBody(connectionName, UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isConflict()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("AZR_STA_004");
    }

    @Test
    void create_blankName_answers422() throws Exception {
        mockMvc.perform(in(tenant, post(CONNECTIONS_PATH), admin,
                        connectionBody("", UUID.randomUUID(), null)))
                .andExpect(status().isUnprocessableEntity());
    }
}

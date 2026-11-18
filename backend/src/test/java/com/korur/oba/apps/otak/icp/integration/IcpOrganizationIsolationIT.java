package com.korur.oba.apps.otak.icp.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against the migrated PostgreSQL schema and the real security chain, that the ICPs of
 * an organization are closed to every other organization: a user who sits in two organizations
 * reaches an ICP only with the header of the organization that owns it; every other header
 * answers 404 without its data and changes nothing.
 */
class IcpOrganizationIsolationIT extends AbstractTenantIT {

    private static final String ICP_NOT_FOUND_CODE = "OTK_NOT_004";
    private static final String ICPS_PATH = "/otak/icps";

    private Tenant tenantA;
    private Tenant tenantB;
    private Person user;
    private String icpName;
    private String icpId;

    @BeforeEach
    void setUpTenantsAndIcpOfA() throws Exception {
        tenantA = newTenant();
        tenantB = newTenant();
        user = signIn(tenantA, "otakicp");
        // ADMIN in B, so even the ADMIN-only delete reaches the lookup with the header of B.
        createMember(tenantB.organization(), user.user(), OrganizationMemberRole.ADMIN, OrganizationMemberStatus.ACTIVE);

        icpName = "Icp of A " + uniqueSuffix();
        icpId = read(mockMvc.perform(in(tenantA, post(ICPS_PATH), user.jwt(), icpBody(icpName)))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private static String icpBody(String name) {
        return "{\"name\":\"" + name + "\",\"industry\":\"SOFTWARE\"}";
    }

    private void assertAnswersNotFound(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isNotFound()).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains(ICP_NOT_FOUND_CODE)
                .doesNotContain(icpName);
    }

    @Test
    void getWithHeaderOfAnotherOrganization_answers404WithoutData() throws Exception {
        assertAnswersNotFound(in(tenantB, get(ICPS_PATH + "/" + icpId), user.jwt(), null));
    }

    @Test
    void putWithHeaderOfAnotherOrganization_answers404AndChangesNothing() throws Exception {
        assertAnswersNotFound(in(tenantB, put(ICPS_PATH + "/" + icpId), user.jwt(), icpBody("Hijacked")));

        assertThat(count("SELECT count(*) FROM otak.icps WHERE id = ?::uuid AND name = ?", icpId, icpName))
                .isEqualTo(1L);
    }

    @Test
    void deleteWithHeaderOfAnotherOrganization_answers404AndDeletesNothing() throws Exception {
        assertAnswersNotFound(in(tenantB, delete(ICPS_PATH + "/" + icpId), user.jwt(), null));

        assertThat(count("SELECT count(*) FROM otak.icps WHERE id = ?::uuid", icpId)).isEqualTo(1L);
    }

    @Test
    void listWithHeaderOfAnotherOrganization_showsNothingOfTheIcpOfA() throws Exception {
        MvcResult other = mockMvc.perform(in(tenantB, get(ICPS_PATH), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();
        MvcResult own = mockMvc.perform(in(tenantA, get(ICPS_PATH), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(other.getResponse().getContentAsString()).doesNotContain(icpId).doesNotContain(icpName);
        assertThat(own.getResponse().getContentAsString()).contains(icpId).contains(icpName);
    }

    @Test
    void relationCreateWithIcpOfAnotherOrganization_answers404AndStoresNothing() throws Exception {
        long before = count("SELECT count(*) FROM otak.relations WHERE organization_id = ?",
                tenantB.organization().getId());

        assertAnswersNotFound(in(tenantB, post("/otak/relations"), user.jwt(),
                "{\"name\":\"Linked\",\"type\":\"LEAD\",\"icpId\":\"" + icpId + "\"}"));

        assertThat(count("SELECT count(*) FROM otak.relations WHERE organization_id = ?",
                tenantB.organization().getId())).isEqualTo(before);
    }

    @Test
    void getWithHeaderOfTheOwningOrganization_sameUser_answers200WithData() throws Exception {
        MvcResult result = mockMvc.perform(in(tenantA, get(ICPS_PATH + "/" + icpId), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(result, "$.data.name")).isEqualTo(icpName);
    }
}

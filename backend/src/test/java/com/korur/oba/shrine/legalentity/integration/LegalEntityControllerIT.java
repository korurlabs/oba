package com.korur.oba.shrine.legalentity.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Proves, against the migrated PostgreSQL schema and the real security chain, that every
 * member reads the legal entities of the organization, that only admins and owners write them,
 * that the short name is unique per organization with case ignored, and that a legal entity of
 * another organization is not found.
 */
class LegalEntityControllerIT extends AbstractTenantIT {

    private static final String PATH = "/legal-entities";
    private static final String NOT_FOUND_CODE = "LGE_NOT_001";
    private static final String SHORT_NAME_TAKEN_CODE = "LGE_STA_001";

    private Tenant tenant;
    private RequestPostProcessor owner;
    private RequestPostProcessor admin;
    private RequestPostProcessor member;
    private RequestPostProcessor guest;
    private String shortName;
    private String legalEntityId;

    @BeforeEach
    void setUpTenantAndLegalEntity() throws Exception {
        tenant = newTenant();
        owner = jwtOf(tenant.owner().getUser());
        admin = jwtOf(tenant.admin().getUser());
        member = jwtOf(tenant.member().getUser());
        guest = jwtOf(createActiveMember(tenant.organization(), tenant.domain(), OrganizationMemberRole.GUEST)
                .getUser());
        shortName = "Korur B.V. " + uniqueSuffix().substring(0, 6);
        legalEntityId = create(tenant, admin, shortName);
    }

    private static String body(String shortName) {
        return "{\"shortName\":\"" + shortName + "\",\"legalName\":\"Korur B.V.\",\"country\":\"NLD\","
                + "\"vatNumber\":\"NL123456789B01\",\"city\":\"Amsterdam\",\"governingLaw\":\"Dutch law\"}";
    }

    private String create(Tenant target, RequestPostProcessor jwt, String name) throws Exception {
        return read(mockMvc.perform(in(target, post(PATH), jwt, body(name)))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private long legalEntitiesOf(Tenant target) {
        return count("SELECT count(*) FROM shrine.legal_entities WHERE organization_id = ?",
                target.organization().getId());
    }

    @Test
    void member_listAndDetail_answer200WithTheFields() throws Exception {
        create(tenant, admin, "Aaa " + shortName);

        MvcResult list = mockMvc.perform(in(tenant, get(PATH), member, null))
                .andExpect(status().isOk()).andReturn();
        MvcResult detail = mockMvc.perform(in(tenant, get(PATH + "/" + legalEntityId), member, null))
                .andExpect(status().isOk()).andReturn();

        List<String> names = JsonPath.read(list.getResponse().getContentAsString(),
                "$.data[*].shortName");
        assertThat(names).containsExactly("Aaa " + shortName, shortName);
        assertThat(read(detail, "$.data.shortName")).isEqualTo(shortName);
        assertThat(read(detail, "$.data.vatNumber")).isEqualTo("NL123456789B01");
        assertThat(read(detail, "$.data.governingLaw")).isEqualTo("Dutch law");
    }

    @Test
    void guest_listAndDetail_answer403() throws Exception {
        mockMvc.perform(in(tenant, get(PATH), guest, null)).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, get(PATH + "/" + legalEntityId), guest, null)).andExpect(status().isForbidden());
    }

    @Test
    void memberAndGuest_writes_answer403AndChangeNothing() throws Exception {
        for (RequestPostProcessor caller : List.of(member, guest)) {
            mockMvc.perform(in(tenant, post(PATH), caller, body("Other " + uniqueSuffix())))
                    .andExpect(status().isForbidden());
            mockMvc.perform(in(tenant, put(PATH + "/" + legalEntityId), caller, body("Renamed")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(in(tenant, delete(PATH + "/" + legalEntityId), caller, null))
                    .andExpect(status().isForbidden());
        }

        assertThat(count("SELECT count(*) FROM shrine.legal_entities WHERE id = ?::uuid AND short_name = ?",
                legalEntityId, shortName)).isEqualTo(1L);
        assertThat(legalEntitiesOf(tenant)).isEqualTo(1L);
    }

    @Test
    void admin_putReplacesEveryFieldAndOwnerDeletes() throws Exception {
        MvcResult updated = mockMvc.perform(in(tenant, put(PATH + "/" + legalEntityId), admin,
                        "{\"shortName\":\"" + shortName + "\",\"legalName\":\"Korur Holding B.V.\",\"country\":\"NLD\"}"))
                .andExpect(status().isOk()).andReturn();
        mockMvc.perform(in(tenant, delete(PATH + "/" + legalEntityId), owner, null)).andExpect(status().isOk());

        assertThat(read(updated, "$.data.legalName")).isEqualTo("Korur Holding B.V.");
        assertThat(read(updated, "$.data.vatNumber")).isEqualTo("null");
        assertThat(legalEntitiesOf(tenant)).isZero();
    }

    @Test
    void admin_postWithBlankShortName_answers422AndStoresNothing() throws Exception {
        mockMvc.perform(in(tenant, post(PATH), admin, body(" "))).andExpect(status().isUnprocessableEntity());

        assertThat(legalEntitiesOf(tenant)).isEqualTo(1L);
    }

    @Test
    void admin_postWithTakenShortNameDifferingInCase_answers409() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post(PATH), admin, body(shortName.toUpperCase())))
                .andExpect(status().isConflict()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(SHORT_NAME_TAKEN_CODE);
        assertThat(legalEntitiesOf(tenant)).isEqualTo(1L);
    }

    @Test
    void admin_putToShortNameOfAnother_answers409() throws Exception {
        String other = "Korur Inc. " + uniqueSuffix().substring(0, 6);
        create(tenant, admin, other);

        MvcResult result = mockMvc.perform(in(tenant, put(PATH + "/" + legalEntityId), admin,
                body(other.toLowerCase()))).andExpect(status().isConflict()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(SHORT_NAME_TAKEN_CODE);
    }

    @Test
    void sameShortNameInAnotherOrganization_isAllowed() throws Exception {
        Tenant other = newTenant();

        create(other, jwtOf(other.admin().getUser()), shortName);

        assertThat(legalEntitiesOf(other)).isEqualTo(1L);
    }

    @Test
    void legalEntityOfAnotherOrganization_answers404OnEveryEndpointAndChangesNothing() throws Exception {
        Tenant other = newTenant();
        RequestPostProcessor otherAdmin = jwtOf(other.admin().getUser());

        assertNotFound(in(other, get(PATH + "/" + legalEntityId), otherAdmin, null));
        assertNotFound(in(other, put(PATH + "/" + legalEntityId), otherAdmin, body("Hijacked")));
        assertNotFound(in(other, delete(PATH + "/" + legalEntityId), otherAdmin, null));
        MvcResult list = mockMvc.perform(in(other, get(PATH), otherAdmin, null))
                .andExpect(status().isOk()).andReturn();

        assertThat(list.getResponse().getContentAsString()).doesNotContain(legalEntityId);
        assertThat(count("SELECT count(*) FROM shrine.legal_entities WHERE id = ?::uuid AND short_name = ?",
                legalEntityId, shortName)).isEqualTo(1L);
    }

    private void assertNotFound(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isNotFound()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains(NOT_FOUND_CODE).doesNotContain(shortName);
    }
}

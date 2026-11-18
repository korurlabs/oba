package com.korur.oba.apps.otak.relation.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;

/**
 * Otak tells which members of an organization can open it and refuses to make one who cannot the owner
 * of a relation. The organization has no Everyone group: the OWNER holds Otak through the role, the
 * ADMIN through a direct grant, and the MEMBER holds nothing.
 */
class OtakOwnerAccessIT extends AbstractTenantIT {

    private static final String MEMBER_WITHOUT_APP_ACCESS = "APP_VAL_002";
    private static final String OWNER_NOT_MEMBER = "OTK_STA_001";

    private static final String RELATIONS_PATH = "/otak/relations";
    private static final String OWNER_CANDIDATES_PATH = "/otak/relations/owner-candidates";

    private Tenant tenant;
    private Tenant other;
    private RequestPostProcessor owner;
    private String ownerId;
    private String adminId;
    private String memberId;

    @BeforeEach
    void setUp() {
        tenant = newTenantWithoutGrants();
        other = newTenant();
        owner = jwtOf(tenant.owner().getUser());
        ownerId = tenant.owner().getUser().getId().toString();
        adminId = tenant.admin().getUser().getId().toString();
        memberId = tenant.member().getUser().getId().toString();
        jdbcTemplate.update("INSERT INTO shrine.organization_app_grants (organization_id, app, user_id) "
                + "VALUES (?, 'OTAK'::shrine_app_grant_app_enum, ?)", tenant.organization().getId(),
                tenant.admin().getUser().getId());
    }

    private static String relationBody(String name, String ownerUserId) {
        return "{\"name\":\"" + name + "\",\"type\":\"LEAD\",\"ownerUserId\":\"" + ownerUserId + "\"}";
    }

    private long relationRows(String name) {
        return count("SELECT count(*) FROM otak.relations WHERE name = ?", name);
    }

    @Test
    void ownerCandidates_listOnlyTheOrganizationWithOtakAccess() throws Exception {
        // Arrange
        String outsiderId = other.member().getUser().getId().toString();

        // Act and assert
        mockMvc.perform(in(tenant, get(OWNER_CANDIDATES_PATH), owner, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[*].userId", not(hasItem(outsiderId))))
                .andExpect(jsonPath("$.data[?(@.userId == '" + ownerId + "')].otakAccess").value(true))
                .andExpect(jsonPath("$.data[?(@.userId == '" + adminId + "')].otakAccess").value(true))
                .andExpect(jsonPath("$.data[?(@.userId == '" + memberId + "')].otakAccess").value(false))
                .andExpect(jsonPath("$.data[?(@.userId == '" + memberId + "')].role").value("MEMBER"));
    }

    @Test
    void createRelation_ownerWithoutOtak_answers422AndStoresNothing() throws Exception {
        // Arrange
        String name = "Owned by member " + uniqueSuffix();

        // Act and assert
        mockMvc.perform(in(tenant, post(RELATIONS_PATH), owner, relationBody(name, memberId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value(MEMBER_WITHOUT_APP_ACCESS));
        assertEquals(0, relationRows(name));
    }

    @Test
    void createRelation_ownerWithOtakGrant_isStored() throws Exception {
        // Arrange
        String name = "Owned by admin " + uniqueSuffix();

        // Act and assert
        mockMvc.perform(in(tenant, post(RELATIONS_PATH), owner, relationBody(name, adminId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.owner.id").value(adminId));
        assertEquals(1, relationRows(name));
    }

    @Test
    void createRelation_ownerOfAnotherOrganization_keepsTheMembershipCode() throws Exception {
        // Arrange
        String outsiderId = other.member().getUser().getId().toString();

        // Act and assert: a non-member gets the membership answer, not the app answer
        mockMvc.perform(in(tenant, post(RELATIONS_PATH), owner, relationBody("Outsider " + uniqueSuffix(),
                        outsiderId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(OWNER_NOT_MEMBER));
    }

    @Test
    void updateRelation_newOwnerWithoutOtak_answers422AndKeepsTheOwner() throws Exception {
        // Arrange
        String name = "Owned by admin " + uniqueSuffix();
        String relationId = read(mockMvc.perform(in(tenant, post(RELATIONS_PATH), owner, relationBody(name, adminId)))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");

        // Act and assert
        mockMvc.perform(in(tenant, put(RELATIONS_PATH + "/" + relationId), owner,
                        "{\"name\":\"" + name + "\",\"ownerUserId\":\"" + memberId + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value(MEMBER_WITHOUT_APP_ACCESS));
        assertEquals(1, count("SELECT count(*) FROM otak.relations WHERE id = ?::uuid AND owner_user_id = ?::uuid",
                relationId, adminId));
    }
}

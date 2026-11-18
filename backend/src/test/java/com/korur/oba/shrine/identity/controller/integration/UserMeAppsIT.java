package com.korur.oba.shrine.identity.controller.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.support.ObaIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The apps of each organization in GET and POST /users/me: the grants of that organization decide, a GUEST
 * never gets KAYA, an OWNER holds every app, and a disabled organization lists none.
 */
class UserMeAppsIT extends ObaIntegrationTest {

    private User user;
    private Organization everyoneMember;
    private Organization everyoneGuest;
    private Organization noGrantMember;
    private Organization directKayaMember;
    private Organization noGrantOwner;
    private Organization disabledMember;
    private Organization guestWithKayaGrant;

    @BeforeEach
    void setUp() {
        String suffix = uniqueSuffix();
        user = createUser("kc-" + suffix, "me" + suffix + "@" + uniqueDomain());

        everyoneMember = organizationWith(true, OrganizationStatus.ACTIVE, OrganizationMemberRole.MEMBER);
        everyoneGuest = organizationWith(true, OrganizationStatus.ACTIVE, OrganizationMemberRole.GUEST);
        noGrantMember = organizationWith(false, OrganizationStatus.ACTIVE, OrganizationMemberRole.MEMBER);
        directKayaMember = organizationWith(false, OrganizationStatus.ACTIVE, OrganizationMemberRole.MEMBER);
        grantDirectly(directKayaMember, "KAYA");
        noGrantOwner = organizationWith(false, OrganizationStatus.ACTIVE, OrganizationMemberRole.OWNER);
        disabledMember = organizationWith(true, OrganizationStatus.DISABLED, OrganizationMemberRole.MEMBER);
        guestWithKayaGrant = organizationWith(false, OrganizationStatus.ACTIVE, OrganizationMemberRole.GUEST);
        grantDirectly(guestWithKayaGrant, "KAYA");
        grantDirectly(guestWithKayaGrant, "KAIZEN");
    }

    private Organization organizationWith(boolean everyoneGranted, OrganizationStatus status, OrganizationMemberRole role) {
        Organization organization = createOrganization(status);
        if (everyoneGranted) {
            grantEveryAppToEveryone(organization);
        }
        createMember(organization, user, role, OrganizationMemberStatus.ACTIVE);
        return organization;
    }

    private void grantDirectly(Organization organization, String app) {
        jdbcTemplate.update("INSERT INTO shrine.organization_app_grants (organization_id, app, user_id) "
                + "VALUES (?, ?::shrine_app_grant_app_enum, ?)", organization.getId(), app, user.getId());
    }

    private Map<String, List<String>> appsPerOrganization(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.with(jwtOf(user)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> organizations = JsonPath.read(body, "$.data.organizations");
        Map<String, List<String>> result = new java.util.HashMap<>();
        for (Map<String, Object> organization : organizations) {
            @SuppressWarnings("unchecked")
            List<String> apps = (List<String>) organization.get("apps");
            result.put((String) organization.get("id"), apps);
        }
        return result;
    }

    private void assertApps(Map<String, List<String>> apps) {
        assertEquals(List.of("KAIZEN", "KAYA", "KALKAN", "OTAK"), apps.get(everyoneMember.getId().toString()));
        assertEquals(List.of("KAIZEN", "KALKAN"), apps.get(everyoneGuest.getId().toString()),
                "a GUEST never gets KAYA or OTAK");
        assertEquals(List.of(), apps.get(noGrantMember.getId().toString()));
        assertEquals(List.of("KAYA"), apps.get(directKayaMember.getId().toString()));
        assertEquals(List.of("KAIZEN", "KAYA", "KALKAN", "OTAK"), apps.get(noGrantOwner.getId().toString()));
        assertEquals(List.of("KAIZEN"), apps.get(guestWithKayaGrant.getId().toString()),
                "a KAYA grant does not raise a GUEST");
        assertTrue(apps.get(disabledMember.getId().toString()).isEmpty(), "a disabled organization lists no app");
    }

    @Test
    void getMe_membershipsInSeveralOrganizations_listsTheAppsOfEachOrganization() throws Exception {
        assertApps(appsPerOrganization(MockMvcRequestBuilders.get("/users/me")));
    }

    @Test
    void postMe_membershipsInSeveralOrganizations_listsTheAppsOfEachOrganization() throws Exception {
        assertApps(appsPerOrganization(MockMvcRequestBuilders.post("/users/me")));
    }

    @Test
    void getMe_grantRevoked_nextCallDropsTheApp() throws Exception {
        jdbcTemplate.update("UPDATE shrine.organization_app_grants SET deleted = TRUE "
                + "WHERE organization_id = ? AND user_id = ?", directKayaMember.getId(), user.getId());

        Map<String, List<String>> apps = appsPerOrganization(MockMvcRequestBuilders.get("/users/me"));

        assertEquals(List.of(), apps.get(directKayaMember.getId().toString()));
    }
}

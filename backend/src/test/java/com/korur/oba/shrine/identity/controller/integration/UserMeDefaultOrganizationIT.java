package com.korur.oba.shrine.identity.controller.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.support.ObaIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Clearing the default organization selects the personal workspace. Only the dedicated DELETE clears it:
 * a PUT /users/me that omits defaultOrganizationId or sends it empty keeps the default, so a caller that
 * saves only its preferences cannot lose the default by accident.
 */
class UserMeDefaultOrganizationIT extends ObaIntegrationTest {

    private static final String DEFAULT_ORGANIZATION_PATH = "/users/me/default-organization";

    private User user;
    private Organization organization;

    @BeforeEach
    void setUp() {
        user = createUser();
        organization = createOrganization();
        createMember(organization, user, OrganizationMemberRole.MEMBER, OrganizationMemberStatus.ACTIVE);
        jdbcTemplate.update("UPDATE shrine.users SET default_organization_id = ? WHERE id = ?",
                organization.getId(), user.getId());
    }

    private UUID storedDefaultOrganizationId() {
        return jdbcTemplate.queryForObject("SELECT default_organization_id FROM shrine.users WHERE id = ?",
                UUID.class, user.getId());
    }

    @Test
    void clearDefaultOrganization_defaultSet_answersWithoutDefaultAndClearsIt() throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.delete(DEFAULT_ORGANIZATION_PATH).with(jwtOf(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Object answered = JsonPath.read(body, "$.data.defaultOrganizationId");
        assertNull(answered);
        assertNull(storedDefaultOrganizationId());
    }

    @Test
    void clearDefaultOrganization_noDefaultSet_answers200AndStaysCleared() throws Exception {
        jdbcTemplate.update("UPDATE shrine.users SET default_organization_id = NULL WHERE id = ?", user.getId());

        mockMvc.perform(MockMvcRequestBuilders.delete(DEFAULT_ORGANIZATION_PATH).with(jwtOf(user)))
                .andExpect(status().isOk());

        assertNull(storedDefaultOrganizationId());
    }

    @Test
    void updateMe_defaultOrganizationOmitted_keepsTheDefault() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/users/me").with(jwtOf(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userPreferences\":{\"theme\":\"dark\"}}"))
                .andExpect(status().isOk());

        assertEquals(organization.getId(), storedDefaultOrganizationId());
    }

    @Test
    void updateMe_defaultOrganizationEmpty_keepsTheDefault() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/users/me").with(jwtOf(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultOrganizationId\":\"\"}"))
                .andExpect(status().isOk());

        assertEquals(organization.getId(), storedDefaultOrganizationId());
    }
}

package com.korur.oba.shrine.organization.controller.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.support.ObaIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Creating an organization through the API gives it an Everyone group that holds one live grant for each
 * app, so every member of a new organization opens every app until an admin removes a grant.
 */
class OrganizationCreateIT extends ObaIntegrationTest {

    @Test
    void createOrganization_validRequest_createsEveryoneGroupWithOneGrantPerApp() throws Exception {
        User user = createUser();

        String body = mockMvc.perform(MockMvcRequestBuilders.post("/organizations").with(jwtOf(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Created " + uniqueSuffix() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID organizationId = UUID.fromString(JsonPath.read(body, "$.data.id"));

        long everyoneGroups = count("SELECT count(*) FROM shrine.organization_groups WHERE organization_id = ? "
                + "AND type = 'EVERYONE'::shrine_organization_group_type_enum AND deleted = FALSE", organizationId);
        assertEquals(1, everyoneGroups);

        List<String> grantedApps = jdbcTemplate.queryForList("""
                SELECT g.app::text FROM shrine.organization_app_grants g
                JOIN shrine.organization_groups og ON og.id = g.group_id
                WHERE g.organization_id = ? AND og.type = 'EVERYONE'::shrine_organization_group_type_enum
                  AND g.deleted = FALSE AND g.user_id IS NULL
                ORDER BY g.app::text
                """, String.class, organizationId);
        List<String> expectedApps = Arrays.stream(App.values()).map(Enum::name).sorted().toList();
        assertEquals(List.of("KAIZEN", "KALKAN", "KAYA", "OTAK"), expectedApps);
        assertEquals(expectedApps, grantedApps);

        long allGrants = count("SELECT count(*) FROM shrine.organization_app_grants WHERE organization_id = ? AND deleted = FALSE",
                organizationId);
        assertEquals(App.values().length, allGrants);
    }
}

package com.korur.oba.apps.kalkan.support;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.support.ObaIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base of the Kalkan integration tests: one organization per test with an OWNER, an ADMIN, a MEMBER
 * and a GUEST, whose Everyone group holds every app as after the backfill, and request helpers that sign a caller in and name the organization header.
 *
 * Tests share the database and create their own organization, so they never see each other's rows.
 */
public abstract class AbstractKalkanIT extends ObaIntegrationTest {

    protected static final String PAGES = "/kalkan/pages";

    protected Organization organization;
    protected User owner;
    protected User admin;
    protected User member;
    protected User guest;

    @BeforeEach
    void setUpOrganization() {
        organization = createOrganization(OrganizationStatus.ACTIVE);
        grantEveryAppToEveryone(organization);
        owner = newMember(organization, OrganizationMemberRole.OWNER);
        admin = newMember(organization, OrganizationMemberRole.ADMIN);
        member = newMember(organization, OrganizationMemberRole.MEMBER);
        guest = newMember(organization, OrganizationMemberRole.GUEST);
    }

    protected User newMember(Organization target, OrganizationMemberRole role) {
        String suffix = uniqueSuffix();
        User user = createUser("kc-" + suffix, "user" + suffix + "@" + uniqueDomain());
        createMember(target, user, role, OrganizationMemberStatus.ACTIVE);
        return user;
    }

    /** Sends a request as the caller to the test organization; a null body sends none. */
    protected ResultActions send(MockHttpServletRequestBuilder builder, User caller, String body) throws Exception {
        return send(builder, caller, organization.getId(), body);
    }

    protected ResultActions send(MockHttpServletRequestBuilder builder, User caller, UUID organizationId, String body)
            throws Exception {
        MockHttpServletRequestBuilder prepared = builder.with(jwtOf(caller))
                .header("X-Organization-ID", organizationId.toString());
        return mockMvc.perform(body == null ? prepared : prepared.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions getPage(User caller, String pageId) throws Exception {
        return send(get(PAGES + "/{id}", pageId), caller, null);
    }

    protected ResultActions deletePage(User caller, String pageId) throws Exception {
        return send(delete(PAGES + "/{id}", pageId), caller, null);
    }

    protected ResultActions restorePage(User caller, String pageId) throws Exception {
        return send(post(PAGES + "/{id}/restore", pageId), caller, null);
    }

    protected ResultActions createPage(User caller, String parentId, String slug) throws Exception {
        return send(post(PAGES), caller, createBody(parentId, slug));
    }

    protected ResultActions updatePage(User caller, String pageId, String body) throws Exception {
        return send(put(PAGES + "/{id}", pageId), caller, body);
    }

    protected ResultActions movePage(User caller, String pageId, String parentId, int position, long version)
            throws Exception {
        return send(patch(PAGES + "/{id}/position", pageId), caller, "{\"parentId\":" + quote(parentId)
                + ",\"position\":" + position + ",\"version\":" + version + "}");
    }

    protected ResultActions putByPath(User caller, String path, String title, boolean force, boolean dryRun)
            throws Exception {
        return send(put(PAGES + "/by-path").param("dryRun", String.valueOf(dryRun)), caller,
                "{\"path\":\"" + path + "\",\"force\":" + force + ",\"translations\":[" + translation("EN", title, "body of " + title)
                        + "]}");
    }

    /** Creates a page as the caller, expects 201 and returns the new page id. */
    protected String createdId(User caller, String parentId, String slug) throws Exception {
        String response = createPage(caller, parentId, slug)
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    /** Creates a chain of pages under the parent, one per slug, and returns their ids in order. */
    protected java.util.List<String> createdChain(User caller, String parentId, String... slugs) throws Exception {
        java.util.List<String> ids = new java.util.ArrayList<>();
        String current = parentId;
        for (String slug : slugs) {
            current = createdId(caller, current, slug);
            ids.add(current);
        }
        return ids;
    }

    protected static String createBody(String parentId, String slug) {
        return "{\"parentId\":" + quote(parentId) + ",\"slug\":\"" + slug + "\",\"compliance\":false,\"translations\":["
                + translation("EN", "Title " + slug, "Body " + slug) + "]}";
    }

    protected static String translation(String locale, String title, String body) {
        return "{\"locale\":\"" + locale + "\",\"title\":\"" + title + "\",\"body\":\"" + body + "\"}";
    }

    protected static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    protected static long versionOf(ResultActions result) throws Exception {
        Number version = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.version");
        return version.longValue();
    }
}

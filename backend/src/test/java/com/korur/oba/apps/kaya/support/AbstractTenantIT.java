package com.korur.oba.apps.kaya.support;

// 1. Framework-level
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;


// 4. Internal
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.support.ObaIntegrationTest;

// 3. Java SDK
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base of the user and machine integration tests: one organization per test with an OWNER, an
 * ADMIN and a MEMBER who never signed in (so they hold no git email), plus a way to sign people
 * in through POST /users/me, which registers the sign-in email as a git email of the user.
 * Because the OWNER exists first, every person signed in through {@link #signIn} is a MEMBER.
 */
public abstract class AbstractTenantIT extends ObaIntegrationTest {

    /** A signed-in person: the Keycloak identity, the email and the user created at sign-in. */
    protected record Person(String keycloakId, String email, User user) {

        public String userId() {
            return user.getId().toString();
        }

        public RequestPostProcessor jwt() {
            return ObaIntegrationTest.jwtOf(keycloakId, email);
        }
    }

    /** The organization of one test and its members that never signed in. */
    protected record Tenant(Organization organization, String domain, OrganizationMember owner,
                            OrganizationMember admin, OrganizationMember member) {

        public String id() {
            return organization.getId().toString();
        }
    }

    protected Tenant newTenant() {
        return newTenant(true);
    }

    /** A tenant whose organization has no Everyone group, so its members hold only what a grant gives them. */
    protected Tenant newTenantWithoutGrants() {
        return newTenant(false);
    }

    private Tenant newTenant(boolean everyoneGranted) {
        String domain = uniqueDomain();
        Organization organization = createOrganization(OrganizationStatus.ACTIVE);
        if (everyoneGranted) {
            grantEveryAppToEveryone(organization);
        }
        OrganizationMember owner = createActiveMember(organization, domain, OrganizationMemberRole.OWNER);
        OrganizationMember admin = createActiveMember(organization, domain, OrganizationMemberRole.ADMIN);
        OrganizationMember member = createActiveMember(organization, domain, OrganizationMemberRole.MEMBER);
        return new Tenant(organization, domain, owner, admin, member);
    }

    /**
     * Signs a new user in and makes it an ACTIVE member of the tenant's organization: the
     * platform owns membership, so the user joins explicitly instead of through an email domain.
     */
    protected Person signIn(Tenant tenant, String localPart) throws Exception {
        String keycloakId = "kc-" + uniqueSuffix();
        String email = localPart + uniqueSuffix().substring(0, 6) + "@" + tenant.domain();
        Person person = signIn(keycloakId, email);
        createMember(tenant.organization(), person.user(), OrganizationMemberRole.MEMBER, OrganizationMemberStatus.ACTIVE);
        return person;
    }

    /** Signs a new user in through the platform profile endpoint and activates it. */
    protected Person signIn(String keycloakId, String email) throws Exception {
        mockMvc.perform(post("/users/me").with(jwtOf(keycloakId, email)))
                .andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE shrine.users SET status = 'ACTIVE'::shrine_identity_user_status_enum "
                + "WHERE keycloak_id = ?", keycloakId);
        User user = userRepository.findByKeycloakId(keycloakId).orElseThrow();
        return new Person(keycloakId, email, user);
    }

    /** Registers a git email on the user of the tenant; returns the id of the email row, stored lower-cased. */
    protected String addGitEmail(Tenant tenant, String userId, String email) {
        return jdbcTemplate.queryForObject("INSERT INTO kaya.user_git_emails (organization_id, user_id, email, source) "
                + "VALUES (?::uuid, ?::uuid, lower(?), 'SIGN_IN'::identity_user_git_email_source_enum) RETURNING id::text",
                String.class, tenant.id(), userId, email);
    }

    /** Adds the organization header and, when a body is given, the JSON content type. */
    protected static MockHttpServletRequestBuilder in(
            Tenant tenant, MockHttpServletRequestBuilder builder, RequestPostProcessor jwt, String body) {
        MockHttpServletRequestBuilder prepared = builder.with(jwt).header("X-Organization-ID", tenant.id());
        return body == null ? prepared : prepared.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected static String read(MvcResult result, String path) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return String.valueOf(value);
    }

    protected long auditCount(String userId, String action) {
        return count("SELECT count(*) FROM kaya.user_audit_entries WHERE user_id = ?::uuid AND action::text = ?",
                userId, action);
    }

    /**
     * Inserts an account of the provider and links it to the user with source RECORDER; returns
     * the account id. A null organization uuid makes a personal account.
     */
    protected String insertAccount(Tenant tenant, String userId, String provider, String email,
                                   String providerOrganizationUuid, String providerOrganizationName) {
        UUID accountId = jdbcTemplate.queryForObject("""
                INSERT INTO kaya.accounts
                    (organization_id, provider, account_email, account_organization_uuid, account_organization_name)
                VALUES (?, ?::account_account_provider_enum, ?, ?, ?)
                RETURNING id
                """, UUID.class, tenant.organization().getId(), provider, email, providerOrganizationUuid,
                providerOrganizationName);
        linkAccount(tenant, accountId.toString(), userId);
        return accountId.toString();
    }

    /** Links an existing account to another user with source RECORDER. */
    protected void linkAccount(Tenant tenant, String accountId, String userId) {
        jdbcTemplate.update("""
                INSERT INTO kaya.account_users (organization_id, account_id, user_id, source)
                VALUES (?, ?::uuid, ?::uuid, 'RECORDER')
                """, tenant.organization().getId(), accountId, userId);
    }
}

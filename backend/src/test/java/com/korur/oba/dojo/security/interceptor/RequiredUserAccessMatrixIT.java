package com.korur.oba.dojo.security.interceptor;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

// 2. Third-party
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

// 3. Java SDK
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.security.constant.SecurityConstant;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calls every handler that carries {@link RequiredUserAccess} as each role of an organization and
 * as callers without access, through the real interceptor, security chain and database. A refused
 * call must answer 403 with the code of the failed step; an allowed call must not be refused by the
 * interceptor. The handlers are read from the running application, so a handler added later joins
 * the matrix without a test change, and a changed annotation value fails here.
 */
class RequiredUserAccessMatrixIT extends AbstractTenantIT {

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{(\\w+)(?::[^}]*)?}");
    private static final Set<String> INTERCEPTOR_CODES = Set.of("ORG_SEC_001", "ORG_SEC_003", "ORG_SEC_005", "GEN_SEC_004", "APP_SEC_001");
    private static final Configuration NULL_WHEN_MISSING =
            Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    /** One annotated handler: its mapping, required role and a path template. */
    private record Target(HandlerMethod handler, RequestMappingInfo info, OrganizationMemberRole required) {

        String template() {
            return info.getPatternValues().iterator().next();
        }

        HttpMethod method() {
            Set<org.springframework.web.bind.annotation.RequestMethod> methods = info.getMethodsCondition().getMethods();
            return methods.isEmpty() ? HttpMethod.GET : HttpMethod.valueOf(methods.iterator().next().name());
        }

        boolean namesTheOrganizationInThePath() {
            return template().contains("{organizationId}");
        }

        /** The app that the handler's package belongs to, or null for a handler outside the app packages. */
        App app() {
            String prefix = "com.korur.oba.apps.";
            String packageName = handler.getBeanType().getPackageName();
            if (!packageName.startsWith(prefix)) {
                return null;
            }
            String segment = packageName.substring(prefix.length()).split("\\.")[0];
            return App.fromAppPackage(segment).orElse(null);
        }

        String label() {
            return handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName()
                    + " " + method() + " " + template() + " requires " + required;
        }
    }

    /** A signed-in user with one membership in the organization, or none. */
    private record Caller(String name, User user) {
    }

    private List<Target> annotatedHandlers() {
        List<Target> targets = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequiredUserAccess access = entry.getValue().getMethodAnnotation(RequiredUserAccess.class);
            if (access != null && !entry.getKey().getPatternValues().isEmpty()) {
                targets.add(new Target(entry.getValue(), entry.getKey(), access.value()));
            }
        }
        assertFalse(targets.isEmpty(), "No annotated handler found, the matrix is empty");
        return targets;
    }

    private MockHttpServletRequestBuilder requestOf(Target target, String organizationId, User caller, String headerOrganization) {
        Matcher matcher = PATH_VARIABLE.matcher(target.template());
        StringBuilder uri = new StringBuilder();
        while (matcher.find()) {
            // {userId} names the caller, so a handler that lets anyone act on themselves, such as leaving
            // a chat channel, is not refused by its own service rule and only the interceptor decides.
            String value = switch (matcher.group(1)) {
                case SecurityConstant.PATH_VARIABLE_ORGANIZATION_ID -> organizationId;
                case "userId" -> caller.getId().toString();
                default -> UUID.randomUUID().toString();
            };
            matcher.appendReplacement(uri, value);
        }
        matcher.appendTail(uri);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(target.method(), uri.toString())
                .with(jwtOf(caller));
        if (headerOrganization != null) {
            builder.header("X-Organization-ID", headerOrganization);
        }
        // A handler that consumes only one media type is not mapped for another, so the request
        // names the one it accepts; the body is irrelevant because the interceptor runs before it is read.
        MediaType contentType = target.info().getConsumesCondition().getConsumableMediaTypes().stream()
                .findFirst().orElse(MediaType.APPLICATION_JSON);
        return builder.contentType(contentType).content("{}");
    }

    private Result call(Target target, Tenant tenant, User caller) throws Exception {
        return call(target, tenant.id(), caller);
    }

    private Result call(Target target, String organizationId, User caller) throws Exception {
        MvcResult result = mockMvc.perform(requestOf(target, organizationId, caller, organizationId)).andReturn();
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();
        String code = status >= 400 && !body.isBlank()
                ? JsonPath.using(NULL_WHEN_MISSING).parse(body).read("$.error.code", String.class)
                : null;
        return new Result(status, code);
    }

    private record Result(int status, String code) {

        boolean refusedByTheInterceptor() {
            return status == 403 && code != null && INTERCEPTOR_CODES.contains(code);
        }

        boolean is(int expectedStatus, String expectedCode) {
            return status == expectedStatus && expectedCode.equals(code);
        }
    }

    private Tenant tenantWithGuest() {
        return newTenant();
    }

    private Caller guestOf(Tenant tenant) {
        User guest = createUser("kc-" + uniqueSuffix(), "guest" + uniqueSuffix() + "@" + tenant.domain());
        createMember(tenant.organization(), guest, OrganizationMemberRole.GUEST, OrganizationMemberStatus.ACTIVE);
        return new Caller("GUEST", guest);
    }

    private List<Caller> rolesOf(Tenant tenant) {
        return List.of(
                guestOf(tenant),
                new Caller("MEMBER", tenant.member().getUser()),
                new Caller("ADMIN", tenant.admin().getUser()),
                new Caller("OWNER", tenant.owner().getUser()));
    }

    private static OrganizationMemberRole roleOf(String name) {
        return OrganizationMemberRole.valueOf(name);
    }

    @Test
    void rolesBelowTheAnnotatedValue_areRefusedWithInsufficientPermissions() throws Exception {
        List<String> failures = new ArrayList<>();
        Tenant tenant = tenantWithGuest();
        List<Caller> callers = rolesOf(tenant);

        for (Target target : annotatedHandlers()) {
            for (Caller caller : callers) {
                if (roleOf(caller.name()).isAtLeast(target.required())) {
                    continue;
                }
                Result result = call(target, tenant, caller.user());
                if (!result.is(403, "ORG_SEC_003")) {
                    failures.add(caller.name() + " on " + target.label() + " answered " + result);
                }
            }
        }

        assertTrue(failures.isEmpty(), "Roles below the annotated value that were not refused:\n" + String.join("\n", failures));
    }

    @Test
    void rolesAtLeastTheAnnotatedValue_areNotRefusedByTheInterceptor() throws Exception {
        List<String> failures = new ArrayList<>();

        for (Target target : annotatedHandlers()) {
            // A handler that changes or deletes the organization gets a fresh one per call, so one
            // role's call cannot change what the next role sees.
            boolean destructive = target.namesTheOrganizationInThePath() || target.method() == HttpMethod.DELETE;
            Tenant shared = destructive ? null : tenantWithGuest();
            List<Caller> sharedCallers = destructive ? List.of() : rolesOf(shared);
            for (String roleName : List.of("GUEST", "MEMBER", "ADMIN", "OWNER")) {
                if (!roleOf(roleName).isAtLeast(target.required())) {
                    continue;
                }
                Tenant tenant = destructive ? tenantWithGuest() : shared;
                Caller caller = (destructive ? rolesOf(tenant) : sharedCallers).stream()
                        .filter(candidate -> candidate.name().equals(roleName)).findFirst().orElseThrow();
                Result result = call(target, tenant, caller.user());
                if (result.refusedByTheInterceptor()) {
                    failures.add(roleName + " on " + target.label() + " was refused: " + result);
                }
            }
        }

        assertTrue(failures.isEmpty(), "Allowed roles that the interceptor refused:\n" + String.join("\n", failures));
    }

    @Test
    void userWithoutMembership_isRefusedAsNotMemberOnEveryHandler() throws Exception {
        List<String> failures = new ArrayList<>();
        Tenant tenant = newTenant();
        Tenant foreign = newTenant();
        User outsider = foreign.owner().getUser();

        for (Target target : annotatedHandlers()) {
            Result result = call(target, tenant, outsider);
            if (!result.is(403, "ORG_SEC_001")) {
                failures.add("non-member on " + target.label() + " answered " + result);
            }
        }

        assertTrue(failures.isEmpty(), "Non-members that were not refused:\n" + String.join("\n", failures));
    }

    @Test
    void unknownOrganization_isRefusedLikeANonMemberOnEveryHandler() throws Exception {
        List<String> failures = new ArrayList<>();
        Tenant tenant = newTenant();
        String unknownOrganizationId = UUID.randomUUID().toString();

        for (Target target : annotatedHandlers()) {
            Result result = call(target, unknownOrganizationId, tenant.owner().getUser());
            if (!result.is(403, "ORG_SEC_001")) {
                failures.add("owner of another organization on " + target.label() + " answered " + result);
            }
        }

        assertTrue(failures.isEmpty(), "Unknown organizations that were not refused like a non-member:\n"
                + String.join("\n", failures));
    }

    @Test
    void disabledMember_isRefusedAsNotMemberOnEveryHandler() throws Exception {
        List<String> failures = new ArrayList<>();
        Tenant tenant = newTenant();
        jdbcTemplate.update("UPDATE shrine.organization_members SET status = 'DISABLED'::shrine_organization_member_status_enum "
                + "WHERE id = ?", tenant.owner().getId());

        for (Target target : annotatedHandlers()) {
            Result result = call(target, tenant, tenant.owner().getUser());
            if (!result.is(403, "ORG_SEC_001")) {
                failures.add("disabled owner on " + target.label() + " answered " + result);
            }
        }

        assertTrue(failures.isEmpty(), "Disabled members that were not refused:\n" + String.join("\n", failures));
    }

    @Test
    void memberOfDisabledOrganization_isRefusedWithOrganizationDisabledOnEveryHandler() throws Exception {
        List<String> failures = new ArrayList<>();
        Tenant tenant = newTenant();
        jdbcTemplate.update("UPDATE shrine.organizations SET status = 'DISABLED'::shrine_organization_status_enum WHERE id = ?",
                tenant.organization().getId());

        for (Target target : annotatedHandlers()) {
            Result result = call(target, tenant, tenant.owner().getUser());
            if (!result.is(403, "ORG_SEC_005")) {
                failures.add("owner of a disabled organization on " + target.label() + " answered " + result);
            }
        }

        assertTrue(failures.isEmpty(), "Members of a disabled organization that were not refused:\n"
                + String.join("\n", failures));
    }

    @Test
    void pathAndHeaderNamingDifferentOrganizations_areRefusedWithMismatchOnEveryPathHandler() throws Exception {
        List<String> failures = new ArrayList<>();
        Tenant tenant = newTenant();
        int checked = 0;

        for (Target target : annotatedHandlers()) {
            if (!target.namesTheOrganizationInThePath()) {
                continue;
            }
            checked++;
            MvcResult mvcResult = mockMvc.perform(requestOf(target, tenant.id(), tenant.owner().getUser(),
                    UUID.randomUUID().toString())).andReturn();
            String body = mvcResult.getResponse().getContentAsString();
            String code = body.isBlank() ? null
                    : JsonPath.using(NULL_WHEN_MISSING).parse(body).read("$.error.code", String.class);
            if (mvcResult.getResponse().getStatus() != 400 || !"ORG_API_002".equals(code)) {
                failures.add(target.label() + " answered " + mvcResult.getResponse().getStatus() + " " + code);
            }
        }

        assertTrue(checked > 0, "No annotated handler names the organization in its path");
        assertTrue(failures.isEmpty(), "Path and header mismatches that were not refused:\n" + String.join("\n", failures));
    }

    private static boolean isDestructive(Target target) {
        return target.namesTheOrganizationInThePath() || target.method() == HttpMethod.DELETE;
    }

    private void grantToUser(Tenant tenant, User user, App app) {
        jdbcTemplate.update("INSERT INTO shrine.organization_app_grants (organization_id, app, user_id) "
                + "VALUES (?, ?::shrine_app_grant_app_enum, ?)", tenant.organization().getId(), app.name(), user.getId());
    }

    @Test
    void memberWithoutAnyGrant_isRefusedWithAppAccessDeniedOnEveryHandlerOfEachApp() throws Exception {
        List<String> failures = new ArrayList<>();
        Tenant shared = newTenantWithoutGrants();
        Map<App, Integer> checked = new java.util.EnumMap<>(App.class);

        for (Target target : annotatedHandlers()) {
            if (!OrganizationMemberRole.ADMIN.isAtLeast(target.required())) {
                continue;
            }
            // A handler that changes or deletes the organization or a member gets a fresh organization.
            Tenant tenant = isDestructive(target) ? newTenantWithoutGrants() : shared;
            Result result = call(target, tenant, tenant.admin().getUser());
            if (target.app() != null) {
                checked.merge(target.app(), 1, Integer::sum);
                if (!result.is(403, "APP_SEC_001")) {
                    failures.add("ADMIN without a grant on " + target.label() + " answered " + result);
                }
            } else if ("APP_SEC_001".equals(result.code())) {
                failures.add("ADMIN on " + target.label() + " outside the app packages was refused: " + result);
            }
        }

        for (App app : App.values()) {
            assertTrue(checked.getOrDefault(app, 0) > 0, "No annotated handler found for " + app);
        }
        assertTrue(failures.isEmpty(), "Members without a grant that were not refused per app:\n" + String.join("\n", failures));
    }

    @Test
    void roleBelowTheHandler_isRefusedWithInsufficientPermissionsBeforeTheAppCheck() throws Exception {
        List<String> failures = new ArrayList<>();
        Tenant tenant = newTenantWithoutGrants();
        Caller guest = guestOf(tenant);

        for (Target target : annotatedHandlers()) {
            if (target.app() == null || OrganizationMemberRole.GUEST.isAtLeast(target.required())) {
                continue;
            }
            Result result = call(target, tenant, guest.user());
            if (!result.is(403, "ORG_SEC_003")) {
                failures.add("GUEST without a grant on " + target.label() + " answered " + result);
            }
        }

        assertTrue(failures.isEmpty(), "Roles below the handler that met the app check first:\n" + String.join("\n", failures));
    }

    @Test
    void ownerWithoutAnyGrant_isNotRefusedWithAppAccessDeniedOnAnyHandler() throws Exception {
        List<String> failures = new ArrayList<>();
        Tenant tenant = newTenantWithoutGrants();

        for (Target target : annotatedHandlers()) {
            // A handler that changes or deletes the organization gets a fresh one per call.
            Tenant used = isDestructive(target) ? newTenantWithoutGrants() : tenant;
            Result result = call(target, used, used.owner().getUser());
            if (result.is(403, "APP_SEC_001")) {
                failures.add("OWNER without a grant on " + target.label() + " was refused: " + result);
            }
        }

        assertTrue(failures.isEmpty(), "Owners that were refused for the app:\n" + String.join("\n", failures));
    }

    @Test
    void directGrantOfOneApp_opensOnlyTheHandlersOfThatApp() throws Exception {
        for (App granted : App.values()) {
            List<String> failures = new ArrayList<>();
            Tenant shared = newTenantWithoutGrants();
            grantToUser(shared, shared.member().getUser(), granted);

            for (Target target : annotatedHandlers()) {
                if (target.app() == null || !OrganizationMemberRole.MEMBER.isAtLeast(target.required())) {
                    continue;
                }
                Tenant tenant = shared;
                if (isDestructive(target)) {
                    tenant = newTenantWithoutGrants();
                    grantToUser(tenant, tenant.member().getUser(), granted);
                }
                User member = tenant.member().getUser();
                Result result = call(target, tenant, member);
                boolean refused = result.is(403, "APP_SEC_001");
                if (target.app() == granted && refused) {
                    failures.add("MEMBER granted " + granted + " was refused on " + target.label());
                } else if (target.app() != granted && !refused) {
                    failures.add("MEMBER granted only " + granted + " answered " + result + " on " + target.label());
                }
            }

            assertTrue(failures.isEmpty(), "A direct grant of " + granted + " opened the wrong handlers:\n"
                    + String.join("\n", failures));
        }
    }

    @Test
    void everyRoleLevel_isPinnedByAtLeastOneHandler() {
        Set<OrganizationMemberRole> pinned = EnumSet.noneOf(OrganizationMemberRole.class);
        for (Target target : annotatedHandlers()) {
            pinned.add(target.required());
        }

        assertTrue(pinned.containsAll(EnumSet.of(OrganizationMemberRole.GUEST, OrganizationMemberRole.MEMBER,
                OrganizationMemberRole.ADMIN, OrganizationMemberRole.OWNER)),
                "The matrix does not exercise every role level; pinned: " + pinned);
    }
}

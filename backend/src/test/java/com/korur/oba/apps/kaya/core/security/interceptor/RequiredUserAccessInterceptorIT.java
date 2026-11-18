package com.korur.oba.apps.kaya.core.security.interceptor;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The access check of the Kaya JWT endpoints against the platform membership in the database:
 * a GUEST is below the MEMBER level, an inactive user, a disabled membership, a caller without a
 * membership and an unknown organization are refused, and a request without a token never reaches
 * the check. GET /kaya/accounts is annotated with the MEMBER level.
 */
class RequiredUserAccessInterceptorIT extends AbstractTenantIT {

    private static final String ACCOUNTS = "/kaya/accounts";

    @Test
    void preHandle_activeMember_passes() throws Exception {
        Tenant tenant = newTenant();

        mockMvc.perform(in(tenant, get(ACCOUNTS), jwtOf(tenant.member().getUser()), null))
                .andExpect(status().isOk());
    }

    @Test
    void preHandle_guestMember_isBelowMemberAndRefused() throws Exception {
        Tenant tenant = newTenant();
        User guest = createUser("kc-" + uniqueSuffix(), "guest" + uniqueSuffix() + "@" + tenant.domain());
        createMember(tenant.organization(), guest, OrganizationMemberRole.GUEST, OrganizationMemberStatus.ACTIVE);

        mockMvc.perform(in(tenant, get(ACCOUNTS), jwtOf(guest), null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORG_SEC_003"));
    }

    @Test
    void preHandle_inactiveUser_isRefusedWithTheUserStatus() throws Exception {
        Tenant tenant = newTenant();
        User user = tenant.member().getUser();
        jdbcTemplate.update("UPDATE shrine.users SET status = 'DISABLED'::shrine_identity_user_status_enum WHERE id = ?",
                user.getId());

        mockMvc.perform(in(tenant, get(ACCOUNTS), jwtOf(user), null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("GEN_SEC_002"))
                .andExpect(jsonPath("$.error.errorValues.status").value("DISABLED"));
    }

    @Test
    void preHandle_disabledMembership_isRefusedAsNotMember() throws Exception {
        Tenant tenant = newTenant();
        jdbcTemplate.update("UPDATE shrine.organization_members SET status = 'DISABLED'::shrine_organization_member_status_enum "
                + "WHERE id = ?", tenant.member().getId());

        mockMvc.perform(in(tenant, get(ACCOUNTS), jwtOf(tenant.member().getUser()), null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORG_SEC_001"));
    }

    @Test
    void preHandle_memberOfAnotherOrganization_isRefusedAsNotMember() throws Exception {
        Tenant tenant = newTenant();
        Tenant foreign = newTenant();

        mockMvc.perform(in(tenant, get(ACCOUNTS), jwtOf(foreign.owner().getUser()), null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORG_SEC_001"));
    }

    @Test
    void preHandle_unknownOrganization_isRefusedLikeANonMember() throws Exception {
        Tenant tenant = newTenant();

        mockMvc.perform(get(ACCOUNTS).with(jwtOf(tenant.member().getUser()))
                        .header("X-Organization-ID", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORG_SEC_001"));
    }

    @Test
    void preHandle_headerIsNotAUuid_answersInvalidOrganizationId() throws Exception {
        Tenant tenant = newTenant();

        mockMvc.perform(get(ACCOUNTS).with(jwtOf(tenant.member().getUser())).header("X-Organization-ID", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ORG_API_001"));
    }

    @Test
    void preHandle_withoutToken_answersUnauthorized() throws Exception {
        Tenant tenant = newTenant();

        mockMvc.perform(get(ACCOUNTS).header("X-Organization-ID", tenant.id()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("GEN_SEC_002"));
    }
}

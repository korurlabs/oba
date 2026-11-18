package com.korur.oba.apps.kaya.identity.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.apps.kaya.identity.mock_data.IdentityMocks;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationAccessQueryServiceImplUnitTest {

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;
    @Mock
    private LoggingService loggingService;

    private OrganizationAccessQueryServiceImpl service;

    private Organization organization;
    private User user;

    @BeforeEach
    void setUp() {
        service = new OrganizationAccessQueryServiceImpl(organizationMemberQueryService, loggingService);
        organization = IdentityMocks.organization(OrganizationStatus.ACTIVE);
        user = IdentityMocks.user("ada@example.com");
    }

    private OrganizationMember stubMember(OrganizationMemberStatus status) {
        OrganizationMember member = IdentityMocks.member(organization, user, OrganizationMemberRole.MEMBER, status);
        when(organizationMemberQueryService.findMember(organization.getId(), user.getId()))
                .thenReturn(Optional.of(member));
        return member;
    }

    @Test
    void verifyTenantAccess_activeEverything_returnsMember() {
        OrganizationMember member = stubMember(OrganizationMemberStatus.ACTIVE);

        assertSame(member, service.verifyTenantAccess(user, organization.getId()));
    }

    @Test
    void verifyTenantAccess_disabledUser_throwsForbiddenWithoutQueries() {
        user.setStatus(UserStatus.DISABLED);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> service.verifyTenantAccess(user, organization.getId()));

        assertEquals("GEN_SEC_002", exception.getErrorCode());
        assertEquals("DISABLED", exception.getErrorValues().get("userStatus"));
        verifyNoInteractions(organizationMemberQueryService);
    }

    @Test
    void verifyTenantAccess_noMembership_throwsForbidden() {
        UUID otherOrganizationId = UUID.randomUUID();
        when(organizationMemberQueryService.findMember(otherOrganizationId, user.getId()))
                .thenReturn(Optional.empty());

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> service.verifyTenantAccess(user, otherOrganizationId));

        assertEquals("GEN_SEC_002", exception.getErrorCode());
        assertEquals(otherOrganizationId.toString(), exception.getErrorValues().get("organizationId"));
    }

    @Test
    void verifyTenantAccess_disabledMembership_throwsMembershipDisabled() {
        stubMember(OrganizationMemberStatus.DISABLED);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> service.verifyTenantAccess(user, organization.getId()));

        assertEquals("KAY_SEC_002", exception.getErrorCode());
        assertEquals("DISABLED", exception.getErrorValues().get("memberStatus"));
    }

    @Test
    void verifyTenantAccess_suspendedOrganization_throwsMembershipDisabled() {
        organization.setStatus(OrganizationStatus.DISABLED);
        stubMember(OrganizationMemberStatus.ACTIVE);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> service.verifyTenantAccess(user, organization.getId()));

        assertEquals("KAY_SEC_002", exception.getErrorCode());
        assertEquals("DISABLED", exception.getErrorValues().get("organizationStatus"));
    }

    @Test
    void verifyTenantAccess_guestMember_throwsIncorrectRole() {
        OrganizationMember member = IdentityMocks.member(
                organization, user, OrganizationMemberRole.GUEST, OrganizationMemberStatus.ACTIVE);
        when(organizationMemberQueryService.findMember(organization.getId(), user.getId()))
                .thenReturn(Optional.of(member));

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> service.verifyTenantAccess(user, organization.getId()));

        assertEquals("GEN_SEC_003", exception.getErrorCode());
        assertEquals("GUEST", exception.getErrorValues().get("actualRole"));
    }
}

package com.korur.oba.apps.kaya.identity.helper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
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
import com.korur.oba.apps.kaya.identity.mock_data.IdentityMocks;
import com.korur.oba.apps.kaya.identity.service.OrganizationAccessQueryService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccessHelperUnitTest {

    @Mock
    private OrganizationAccessQueryService organizationAccessQueryService;
    @Mock
    private LoggingService loggingService;

    private UserAccessHelper helper;
    private Organization organization;
    private User actor;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        helper = new UserAccessHelper(organizationAccessQueryService, loggingService);
        organization = IdentityMocks.organization(OrganizationStatus.ACTIVE);
        actor = IdentityMocks.user("ada@example.com");
        otherUserId = UUID.randomUUID();
    }

    private void actorHasRole(OrganizationMemberRole role) {
        OrganizationMember member = IdentityMocks.member(organization, actor, role, OrganizationMemberStatus.ACTIVE);
        when(organizationAccessQueryService.verifyTenantAccess(actor, organization.getId())).thenReturn(member);
    }

    @Test
    void requireAdminOrSelf_actorIsTheTarget_passesWithoutLookingUpTheRole() {
        assertDoesNotThrow(() -> helper.requireAdminOrSelf(actor, organization.getId(), actor.getId()));

        verifyNoInteractions(organizationAccessQueryService);
    }

    @Test
    void requireAdminOrSelf_adminOnAnotherUser_passes() {
        actorHasRole(OrganizationMemberRole.ADMIN);

        assertDoesNotThrow(() -> helper.requireAdminOrSelf(actor, organization.getId(), otherUserId));
    }

    @Test
    void requireAdminOrSelf_ownerOnAnotherUser_passes() {
        actorHasRole(OrganizationMemberRole.OWNER);

        assertDoesNotThrow(() -> helper.requireAdminOrSelf(actor, organization.getId(), otherUserId));
    }

    @Test
    void requireAdminOrSelf_memberOnAnotherUser_throwsForbiddenWithRolesAndUserId() {
        actorHasRole(OrganizationMemberRole.MEMBER);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> helper.requireAdminOrSelf(actor, organization.getId(), otherUserId));

        assertEquals("GEN_SEC_003", exception.getErrorCode());
        assertEquals("ADMIN", exception.getErrorValues().get("requiredRole"));
        assertEquals("MEMBER", exception.getErrorValues().get("actualRole"));
        assertEquals(otherUserId.toString(), exception.getErrorValues().get("userId"));
    }
}

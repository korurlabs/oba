package com.korur.oba.shrine.organization.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.constant.AccessConstant;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.organization.constant.service.OrganizationAccessServiceLogConstant;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.validator.OrganizationValidator;

@ExtendWith(MockitoExtension.class)
class OrganizationAccessServiceImplUnitTest {

    @Mock
    private OrganizationValidator organizationValidator;

    @Mock
    private AppAccessQueryService appAccessQueryService;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private OrganizationAccessServiceImpl organizationAccessServiceImpl;

    // ── Shared helpers ──────────────────────────────────────────────────────

    private Organization stubOrganization(UUID organizationId) {
        Organization organization = Organization.builder().name("Test Org").build();
        organization.setId(organizationId);
        return organization;
    }

    private OrganizationMember stubMember(UUID memberId, Organization organization, OrganizationMemberRole role) {
        OrganizationMember member = OrganizationMember.builder()
                .organization(organization)
                .role(role)
                .status(OrganizationMemberStatus.ACTIVE)
                .build();
        member.setId(memberId);
        return member;
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Nested
    class VerifyOrganizationAccess {

        // ── Organization not found ───────────────────────────────────────────

        @Test
        void organizationDoesNotExist_throwsNotFoundException() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(organizationValidator.ensureOrganizationExists(organizationId))
                    .thenThrow(new NotFoundException(ExceptionDomainOrganizationError.NOT_ORGANIZATION_FOUND));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    organizationAccessServiceImpl.verifyOrganizationAccess(
                            organizationId, userId, OrganizationMemberRole.MEMBER));

            verify(organizationValidator).ensureOrganizationExists(organizationId);
            verify(organizationValidator, never()).ensureMemberExists(organizationId, userId);
        }

        // ── Member not found ─────────────────────────────────────────────────

        @Test
        void memberDoesNotExist_throwsNotFoundException() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId))
                    .thenThrow(new NotFoundException(ExceptionDomainOrganizationError.NOT_ORGANIZATION_MEMBER_FOUND));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    organizationAccessServiceImpl.verifyOrganizationAccess(
                            organizationId, userId, OrganizationMemberRole.MEMBER));

            verify(organizationValidator).ensureOrganizationExists(organizationId);
            verify(organizationValidator).ensureMemberExists(organizationId, userId);
        }

        // ── Successful access — role hierarchy ───────────────────────────────

        @Test
        void guestRole_withMinimumGuest_returnsOrganization() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.GUEST);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            // Act
            Organization result = organizationAccessServiceImpl.verifyOrganizationAccess(
                    organizationId, userId, OrganizationMemberRole.GUEST);

            // Assert
            assertNotNull(result);
            assertEquals(organizationId, result.getId());
        }

        @Test
        void memberRole_withMinimumMember_returnsOrganization() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.MEMBER);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            // Act
            Organization result = organizationAccessServiceImpl.verifyOrganizationAccess(
                    organizationId, userId, OrganizationMemberRole.MEMBER);

            // Assert
            assertNotNull(result);
            assertEquals(organizationId, result.getId());
        }

        @Test
        void adminRole_withMinimumAdmin_returnsOrganization() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.ADMIN);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            // Act
            Organization result = organizationAccessServiceImpl.verifyOrganizationAccess(
                    organizationId, userId, OrganizationMemberRole.ADMIN);

            // Assert
            assertNotNull(result);
            assertEquals(organizationId, result.getId());
        }

        @Test
        void ownerRole_withMinimumOwner_returnsOrganization() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.OWNER);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            // Act
            Organization result = organizationAccessServiceImpl.verifyOrganizationAccess(
                    organizationId, userId, OrganizationMemberRole.OWNER);

            // Assert
            assertNotNull(result);
            assertEquals(organizationId, result.getId());
        }

        // ── Role hierarchy: higher role passes lower minimum ─────────────────

        @Test
        void ownerRole_withMinimumAdmin_returnsOrganization() {
            // Arrange — OWNER satisfies ADMIN minimum.
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.OWNER);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            // Act
            Organization result = organizationAccessServiceImpl.verifyOrganizationAccess(
                    organizationId, userId, OrganizationMemberRole.ADMIN);

            // Assert
            assertNotNull(result);
        }

        @Test
        void adminRole_withMinimumMember_returnsOrganization() {
            // Arrange — ADMIN satisfies MEMBER minimum.
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.ADMIN);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            // Act
            Organization result = organizationAccessServiceImpl.verifyOrganizationAccess(
                    organizationId, userId, OrganizationMemberRole.MEMBER);

            // Assert
            assertNotNull(result);
        }

        @Test
        void memberRole_withMinimumGuest_returnsOrganization() {
            // Arrange — MEMBER satisfies GUEST minimum.
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.MEMBER);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            // Act
            Organization result = organizationAccessServiceImpl.verifyOrganizationAccess(
                    organizationId, userId, OrganizationMemberRole.GUEST);

            // Assert
            assertNotNull(result);
        }

        // ── Insufficient role — throws ForbiddenException ────────────────────

        @Test
        void guestRole_withMinimumMember_throwsForbiddenException() {
            // Arrange — GUEST does NOT satisfy MEMBER minimum.
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.GUEST);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    organizationAccessServiceImpl.verifyOrganizationAccess(
                            organizationId, userId, OrganizationMemberRole.MEMBER));
        }

        @Test
        void memberRole_withMinimumAdmin_throwsForbiddenException() {
            // Arrange — MEMBER does NOT satisfy ADMIN minimum.
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.MEMBER);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    organizationAccessServiceImpl.verifyOrganizationAccess(
                            organizationId, userId, OrganizationMemberRole.ADMIN));
        }

        @Test
        void adminRole_withMinimumOwner_throwsForbiddenException() {
            // Arrange — ADMIN does NOT satisfy OWNER minimum.
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.ADMIN);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    organizationAccessServiceImpl.verifyOrganizationAccess(
                            organizationId, userId, OrganizationMemberRole.OWNER));
        }

        @Test
        void guestRole_withMinimumAdmin_throwsForbiddenException() {
            // Arrange — GUEST does NOT satisfy ADMIN minimum.
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.GUEST);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    organizationAccessServiceImpl.verifyOrganizationAccess(
                            organizationId, userId, OrganizationMemberRole.ADMIN));
        }

        @Test
        void memberRole_withMinimumOwner_throwsForbiddenException() {
            // Arrange — MEMBER does NOT satisfy OWNER minimum.
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.MEMBER);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    organizationAccessServiceImpl.verifyOrganizationAccess(
                            organizationId, userId, OrganizationMemberRole.OWNER));
        }

        // ── Disabled membership ──────────────────────────────────────────────

        @Test
        void disabledMember_throwsForbiddenWithMembershipDisabledCode() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.MEMBER);
            member.setStatus(OrganizationMemberStatus.DISABLED);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            // Act & Assert
            ForbiddenException exception = assertThrows(ForbiddenException.class, () ->
                    organizationAccessServiceImpl.verifyOrganizationAccess(
                            organizationId, userId, OrganizationMemberRole.GUEST));
            assertEquals(ExceptionDomainOrganizationError.SEC_MEMBERSHIP_DISABLED.getCode(),
                    exception.getErrorCode());
        }

        @Test
        void disabledOwner_throwsForbiddenWithMembershipDisabledCode() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.OWNER);
            member.setStatus(OrganizationMemberStatus.DISABLED);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            // Act & Assert
            ForbiddenException exception = assertThrows(ForbiddenException.class, () ->
                    organizationAccessServiceImpl.verifyOrganizationAccess(
                            organizationId, userId, OrganizationMemberRole.GUEST));
            assertEquals(ExceptionDomainOrganizationError.SEC_MEMBERSHIP_DISABLED.getCode(),
                    exception.getErrorCode());
        }

        // ── Refusals log at warn, never at error ─────────────────────────────

        @Test
        void disabledOrganization_throwsForbiddenAndLogsWarn() {
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Organization organization = stubOrganization(organizationId);
            organization.setStatus(OrganizationStatus.DISABLED);
            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);

            ForbiddenException exception = assertThrows(ForbiddenException.class, () ->
                    organizationAccessServiceImpl.verifyOrganizationAccess(
                            organizationId, userId, OrganizationMemberRole.GUEST));

            assertEquals(ExceptionDomainOrganizationError.SEC_ORGANIZATION_DISABLED.getCode(),
                    exception.getErrorCode());
            verify(loggingService).warn(any(), eq(LogLayer.SERVICE),
                    eq(OrganizationAccessServiceLogConstant.ORGANIZATION_DISABLED),
                    eq(organizationId), eq(userId));
            verify(organizationValidator, never()).ensureMemberExists(organizationId, userId);
            verify(loggingService, never()).error(any(), any(), any(String.class), any(Object[].class));
        }

        @Test
        void disabledMember_logsWarnNotError() {
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.MEMBER);
            member.setStatus(OrganizationMemberStatus.DISABLED);
            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            assertThrows(ForbiddenException.class, () ->
                    organizationAccessServiceImpl.verifyOrganizationAccess(
                            organizationId, userId, OrganizationMemberRole.GUEST));

            verify(loggingService).warn(any(), eq(LogLayer.SERVICE),
                    eq(OrganizationAccessServiceLogConstant.MEMBERSHIP_DISABLED),
                    eq(organizationId), eq(userId), eq(OrganizationMemberStatus.DISABLED));
            verify(loggingService, never()).error(any(), any(), any(String.class), any(Object[].class));
        }

        @Test
        void insufficientRole_logsWarnNotError() {
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.GUEST);
            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            assertThrows(ForbiddenException.class, () ->
                    organizationAccessServiceImpl.verifyOrganizationAccess(
                            organizationId, userId, OrganizationMemberRole.ADMIN));

            verify(loggingService).warn(any(), eq(LogLayer.SERVICE),
                    eq(OrganizationAccessServiceLogConstant.INSUFFICIENT_PERMISSIONS),
                    eq(organizationId), eq(userId), eq(OrganizationMemberRole.ADMIN));
            verify(loggingService, never()).error(any(), any(), any(String.class), any(Object[].class));
        }

        @Test
        void success_logsNothing() {
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.ADMIN);
            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            organizationAccessServiceImpl.verifyOrganizationAccess(
                    organizationId, userId, OrganizationMemberRole.MEMBER);

            verify(loggingService, never()).warn(any(), any(), any(String.class), any(Object[].class));
            verify(loggingService, never()).error(any(), any(), any(String.class), any(Object[].class));
        }
    }

    @Nested
    class VerifyOrganizationAccessWithApp {

        @Test
        void appAccessGranted_returnsOrganization() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.MEMBER);
            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);
            when(appAccessQueryService.hasAppAccess(member, App.KAIZEN)).thenReturn(true);

            // Act
            Organization result = organizationAccessServiceImpl.verifyOrganizationAccess(
                    organizationId, userId, OrganizationMemberRole.MEMBER, App.KAIZEN);

            // Assert
            assertSame(organization, result);
        }

        @Test
        void appAccessMissing_throwsAppAccessDeniedNamingTheApp() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.MEMBER);
            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);
            when(appAccessQueryService.hasAppAccess(member, App.KAYA)).thenReturn(false);

            // Act
            ForbiddenException exception = assertThrows(ForbiddenException.class, () ->
                    organizationAccessServiceImpl.verifyOrganizationAccess(
                            organizationId, userId, OrganizationMemberRole.MEMBER, App.KAYA));

            // Assert
            assertEquals("APP_SEC_001", exception.getErrorCode());
            assertEquals("KAYA", exception.getErrorValues().get(AccessConstant.FIELD_APP));
        }

        @Test
        void roleTooLow_throwsBeforeTheAppIsChecked() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Organization organization = stubOrganization(organizationId);
            OrganizationMember member = stubMember(UUID.randomUUID(), organization, OrganizationMemberRole.GUEST);
            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationValidator.ensureMemberExists(organizationId, userId)).thenReturn(member);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    organizationAccessServiceImpl.verifyOrganizationAccess(
                            organizationId, userId, OrganizationMemberRole.ADMIN, App.KAIZEN));
            verifyNoInteractions(appAccessQueryService);
        }

        @Test
        void organizationDisabled_throwsBeforeTheAppIsChecked() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Organization organization = stubOrganization(organizationId);
            organization.setStatus(OrganizationStatus.DISABLED);
            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    organizationAccessServiceImpl.verifyOrganizationAccess(
                            organizationId, userId, OrganizationMemberRole.GUEST, App.KAIZEN));
            verifyNoInteractions(appAccessQueryService);
        }
    }
}

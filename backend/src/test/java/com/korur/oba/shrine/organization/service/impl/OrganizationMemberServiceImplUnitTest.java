package com.korur.oba.shrine.organization.service.impl;

// 1. Framework-level
import org.springframework.context.ApplicationEventPublisher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.audit.dto.request.AuditLogRequest;
import com.korur.oba.dojo.audit.mapper.AuditLogMapper;
import com.korur.oba.dojo.audit.service.AuditService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.access.service.AppAccessRevocationService;
import com.korur.oba.shrine.access.service.AppAccessService;
import com.korur.oba.shrine.agent.validator.AgentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.repository.UserRepository;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.dto.request.OrganizationMemberRoleUpdateRequest;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.event.AccessRevokedEvent;
import com.korur.oba.shrine.organization.mapper.OrganizationMemberMapper;
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;
import com.korur.oba.shrine.organization.validator.OrganizationValidator;

@ExtendWith(MockitoExtension.class)
class OrganizationMemberServiceImplUnitTest {

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationMemberMapper organizationMemberMapper;

    @Mock
    private OrganizationValidator organizationValidator;

    @Mock
    private AppAccessService appAccessService;

    @Mock
    private AppAccessQueryService appAccessQueryService;

    @Mock
    private AppAccessRevocationService appAccessRevocationService;

    @Mock
    private AuditLogMapper auditLogMapper;

    @Mock
    private AuditService auditService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private LoggingService loggingService;

    // Real instance: which users an agent refusal applies to is behaviour under test.
    private final UserQueryService agentUserQueryService = mock(UserQueryService.class);

    @Spy
    private AgentValidator agentValidator = new AgentValidator(agentUserQueryService, new LoggingService());

    @InjectMocks
    private OrganizationMemberServiceImpl organizationMemberServiceImpl;

    // ── Shared helpers ──────────────────────────────────────────────────────

    private void stubAuditLogMapper() {
        when(auditLogMapper.toRequest(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AuditLogRequest(null, null, null, null, null, null, null));
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Nested
    class RemoveMember {

        @Test
        void validRequest_softDeletesMember() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            User requestingUser = User.builder().build();
            requestingUser.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMember targetMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.MEMBER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            targetMember.setId(UUID.randomUUID());

            OrganizationMember actorMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.ADMIN)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            actorMember.setId(UUID.randomUUID());

            when(organizationValidator.ensureMemberExists(organizationId, targetUserId))
                    .thenReturn(targetMember);
            when(organizationValidator.ensureMemberExists(organizationId, requestingUser.getId()))
                    .thenReturn(actorMember);
            stubAuditLogMapper();

            // Act
            organizationMemberServiceImpl.removeMember(requestingUser, organizationId, targetUserId);

            // Assert — member is soft-deleted (not status change)
            verify(organizationMemberRepository).delete(targetMember);
            verify(auditService).log(any(AuditLogRequest.class));
            verify(applicationEventPublisher).publishEvent(
                    AccessRevokedEvent.member(targetUserId, organizationId));
            verify(appAccessService).removeMemberAccess(requestingUser, organizationId, targetUserId);
        }
    }

    @Nested
    class ChangeMemberRole {

        @Test
        void validRequest_updatesRoleAndReturnsMemberResponse() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();

            User requestingUser = User.builder().build();
            requestingUser.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMember targetMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.MEMBER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            targetMember.setId(memberId);

            OrganizationMember actorMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.ADMIN)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            actorMember.setId(UUID.randomUUID());

            OrganizationMemberRoleUpdateRequest request = new OrganizationMemberRoleUpdateRequest(OrganizationMemberRole.ADMIN);

            OrganizationMemberResponse expectedResponse = new OrganizationMemberResponse(
                    memberId, targetUserId, "target@example.com", null, null, OrganizationMemberRole.ADMIN, OrganizationMemberStatus.ACTIVE);

            when(organizationValidator.ensureMemberExists(organizationId, targetUserId))
                    .thenReturn(targetMember);
            when(organizationValidator.ensureMemberExists(organizationId, requestingUser.getId()))
                    .thenReturn(actorMember);
            when(organizationMemberRepository.save(targetMember)).thenReturn(targetMember);
            when(organizationMemberMapper.toResponse(targetMember)).thenReturn(expectedResponse);

            // Act
            OrganizationMemberResponse result = organizationMemberServiceImpl.changeMemberRole(
                    requestingUser, organizationId, targetUserId, request);

            // Assert
            assertNotNull(result);
            assertEquals(OrganizationMemberRole.ADMIN, result.role());
            verify(organizationMemberRepository).save(targetMember);
        }

        @Test
        void validRequest_findsAppHoldersBeforeTheRoleWriteAndPublishesTheLossesAfterIt() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            User requestingUser = User.builder().build();
            requestingUser.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMember targetMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.MEMBER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            targetMember.setId(UUID.randomUUID());

            OrganizationMember actorMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.ADMIN)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            actorMember.setId(UUID.randomUUID());

            OrganizationMemberRoleUpdateRequest request = new OrganizationMemberRoleUpdateRequest(OrganizationMemberRole.GUEST);
            Map<App, Set<UUID>> holders = Map.of(
                    App.KAZAN, Set.of(targetUserId),
                    App.KAYA, Set.of(targetUserId));

            when(organizationValidator.ensureMemberExists(organizationId, targetUserId))
                    .thenReturn(targetMember);
            when(organizationValidator.ensureMemberExists(organizationId, requestingUser.getId()))
                    .thenReturn(actorMember);
            when(appAccessQueryService.findAppHolders(
                    organizationId, EnumSet.allOf(App.class), Set.of(targetUserId)))
                    .thenAnswer((InvocationOnMock invocation) -> {
                        assertEquals(OrganizationMemberRole.MEMBER, targetMember.getRole(),
                                "the holders are read with the role before the change");
                        return holders;
                    });
            when(organizationMemberRepository.save(targetMember)).thenReturn(targetMember);

            // Act
            organizationMemberServiceImpl.changeMemberRole(requestingUser, organizationId, targetUserId, request);

            // Assert
            InOrder order = inOrder(appAccessQueryService, appAccessRevocationService, organizationMemberRepository);
            order.verify(appAccessQueryService).findAppHolders(
                    organizationId, EnumSet.allOf(App.class), Set.of(targetUserId));
            order.verify(organizationMemberRepository).save(targetMember);
            order.verify(appAccessRevocationService).publishLostAppAccess(organizationId, holders);
            assertEquals(OrganizationMemberRole.GUEST, targetMember.getRole());
        }

        @Test
        void ownerRoleInRequest_throwsValidationException() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            User requestingUser = User.builder().build();
            requestingUser.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMemberRoleUpdateRequest request = new OrganizationMemberRoleUpdateRequest(OrganizationMemberRole.OWNER);

            doThrow(new ValidationException())
                    .when(organizationValidator).ensureRoleIsNotOwner(OrganizationMemberRole.OWNER);

            // Act & Assert
            assertThrows(ValidationException.class, () ->
                    organizationMemberServiceImpl.changeMemberRole(
                            requestingUser, organizationId, targetUserId, request));
        }

        @Test
        void selfRoleUpdate_throwsForbiddenException() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID sameUserId = UUID.randomUUID();

            User requestingUser = User.builder().build();
            requestingUser.setId(sameUserId);

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMemberRoleUpdateRequest request = new OrganizationMemberRoleUpdateRequest(OrganizationMemberRole.ADMIN);

            doNothing().when(organizationValidator).ensureRoleIsNotOwner(OrganizationMemberRole.ADMIN);
            doThrow(new ForbiddenException())
                    .when(organizationValidator).ensureNotSelfRoleUpdate(sameUserId, sameUserId);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    organizationMemberServiceImpl.changeMemberRole(
                            requestingUser, organizationId, sameUserId, request));
        }
    }

    @Nested
    class SaveMember {

        @Test
        void validMember_delegatesToRepository() {
            // Arrange
            OrganizationMember member = OrganizationMember.builder()
                    .role(OrganizationMemberRole.MEMBER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            member.setId(UUID.randomUUID());

            when(organizationMemberRepository.save(member)).thenReturn(member);

            // Act
            OrganizationMember result = organizationMemberServiceImpl.saveMember(member);

            // Assert
            assertNotNull(result);
            assertEquals(member.getId(), result.getId());
            verify(organizationMemberRepository).save(member);
        }
    }

    @Nested
    class RestoreMember {

        @Test
        void deletedMemberExists_restoresSuccessfully() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            User actor = User.builder().build();
            actor.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMember deletedMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.MEMBER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            deletedMember.setId(UUID.randomUUID());

            OrganizationMember actorMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.ADMIN)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            actorMember.setId(UUID.randomUUID());

            when(organizationMemberRepository.findDeletedByOrgIdAndUserId(organizationId, targetUserId))
                    .thenReturn(Optional.of(deletedMember));
            when(organizationValidator.ensureMemberExists(organizationId, actor.getId()))
                    .thenReturn(actorMember);
            stubAuditLogMapper();

            // Act
            organizationMemberServiceImpl.restoreMember(actor, organizationId, targetUserId);

            // Assert
            verify(organizationMemberRepository).restoreById(deletedMember.getId());
            verify(auditService).log(any(AuditLogRequest.class));
        }

        @Test
        void noDeletedMember_throwsNotFoundException() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            User actor = User.builder().build();
            actor.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            when(organizationMemberRepository.findDeletedByOrgIdAndUserId(organizationId, targetUserId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    organizationMemberServiceImpl.restoreMember(actor, organizationId, targetUserId));

            verify(organizationMemberRepository, never()).restoreById(any());
        }
    }

    @Nested
    class UpdateMemberStatus_Disable {

        @Test
        void activeMember_setsStatusToDisabled() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            User actor = User.builder().build();
            actor.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMember member = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.MEMBER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            member.setId(UUID.randomUUID());

            OrganizationMember actorMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.ADMIN)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            actorMember.setId(UUID.randomUUID());

            when(organizationValidator.ensureMemberExists(organizationId, targetUserId))
                    .thenReturn(member);
            when(organizationValidator.ensureMemberExists(organizationId, actor.getId()))
                    .thenReturn(actorMember);
            when(organizationMemberRepository.save(member)).thenReturn(member);
            stubAuditLogMapper();

            // Act
            organizationMemberServiceImpl.updateMemberStatus(actor, organizationId, targetUserId, OrganizationMemberStatus.DISABLED);

            // Assert
            assertEquals(OrganizationMemberStatus.DISABLED, member.getStatus());
            verify(organizationMemberRepository).save(member);
            verify(auditService).log(any(AuditLogRequest.class));
            verify(applicationEventPublisher).publishEvent(
                    AccessRevokedEvent.member(targetUserId, organizationId));
        }

        @Test
        void alreadyDisabledMember_throwsConflictException() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            User actor = User.builder().build();
            actor.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMember member = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.MEMBER)
                    .status(OrganizationMemberStatus.DISABLED)
                    .build();
            member.setId(UUID.randomUUID());

            OrganizationMember actorMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.ADMIN)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            actorMember.setId(UUID.randomUUID());

            when(organizationValidator.ensureMemberExists(organizationId, targetUserId))
                    .thenReturn(member);
            when(organizationValidator.ensureMemberExists(organizationId, actor.getId()))
                    .thenReturn(actorMember);
            doThrow(new ConflictException()).when(organizationValidator).ensureMemberIsActive(member);

            // Act & Assert
            assertThrows(ConflictException.class, () ->
                    organizationMemberServiceImpl.updateMemberStatus(actor, organizationId, targetUserId, OrganizationMemberStatus.DISABLED));

            verify(organizationMemberRepository, never()).save(any());
        }
    }

    @Nested
    class UpdateMemberStatus_Enable {

        @Test
        void disabledMember_setsStatusToActive() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            User actor = User.builder().build();
            actor.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMember member = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.MEMBER)
                    .status(OrganizationMemberStatus.DISABLED)
                    .build();
            member.setId(UUID.randomUUID());

            OrganizationMember actorMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.ADMIN)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            actorMember.setId(UUID.randomUUID());

            when(organizationValidator.ensureMemberExists(organizationId, targetUserId))
                    .thenReturn(member);
            when(organizationValidator.ensureMemberExists(organizationId, actor.getId()))
                    .thenReturn(actorMember);
            when(organizationMemberRepository.save(member)).thenReturn(member);
            stubAuditLogMapper();

            // Act
            organizationMemberServiceImpl.updateMemberStatus(actor, organizationId, targetUserId, OrganizationMemberStatus.ACTIVE);

            // Assert
            assertEquals(OrganizationMemberStatus.ACTIVE, member.getStatus());
            verify(organizationMemberRepository).save(member);
            verify(auditService).log(any(AuditLogRequest.class));
            verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
        }

        @Test
        void nonDisabledMember_throwsConflictException() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            User actor = User.builder().build();
            actor.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMember member = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.MEMBER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            member.setId(UUID.randomUUID());

            OrganizationMember actorMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.ADMIN)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            actorMember.setId(UUID.randomUUID());

            when(organizationValidator.ensureMemberExists(organizationId, targetUserId))
                    .thenReturn(member);
            when(organizationValidator.ensureMemberExists(organizationId, actor.getId()))
                    .thenReturn(actorMember);
            doThrow(new ConflictException()).when(organizationValidator).ensureMemberIsDisabled(member);

            // Act & Assert
            assertThrows(ConflictException.class, () ->
                    organizationMemberServiceImpl.updateMemberStatus(actor, organizationId, targetUserId, OrganizationMemberStatus.ACTIVE));

            verify(organizationMemberRepository, never()).save(any());
        }
    }

    @Nested
    class LeaveOrganization {

        @Test
        void memberLeaves_softDeletesMembership() {
            // Arrange
            UUID organizationId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMember member = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.MEMBER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            member.setId(UUID.randomUUID());

            when(organizationValidator.ensureMemberExists(organizationId, user.getId()))
                    .thenReturn(member);
            stubAuditLogMapper();

            // Act
            organizationMemberServiceImpl.leaveOrganization(user, organizationId);

            // Assert — membership is soft-deleted.
            verify(organizationMemberRepository).delete(member);
            verify(auditService).log(any(AuditLogRequest.class));
            verify(applicationEventPublisher).publishEvent(
                    AccessRevokedEvent.member(user.getId(), organizationId));
            verify(appAccessService).removeMemberAccess(user, organizationId, user.getId());
        }

        @Test
        void soleOwner_throwsConflictException() {
            // Arrange
            UUID organizationId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMember member = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.OWNER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            member.setId(UUID.randomUUID());

            when(organizationValidator.ensureMemberExists(organizationId, user.getId()))
                    .thenReturn(member);
            doThrow(new ConflictException()).when(organizationValidator).ensureNotSoleOwner(organizationId, member);

            // Act & Assert
            assertThrows(ConflictException.class, () ->
                    organizationMemberServiceImpl.leaveOrganization(user, organizationId));

            verify(organizationMemberRepository, never()).delete(any(OrganizationMember.class));
        }

        @Test
        void lastMembership_setsUserToPendingOnboarding() {
            // Arrange
            UUID organizationId = UUID.randomUUID();

            User user = User.builder().status(UserStatus.ACTIVE).build();
            user.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);
            user.setDefaultOrganization(organization);

            OrganizationMember member = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.MEMBER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            member.setId(UUID.randomUUID());

            when(organizationValidator.ensureMemberExists(organizationId, user.getId()))
                    .thenReturn(member);
            stubAuditLogMapper();
            // After soft-delete, no memberships remain.
            when(organizationMemberRepository.findByUser_Id(user.getId()))
                    .thenReturn(Collections.emptyList());

            // Act
            organizationMemberServiceImpl.leaveOrganization(user, organizationId);

            // Assert — direct queries used to avoid EAGER collection merge issues.
            verify(userRepository).reassignDefaultOrganization(user.getId(), organizationId);
            verify(userRepository).updateUserStatus(user.getId(), UserStatus.PENDING_ONBOARDING);
        }
    }

    @Nested
    class AppAccessKeptOnDisableAndRestore {

        private OrganizationMember stubMember(UUID organizationId, OrganizationMemberRole role,
                                              OrganizationMemberStatus status) {
            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);
            OrganizationMember member = OrganizationMember.builder()
                    .organization(organization)
                    .role(role)
                    .status(status)
                    .build();
            member.setId(UUID.randomUUID());
            return member;
        }

        @Test
        void disableMember_keepsGroupRowsAndGrants() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();
            User actor = User.builder().build();
            actor.setId(UUID.randomUUID());
            OrganizationMember member = stubMember(organizationId, OrganizationMemberRole.MEMBER,
                    OrganizationMemberStatus.ACTIVE);
            OrganizationMember actorMember = stubMember(organizationId, OrganizationMemberRole.ADMIN,
                    OrganizationMemberStatus.ACTIVE);
            when(organizationValidator.ensureMemberExists(organizationId, targetUserId)).thenReturn(member);
            when(organizationValidator.ensureMemberExists(organizationId, actor.getId())).thenReturn(actorMember);
            when(organizationMemberRepository.save(member)).thenReturn(member);
            stubAuditLogMapper();

            // Act
            organizationMemberServiceImpl.updateMemberStatus(
                    actor, organizationId, targetUserId, OrganizationMemberStatus.DISABLED);

            // Assert
            verifyNoInteractions(appAccessService);
        }

        @Test
        void enableMember_keepsGroupRowsAndGrants() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();
            User actor = User.builder().build();
            actor.setId(UUID.randomUUID());
            OrganizationMember member = stubMember(organizationId, OrganizationMemberRole.MEMBER,
                    OrganizationMemberStatus.DISABLED);
            OrganizationMember actorMember = stubMember(organizationId, OrganizationMemberRole.ADMIN,
                    OrganizationMemberStatus.ACTIVE);
            when(organizationValidator.ensureMemberExists(organizationId, targetUserId)).thenReturn(member);
            when(organizationValidator.ensureMemberExists(organizationId, actor.getId())).thenReturn(actorMember);
            when(organizationMemberRepository.save(member)).thenReturn(member);
            stubAuditLogMapper();

            // Act
            organizationMemberServiceImpl.updateMemberStatus(
                    actor, organizationId, targetUserId, OrganizationMemberStatus.ACTIVE);

            // Assert
            verifyNoInteractions(appAccessService);
        }

        @Test
        void restoreMember_doesNotRestoreGroupRowsOrGrants() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();
            User actor = User.builder().build();
            actor.setId(UUID.randomUUID());
            OrganizationMember deletedMember = stubMember(organizationId, OrganizationMemberRole.MEMBER,
                    OrganizationMemberStatus.ACTIVE);
            OrganizationMember actorMember = stubMember(organizationId, OrganizationMemberRole.ADMIN,
                    OrganizationMemberStatus.ACTIVE);
            when(organizationMemberRepository.findDeletedByOrgIdAndUserId(organizationId, targetUserId))
                    .thenReturn(Optional.of(deletedMember));
            when(organizationValidator.ensureMemberExists(organizationId, actor.getId())).thenReturn(actorMember);
            stubAuditLogMapper();

            // Act
            organizationMemberServiceImpl.restoreMember(actor, organizationId, targetUserId);

            // Assert
            verifyNoInteractions(appAccessService);
        }
    }

    @Nested
    class AdminCannotModifyOwner {

        @Test
        void adminModifyingOwner_changeMemberRole_throwsForbiddenException() {
            // Arrange — actor is ADMIN, target member is OWNER. Validator guard rejects.
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            User actor = User.builder().build();
            actor.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMember targetMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.OWNER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            targetMember.setId(UUID.randomUUID());

            OrganizationMember actorMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.ADMIN)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            actorMember.setId(UUID.randomUUID());

            OrganizationMemberRoleUpdateRequest request = new OrganizationMemberRoleUpdateRequest(OrganizationMemberRole.ADMIN);

            when(organizationValidator.ensureMemberExists(organizationId, targetUserId))
                    .thenReturn(targetMember);
            when(organizationValidator.ensureMemberExists(organizationId, actor.getId()))
                    .thenReturn(actorMember);
            doThrow(new ForbiddenException())
                    .when(organizationValidator)
                    .ensureAdminCannotModifyOwner(OrganizationMemberRole.ADMIN, targetMember);

            // Act & Assert — guard short-circuits before any mutation.
            assertThrows(ForbiddenException.class, () ->
                    organizationMemberServiceImpl.changeMemberRole(actor, organizationId, targetUserId, request));

            verify(organizationMemberRepository, never()).save(any());
        }

        @Test
        void adminModifyingOwner_removeMember_throwsForbiddenException() {
            // Arrange — actor is ADMIN, target member is OWNER. Validator guard rejects.
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            User actor = User.builder().build();
            actor.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMember targetMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.OWNER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            targetMember.setId(UUID.randomUUID());

            OrganizationMember actorMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.ADMIN)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            actorMember.setId(UUID.randomUUID());

            when(organizationValidator.ensureMemberExists(organizationId, targetUserId))
                    .thenReturn(targetMember);
            when(organizationValidator.ensureMemberExists(organizationId, actor.getId()))
                    .thenReturn(actorMember);
            doThrow(new ForbiddenException())
                    .when(organizationValidator)
                    .ensureAdminCannotModifyOwner(OrganizationMemberRole.ADMIN, targetMember);

            // Act & Assert — guard short-circuits before delete.
            assertThrows(ForbiddenException.class, () ->
                    organizationMemberServiceImpl.removeMember(actor, organizationId, targetUserId));

            verify(organizationMemberRepository, never()).delete(any(OrganizationMember.class));
            verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
        }

        @Test
        void adminModifyingOwner_updateMemberStatus_throwsForbiddenException() {
            // Arrange — actor is ADMIN, target member is OWNER. Validator guard rejects.
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            User actor = User.builder().build();
            actor.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMember targetMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.OWNER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            targetMember.setId(UUID.randomUUID());

            OrganizationMember actorMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.ADMIN)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            actorMember.setId(UUID.randomUUID());

            when(organizationValidator.ensureMemberExists(organizationId, targetUserId))
                    .thenReturn(targetMember);
            when(organizationValidator.ensureMemberExists(organizationId, actor.getId()))
                    .thenReturn(actorMember);
            doThrow(new ForbiddenException())
                    .when(organizationValidator)
                    .ensureAdminCannotModifyOwner(OrganizationMemberRole.ADMIN, targetMember);

            // Act & Assert — guard short-circuits before any save.
            assertThrows(ForbiddenException.class, () ->
                    organizationMemberServiceImpl.updateMemberStatus(
                            actor, organizationId, targetUserId, OrganizationMemberStatus.DISABLED));

            verify(organizationMemberRepository, never()).save(any());
        }

        @Test
        void adminModifyingOwner_restoreMember_throwsForbiddenException() {
            // Arrange — actor is ADMIN, soft-deleted target was an OWNER. Validator guard rejects.
            UUID organizationId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            User actor = User.builder().build();
            actor.setId(UUID.randomUUID());

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMember deletedMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.OWNER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            deletedMember.setId(UUID.randomUUID());

            OrganizationMember actorMember = OrganizationMember.builder()
                    .organization(organization)
                    .role(OrganizationMemberRole.ADMIN)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            actorMember.setId(UUID.randomUUID());

            when(organizationMemberRepository.findDeletedByOrgIdAndUserId(organizationId, targetUserId))
                    .thenReturn(Optional.of(deletedMember));
            when(organizationValidator.ensureMemberExists(organizationId, actor.getId()))
                    .thenReturn(actorMember);
            doThrow(new ForbiddenException())
                    .when(organizationValidator)
                    .ensureAdminCannotModifyOwner(OrganizationMemberRole.ADMIN, deletedMember);

            // Act & Assert — guard short-circuits before restore.
            assertThrows(ForbiddenException.class, () ->
                    organizationMemberServiceImpl.restoreMember(actor, organizationId, targetUserId));

            verify(organizationMemberRepository, never()).restoreById(any());
        }
    }

    @Nested
    class AgentRefusals {

        private final UUID organizationId = UUID.randomUUID();
        private User admin;
        private User agent;
        private OrganizationMember agentMember;

        @BeforeEach
        void setUpAgent() {
            admin = User.builder().id(UUID.randomUUID()).build();
            agent = User.builder().id(UUID.randomUUID()).kind(UserKind.AGENT).build();
            agentMember = OrganizationMember.builder()
                    .user(agent)
                    .role(OrganizationMemberRole.MEMBER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            agentMember.setId(UUID.randomUUID());
        }

        @Test
        void removeMember_agentTarget_throwsAgentTargetRefusedAndRemovesNothing() {
            when(organizationValidator.ensureMemberExists(organizationId, agent.getId())).thenReturn(agentMember);

            ValidationException exception = assertThrows(ValidationException.class,
                    () -> organizationMemberServiceImpl.removeMember(admin, organizationId, agent.getId()));

            assertEquals("AGT_VAL_001", exception.getErrorCode());
            verify(organizationMemberRepository, never()).delete(any(OrganizationMember.class));
            verifyNoInteractions(appAccessService, applicationEventPublisher);
        }

        @Test
        void restoreMember_agentTarget_throwsAgentTargetRefusedAndRestoresNothing() {
            when(organizationMemberRepository.findDeletedByOrgIdAndUserId(organizationId, agent.getId()))
                    .thenReturn(Optional.of(agentMember));

            ValidationException exception = assertThrows(ValidationException.class,
                    () -> organizationMemberServiceImpl.restoreMember(admin, organizationId, agent.getId()));

            assertEquals("AGT_VAL_001", exception.getErrorCode());
            verify(organizationMemberRepository, never()).restoreById(any());
        }

        @Test
        void updateMemberStatus_agentTarget_throwsAgentTargetRefusedAndKeepsStatus() {
            when(organizationValidator.ensureMemberExists(organizationId, agent.getId())).thenReturn(agentMember);

            ValidationException exception = assertThrows(ValidationException.class,
                    () -> organizationMemberServiceImpl.updateMemberStatus(admin, organizationId, agent.getId(),
                            OrganizationMemberStatus.DISABLED));

            assertEquals("AGT_VAL_001", exception.getErrorCode());
            assertEquals(OrganizationMemberStatus.ACTIVE, agentMember.getStatus());
            verify(organizationMemberRepository, never()).save(any(OrganizationMember.class));
        }

        @Test
        void changeMemberRole_agentTargetToAdmin_throwsAgentTargetRefusedAndKeepsRole() {
            when(organizationValidator.ensureMemberExists(organizationId, agent.getId())).thenReturn(agentMember);

            ValidationException exception = assertThrows(ValidationException.class,
                    () -> organizationMemberServiceImpl.changeMemberRole(admin, organizationId, agent.getId(),
                            new OrganizationMemberRoleUpdateRequest(OrganizationMemberRole.ADMIN)));

            assertEquals("AGT_VAL_001", exception.getErrorCode());
            assertEquals(OrganizationMemberRole.MEMBER, agentMember.getRole());
            verify(organizationMemberRepository, never()).save(any(OrganizationMember.class));
        }

        @Test
        void leaveOrganization_agentCaller_throwsForbiddenAndLeavesNothing() {
            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> organizationMemberServiceImpl.leaveOrganization(agent, organizationId));

            assertEquals("AGT_SEC_002", exception.getErrorCode());
            verifyNoInteractions(organizationValidator, organizationMemberRepository, applicationEventPublisher);
        }
    }
}

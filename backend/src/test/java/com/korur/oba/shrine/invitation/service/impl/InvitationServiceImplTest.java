package com.korur.oba.shrine.invitation.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.notification.service.EmailNotificationService;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.identity.service.UserService;
import com.korur.oba.shrine.invitation.dto.request.InvitationCreateRequest;
import com.korur.oba.shrine.invitation.dto.request.InvitationDecisionRequest;
import com.korur.oba.shrine.invitation.dto.response.InvitationResponse;
import com.korur.oba.shrine.invitation.entity.Invitation;
import com.korur.oba.shrine.invitation.enums.InvitationDecision;
import com.korur.oba.shrine.invitation.enums.InvitationStatus;
import com.korur.oba.shrine.invitation.exception.ExceptionDomainInvitationError;
import com.korur.oba.shrine.invitation.mapper.InvitationMapper;
import com.korur.oba.shrine.invitation.repository.InvitationRepository;
import com.korur.oba.shrine.invitation.validator.InvitationValidator;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.service.OrganizationMemberService;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;

@ExtendWith(MockitoExtension.class)
class InvitationServiceImplTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private InvitationMapper invitationMapper;

    @Mock
    private InvitationValidator invitationValidator;

    @Mock
    private OrganizationQueryService organizationQueryService;

    @Mock
    private OrganizationMemberService organizationMemberService;

    @Mock
    private com.korur.oba.shrine.organization.repository.OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private UserService userService;

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private InvitationServiceImpl invitationServiceImpl;

    // ── Shared helpers ────────────────────────────────────────────────────────

    private User stubUser(String email) {
        UserProfile profile = UserProfile.builder()
                .firstName("Jane")
                .lastName("Doe")
                .build();
        User user = User.builder()
                .email(email)
                .profile(profile)
                .build();
        user.setId(UUID.randomUUID());
        return user;
    }

    private Organization stubOrganization() {
        Organization org = Organization.builder().name("Acme Corp").build();
        org.setId(UUID.randomUUID());
        return org;
    }

    private Invitation stubPendingInvitation(Organization org, User invitedBy, String email) {
        Invitation invitation = Invitation.builder()
                .organization(org)
                .email(email)
                .role(OrganizationMemberRole.MEMBER)
                .status(InvitationStatus.PENDING)
                .invitedBy(invitedBy)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        invitation.setId(UUID.randomUUID());
        return invitation;
    }

    private InvitationResponse stubResponse(UUID invitationId) {
        return new InvitationResponse(
                invitationId, UUID.randomUUID(), "Acme Corp",
                "invitee@example.com", OrganizationMemberRole.MEMBER,
                InvitationStatus.PENDING, "admin@example.com",
                null, Instant.now().plusSeconds(3600), null, Instant.now());
    }

    // ── CreateInvitation ──────────────────────────────────────────────────────

    @Nested
    class CreateInvitation {

        @Test
        void validRequest_createsPendingInvitationWithExpiryAndSendsEmail() {
            // Arrange
            User currentUser = stubUser("admin@example.com");
            UUID organizationId = UUID.randomUUID();
            InvitationCreateRequest request = new InvitationCreateRequest("invitee@example.com", OrganizationMemberRole.MEMBER);

            Organization organization = stubOrganization();
            organization.setId(organizationId);

            Invitation savedInvitation = stubPendingInvitation(organization, currentUser, "invitee@example.com");
            InvitationResponse expectedResponse = stubResponse(savedInvitation.getId());

            when(userQueryService.findUserByEmail(request.email())).thenReturn(Optional.empty());
            when(organizationQueryService.getOrganizationEntityById(organizationId)).thenReturn(organization);
            when(invitationRepository.save(any(Invitation.class))).thenReturn(savedInvitation);
            when(invitationMapper.toResponse(savedInvitation)).thenReturn(expectedResponse);

            // Act
            InvitationResponse result = invitationServiceImpl.createInvitation(currentUser, organizationId, request);

            // Assert
            assertNotNull(result);
            assertEquals(expectedResponse.id(), result.id());

            ArgumentCaptor<Invitation> invitationCaptor = ArgumentCaptor.forClass(Invitation.class);
            verify(invitationRepository).save(invitationCaptor.capture());
            Invitation captured = invitationCaptor.getValue();
            assertEquals(InvitationStatus.PENDING, captured.getStatus());
            assertNotNull(captured.getExpiresAt());
            assertEquals("invitee@example.com", captured.getEmail());

            verify(emailNotificationService).sendEmail(any());
        }

        @Test
        void existingActiveMember_throwsValidationException() {
            // Arrange
            User currentUser = stubUser("admin@example.com");
            UUID organizationId = UUID.randomUUID();
            InvitationCreateRequest request = new InvitationCreateRequest("member@example.com", OrganizationMemberRole.MEMBER);

            User existingUser = stubUser("member@example.com");
            when(userQueryService.findUserByEmail(request.email())).thenReturn(Optional.of(existingUser));

            doThrow(new ValidationException(ExceptionDomainInvitationError.VAL_ALREADY_MEMBER))
                    .when(invitationValidator).ensureNotAlreadyMember(organizationId, existingUser.getId());

            // Act & Assert
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    invitationServiceImpl.createInvitation(currentUser, organizationId, request));

            assertEquals(ExceptionDomainInvitationError.VAL_ALREADY_MEMBER.getCode(), exception.getErrorCode());
            verify(invitationRepository, never()).save(any());
        }

        @Test
        void pendingInvitationAlreadyExists_throwsValidationException() {
            // Arrange
            User currentUser = stubUser("admin@example.com");
            UUID organizationId = UUID.randomUUID();
            InvitationCreateRequest request = new InvitationCreateRequest("invitee@example.com", OrganizationMemberRole.MEMBER);

            when(userQueryService.findUserByEmail(request.email())).thenReturn(Optional.empty());
            doThrow(new ValidationException(ExceptionDomainInvitationError.VAL_PENDING_INVITATION_EXISTS))
                    .when(invitationValidator).ensureNoPendingInvitation(organizationId, request.email());

            // Act & Assert
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    invitationServiceImpl.createInvitation(currentUser, organizationId, request));

            assertEquals(ExceptionDomainInvitationError.VAL_PENDING_INVITATION_EXISTS.getCode(), exception.getErrorCode());
            verify(invitationRepository, never()).save(any());
        }

        @Test
        void inviterEmailMatchesRequestEmail_throwsValidationException() {
            // Arrange
            User currentUser = stubUser("self@example.com");
            UUID organizationId = UUID.randomUUID();
            InvitationCreateRequest request = new InvitationCreateRequest("self@example.com", OrganizationMemberRole.MEMBER);

            doThrow(new ValidationException(ExceptionDomainInvitationError.VAL_CANNOT_INVITE_SELF))
                    .when(invitationValidator).ensureCannotInviteSelf(currentUser, request.email());

            // Act & Assert
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    invitationServiceImpl.createInvitation(currentUser, organizationId, request));

            assertEquals(ExceptionDomainInvitationError.VAL_CANNOT_INVITE_SELF.getCode(), exception.getErrorCode());
            verify(invitationRepository, never()).save(any());
        }

        @Test
        void roleIsOwner_throwsValidationException() {
            // Arrange
            User currentUser = stubUser("admin@example.com");
            UUID organizationId = UUID.randomUUID();
            InvitationCreateRequest request = new InvitationCreateRequest("invitee@example.com", OrganizationMemberRole.OWNER);

            doThrow(new ValidationException(ExceptionDomainInvitationError.VAL_CANNOT_INVITE_AS_OWNER))
                    .when(invitationValidator).ensureRoleNotOwner(OrganizationMemberRole.OWNER);

            // Act & Assert
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    invitationServiceImpl.createInvitation(currentUser, organizationId, request));

            assertEquals(ExceptionDomainInvitationError.VAL_CANNOT_INVITE_AS_OWNER.getCode(), exception.getErrorCode());
            verify(invitationRepository, never()).save(any());
        }
    }

    // ── ProcessDecision ───────────────────────────────────────────────────────

    @Nested
    class ProcessDecision {

        @Test
        void acceptPendingInvitation_setsStatusAcceptedAndCreatesMember() {
            // Arrange
            User user = stubUser("invitee@example.com");
            user.setDefaultOrganization(null);

            Organization org = stubOrganization();
            Invitation invitation = stubPendingInvitation(org, stubUser("admin@example.com"), "invitee@example.com");
            invitation.setExpiresAt(Instant.now().plusSeconds(3600));

            InvitationDecisionRequest request = new InvitationDecisionRequest(invitation.getId(), InvitationDecision.ACCEPT);
            InvitationResponse expectedResponse = stubResponse(invitation.getId());

            when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
            when(organizationMemberRepository.findDeletedByOrgIdAndUserId(org.getId(), user.getId()))
                    .thenReturn(Optional.empty());
            when(invitationRepository.save(invitation)).thenReturn(invitation);
            when(invitationMapper.toResponse(invitation)).thenReturn(expectedResponse);

            // Act
            InvitationResponse result = invitationServiceImpl.processDecision(user, request);

            // Assert
            assertNotNull(result);
            assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus());
            assertNotNull(invitation.getAcceptedAt());
            verify(organizationMemberService).saveMember(any());
            verify(userService).setDefaultOrganization(user.getId(), org.getId());
        }

        @Test
        void acceptPendingInvitationWithExistingDefaultOrg_doesNotOverrideDefaultOrg() {
            // Arrange
            User user = stubUser("invitee@example.com");
            Organization existingDefaultOrg = stubOrganization();
            user.setDefaultOrganization(existingDefaultOrg);

            Organization invitedOrg = stubOrganization();
            Invitation invitation = stubPendingInvitation(invitedOrg, stubUser("admin@example.com"), "invitee@example.com");
            invitation.setExpiresAt(Instant.now().plusSeconds(3600));

            InvitationDecisionRequest request = new InvitationDecisionRequest(invitation.getId(), InvitationDecision.ACCEPT);

            when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
            when(organizationMemberRepository.findDeletedByOrgIdAndUserId(invitedOrg.getId(), user.getId()))
                    .thenReturn(Optional.empty());
            when(invitationRepository.save(invitation)).thenReturn(invitation);
            when(invitationMapper.toResponse(invitation)).thenReturn(stubResponse(invitation.getId()));

            // Act
            invitationServiceImpl.processDecision(user, request);

            // Assert
            verify(userService, never()).setDefaultOrganization(any(), any());
        }

        @Test
        void declinePendingInvitation_setsStatusDeclinedAndDeclinedAt() {
            // Arrange
            User user = stubUser("invitee@example.com");
            Organization org = stubOrganization();
            Invitation invitation = stubPendingInvitation(org, stubUser("admin@example.com"), "invitee@example.com");
            invitation.setExpiresAt(Instant.now().plusSeconds(3600));

            InvitationDecisionRequest request = new InvitationDecisionRequest(invitation.getId(), InvitationDecision.DECLINE);
            InvitationResponse expectedResponse = stubResponse(invitation.getId());

            when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
            when(invitationRepository.save(invitation)).thenReturn(invitation);
            when(invitationMapper.toResponse(invitation)).thenReturn(expectedResponse);

            // Act
            InvitationResponse result = invitationServiceImpl.processDecision(user, request);

            // Assert
            assertNotNull(result);
            assertEquals(InvitationStatus.DECLINED, invitation.getStatus());
            assertNotNull(invitation.getDeclinedAt());
            verify(organizationMemberService, never()).saveMember(any());
        }

        @Test
        void expiredInvitationOnAccess_marksExpiredAndThrowsValidationException() {
            // Arrange
            User user = stubUser("invitee@example.com");
            Organization org = stubOrganization();
            Invitation invitation = stubPendingInvitation(org, stubUser("admin@example.com"), "invitee@example.com");
            invitation.setExpiresAt(Instant.now().minusSeconds(1));

            InvitationDecisionRequest request = new InvitationDecisionRequest(invitation.getId(), InvitationDecision.ACCEPT);

            when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
            when(invitationRepository.save(invitation)).thenReturn(invitation);

            // Act & Assert
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    invitationServiceImpl.processDecision(user, request));

            assertEquals(ExceptionDomainInvitationError.VAL_INVITATION_EXPIRED.getCode(), exception.getErrorCode());
            assertEquals(InvitationStatus.EXPIRED, invitation.getStatus());
            verify(invitationRepository).save(invitation);
            verify(organizationMemberService, never()).saveMember(any());
        }

        @Test
        void alreadyAcceptedInvitation_throwsValidationException() {
            // Arrange
            User user = stubUser("invitee@example.com");
            UUID invitationId = UUID.randomUUID();
            InvitationDecisionRequest request = new InvitationDecisionRequest(invitationId, InvitationDecision.ACCEPT);

            Invitation invitation = Invitation.builder()
                    .status(InvitationStatus.ACCEPTED)
                    .email("invitee@example.com")
                    .build();
            invitation.setId(invitationId);

            when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
            doThrow(new ValidationException(ExceptionDomainInvitationError.VAL_INVITATION_ALREADY_ACCEPTED))
                    .when(invitationValidator).ensureInvitationIsActionable(invitation);

            // Act & Assert
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    invitationServiceImpl.processDecision(user, request));

            assertEquals(ExceptionDomainInvitationError.VAL_INVITATION_ALREADY_ACCEPTED.getCode(), exception.getErrorCode());
        }

        @Test
        void revokedInvitation_throwsValidationException() {
            // Arrange
            User user = stubUser("invitee@example.com");
            UUID invitationId = UUID.randomUUID();
            InvitationDecisionRequest request = new InvitationDecisionRequest(invitationId, InvitationDecision.ACCEPT);

            Invitation invitation = Invitation.builder()
                    .status(InvitationStatus.REVOKED)
                    .email("invitee@example.com")
                    .build();
            invitation.setId(invitationId);

            when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
            doThrow(new ValidationException(ExceptionDomainInvitationError.VAL_INVITATION_REVOKED))
                    .when(invitationValidator).ensureInvitationIsActionable(invitation);

            // Act & Assert
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    invitationServiceImpl.processDecision(user, request));

            assertEquals(ExceptionDomainInvitationError.VAL_INVITATION_REVOKED.getCode(), exception.getErrorCode());
        }

        @Test
        void invitationEmailDoesNotMatchUser_throwsValidationException() {
            // Arrange
            User user = stubUser("other@example.com");
            UUID invitationId = UUID.randomUUID();
            InvitationDecisionRequest request = new InvitationDecisionRequest(invitationId, InvitationDecision.ACCEPT);

            Invitation invitation = Invitation.builder()
                    .status(InvitationStatus.PENDING)
                    .email("invitee@example.com")
                    .build();
            invitation.setId(invitationId);

            when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
            doThrow(new ValidationException(ExceptionDomainInvitationError.VAL_EMAIL_MISMATCH))
                    .when(invitationValidator).ensureInvitationBelongsToUser(invitation, user.getEmail());

            // Act & Assert
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    invitationServiceImpl.processDecision(user, request));

            assertEquals(ExceptionDomainInvitationError.VAL_EMAIL_MISMATCH.getCode(), exception.getErrorCode());
        }

        @Test
        void invitationNotFound_throwsNotFoundException() {
            // Arrange
            User user = stubUser("invitee@example.com");
            UUID invitationId = UUID.randomUUID();
            InvitationDecisionRequest request = new InvitationDecisionRequest(invitationId, InvitationDecision.ACCEPT);

            when(invitationRepository.findById(invitationId)).thenReturn(Optional.empty());

            // Act & Assert
            NotFoundException exception = assertThrows(NotFoundException.class, () ->
                    invitationServiceImpl.processDecision(user, request));

            assertEquals(ExceptionDomainInvitationError.NOT_INVITATION_FOUND.getCode(), exception.getErrorCode());
        }
    }

    // ── RevokeInvitation ──────────────────────────────────────────────────────

    @Nested
    class RevokeInvitation {

        @Test
        void adminRevokesOwnOrgPendingInvitation_setsStatusRevoked() {
            // Arrange
            User admin = stubUser("admin@example.com");
            UUID organizationId = UUID.randomUUID();

            Organization org = stubOrganization();
            org.setId(organizationId);

            Invitation invitation = stubPendingInvitation(org, admin, "invitee@example.com");

            when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
            when(invitationRepository.save(invitation)).thenReturn(invitation);

            // Act
            invitationServiceImpl.revokeInvitation(admin, organizationId, invitation.getId());

            // Assert
            assertEquals(InvitationStatus.REVOKED, invitation.getStatus());
            verify(invitationRepository).save(invitation);
        }

        @Test
        void invitationNotFound_throwsNotFoundException() {
            // Arrange
            User admin = stubUser("admin@example.com");
            UUID organizationId = UUID.randomUUID();
            UUID invitationId = UUID.randomUUID();

            when(invitationRepository.findById(invitationId)).thenReturn(Optional.empty());

            // Act & Assert
            NotFoundException exception = assertThrows(NotFoundException.class, () ->
                    invitationServiceImpl.revokeInvitation(admin, organizationId, invitationId));

            assertEquals(ExceptionDomainInvitationError.NOT_INVITATION_FOUND.getCode(), exception.getErrorCode());
        }

        @Test
        void invitationBelongsToDifferentOrg_throwsForbiddenException() {
            // Arrange
            User admin = stubUser("admin@example.com");
            UUID organizationId = UUID.randomUUID();

            Organization differentOrg = stubOrganization();
            // differentOrg has a different ID from organizationId

            Invitation invitation = stubPendingInvitation(differentOrg, admin, "invitee@example.com");

            when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

            // Act & Assert
            assertThrows(com.korur.oba.dojo.exception.internal.auth.ForbiddenException.class, () ->
                    invitationServiceImpl.revokeInvitation(admin, organizationId, invitation.getId()));

            verify(invitationRepository, never()).save(any());
        }

        @Test
        void invitationNotPending_throwsValidationException() {
            // Arrange
            User admin = stubUser("admin@example.com");
            UUID organizationId = UUID.randomUUID();

            Organization org = stubOrganization();
            org.setId(organizationId);

            Invitation invitation = Invitation.builder()
                    .organization(org)
                    .email("invitee@example.com")
                    .role(OrganizationMemberRole.MEMBER)
                    .status(InvitationStatus.ACCEPTED)
                    .invitedBy(admin)
                    .build();
            invitation.setId(UUID.randomUUID());

            when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
            doThrow(new ValidationException(ExceptionDomainInvitationError.VAL_INVITATION_NOT_PENDING))
                    .when(invitationValidator).ensureInvitationIsPending(invitation);

            // Act & Assert
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    invitationServiceImpl.revokeInvitation(admin, organizationId, invitation.getId()));

            assertEquals(ExceptionDomainInvitationError.VAL_INVITATION_NOT_PENDING.getCode(), exception.getErrorCode());
        }
    }
}

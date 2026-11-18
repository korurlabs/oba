package com.korur.oba.shrine.invitation.validator;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.invitation.entity.Invitation;
import com.korur.oba.shrine.invitation.enums.InvitationStatus;
import com.korur.oba.shrine.invitation.exception.ExceptionDomainInvitationError;
import com.korur.oba.shrine.invitation.repository.InvitationRepository;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class InvitationValidatorTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private InvitationValidator invitationValidator;

    // ── Shared helpers ────────────────────────────────────────────────────────

    private Invitation stubInvitation(String email, InvitationStatus status) {
        Invitation invitation = Invitation.builder()
                .email(email)
                .status(status)
                .build();
        invitation.setId(UUID.randomUUID());
        return invitation;
    }

    // ── EnsureInvitationBelongsToUser ─────────────────────────────────────────

    @Nested
    class EnsureInvitationBelongsToUser {

        @Test
        void emailMatchesExactly_doesNotThrow() {
            // Arrange
            Invitation invitation = stubInvitation("invitee@example.com", InvitationStatus.PENDING);

            // Act & Assert
            assertDoesNotThrow(() ->
                    invitationValidator.ensureInvitationBelongsToUser(invitation, "invitee@example.com"));
        }

        @Test
        void emailMatchesCaseInsensitively_doesNotThrow() {
            // Arrange
            Invitation invitation = stubInvitation("INVITEE@EXAMPLE.COM", InvitationStatus.PENDING);

            // Act & Assert
            assertDoesNotThrow(() ->
                    invitationValidator.ensureInvitationBelongsToUser(invitation, "invitee@example.com"));
        }

        @Test
        void emailMatchesInviteeUpperVsUserLower_doesNotThrow() {
            // Arrange
            Invitation invitation = stubInvitation("invitee@example.com", InvitationStatus.PENDING);

            // Act & Assert
            assertDoesNotThrow(() ->
                    invitationValidator.ensureInvitationBelongsToUser(invitation, "INVITEE@EXAMPLE.COM"));
        }

        @Test
        void emailDoesNotMatch_throwsValidationException() {
            // Arrange
            Invitation invitation = stubInvitation("invitee@example.com", InvitationStatus.PENDING);

            // Act
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    invitationValidator.ensureInvitationBelongsToUser(invitation, "other@example.com"));

            // Assert
            assertEquals(ExceptionDomainInvitationError.VAL_EMAIL_MISMATCH.getCode(), exception.getErrorCode());
        }
    }

    // ── EnsureInvitationIsActionable ──────────────────────────────────────────

    @Nested
    class EnsureInvitationIsActionable {

        @Test
        void pendingInvitation_doesNotThrow() {
            // Arrange
            Invitation invitation = stubInvitation("invitee@example.com", InvitationStatus.PENDING);

            // Act & Assert
            assertDoesNotThrow(() -> invitationValidator.ensureInvitationIsActionable(invitation));
        }

        @Test
        void acceptedInvitation_throwsAlreadyAcceptedError() {
            // Arrange
            Invitation invitation = stubInvitation("invitee@example.com", InvitationStatus.ACCEPTED);

            // Act
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    invitationValidator.ensureInvitationIsActionable(invitation));

            // Assert
            assertEquals(ExceptionDomainInvitationError.VAL_INVITATION_ALREADY_ACCEPTED.getCode(), exception.getErrorCode());
        }

        @Test
        void revokedInvitation_throwsRevokedError() {
            // Arrange
            Invitation invitation = stubInvitation("invitee@example.com", InvitationStatus.REVOKED);

            // Act
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    invitationValidator.ensureInvitationIsActionable(invitation));

            // Assert
            assertEquals(ExceptionDomainInvitationError.VAL_INVITATION_REVOKED.getCode(), exception.getErrorCode());
        }

        @Test
        void declinedInvitation_throwsDeclinedError() {
            // Arrange
            Invitation invitation = stubInvitation("invitee@example.com", InvitationStatus.DECLINED);

            // Act
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    invitationValidator.ensureInvitationIsActionable(invitation));

            // Assert
            assertEquals(ExceptionDomainInvitationError.VAL_INVITATION_DECLINED.getCode(), exception.getErrorCode());
        }

        @Test
        void expiredInvitation_throwsExpiredError() {
            // Arrange
            Invitation invitation = stubInvitation("invitee@example.com", InvitationStatus.EXPIRED);

            // Act
            ValidationException exception = assertThrows(ValidationException.class, () ->
                    invitationValidator.ensureInvitationIsActionable(invitation));

            // Assert
            assertEquals(ExceptionDomainInvitationError.VAL_INVITATION_EXPIRED.getCode(), exception.getErrorCode());
        }
    }
}

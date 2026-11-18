package com.korur.oba.shrine.invitation.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.invitation.constant.InvitationConstant;
import com.korur.oba.shrine.invitation.entity.Invitation;
import com.korur.oba.shrine.invitation.enums.InvitationStatus;
import com.korur.oba.shrine.invitation.exception.ExceptionDomainInvitationError;
import com.korur.oba.shrine.invitation.repository.InvitationRepository;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Validates business rules for invitation operations.
 */
@Component
@RequiredArgsConstructor
public class InvitationValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(InvitationValidator.class);

    private static final String PENDING_INVITATION_EXISTS = "Pending invitation already exists for email: {} in organization: {}";
    private static final String USER_ALREADY_MEMBER = "User is already a member of organization: {}";
    private static final String CANNOT_INVITE_SELF = "User attempted to invite themselves: {}";
    private static final String CANNOT_INVITE_AS_OWNER = "Cannot invite user as OWNER role";
    private static final String INVITATION_NOT_PENDING = "Invitation is not in PENDING status: {}";
    private static final String INVITATION_EMAIL_MISMATCH = "Invitation email mismatch — invitation: {}, user: {}";
    private static final String INVITATION_NOT_ACTIONABLE = "Invitation is not actionable — status: {} for invitation: {}";

    private final InvitationRepository invitationRepository;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final LoggingService loggingService;

    /**
     * Ensures no pending invitation exists for the same email in the organization.
     */
    public void ensureNoPendingInvitation(UUID organizationId, String email) {
        Optional<Invitation> existing = invitationRepository
                .findByEmailIgnoreCaseAndOrganizationIdAndStatus(email, organizationId, InvitationStatus.PENDING);
        if (existing.isPresent()) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, PENDING_INVITATION_EXISTS, email, organizationId);
            throw new ValidationException(ExceptionDomainInvitationError.VAL_PENDING_INVITATION_EXISTS,
                    Map.of(InvitationConstant.FIELD_EMAIL, email));
        }
    }

    /**
     * Ensures the user does not already have a membership record in the organization.
     */
    public void ensureNotAlreadyMember(UUID organizationId, UUID userId) {
        Optional<OrganizationMember> existingMember = organizationMemberQueryService.findMember(organizationId, userId);
        if (existingMember.isPresent()) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, USER_ALREADY_MEMBER, organizationId);
            throw new ValidationException(ExceptionDomainInvitationError.VAL_ALREADY_MEMBER,
                    Map.of(InvitationConstant.FIELD_ORGANIZATION_ID, organizationId.toString()));
        }
    }

    /**
     * Ensures the inviter is not inviting themselves.
     */
    public void ensureCannotInviteSelf(User inviter, String email) {
        if (inviter.getEmail().equalsIgnoreCase(email)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, CANNOT_INVITE_SELF, email);
            throw new ValidationException(ExceptionDomainInvitationError.VAL_CANNOT_INVITE_SELF,
                    Map.of(InvitationConstant.FIELD_EMAIL, email));
        }
    }

    /**
     * Ensures the invited role is not OWNER.
     */
    public void ensureRoleNotOwner(OrganizationMemberRole role) {
        if (role == OrganizationMemberRole.OWNER) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, CANNOT_INVITE_AS_OWNER);
            throw new ValidationException(ExceptionDomainInvitationError.VAL_CANNOT_INVITE_AS_OWNER);
        }
    }

    /**
     * Ensures the invitation is in PENDING status (for revoke operations).
     */
    public void ensureInvitationIsPending(Invitation invitation) {
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, INVITATION_NOT_PENDING, invitation.getId());
            throw new ValidationException(ExceptionDomainInvitationError.VAL_INVITATION_NOT_PENDING,
                    Map.of(InvitationConstant.FIELD_INVITATION_ID, invitation.getId().toString()));
        }
    }

    /**
     * Ensures the invitation belongs to the given user by comparing email addresses.
     */
    public void ensureInvitationBelongsToUser(Invitation invitation, String email) {
        if (!invitation.getEmail().equalsIgnoreCase(email)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, INVITATION_EMAIL_MISMATCH,
                    invitation.getEmail(), email);
            throw new ValidationException(ExceptionDomainInvitationError.VAL_EMAIL_MISMATCH,
                    Map.of(InvitationConstant.FIELD_EMAIL, email));
        }
    }

    /**
     * Ensures the invitation is in an actionable state (PENDING).
     * Throws specific errors for each non-actionable status.
     */
    public void ensureInvitationIsActionable(Invitation invitation) {
        InvitationStatus status = invitation.getStatus();
        if (status == InvitationStatus.PENDING) {
            return;
        }

        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, INVITATION_NOT_ACTIONABLE,
                status, invitation.getId());

        ExceptionDomainInvitationError error = switch (status) {
            case ACCEPTED -> ExceptionDomainInvitationError.VAL_INVITATION_ALREADY_ACCEPTED;
            case REVOKED -> ExceptionDomainInvitationError.VAL_INVITATION_REVOKED;
            case DECLINED -> ExceptionDomainInvitationError.VAL_INVITATION_DECLINED;
            case EXPIRED -> ExceptionDomainInvitationError.VAL_INVITATION_EXPIRED;
            default -> ExceptionDomainInvitationError.VAL_INVITATION_NOT_PENDING;
        };

        throw new ValidationException(error,
                Map.of(InvitationConstant.FIELD_INVITATION_ID, invitation.getId().toString()));
    }
}

package com.korur.oba.shrine.invitation.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.notification.dto.EmailRequest;
import com.korur.oba.connect.notification.enums.EmailTemplateType;
import com.korur.oba.connect.notification.service.EmailNotificationService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.identity.service.UserService;
import com.korur.oba.shrine.invitation.constant.InvitationConstant;
import com.korur.oba.shrine.invitation.constant.service.InvitationServiceLogConstant;
import com.korur.oba.shrine.invitation.dto.request.InvitationCreateRequest;
import com.korur.oba.shrine.invitation.dto.request.InvitationDecisionRequest;
import com.korur.oba.shrine.invitation.dto.response.InvitationResponse;
import com.korur.oba.shrine.invitation.entity.Invitation;
import com.korur.oba.shrine.invitation.enums.InvitationDecision;
import com.korur.oba.shrine.invitation.enums.InvitationStatus;
import com.korur.oba.shrine.invitation.exception.ExceptionDomainInvitationError;
import com.korur.oba.shrine.invitation.mapper.InvitationMapper;
import com.korur.oba.shrine.invitation.repository.InvitationRepository;
import com.korur.oba.shrine.invitation.service.InvitationService;
import com.korur.oba.shrine.invitation.validator.InvitationValidator;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;
import com.korur.oba.shrine.organization.service.OrganizationMemberService;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;

/**
 * Write operations for organization invitations.
 * All invitations are created as PENDING and require explicit user decision.
 */
@Service
@RequiredArgsConstructor
public class InvitationServiceImpl implements InvitationService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(InvitationServiceImpl.class);

    private final InvitationRepository invitationRepository;
    private final InvitationMapper invitationMapper;
    private final InvitationValidator invitationValidator;
    private final OrganizationQueryService organizationQueryService;
    private final OrganizationMemberService organizationMemberService;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserQueryService userQueryService;
    private final UserService userService;
    private final EmailNotificationService emailNotificationService;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public InvitationResponse createInvitation(User currentUser, UUID organizationId, InvitationCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                InvitationServiceLogConstant.INVITATION_CREATING, request.email(), organizationId);

        // Validate business rules
        invitationValidator.ensureCannotInviteSelf(currentUser, request.email());
        invitationValidator.ensureRoleNotOwner(request.role());

        // If user exists, check they are not already a member
        Optional<User> existingUser = userQueryService.findUserByEmail(request.email());
        if (existingUser.isPresent()) {
            invitationValidator.ensureNotAlreadyMember(organizationId, existingUser.get().getId());
        }

        // Ensure no duplicate pending invitation
        invitationValidator.ensureNoPendingInvitation(organizationId, request.email());

        Organization organization = organizationQueryService.getOrganizationEntityById(organizationId);

        // Create pending invitation with expiry
        Invitation invitation = Invitation.builder()
                .organization(organization)
                .email(request.email().toLowerCase())
                .role(request.role())
                .status(InvitationStatus.PENDING)
                .invitedBy(currentUser)
                .expiresAt(Instant.now().plus(InvitationConstant.INVITATION_EXPIRY_DAYS, ChronoUnit.DAYS))
                .build();

        invitation = invitationRepository.save(invitation);

        // Send invitation email
        sendInvitationEmail(request.email().toLowerCase(), organization, currentUser, request.role());

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                InvitationServiceLogConstant.INVITATION_CREATED, request.email());

        return invitationMapper.toResponse(invitation);
    }

    @Override
    @Transactional
    public void revokeInvitation(User user, UUID organizationId, UUID invitationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                InvitationServiceLogConstant.INVITATION_REVOKING, invitationId);

        Invitation invitation = invitationRepository.findByIdInLiveOrganization(invitationId)
                .orElseThrow(() -> new NotFoundException(ExceptionDomainInvitationError.NOT_INVITATION_FOUND,
                        Map.of(InvitationConstant.FIELD_INVITATION_ID, invitationId.toString())));

        // Verify invitation belongs to organization
        if (!invitation.getOrganization().getId().equals(organizationId)) {
            throw new ForbiddenException(ExceptionDomainOrganizationError.SEC_INSUFFICIENT_PERMISSIONS);
        }

        invitationValidator.ensureInvitationIsPending(invitation);

        invitation.setStatus(InvitationStatus.REVOKED);
        invitationRepository.save(invitation);

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                InvitationServiceLogConstant.INVITATION_REVOKED, invitationId);
    }

    @Override
    @Transactional
    public InvitationResponse processDecision(User user, InvitationDecisionRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                InvitationServiceLogConstant.INVITATION_DECISION_PROCESSING,
                request.invitationId(), request.decision());

        // Find invitation; one of a deleted organization is not found.
        Invitation invitation = invitationRepository.findByIdInLiveOrganization(request.invitationId())
                .orElseThrow(() -> new NotFoundException(ExceptionDomainInvitationError.NOT_INVITATION_FOUND,
                        Map.of(InvitationConstant.FIELD_INVITATION_ID, request.invitationId().toString())));

        // Validate ownership and actionability
        invitationValidator.ensureInvitationBelongsToUser(invitation, user.getEmail());
        invitationValidator.ensureInvitationIsActionable(invitation);

        // Check runtime expiry
        if (invitation.getExpiresAt() != null && Instant.now().isAfter(invitation.getExpiresAt())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);

            loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                    InvitationServiceLogConstant.INVITATION_EXPIRED_ON_ACCESS, invitation.getId());

            throw new ValidationException(ExceptionDomainInvitationError.VAL_INVITATION_EXPIRED,
                    Map.of(InvitationConstant.FIELD_INVITATION_ID, invitation.getId().toString()));
        }

        if (request.decision() == InvitationDecision.ACCEPT) {
            return acceptInvitation(invitation, user);
        } else {
            return declineInvitation(invitation);
        }
    }

    @Override
    @Transactional
    public void revokePendingInvitationsOfOrganization(UUID organizationId) {
        int revokedCount = invitationRepository.revokePendingByOrganizationId(organizationId, Instant.now());

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                InvitationServiceLogConstant.ORGANIZATION_INVITATIONS_REVOKED, revokedCount, organizationId);
    }

    // ---- Private helpers ----

    private InvitationResponse acceptInvitation(Invitation invitation, User user) {
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());

        UUID orgId = invitation.getOrganization().getId();

        // Check for a previously soft-deleted membership (re-invite scenario).
        Optional<OrganizationMember> deletedMember = organizationMemberRepository
                .findDeletedByOrgIdAndUserId(orgId, user.getId());

        if (deletedMember.isPresent()) {
            // Restore the soft-deleted membership with the new invitation role.
            organizationMemberRepository.restoreById(deletedMember.get().getId());
            OrganizationMember restored = organizationMemberRepository.findById(deletedMember.get().getId())
                    .orElseThrow();
            restored.setRole(invitation.getRole());
            restored.setStatus(OrganizationMemberStatus.ACTIVE);
            organizationMemberRepository.save(restored);
        } else {
            // Create new organization membership.
            OrganizationMember member = OrganizationMember.builder()
                    .organization(invitation.getOrganization())
                    .user(user)
                    .role(invitation.getRole())
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            organizationMemberService.saveMember(member);
        }

        // Set default organization if user has none.
        if (user.getDefaultOrganization() == null) {
            userService.setDefaultOrganization(user.getId(), orgId);
        }

        invitationRepository.save(invitation);

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                InvitationServiceLogConstant.INVITATION_DECISION_ACCEPTED, orgId);

        return invitationMapper.toResponse(invitation);
    }

    private InvitationResponse declineInvitation(Invitation invitation) {
        invitation.setStatus(InvitationStatus.DECLINED);
        invitation.setDeclinedAt(Instant.now());
        invitationRepository.save(invitation);

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                InvitationServiceLogConstant.INVITATION_DECISION_DECLINED,
                invitation.getId());

        return invitationMapper.toResponse(invitation);
    }

    private void sendInvitationEmail(String email, Organization organization, User inviter, OrganizationMemberRole role) {
        Map<String, String> variables = Map.of(
                "organizationName", organization.getName(),
                "inviterName", inviter.getProfile().getFirstName() + " " + inviter.getProfile().getLastName(),
                "role", role.name()
        );

        EmailRequest emailRequest = new EmailRequest(
                null,
                List.of(email),
                EmailTemplateType.ORGANIZATION_INVITATION_NEW_USER,
                variables,
                null
        );

        emailNotificationService.sendEmail(emailRequest);
    }
}

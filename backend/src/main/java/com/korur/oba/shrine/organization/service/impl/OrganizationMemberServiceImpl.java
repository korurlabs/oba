package com.korur.oba.shrine.organization.service.impl;

// 1. Framework-level
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.audit.mapper.AuditLogMapper;
import com.korur.oba.dojo.audit.service.AuditService;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.access.service.AppAccessRevocationService;
import com.korur.oba.shrine.access.service.AppAccessService;
import com.korur.oba.shrine.agent.constant.AgentConstant;
import com.korur.oba.shrine.agent.validator.AgentValidator;
import com.korur.oba.shrine.identity.constant.UserConstant;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.exception.ExceptionDomainIdentityError;
import com.korur.oba.shrine.identity.repository.UserRepository;
import com.korur.oba.shrine.organization.constant.service.OrganizationMemberServiceLogConstant;
import com.korur.oba.shrine.organization.dto.request.OrganizationMemberRoleUpdateRequest;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.event.AccessRevokedEvent;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.mapper.OrganizationMemberMapper;
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;
import com.korur.oba.shrine.organization.service.OrganizationMemberService;
import com.korur.oba.shrine.organization.validator.OrganizationValidator;

/**
 * Implements write operations for organization membership management.
 *
 * Every change that ends a user's access to an organization (removal, leaving, disabling)
 * publishes {@link AccessRevokedEvent}, so state built on that access, such as open
 * realtime sessions, is torn down once the change commits. A role change keeps the organization
 * but can take apps away, through the app's minimum role or the owner's grant-free access, so it
 * publishes {@link com.korur.oba.shrine.access.event.AppAccessRevokedEvent} for each app the member lost.
 *
 * An agent's membership is managed on the Agents page, so remove, restore, status and role refuse an
 * agent target, and an agent cannot leave on its own; see {@link AgentValidator}.
 */
@Service
@RequiredArgsConstructor
public class OrganizationMemberServiceImpl implements OrganizationMemberService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OrganizationMemberServiceImpl.class);

    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserRepository userRepository;
    private final OrganizationMemberMapper organizationMemberMapper;
    private final OrganizationValidator organizationValidator;
    private final AppAccessService appAccessService;
    private final AppAccessQueryService appAccessQueryService;
    private final AppAccessRevocationService appAccessRevocationService;
    private final AuditLogMapper auditLogMapper;
    private final AuditService auditService;
    private final AgentValidator agentValidator;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public void removeMember(User actor, UUID organizationId, UUID userId) {
        OrganizationMember member = organizationValidator.ensureMemberExists(organizationId, userId);
        agentValidator.ensureTargetIsNotAgent(member.getUser(), AgentConstant.ACTION_MEMBER_REMOVE);
        OrganizationMember actorMember = organizationValidator.ensureMemberExists(organizationId, actor.getId());
        organizationValidator.ensureAdminCannotModifyOwner(actorMember.getRole(), member);

        Map<String, Object> oldValues = Map.of(
                "role", member.getRole().name(),
                "status", member.getStatus().name());

        // Soft delete the membership.
        organizationMemberRepository.delete(member);

        // Clear default organization if needed.
        clearDefaultOrganizationIfNeeded(userId, organizationId);

        // Drop the user's group rows and direct grants, so a later rejoin starts with no app access.
        appAccessService.removeMemberAccess(actor, organizationId, userId);

        publishAccessRevoked(userId, organizationId);

        auditService.log(auditLogMapper.toRequest(organizationId, actor.getId(),
                OrganizationMemberServiceLogConstant.AUDIT_ACTION_MEMBER_REMOVED,
                OrganizationMemberServiceLogConstant.AUDIT_ENTITY_TYPE, member.getId(), oldValues,
                Map.of("softDeleted", true)));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                OrganizationMemberServiceLogConstant.ORG_MEMBER_REMOVED,
                organizationId, member.getId());
    }

    @Override
    @Transactional
    public void restoreMember(User actor, UUID organizationId, UUID userId) {
        OrganizationMember deletedMember = organizationMemberRepository
                .findDeletedByOrgIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new NotFoundException(
                        ExceptionDomainOrganizationError.NOT_DELETED_MEMBER_FOUND,
                        Map.of("organizationId", organizationId, "userId", userId)));
        // The user of a deleted agent is deleted with it and loads as null. Without a user the agent
        // check cannot see the agent, and the membership would come back active for no one.
        User memberUser = deletedMember.getUser();
        if (memberUser == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                    OrganizationMemberServiceLogConstant.ORG_MEMBER_RESTORE_USER_MISSING,
                    organizationId, userId);
            throw new NotFoundException(ExceptionDomainIdentityError.NOT_USER_FOUND,
                    Map.of(UserConstant.FIELD_USER_DATABASE_ID, userId));
        }
        agentValidator.ensureTargetIsNotAgent(memberUser, AgentConstant.ACTION_MEMBER_RESTORE);

        OrganizationMember actorMember = organizationValidator.ensureMemberExists(organizationId, actor.getId());
        organizationValidator.ensureAdminCannotModifyOwner(actorMember.getRole(), deletedMember);

        organizationMemberRepository.restoreById(deletedMember.getId());

        auditService.log(auditLogMapper.toRequest(organizationId, actor.getId(),
                OrganizationMemberServiceLogConstant.AUDIT_ACTION_MEMBER_RESTORED,
                OrganizationMemberServiceLogConstant.AUDIT_ENTITY_TYPE, deletedMember.getId(),
                Map.of("softDeleted", true),
                Map.of("softDeleted", false)));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                OrganizationMemberServiceLogConstant.ORG_MEMBER_RESTORED,
                organizationId, deletedMember.getId());
    }

    @Override
    @Transactional
    public void updateMemberStatus(User actor, UUID organizationId, UUID userId, OrganizationMemberStatus newStatus) {
        OrganizationMember member = organizationValidator.ensureMemberExists(organizationId, userId);
        agentValidator.ensureTargetIsNotAgent(member.getUser(), AgentConstant.ACTION_MEMBER_STATUS);
        OrganizationMember actorMember = organizationValidator.ensureMemberExists(organizationId, actor.getId());
        organizationValidator.ensureAdminCannotModifyOwner(actorMember.getRole(), member);

        Map<String, Object> oldValues = Map.of("status", member.getStatus().name());

        if (newStatus == OrganizationMemberStatus.DISABLED) {
            organizationValidator.ensureMemberIsActive(member);

            member.setStatus(OrganizationMemberStatus.DISABLED);
            organizationMemberRepository.save(member);

            clearDefaultOrganizationIfNeeded(userId, organizationId);

            publishAccessRevoked(userId, organizationId);

            auditService.log(auditLogMapper.toRequest(organizationId, actor.getId(),
                    OrganizationMemberServiceLogConstant.AUDIT_ACTION_MEMBER_DISABLED,
                    OrganizationMemberServiceLogConstant.AUDIT_ENTITY_TYPE, member.getId(), oldValues,
                    Map.of("status", OrganizationMemberStatus.DISABLED.name())));

            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    OrganizationMemberServiceLogConstant.ORG_MEMBER_DISABLED,
                    organizationId, member.getId());

        } else if (newStatus == OrganizationMemberStatus.ACTIVE) {
            organizationValidator.ensureMemberIsDisabled(member);

            member.setStatus(OrganizationMemberStatus.ACTIVE);
            organizationMemberRepository.save(member);

            auditService.log(auditLogMapper.toRequest(organizationId, actor.getId(),
                    OrganizationMemberServiceLogConstant.AUDIT_ACTION_MEMBER_ENABLED,
                    OrganizationMemberServiceLogConstant.AUDIT_ENTITY_TYPE, member.getId(), oldValues,
                    Map.of("status", OrganizationMemberStatus.ACTIVE.name())));

            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    OrganizationMemberServiceLogConstant.ORG_MEMBER_ENABLED,
                    organizationId, member.getId());

        } else {
            throw new ValidationException(ExceptionDomainOrganizationError.VAL_INVALID_MEMBER_STATUS,
                    Map.of("status", newStatus.name()));
        }
    }

    @Override
    @Transactional
    public void leaveOrganization(User user, UUID organizationId) {
        agentValidator.ensureCallerIsNotAgent(user, AgentConstant.ACTION_ORGANIZATION_LEAVE);
        OrganizationMember member = organizationValidator.ensureMemberExists(organizationId, user.getId());

        // Prevent sole owner from leaving.
        organizationValidator.ensureNotSoleOwner(organizationId, member);

        Map<String, Object> oldValues = Map.of(
                "role", member.getRole().name(),
                "status", member.getStatus().name());

        // Soft delete the membership.
        organizationMemberRepository.delete(member);

        // Clear default organization if needed.
        clearDefaultOrganizationIfNeeded(user.getId(), organizationId);

        // Drop the user's group rows and direct grants, so a later rejoin starts with no app access.
        appAccessService.removeMemberAccess(user, organizationId, user.getId());

        publishAccessRevoked(user.getId(), organizationId);

        auditService.log(auditLogMapper.toRequest(organizationId, user.getId(),
                OrganizationMemberServiceLogConstant.AUDIT_ACTION_MEMBER_LEFT,
                OrganizationMemberServiceLogConstant.AUDIT_ENTITY_TYPE, member.getId(), oldValues,
                Map.of("softDeleted", true)));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                OrganizationMemberServiceLogConstant.ORG_MEMBER_LEFT,
                organizationId, member.getId());
    }

    @Override
    @Transactional
    public OrganizationMemberResponse changeMemberRole(User user, UUID organizationId, UUID userId,
            OrganizationMemberRoleUpdateRequest request) {
        organizationValidator.ensureRoleIsNotOwner(request.role());
        organizationValidator.ensureNotSelfRoleUpdate(user.getId(), userId);

        OrganizationMember member = organizationValidator.ensureMemberExists(organizationId, userId);
        agentValidator.ensureTargetIsNotAgent(member.getUser(), AgentConstant.ACTION_MEMBER_ROLE);
        OrganizationMember actorMember = organizationValidator.ensureMemberExists(organizationId, user.getId());
        organizationValidator.ensureAdminCannotModifyOwner(actorMember.getRole(), member);

        Map<App, Set<UUID>> holdersBefore = appAccessQueryService.findAppHolders(
                organizationId, EnumSet.allOf(App.class), Set.of(userId));

        member.setRole(request.role());
        member = organizationMemberRepository.save(member);

        appAccessRevocationService.publishLostAppAccess(organizationId, holdersBefore);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                OrganizationMemberServiceLogConstant.ORG_MEMBER_ROLE_CHANGED,
                organizationId, member.getId(), request.role());

        return organizationMemberMapper.toResponse(member);
    }

    @Override
    @Transactional
    public OrganizationMember saveMember(OrganizationMember member) {
        return organizationMemberRepository.save(member);
    }

    private void publishAccessRevoked(UUID userId, UUID organizationId) {
        applicationEventPublisher.publishEvent(AccessRevokedEvent.member(userId, organizationId));
    }

    private void clearDefaultOrganizationIfNeeded(UUID userId, UUID organizationId) {
        // Reassign default org to next available membership, or NULL if none exist.
        // Uses native query to avoid EAGER collection merge issues after soft-delete.
        userRepository.reassignDefaultOrganization(userId, organizationId);

        // The membership was already soft-deleted, so findByUser_Id auto-filters it.
        // If no remaining active memberships, revert user to pending onboarding.
        if (organizationMemberRepository.findByUser_Id(userId).isEmpty()) {
            userRepository.updateUserStatus(userId, UserStatus.PENDING_ONBOARDING);
        }
    }
}

package com.korur.oba.shrine.organization.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.organization.constant.OrganizationConstant;
import com.korur.oba.shrine.organization.constant.exception.OrganizationExceptionLogConstant;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;
import com.korur.oba.shrine.organization.repository.OrganizationRepository;

/**
 * Validator for organization and membership lookups.
 */
@Component
@RequiredArgsConstructor
public class OrganizationValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OrganizationValidator.class);

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the organization exists and returns it.
     */
    public Organization ensureOrganizationExists(UUID organizationId) {
        // Fetch the organization or raise a domain-specific not-found error.
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> {
                    // Log for observability before throwing the exception.
                    loggingService.warn(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            OrganizationExceptionLogConstant.ORG_NOT_FOUND,
                            organizationId);
                    // Propagate a NotFoundException with field context.
                    return new NotFoundException(
                            ExceptionDomainOrganizationError.NOT_ORGANIZATION_FOUND,
                            Map.of(OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId));
                });
    }

    /**
     * Ensures the membership exists and returns it.
     */
    public OrganizationMember ensureMemberExists(UUID organizationId, UUID userId) {
        // Fetch membership for an organization and user pair.
        return organizationMemberRepository.findByOrganization_IdAndUser_Id(organizationId, userId)
                .orElseThrow(() -> {
                    // Log for debugging missing membership cases.
                    loggingService.warn(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            OrganizationExceptionLogConstant.ORG_MEMBER_NOT_FOUND,
                            organizationId,
                            userId);
                    // Propagate a NotFoundException with field context.
                    return new NotFoundException(
                            ExceptionDomainOrganizationError.NOT_ORGANIZATION_MEMBER_FOUND,
                            Map.of(
                                    OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId,
                                    OrganizationConstant.FIELD_ORGANIZATION_USER_ID, userId));
                });
    }

    /**
     * Ensures the requested role is not OWNER.
     * The OWNER role cannot be assigned through the role update endpoint.
     */
    public void ensureRoleIsNotOwner(OrganizationMemberRole role) {
        if (role == OrganizationMemberRole.OWNER) {
            // Log the rejected OWNER assignment attempt.
            loggingService.error(
                    LOG_PREFIX,
                    LogLayer.VALIDATOR,
                    OrganizationExceptionLogConstant.ORG_OWNER_ROLE_NOT_ASSIGNABLE);
            // Propagate a ValidationException with field context.
            throw new ValidationException(
                    ExceptionDomainOrganizationError.VAL_OWNER_ROLE_NOT_ASSIGNABLE,
                    Map.of(OrganizationConstant.FIELD_ORGANIZATION_ROLE, role));
        }
    }

    /**
     * Ensures the requesting user is not updating their own role.
     */
    public void ensureNotSelfRoleUpdate(UUID requestingUserId, UUID targetUserId) {
        if (requestingUserId.equals(targetUserId)) {
            // Log the rejected self-update attempt.
            loggingService.error(
                    LOG_PREFIX,
                    LogLayer.VALIDATOR,
                    OrganizationExceptionLogConstant.ORG_CANNOT_UPDATE_OWN_ROLE,
                    requestingUserId);
            // Propagate a ForbiddenException with field context.
            throw new ForbiddenException(
                    ExceptionDomainOrganizationError.SEC_CANNOT_UPDATE_OWN_ROLE,
                    Map.of(OrganizationConstant.FIELD_ORGANIZATION_USER_ID, requestingUserId));
        }
    }

    /**
     * Ensures the organization is active (not disabled).
     * Used before disabling to guard against double-disable.
     *
     * @param organization the organization to validate
     * @throws ConflictException if the organization is already disabled
     */
    public void ensureOrganizationIsActive(Organization organization) {
        if (organization.getStatus() == OrganizationStatus.DISABLED) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    OrganizationExceptionLogConstant.ORG_ALREADY_DISABLED,
                    organization.getId());
            throw new ConflictException(
                    ExceptionDomainOrganizationError.STA_ORG_ALREADY_DISABLED,
                    Map.of(OrganizationConstant.FIELD_ORGANIZATION_ID, organization.getId()));
        }
    }

    /**
     * Ensures the organization is disabled.
     * Used before enabling to guard against enabling an already-active organization.
     *
     * @param organization the organization to validate
     * @throws ConflictException if the organization is not disabled
     */
    public void ensureOrganizationIsDisabled(Organization organization) {
        if (organization.getStatus() != OrganizationStatus.DISABLED) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    OrganizationExceptionLogConstant.ORG_NOT_DISABLED,
                    organization.getId());
            throw new ConflictException(
                    ExceptionDomainOrganizationError.STA_ORG_NOT_DISABLED,
                    Map.of(OrganizationConstant.FIELD_ORGANIZATION_ID, organization.getId()));
        }
    }

    /**
     * Ensures the organization member is active (not disabled).
     * Used before disabling to guard against double-disable.
     *
     * @param member the organization member to validate
     * @throws ConflictException if the member is already disabled
     */
    public void ensureMemberIsActive(OrganizationMember member) {
        if (member.getStatus() == OrganizationMemberStatus.DISABLED) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    OrganizationExceptionLogConstant.MEMBER_ALREADY_DISABLED,
                    member.getId());
            throw new ConflictException(
                    ExceptionDomainOrganizationError.STA_MEMBER_ALREADY_DISABLED,
                    Map.of(OrganizationConstant.FIELD_ORGANIZATION_MEMBER_ID, member.getId()));
        }
    }

    /**
     * Ensures the organization member is disabled.
     * Used before enabling to guard against enabling an already-active member.
     *
     * @param member the organization member to validate
     * @throws ConflictException if the member is not disabled
     */
    public void ensureMemberIsDisabled(OrganizationMember member) {
        if (member.getStatus() != OrganizationMemberStatus.DISABLED) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    OrganizationExceptionLogConstant.MEMBER_NOT_DISABLED,
                    member.getId());
            throw new ConflictException(
                    ExceptionDomainOrganizationError.STA_MEMBER_NOT_DISABLED,
                    Map.of(OrganizationConstant.FIELD_ORGANIZATION_MEMBER_ID, member.getId()));
        }
    }

    /**
     * Ensures the user is an owner of the organization.
     * Used by lifecycle operations that bypass OrganizationAccessService.
     *
     * @param organizationId the organization ID
     * @param userId         the user ID
     * @throws NotFoundException if the membership does not exist
     * @throws ForbiddenException if the member is not an owner
     */
    public void ensureUserIsOwner(UUID organizationId, UUID userId) {
        OrganizationMember member = ensureMemberExists(organizationId, userId);
        if (member.getRole() != OrganizationMemberRole.OWNER) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    OrganizationExceptionLogConstant.ORG_INSUFFICIENT_PERMISSIONS,
                    organizationId, userId);
            throw new ForbiddenException(
                    ExceptionDomainOrganizationError.SEC_INSUFFICIENT_PERMISSIONS,
                    Map.of(
                            OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId,
                            OrganizationConstant.FIELD_ORGANIZATION_USER_ID, userId));
        }
    }

    /**
     * Ensures an admin actor cannot modify an OWNER-role member.
     * Only OWNER actors may take action (role change, removal, status change, restore)
     * on another OWNER's membership.
     *
     * @param actorRole    the role of the requesting actor in this organization
     * @param targetMember the membership being acted upon
     * @throws ForbiddenException if the target is an OWNER and the actor is not an OWNER
     */
    public void ensureAdminCannotModifyOwner(OrganizationMemberRole actorRole, OrganizationMember targetMember) {
        if (targetMember.getRole() == OrganizationMemberRole.OWNER && actorRole != OrganizationMemberRole.OWNER) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    OrganizationExceptionLogConstant.ADMIN_CANNOT_MODIFY_OWNER,
                    targetMember.getId());
            throw new ForbiddenException(
                    ExceptionDomainOrganizationError.SEC_ADMIN_CANNOT_MODIFY_OWNER,
                    Map.of(
                            OrganizationConstant.FIELD_ORGANIZATION_MEMBER_ID, targetMember.getId().toString(),
                            OrganizationConstant.FIELD_ORGANIZATION_ROLE, targetMember.getRole().toString()));
        }
    }

    /**
     * Ensures the member is not the sole owner of the organization.
     * Used before leave/remove to prevent orphaning an organization.
     *
     * @param organizationId the organization ID
     * @param member         the member attempting to leave
     * @throws ConflictException if the member is the sole owner
     */
    public void ensureNotSoleOwner(UUID organizationId, OrganizationMember member) {
        if (member.getRole() == OrganizationMemberRole.OWNER) {
            long ownerCount = organizationMemberRepository.countActiveOwners(organizationId);
            if (ownerCount <= 1) {
                loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                        OrganizationExceptionLogConstant.MEMBER_SOLE_OWNER,
                        organizationId, member.getId());
                throw new ConflictException(
                        ExceptionDomainOrganizationError.STA_SOLE_OWNER_CANNOT_LEAVE,
                        Map.of(OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId));
            }
        }
    }
}

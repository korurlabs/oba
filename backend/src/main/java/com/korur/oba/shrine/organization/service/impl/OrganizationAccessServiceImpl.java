package com.korur.oba.shrine.organization.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.constant.AccessConstant;
import com.korur.oba.shrine.access.constant.exception.AccessExceptionLogConstant;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.organization.constant.OrganizationConstant;
import com.korur.oba.shrine.organization.constant.service.OrganizationAccessServiceLogConstant;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.service.OrganizationAccessService;
import com.korur.oba.shrine.organization.validator.OrganizationValidator;

/**
 * Centralised organization access control implementation.
 *
 * Validates entity existence via OrganizationValidator and enforces
 * role-based access on the organization membership, and app access through
 * {@link AppAccessQueryService} when the caller names an app.
 */
@Service
@RequiredArgsConstructor
public class OrganizationAccessServiceImpl implements OrganizationAccessService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OrganizationAccessServiceImpl.class);

    /**
     * All roles -- any organization member may proceed.
     */
    private static final EnumSet<OrganizationMemberRole> GUEST_AND_ABOVE = EnumSet.of(
            OrganizationMemberRole.OWNER,
            OrganizationMemberRole.ADMIN,
            OrganizationMemberRole.MEMBER,
            OrganizationMemberRole.GUEST);

    /**
     * Roles that can contribute (standard operations).
     */
    private static final EnumSet<OrganizationMemberRole> MEMBER_AND_ABOVE = EnumSet.of(
            OrganizationMemberRole.OWNER,
            OrganizationMemberRole.ADMIN,
            OrganizationMemberRole.MEMBER);

    /**
     * Roles that can manage organization settings and members.
     */
    private static final EnumSet<OrganizationMemberRole> ADMIN_AND_ABOVE = EnumSet.of(
            OrganizationMemberRole.OWNER,
            OrganizationMemberRole.ADMIN);

    /**
     * Only the organization owner.
     */
    private static final EnumSet<OrganizationMemberRole> OWNER_ONLY = EnumSet.of(
            OrganizationMemberRole.OWNER);

    private final OrganizationValidator organizationValidator;
    private final AppAccessQueryService appAccessQueryService;
    private final LoggingService loggingService;

    @Override
    public Organization verifyOrganizationAccess(UUID organizationId, UUID userId, OrganizationMemberRole minimumRole) {
        Organization organization = requireActiveOrganization(organizationId, userId);
        requireActiveMembership(organizationId, userId, minimumRole);
        return organization;
    }

    @Override
    public Organization verifyOrganizationAccess(
            UUID organizationId, UUID userId, OrganizationMemberRole minimumRole, App app) {
        Organization organization = requireActiveOrganization(organizationId, userId);
        OrganizationMember member = requireActiveMembership(organizationId, userId, minimumRole);

        if (!appAccessQueryService.hasAppAccess(member, app)) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, AccessExceptionLogConstant.APP_ACCESS_DENIED,
                    organizationId, userId, app);
            throw new ForbiddenException(
                    ExceptionDomainAccessError.SEC_APP_ACCESS_DENIED,
                    Map.of(
                            OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId,
                            AccessConstant.FIELD_APP, app.name()));
        }
        return organization;
    }

    /**
     * Loads the organization and refuses a disabled one.
     *
     * @param organizationId the organization identifier
     * @param userId         the user identifier, for the log line
     * @return the organization
     */
    private Organization requireActiveOrganization(UUID organizationId, UUID userId) {
        Organization organization = organizationValidator.ensureOrganizationExists(organizationId);

        if (organization.getStatus() == OrganizationStatus.DISABLED) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                    OrganizationAccessServiceLogConstant.ORGANIZATION_DISABLED,
                    organizationId, userId);
            throw new ForbiddenException(
                    ExceptionDomainOrganizationError.SEC_ORGANIZATION_DISABLED,
                    Map.of(OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId));
        }
        return organization;
    }

    /**
     * Looks up the organization membership, verifies it is active and that the
     * member's role is within the allowed set derived from the minimum role.
     *
     * A disabled member keeps the membership row but has lost access, so the
     * status is checked before the role, as
     * {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor} does.
     *
     * @param organizationId the organization identifier
     * @param userId         the user identifier
     * @param minimumRole    the minimum role required
     * @return the membership, for a check that follows on the same row
     */
    private OrganizationMember requireActiveMembership(UUID organizationId, UUID userId, OrganizationMemberRole minimumRole) {
        OrganizationMember member = organizationValidator.ensureMemberExists(organizationId, userId);

        if (member.getStatus() != OrganizationMemberStatus.ACTIVE) {
            loggingService.warn(
                    LOG_PREFIX,
                    LogLayer.SERVICE,
                    OrganizationAccessServiceLogConstant.MEMBERSHIP_DISABLED,
                    organizationId,
                    userId,
                    member.getStatus());
            throw new ForbiddenException(
                    ExceptionDomainOrganizationError.SEC_MEMBERSHIP_DISABLED,
                    Map.of(
                            OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId,
                            OrganizationConstant.FIELD_ORGANIZATION_USER_ID, userId));
        }

        EnumSet<OrganizationMemberRole> allowedRoles = resolveAllowedRoles(minimumRole);
        if (!allowedRoles.contains(member.getRole())) {
            loggingService.warn(
                    LOG_PREFIX,
                    LogLayer.SERVICE,
                    OrganizationAccessServiceLogConstant.INSUFFICIENT_PERMISSIONS,
                    organizationId,
                    userId,
                    minimumRole);
            throw new ForbiddenException(
                    ExceptionDomainOrganizationError.SEC_INSUFFICIENT_PERMISSIONS,
                    Map.of(
                            OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId,
                            OrganizationConstant.FIELD_ORGANIZATION_USER_ID, userId));
        }
        return member;
    }

    /**
     * Maps a minimum role to the corresponding set of allowed roles.
     */
    private static EnumSet<OrganizationMemberRole> resolveAllowedRoles(OrganizationMemberRole minimumRole) {
        return switch (minimumRole) {
            case GUEST -> GUEST_AND_ABOVE;
            case MEMBER -> MEMBER_AND_ABOVE;
            case ADMIN -> ADMIN_AND_ABOVE;
            case OWNER -> OWNER_ONLY;
        };
    }
}

package com.korur.oba.apps.kaya.identity.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.identity.constant.IdentityConstant;
import com.korur.oba.apps.kaya.identity.constant.service.UserServiceLogConstant;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.apps.kaya.identity.service.OrganizationAccessQueryService;

/**
 * The "ADMIN or self" rule of actions on a user's data: the caller is an ADMIN or OWNER of the
 * organization, or the user acted on.
 */
@Component
@RequiredArgsConstructor
public class UserAccessHelper {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(UserAccessHelper.class);

    private final OrganizationAccessQueryService organizationAccessQueryService;
    private final LoggingService loggingService;

    /**
     * Passes when the actor is the target user or holds at least ADMIN in its organization. The
     * caller resolves the target within the organization first, so an id outside it answers 404
     * before this check can answer 403.
     *
     * @param actor          the signed-in user
     * @param organizationId the organization of the request
     * @param targetUserId   the user whose data is acted on
     * @throws ForbiddenException with GEN_SEC_003 and {requiredRole, actualRole, userId} otherwise
     */
    public void requireAdminOrSelf(User actor, UUID organizationId, UUID targetUserId) {
        if (actor.getId().equals(targetUserId)) {
            return;
        }
        OrganizationMemberRole role = organizationAccessQueryService.verifyTenantAccess(actor, organizationId).getRole();
        if (role.isAtLeast(OrganizationMemberRole.ADMIN)) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, UserServiceLogConstant.ADMIN_OR_SELF_REQUIRED,
                actor.getId(), role, targetUserId);
        throw new ForbiddenException(ExceptionSecurityError.INCORRECT_ROLE,
                Map.of(IdentityConstant.FIELD_REQUIRED_ROLE, OrganizationMemberRole.ADMIN.name(),
                        IdentityConstant.FIELD_ACTUAL_ROLE, role.name(),
                        IdentityConstant.FIELD_USER_ID, targetUserId.toString()));
    }
}

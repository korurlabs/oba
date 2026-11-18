package com.korur.oba.shrine.access.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.constant.AccessConstant;
import com.korur.oba.shrine.access.constant.exception.AccessExceptionLogConstant;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.organization.constant.OrganizationConstant;

/**
 * Validates that the users an app write attaches to its data may open the app, so an app never assigns,
 * mentions or adds a member who could not see the result.
 *
 * Apps call it from every write that names another user; the rule itself is
 * {@link AppAccessQueryService#hasAppAccess(UUID, UUID, App)}, read for all users at once.
 */
@Component
@RequiredArgsConstructor
public class AppAccessValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AppAccessValidator.class);

    private final AppAccessQueryService appAccessQueryService;
    private final LoggingService loggingService;

    /**
     * Refuses a write that attaches a user who may not open the app in the organization. All users are
     * checked with one query; a user who is no member counts as one without access.
     *
     * @param organizationId the organization identifier
     * @param app            the app whose data the users are attached to
     * @param userIds        the users the write attaches; empty passes without a query
     * @throws ValidationException APP_VAL_002 naming the organization, the app and the first refused user
     */
    public void ensureUsersHaveAppAccess(UUID organizationId, App app, Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        Set<UUID> holders = appAccessQueryService.findUserIdsWithAppAccess(organizationId, app, userIds);
        List<UUID> refusedIds = userIds.stream()
                .filter((UUID userId) -> !holders.contains(userId))
                .distinct()
                .toList();
        if (refusedIds.isEmpty()) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, AccessExceptionLogConstant.MEMBER_WITHOUT_APP_ACCESS,
                organizationId, app, refusedIds);
        throw new ValidationException(
                ExceptionDomainAccessError.VAL_MEMBER_WITHOUT_APP_ACCESS,
                Map.of(
                        OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId,
                        AccessConstant.FIELD_APP, app.name(),
                        AccessConstant.FIELD_USER_ID, refusedIds.getFirst()));
    }
}

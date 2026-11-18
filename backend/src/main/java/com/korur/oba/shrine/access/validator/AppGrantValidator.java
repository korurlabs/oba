package com.korur.oba.shrine.access.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.constant.AccessConstant;
import com.korur.oba.shrine.access.constant.exception.AccessExceptionLogConstant;
import com.korur.oba.shrine.access.dto.request.AppGrantCreateRequest;
import com.korur.oba.shrine.access.entity.AppGrant;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.repository.AppGrantRepository;
import com.korur.oba.shrine.organization.constant.OrganizationConstant;

/**
 * Validates the business rules of app grants.
 */
@Component
@RequiredArgsConstructor
public class AppGrantValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AppGrantValidator.class);

    private final AppGrantRepository appGrantRepository;
    private final LoggingService loggingService;

    /**
     * Refuses a grant request that names no target or both a user and a group; the database would refuse
     * the row as well, but only with a constraint error that tells the caller nothing.
     *
     * @param organizationId the organization identifier
     * @param request        the grant request
     */
    public void ensureOneTarget(UUID organizationId, AppGrantCreateRequest request) {
        if ((request.userId() == null) != (request.groupId() == null)) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, AccessExceptionLogConstant.GRANT_TARGET_INVALID,
                organizationId);
        throw new ValidationException(
                ExceptionDomainAccessError.VAL_GRANT_TARGET_INVALID,
                Map.of(OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId));
    }

    /**
     * Ensures the grant exists in the organization and returns it. A grant of another organization is not
     * found, so a caller cannot reach across organizations by grant id.
     *
     * @param organizationId the organization identifier
     * @param grantId        the grant identifier
     * @return the grant
     */
    public AppGrant ensureGrantExists(UUID organizationId, UUID grantId) {
        return appGrantRepository.findByIdAndOrganization_Id(grantId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, AccessExceptionLogConstant.GRANT_NOT_FOUND,
                            organizationId, grantId);
                    return new NotFoundException(
                            ExceptionDomainAccessError.NOT_GRANT_FOUND,
                            Map.of(
                                    OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId,
                                    AccessConstant.FIELD_GRANT_ID, grantId));
                });
    }
}

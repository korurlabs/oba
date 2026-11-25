package com.korur.oba.apps.kazan.cyberscore.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.constant.CheckConstant;
import com.korur.oba.apps.kazan.cyberscore.constant.CheckExceptionLogConstant;
import com.korur.oba.apps.kazan.cyberscore.entity.Check;
import com.korur.oba.apps.kazan.cyberscore.repository.CheckRepository;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Validator for check lookups and the one-name-per-organization rule.
 */
@Component
@RequiredArgsConstructor
public class CheckValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CheckValidator.class);

    private final CheckRepository checkRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the organization has the check and returns it. A check of another
     * organization is a missing one, so its existence does not leak.
     *
     * @param organizationId organization of the request
     * @param checkId        ID of the check
     * @return the check
     * @throws NotFoundException if the organization has no check with the ID
     */
    public Check ensureCheckExists(UUID organizationId, UUID checkId) {
        return checkRepository.findByIdAndOrganizationId(checkId, organizationId)
                .orElseThrow(() -> {
                    loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                            CheckExceptionLogConstant.CHECK_NOT_FOUND, checkId, organizationId);
                    return new NotFoundException(ExceptionDomainKazanError.NOT_CHECK_FOUND,
                            Map.of(CheckConstant.FIELD_CHECK_ID, checkId));
                });
    }

    /**
     * Ensures no check of the organization has the name yet. The unique
     * constraint on (organization_id, name) backs this check against a
     * concurrent write.
     *
     * @param organizationId organization of the request
     * @param name           name of the new check
     * @throws ConflictException if the organization already has a check with the name
     */
    public void ensureNameIsUnique(UUID organizationId, String name) {
        if (checkRepository.existsByOrganizationIdAndName(organizationId, name)) {
            throw nameTaken(organizationId, name);
        }
    }

    /**
     * Ensures no other check of the organization has the name, for an edit.
     *
     * @param organizationId organization of the request
     * @param name           new name of the check
     * @param checkId        ID of the check being edited, which may keep its name
     * @throws ConflictException if another check of the organization has the name
     */
    public void ensureNameIsUnique(UUID organizationId, String name, UUID checkId) {
        if (checkRepository.existsByOrganizationIdAndNameAndIdNot(organizationId, name, checkId)) {
            throw nameTaken(organizationId, name);
        }
    }

    /**
     * Logs a taken name and builds the conflict the client shows on the name field.
     */
    private ConflictException nameTaken(UUID organizationId, String name) {
        loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                CheckExceptionLogConstant.CHECK_NAME_DUPLICATE, name, organizationId);
        return new ConflictException(ExceptionDomainKazanError.STA_CHECK_NAME_DUPLICATE,
                Map.of(CheckConstant.FIELD_NAME, name));
    }
}

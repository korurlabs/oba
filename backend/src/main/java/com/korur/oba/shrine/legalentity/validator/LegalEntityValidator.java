package com.korur.oba.shrine.legalentity.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.legalentity.constant.LegalEntityConstant;
import com.korur.oba.shrine.legalentity.constant.LegalEntityExceptionLogConstant;
import com.korur.oba.shrine.legalentity.entity.LegalEntity;
import com.korur.oba.shrine.legalentity.exception.ExceptionDomainLegalEntityError;
import com.korur.oba.shrine.legalentity.repository.LegalEntityRepository;

/**
 * Business rules of legal entities that a request annotation can not check, because they
 * need the database:
 * - the lookup of one legal entity within the caller's organization ({@link #ensureLegalEntityExists});
 * - the short name rule: unique per organization, case ignored ({@link #ensureShortNameUnique}).
 *
 * Each method throws a business exception that the global exception handler turns into the
 * error response; failures are logged at warn level, because they are client errors, not
 * faults.
 *
 * Callers: {@link com.korur.oba.shrine.legalentity.service.impl.LegalEntityServiceImpl} and
 * {@link com.korur.oba.shrine.legalentity.service.impl.LegalEntityQueryServiceImpl}.
 */
@Component
@RequiredArgsConstructor
public class LegalEntityValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(LegalEntityValidator.class);

    private final LegalEntityRepository legalEntityRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the organization has the legal entity and returns it. One of another
     * organization answers the same not found as a missing one, so its id reveals nothing to
     * another tenant. Every read and write of a single legal entity passes through here.
     *
     * @param organizationId the organization the caller acts in
     * @param legalEntityId  the legal entity identifier
     * @return the legal entity, a managed entity of the caller's transaction
     * @throws NotFoundException {@link ExceptionDomainLegalEntityError#NOT_LEGAL_ENTITY_FOUND} (LGE_NOT_001)
     *         if the organization has no legal entity with the id
     */
    public LegalEntity ensureLegalEntityExists(UUID organizationId, UUID legalEntityId) {
        return legalEntityRepository.findByIdAndOrganizationId(legalEntityId, organizationId)
                .orElseThrow(() -> {
                    // Logged with both ids, so a probe for another tenant's legal entity shows in the logs.
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            LegalEntityExceptionLogConstant.LEGAL_ENTITY_NOT_FOUND, legalEntityId, organizationId);
                    // The response names only the legal entity id, never the organization.
                    return new NotFoundException(ExceptionDomainLegalEntityError.NOT_LEGAL_ENTITY_FOUND,
                            Map.of(LegalEntityConstant.FIELD_LEGAL_ENTITY_ID, legalEntityId));
                });
    }

    /**
     * Ensures no other legal entity of the organization has the short name, case ignored, so
     * a picker never shows two equal labels. Two requests that race past this check are still
     * stopped by the unique index on the lower-case short name.
     *
     * @param organizationId        the organization that holds the legal entities
     * @param shortName             the trimmed short name to check
     * @param exceptLegalEntityId   the legal entity that may keep its own short name on an update, or null on create
     * @throws ConflictException {@link ExceptionDomainLegalEntityError#STA_LEGAL_ENTITY_SHORT_NAME_TAKEN}
     *         (LGE_STA_001) when another legal entity of the organization has the short name
     */
    public void ensureShortNameUnique(UUID organizationId, String shortName, UUID exceptLegalEntityId) {
        boolean taken = exceptLegalEntityId == null
                ? legalEntityRepository.existsByOrganizationIdAndShortNameIgnoreCase(organizationId, shortName)
                : legalEntityRepository.existsByOrganizationIdAndShortNameIgnoreCaseAndIdNot(
                        organizationId, shortName, exceptLegalEntityId);
        if (taken) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    LegalEntityExceptionLogConstant.LEGAL_ENTITY_SHORT_NAME_TAKEN, organizationId, shortName);
            throw new ConflictException(ExceptionDomainLegalEntityError.STA_LEGAL_ENTITY_SHORT_NAME_TAKEN,
                    Map.of(LegalEntityConstant.FIELD_SHORT_NAME, shortName));
        }
    }
}

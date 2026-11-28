package com.korur.oba.shrine.legalentity.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import static com.korur.oba.shrine.legalentity.constant.service.LegalEntityServiceLogConstant.*;

import com.korur.oba.apps.otak.campaign.service.CampaignContentQueryService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.document.service.document.DocumentQueryService;
import com.korur.oba.shrine.document.service.template.DocumentTemplateQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.legalentity.dto.request.LegalEntityCreateRequest;
import com.korur.oba.shrine.legalentity.dto.request.LegalEntityFields;
import com.korur.oba.shrine.legalentity.dto.request.LegalEntityUpdateRequest;
import com.korur.oba.shrine.legalentity.dto.response.LegalEntityDetailResponse;
import com.korur.oba.shrine.legalentity.entity.LegalEntity;
import com.korur.oba.shrine.legalentity.mapper.LegalEntityMapper;
import com.korur.oba.shrine.legalentity.repository.LegalEntityRepository;
import com.korur.oba.shrine.legalentity.service.LegalEntityService;
import com.korur.oba.shrine.legalentity.validator.LegalEntityValidator;

/**
 * Implementation of the legal entity writes declared by {@link LegalEntityService}. Create
 * and update store every field through {@link #applyFields}, so both trim the same way and an
 * update fully replaces the row.
 */
@Service
@RequiredArgsConstructor
public class LegalEntityServiceImpl implements LegalEntityService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(LegalEntityServiceImpl.class);

    private final LegalEntityRepository legalEntityRepository;
    private final LegalEntityMapper legalEntityMapper;
    private final LegalEntityValidator legalEntityValidator;
    private final DocumentQueryService documentQueryService;
    private final DocumentTemplateQueryService documentTemplateQueryService;
    private final CampaignContentQueryService campaignContentQueryService;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public LegalEntityDetailResponse create(UUID organizationId, User user, LegalEntityCreateRequest request) {
        // Trimmed before the check, so " Korur B.V." and "Korur B.V." count as the same short name.
        String shortName = request.shortName().trim();
        legalEntityValidator.ensureShortNameUnique(organizationId, shortName, null);

        LegalEntity legalEntity = new LegalEntity();
        applyFields(legalEntity, shortName, request);
        // The organization comes from the checked header, never from the request body.
        legalEntity.setOrganizationId(organizationId);
        legalEntity = legalEntityRepository.save(legalEntity);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, LEGAL_ENTITY_CREATED, legalEntity.getId(), organizationId,
                user.getId());

        return legalEntityMapper.toDetailResponse(legalEntity);
    }

    @Override
    @Transactional
    public LegalEntityDetailResponse update(UUID organizationId, UUID legalEntityId,
                                            LegalEntityUpdateRequest request) {
        LegalEntity legalEntity = legalEntityValidator.ensureLegalEntityExists(organizationId, legalEntityId);
        String shortName = request.shortName().trim();
        // The legal entity itself is excluded, so it may keep its own short name.
        legalEntityValidator.ensureShortNameUnique(organizationId, shortName, legalEntityId);

        applyFields(legalEntity, shortName, request);
        // Flushed here so the response carries the new last-change time.
        legalEntity = legalEntityRepository.saveAndFlush(legalEntity);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, LEGAL_ENTITY_UPDATED, legalEntityId, organizationId);

        return legalEntityMapper.toDetailResponse(legalEntity);
    }

    @Override
    @Transactional
    public void delete(UUID organizationId, UUID legalEntityId) {
        LegalEntity legalEntity = legalEntityValidator.ensureLegalEntityExists(organizationId, legalEntityId);
        // Checked before the delete, so the client gets a named conflict instead of a foreign key error.
        boolean inUse = documentQueryService.existsByLegalEntity(organizationId, legalEntityId)
                || documentTemplateQueryService.existsByLegalEntity(organizationId, legalEntityId)
                || campaignContentQueryService.existsByLegalEntity(organizationId, legalEntityId);
        legalEntityValidator.ensureNotInUse(organizationId, legalEntityId, inUse);

        legalEntityRepository.delete(legalEntity);
        // Flushed so a row that later tables still reference fails inside this call, not at commit.
        legalEntityRepository.flush();

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, LEGAL_ENTITY_DELETED, legalEntityId, organizationId);
    }

    /**
     * Writes every field of a create or update request, trimmed. The short name arrives
     * trimmed already, because the uniqueness check needs the trimmed value. An optional field
     * sent as null clears the stored value.
     */
    private void applyFields(LegalEntity legalEntity, String shortName, LegalEntityFields fields) {
        legalEntity.setShortName(shortName);
        legalEntity.setLegalName(fields.legalName().trim());
        legalEntity.setCountry(fields.country().trim());
        legalEntity.setRegistrationNumber(trimOrNull(fields.registrationNumber()));
        legalEntity.setVatNumber(trimOrNull(fields.vatNumber()));
        legalEntity.setStreet(trimOrNull(fields.street()));
        legalEntity.setPostalCode(trimOrNull(fields.postalCode()));
        legalEntity.setCity(trimOrNull(fields.city()));
        legalEntity.setGoverningLaw(trimOrNull(fields.governingLaw()));
        legalEntity.setCourt(trimOrNull(fields.court()));
    }

    private static String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }
}

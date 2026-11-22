package com.korur.oba.shrine.legalentity.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.shrine.legalentity.constant.service.LegalEntityServiceLogConstant.*;

import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.legalentity.constant.LegalEntityConstant;
import com.korur.oba.shrine.legalentity.dto.response.LegalEntityDetailResponse;
import com.korur.oba.shrine.legalentity.dto.response.LegalEntityListResponse;
import com.korur.oba.shrine.legalentity.entity.LegalEntity;
import com.korur.oba.shrine.legalentity.mapper.LegalEntityMapper;
import com.korur.oba.shrine.legalentity.repository.LegalEntityRepository;
import com.korur.oba.shrine.legalentity.service.LegalEntityQueryService;
import com.korur.oba.shrine.legalentity.validator.LegalEntityValidator;

/**
 * Implementation of the legal entity reads declared by {@link LegalEntityQueryService}, in a
 * read-only transaction. The single lookup by id and organization is
 * {@link LegalEntityValidator#ensureLegalEntityExists}, shared with the command side, so both
 * answer a foreign or missing id with the same not found.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LegalEntityQueryServiceImpl implements LegalEntityQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(LegalEntityQueryServiceImpl.class);

    // Case ignored, so "korur Inc." sorts next to "Korur B.V." whatever the database collation;
    // the id breaks no real tie (short names are unique) but keeps the order total.
    private static final Sort BY_SHORT_NAME = Sort.by(Sort.Order.asc(LegalEntityConstant.FIELD_SHORT_NAME).ignoreCase())
            .and(Sort.by(Sort.Direction.ASC, LegalEntityConstant.FIELD_ID));

    private final LegalEntityRepository legalEntityRepository;
    private final LegalEntityMapper legalEntityMapper;
    private final LegalEntityValidator legalEntityValidator;
    private final LoggingService loggingService;

    @Override
    public List<LegalEntityListResponse> list(UUID organizationId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_LEGAL_ENTITIES, organizationId);

        return legalEntityRepository.findAllByOrganizationId(organizationId, BY_SHORT_NAME).stream()
                .map(legalEntityMapper::toListResponse)
                .toList();
    }

    @Override
    public LegalEntityDetailResponse get(UUID organizationId, UUID legalEntityId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, READING_LEGAL_ENTITY, legalEntityId, organizationId);

        return legalEntityMapper.toDetailResponse(
                legalEntityValidator.ensureLegalEntityExists(organizationId, legalEntityId));
    }

    @Override
    public LegalEntity getEntity(UUID organizationId, UUID legalEntityId) {
        return legalEntityValidator.ensureLegalEntityExists(organizationId, legalEntityId);
    }
}

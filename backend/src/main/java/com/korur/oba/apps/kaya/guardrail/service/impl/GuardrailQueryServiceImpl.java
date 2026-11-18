package com.korur.oba.apps.kaya.guardrail.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.guardrail.constant.GuardrailConstant;
import com.korur.oba.apps.kaya.guardrail.constant.service.GuardrailServiceLogConstant;
import com.korur.oba.apps.kaya.guardrail.dto.request.GuardrailQueryRequest;
import com.korur.oba.apps.kaya.guardrail.dto.response.GuardrailDetailResponse;
import com.korur.oba.apps.kaya.guardrail.dto.response.GuardrailListResponse;
import com.korur.oba.apps.kaya.guardrail.entity.Guardrail;
import com.korur.oba.apps.kaya.guardrail.enums.GuardrailEnforcement;
import com.korur.oba.apps.kaya.guardrail.mapper.GuardrailMapper;
import com.korur.oba.apps.kaya.guardrail.repository.GuardrailRepository;
import com.korur.oba.apps.kaya.guardrail.service.GuardrailQueryService;
import com.korur.oba.apps.kaya.guardrail.specification.GuardrailSpecification;

/**
 * The {@link GuardrailQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuardrailQueryServiceImpl implements GuardrailQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(GuardrailQueryServiceImpl.class);

    // Version first, so the guardrails of one version stay together when a page lists several.
    private static final Sort LIST_SORT = Sort.by(GuardrailConstant.FIELD_VERSION_ID, GuardrailConstant.FIELD_NAME,
            GuardrailConstant.FIELD_ID);

    private final GuardrailRepository guardrailRepository;
    private final BlobQueryService blobQueryService;
    private final GuardrailMapper guardrailMapper;
    private final LoggingService loggingService;

    @Override
    public List<GuardrailListResponse> getGuardrails(UUID organizationId, GuardrailQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, GuardrailServiceLogConstant.LISTING_GUARDRAILS, organizationId,
                request);
        List<Guardrail> guardrails = guardrailRepository.findAll(GuardrailSpecification.filterBy(organizationId, request),
                LIST_SORT);
        if (guardrails.isEmpty()) {
            return List.of();
        }
        // The sizes are read in one query, not once per guardrail.
        Map<UUID, Integer> sizes = blobQueryService.getSizesByIds(organizationId,
                guardrails.stream().map(Guardrail::getContentBlobId).collect(Collectors.toSet()));
        return guardrails.stream()
                .map(guardrail -> guardrailMapper.toListResponse(guardrail, pathOf(guardrail), enforcementOf(guardrail),
                        sizes.get(guardrail.getContentBlobId())))
                .toList();
    }

    @Override
    public GuardrailDetailResponse getGuardrail(UUID organizationId, UUID guardrailId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, GuardrailServiceLogConstant.READING_GUARDRAIL, guardrailId,
                organizationId);
        Guardrail guardrail = getEntityById(organizationId, guardrailId);
        Blob blob = blobQueryService.getEntityById(organizationId, guardrail.getContentBlobId());
        return guardrailMapper.toDetailResponse(guardrail, pathOf(guardrail), enforcementOf(guardrail), blob.getSize(),
                new String(blob.getContent(), StandardCharsets.UTF_8));
    }

    @Override
    public Guardrail getEntityById(UUID organizationId, UUID guardrailId) {
        // Looked up within the organization, so a guardrail of another organization answers the same
        // 404 as an unknown id.
        return guardrailRepository.findByIdAndOrganizationId(guardrailId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, GuardrailServiceLogConstant.GUARDRAIL_NOT_FOUND, guardrailId,
                    organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(GuardrailConstant.FIELD_GUARDRAIL_ID, guardrailId.toString()));
        });
    }

    @Override
    public List<Guardrail> getEntitiesByVersionId(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, GuardrailServiceLogConstant.LOADING_VERSION_GUARDRAILS, versionId);
        return guardrailRepository.findByOrganizationIdAndVersionIdOrderByNameAsc(organizationId, versionId);
    }

    private static String pathOf(Guardrail guardrail) {
        return guardrail.getName() + GuardrailConstant.FILE_SUFFIX;
    }

    // Derived rather than stored, so it always agrees with the deny rules.
    private static GuardrailEnforcement enforcementOf(Guardrail guardrail) {
        return guardrail.getDeny() == null || guardrail.getDeny().isEmpty()
                ? GuardrailEnforcement.INSTRUCT
                : GuardrailEnforcement.BLOCK;
    }
}

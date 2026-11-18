package com.korur.oba.apps.kaya.convention.service.impl;

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
import com.korur.oba.apps.kaya.convention.constant.ConventionConstant;
import com.korur.oba.apps.kaya.convention.constant.service.ConventionServiceLogConstant;
import com.korur.oba.apps.kaya.convention.dto.request.ConventionQueryRequest;
import com.korur.oba.apps.kaya.convention.dto.response.ConventionDetailResponse;
import com.korur.oba.apps.kaya.convention.dto.response.ConventionListResponse;
import com.korur.oba.apps.kaya.convention.entity.Convention;
import com.korur.oba.apps.kaya.convention.mapper.ConventionMapper;
import com.korur.oba.apps.kaya.convention.repository.ConventionRepository;
import com.korur.oba.apps.kaya.convention.service.ConventionQueryService;
import com.korur.oba.apps.kaya.convention.specification.ConventionSpecification;

/**
 * The {@link ConventionQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConventionQueryServiceImpl implements ConventionQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ConventionQueryServiceImpl.class);

    // Version first, so the conventions of one version stay together when a page lists several;
    // PostgreSQL sorts a null path last in ascending order.
    private static final Sort LIST_SORT = Sort.by(ConventionConstant.FIELD_VERSION_ID,
            ConventionConstant.FIELD_RELATIVE_PATH, ConventionConstant.FIELD_ID);

    private final ConventionRepository conventionRepository;
    private final BlobQueryService blobQueryService;
    private final ConventionMapper conventionMapper;
    private final LoggingService loggingService;

    @Override
    public List<ConventionListResponse> getConventions(UUID organizationId, ConventionQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ConventionServiceLogConstant.LISTING_CONVENTIONS,
                organizationId, request);
        List<Convention> conventions = conventionRepository.findAll(
                ConventionSpecification.filterBy(organizationId, request), LIST_SORT);
        if (conventions.isEmpty()) {
            return List.of();
        }

        // The sizes are read in one query, without loading any content.
        Map<UUID, Integer> sizes = blobQueryService.getSizesByIds(organizationId,
                conventions.stream().map(Convention::getContentBlobId).collect(Collectors.toSet()));
        return conventions.stream()
                .map(convention -> conventionMapper.toListResponse(convention, sizes.get(convention.getContentBlobId())))
                .toList();
    }

    @Override
    public ConventionDetailResponse getConvention(UUID organizationId, UUID conventionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ConventionServiceLogConstant.READING_CONVENTION,
                conventionId, organizationId);
        Convention convention = getEntityById(organizationId, conventionId);
        Blob blob = blobQueryService.getEntityById(organizationId, convention.getContentBlobId());
        return conventionMapper.toDetailResponse(convention, blob.getSize(),
                new String(blob.getContent(), StandardCharsets.UTF_8));
    }

    @Override
    public Convention getEntityById(UUID organizationId, UUID conventionId) {
        // Looked up within the organization, so a convention of another organization answers the same
        // 404 as an unknown id.
        return conventionRepository.findByIdAndOrganizationId(conventionId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ConventionServiceLogConstant.CONVENTION_NOT_FOUND,
                    conventionId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(ConventionConstant.FIELD_CONVENTION_ID, conventionId.toString()));
        });
    }

    @Override
    public List<Convention> getEntitiesByVersionId(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ConventionServiceLogConstant.LOADING_VERSION_CONVENTIONS,
                versionId);
        return conventionRepository.findByOrganizationIdAndVersionIdOrderByRelativePathAsc(organizationId, versionId);
    }
}

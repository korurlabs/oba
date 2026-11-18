package com.korur.oba.apps.kaya.subagent.service.impl;

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
import com.korur.oba.apps.kaya.subagent.constant.SubagentConstant;
import com.korur.oba.apps.kaya.subagent.constant.service.SubagentServiceLogConstant;
import com.korur.oba.apps.kaya.subagent.dto.request.SubagentQueryRequest;
import com.korur.oba.apps.kaya.subagent.dto.response.SubagentDetailResponse;
import com.korur.oba.apps.kaya.subagent.dto.response.SubagentListResponse;
import com.korur.oba.apps.kaya.subagent.entity.Subagent;
import com.korur.oba.apps.kaya.subagent.mapper.SubagentMapper;
import com.korur.oba.apps.kaya.subagent.repository.SubagentRepository;
import com.korur.oba.apps.kaya.subagent.service.SubagentQueryService;
import com.korur.oba.apps.kaya.subagent.specification.SubagentSpecification;

/**
 * The {@link SubagentQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubagentQueryServiceImpl implements SubagentQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SubagentQueryServiceImpl.class);

    // Version first, so the subagents of one version stay together when a page lists several.
    private static final Sort LIST_SORT = Sort.by(SubagentConstant.FIELD_VERSION_ID,
            SubagentConstant.FIELD_RELATIVE_PATH, SubagentConstant.FIELD_ID);

    private final SubagentRepository subagentRepository;
    private final BlobQueryService blobQueryService;
    private final SubagentMapper subagentMapper;
    private final LoggingService loggingService;

    @Override
    public List<SubagentListResponse> getSubagents(UUID organizationId, SubagentQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SubagentServiceLogConstant.LISTING_SUBAGENTS,
                organizationId, request);
        List<Subagent> subagents = subagentRepository.findAll(
                SubagentSpecification.filterBy(organizationId, request), LIST_SORT);
        if (subagents.isEmpty()) {
            return List.of();
        }

        // The sizes are read in one query, without loading any content.
        Map<UUID, Integer> sizes = blobQueryService.getSizesByIds(organizationId,
                subagents.stream().map(Subagent::getContentBlobId).collect(Collectors.toSet()));
        return subagents.stream()
                .map(subagent -> subagentMapper.toListResponse(subagent, sizes.get(subagent.getContentBlobId())))
                .toList();
    }

    @Override
    public SubagentDetailResponse getSubagent(UUID organizationId, UUID subagentId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SubagentServiceLogConstant.READING_SUBAGENT,
                subagentId, organizationId);
        Subagent subagent = getEntityById(organizationId, subagentId);
        Blob blob = blobQueryService.getEntityById(organizationId, subagent.getContentBlobId());
        return subagentMapper.toDetailResponse(subagent, blob.getSize(),
                new String(blob.getContent(), StandardCharsets.UTF_8));
    }

    @Override
    public Subagent getEntityById(UUID organizationId, UUID subagentId) {
        // Looked up within the organization, so a subagent of another organization answers the same
        // 404 as an unknown id.
        return subagentRepository.findByIdAndOrganizationId(subagentId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SubagentServiceLogConstant.SUBAGENT_NOT_FOUND,
                    subagentId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(SubagentConstant.FIELD_SUBAGENT_ID, subagentId.toString()));
        });
    }

    @Override
    public List<Subagent> getEntitiesByVersionId(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SubagentServiceLogConstant.LOADING_VERSION_SUBAGENTS,
                versionId);
        return subagentRepository.findByOrganizationIdAndVersionIdOrderByRelativePathAsc(organizationId, versionId);
    }
}

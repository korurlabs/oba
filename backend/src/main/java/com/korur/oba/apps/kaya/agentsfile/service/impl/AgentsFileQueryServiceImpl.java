package com.korur.oba.apps.kaya.agentsfile.service.impl;

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
import com.korur.oba.apps.kaya.agentsfile.constant.AgentsFileConstant;
import com.korur.oba.apps.kaya.agentsfile.constant.service.AgentsFileServiceLogConstant;
import com.korur.oba.apps.kaya.agentsfile.dto.request.AgentsFileQueryRequest;
import com.korur.oba.apps.kaya.agentsfile.dto.response.AgentsFileDetailResponse;
import com.korur.oba.apps.kaya.agentsfile.dto.response.AgentsFileListResponse;
import com.korur.oba.apps.kaya.agentsfile.entity.AgentsFile;
import com.korur.oba.apps.kaya.agentsfile.mapper.AgentsFileMapper;
import com.korur.oba.apps.kaya.agentsfile.repository.AgentsFileRepository;
import com.korur.oba.apps.kaya.agentsfile.service.AgentsFileQueryService;
import com.korur.oba.apps.kaya.agentsfile.specification.AgentsFileSpecification;

/**
 * The {@link AgentsFileQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentsFileQueryServiceImpl implements AgentsFileQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AgentsFileQueryServiceImpl.class);

    // Version first, so the agents files of one version stay together when a page lists several;
    // PostgreSQL orders the placement enum as declared: ROOT, AGENTS_DIR, MOUNT_ROOT.
    private static final Sort LIST_SORT = Sort.by(AgentsFileConstant.FIELD_VERSION_ID,
            AgentsFileConstant.FIELD_PLACEMENT, AgentsFileConstant.FIELD_ID);

    private final AgentsFileRepository agentsFileRepository;
    private final BlobQueryService blobQueryService;
    private final AgentsFileMapper agentsFileMapper;
    private final LoggingService loggingService;

    @Override
    public List<AgentsFileListResponse> getAgentsFiles(UUID organizationId, AgentsFileQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, AgentsFileServiceLogConstant.LISTING_AGENTS_FILES,
                organizationId, request);
        List<AgentsFile> agentsFiles = agentsFileRepository.findAll(
                AgentsFileSpecification.filterBy(organizationId, request), LIST_SORT);
        if (agentsFiles.isEmpty()) {
            return List.of();
        }

        // The sizes are read in one query, without loading any content.
        Map<UUID, Integer> sizes = blobQueryService.getSizesByIds(organizationId,
                agentsFiles.stream().map(AgentsFile::getContentBlobId).collect(Collectors.toSet()));
        return agentsFiles.stream()
                .map(agentsFile -> agentsFileMapper.toListResponse(agentsFile, sizes.get(agentsFile.getContentBlobId())))
                .toList();
    }

    @Override
    public AgentsFileDetailResponse getAgentsFile(UUID organizationId, UUID agentsFileId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, AgentsFileServiceLogConstant.READING_AGENTS_FILE,
                agentsFileId, organizationId);
        AgentsFile agentsFile = getEntityById(organizationId, agentsFileId);
        Blob blob = blobQueryService.getEntityById(organizationId, agentsFile.getContentBlobId());
        return agentsFileMapper.toDetailResponse(agentsFile, blob.getSize(),
                new String(blob.getContent(), StandardCharsets.UTF_8));
    }

    @Override
    public AgentsFile getEntityById(UUID organizationId, UUID agentsFileId) {
        // Looked up within the organization, so an agents file of another organization answers the same
        // 404 as an unknown id.
        return agentsFileRepository.findByIdAndOrganizationId(agentsFileId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, AgentsFileServiceLogConstant.AGENTS_FILE_NOT_FOUND,
                    agentsFileId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(AgentsFileConstant.FIELD_AGENTS_FILE_ID, agentsFileId.toString()));
        });
    }

    @Override
    public List<AgentsFile> getEntitiesByVersionId(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, AgentsFileServiceLogConstant.LOADING_VERSION_AGENTS_FILES,
                versionId);
        return agentsFileRepository.findByOrganizationIdAndVersionIdOrderByPlacementAsc(organizationId, versionId);
    }
}

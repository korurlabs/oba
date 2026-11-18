package com.korur.oba.apps.kaya.workflow.service.impl;

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
import com.korur.oba.apps.kaya.workflow.constant.WorkflowConstant;
import com.korur.oba.apps.kaya.workflow.constant.service.WorkflowServiceLogConstant;
import com.korur.oba.apps.kaya.workflow.dto.request.WorkflowQueryRequest;
import com.korur.oba.apps.kaya.workflow.dto.response.WorkflowDetailResponse;
import com.korur.oba.apps.kaya.workflow.dto.response.WorkflowListResponse;
import com.korur.oba.apps.kaya.workflow.entity.Workflow;
import com.korur.oba.apps.kaya.workflow.mapper.WorkflowMapper;
import com.korur.oba.apps.kaya.workflow.repository.WorkflowRepository;
import com.korur.oba.apps.kaya.workflow.service.WorkflowQueryService;
import com.korur.oba.apps.kaya.workflow.specification.WorkflowSpecification;

/**
 * The {@link WorkflowQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkflowQueryServiceImpl implements WorkflowQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(WorkflowQueryServiceImpl.class);

    // Version first, so the workflows of one version stay together when a page lists several;
    // PostgreSQL sorts a null path last in ascending order.
    private static final Sort LIST_SORT = Sort.by(WorkflowConstant.FIELD_VERSION_ID,
            WorkflowConstant.FIELD_RELATIVE_PATH, WorkflowConstant.FIELD_ID);

    private final WorkflowRepository workflowRepository;
    private final BlobQueryService blobQueryService;
    private final WorkflowMapper workflowMapper;
    private final LoggingService loggingService;

    @Override
    public List<WorkflowListResponse> getWorkflows(UUID organizationId, WorkflowQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, WorkflowServiceLogConstant.LISTING_WORKFLOWS,
                organizationId, request);
        List<Workflow> workflows = workflowRepository.findAll(WorkflowSpecification.filterBy(organizationId, request), LIST_SORT);
        if (workflows.isEmpty()) {
            return List.of();
        }

        // The sizes are read in one query, without loading any content.
        Map<UUID, Integer> sizes = blobQueryService.getSizesByIds(organizationId,
                workflows.stream().map(Workflow::getContentBlobId).collect(Collectors.toSet()));
        return workflows.stream()
                .map(workflow -> workflowMapper.toListResponse(workflow, sizes.get(workflow.getContentBlobId())))
                .toList();
    }

    @Override
    public WorkflowDetailResponse getWorkflow(UUID organizationId, UUID workflowId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, WorkflowServiceLogConstant.READING_WORKFLOW, workflowId, organizationId);
        Workflow workflow = getEntityById(organizationId, workflowId);
        Blob blob = blobQueryService.getEntityById(organizationId, workflow.getContentBlobId());
        return workflowMapper.toDetailResponse(workflow, blob.getSize(), new String(blob.getContent(), StandardCharsets.UTF_8));
    }

    @Override
    public Workflow getEntityById(UUID organizationId, UUID workflowId) {
        // Looked up within the organization, so a workflow of another organization answers the same 404 as
        // an unknown id.
        return workflowRepository.findByIdAndOrganizationId(workflowId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, WorkflowServiceLogConstant.WORKFLOW_NOT_FOUND, workflowId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(WorkflowConstant.FIELD_WORKFLOW_ID, workflowId.toString()));
        });
    }

    @Override
    public List<Workflow> getEntitiesByVersionId(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, WorkflowServiceLogConstant.LOADING_VERSION_WORKFLOWS, versionId);
        return workflowRepository.findByOrganizationIdAndVersionIdOrderByRelativePathAsc(organizationId, versionId);
    }
}

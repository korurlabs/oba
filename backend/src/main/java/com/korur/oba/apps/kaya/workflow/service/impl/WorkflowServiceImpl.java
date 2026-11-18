package com.korur.oba.apps.kaya.workflow.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.workflow.constant.service.WorkflowServiceLogConstant;
import com.korur.oba.apps.kaya.workflow.dto.embedded.WorkflowIndex;
import com.korur.oba.apps.kaya.workflow.entity.Workflow;
import com.korur.oba.apps.kaya.workflow.repository.WorkflowRepository;
import com.korur.oba.apps.kaya.workflow.service.WorkflowService;

/**
 * The {@link WorkflowService} implementation.
 */
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(WorkflowServiceImpl.class);

    private final WorkflowRepository workflowRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public List<Workflow> replaceVersion(UUID organizationId, UUID versionId, List<WorkflowIndex> workflows) {
        // The old rows are deleted and flushed first, so a workflow saved again at the same path does not
        // collide with the old row on the version and path unique index.
        int removed = workflowRepository.deleteByVersion(organizationId, versionId);
        List<Workflow> entities = workflows.stream()
                .map(index -> toEntity(organizationId, versionId, index))
                .toList();
        List<Workflow> saved = workflowRepository.saveAll(entities);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, WorkflowServiceLogConstant.WORKFLOWS_REPLACED,
                versionId, organizationId, removed, saved.size());
        return saved;
    }

    @Override
    @Transactional
    public Workflow saveFile(UUID organizationId, UUID versionId, WorkflowIndex index) {
        // The old row is deleted and flushed first, so the new row does not collide with it on the
        // version and path unique key.
        int removed = workflowRepository.deleteByPath(organizationId, versionId, index.relativePath());
        Workflow saved = workflowRepository.save(toEntity(organizationId, versionId, index));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, WorkflowServiceLogConstant.WORKFLOW_SAVED,
                index.relativePath(), versionId, organizationId, removed);
        return saved;
    }

    @Override
    @Transactional
    public boolean deleteFile(UUID organizationId, UUID versionId, String relativePath) {
        int removed = workflowRepository.deleteByPath(organizationId, versionId, relativePath);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, WorkflowServiceLogConstant.WORKFLOW_DELETED,
                relativePath, versionId, organizationId, removed);
        return removed > 0;
    }

    @Override
    @Transactional
    public Workflow savePart(UUID organizationId, UUID versionId, WorkflowIndex index) {
        Workflow saved = workflowRepository.save(toEntity(organizationId, versionId, index));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, WorkflowServiceLogConstant.WORKFLOW_PART_SAVED,
                saved.getId(), versionId, organizationId);
        return saved;
    }

    @Override
    @Transactional
    public int deleteParts(UUID organizationId, UUID versionId, Collection<UUID> partIds) {
        if (partIds.isEmpty()) {
            return 0;
        }
        int removed = workflowRepository.deleteParts(organizationId, versionId, partIds);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, WorkflowServiceLogConstant.WORKFLOW_PARTS_DELETED,
                removed, versionId, organizationId);
        return removed;
    }

    private Workflow toEntity(UUID organizationId, UUID versionId, WorkflowIndex index) {
        return Workflow.builder()
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(index.contentBlobId())
                .relativePath(index.relativePath())
                .title(index.title())
                .build();
    }
}

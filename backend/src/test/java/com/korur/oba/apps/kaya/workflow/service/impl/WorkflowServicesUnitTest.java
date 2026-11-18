package com.korur.oba.apps.kaya.workflow.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party
import org.mapstruct.factory.Mappers;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.workflow.dto.embedded.WorkflowIndex;
import com.korur.oba.apps.kaya.workflow.dto.request.WorkflowQueryRequest;
import com.korur.oba.apps.kaya.workflow.dto.response.WorkflowDetailResponse;
import com.korur.oba.apps.kaya.workflow.dto.response.WorkflowListResponse;
import com.korur.oba.apps.kaya.workflow.entity.Workflow;
import com.korur.oba.apps.kaya.workflow.mapper.WorkflowMapper;
import com.korur.oba.apps.kaya.workflow.repository.WorkflowRepository;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServicesUnitTest {

    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private BlobQueryService blobQueryService;
    @Mock
    private LoggingService loggingService;

    private WorkflowQueryServiceImpl workflowQueryService;
    private WorkflowServiceImpl workflowService;
    private UUID organizationId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        workflowQueryService = new WorkflowQueryServiceImpl(workflowRepository, blobQueryService,
                Mappers.getMapper(WorkflowMapper.class), loggingService);
        workflowService = new WorkflowServiceImpl(workflowRepository, loggingService);
        organizationId = UUID.randomUUID();
        versionId = UUID.randomUUID();
    }

    private Workflow workflow(String relativePath) {
        return Workflow.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(UUID.randomUUID())
                .relativePath(relativePath)
                .title("Implement")
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getWorkflows_workflowsFound_returnsIndexFieldsAndSizes() {
        Workflow first = workflow("implement.md");
        Workflow second = workflow(null);
        WorkflowQueryRequest request = new WorkflowQueryRequest();
        request.setVersionId(List.of(versionId));
        when(workflowRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(first, second));
        when(blobQueryService.getSizesByIds(organizationId, Set.of(first.getContentBlobId(), second.getContentBlobId())))
                .thenReturn(Map.of(first.getContentBlobId(), 900, second.getContentBlobId(), 50));

        List<WorkflowListResponse> result = workflowQueryService.getWorkflows(organizationId, request);

        assertEquals(2, result.size());
        assertEquals("implement.md", result.get(0).path());
        assertEquals(900, result.get(0).sizeBytes());
        assertEquals(50, result.get(1).sizeBytes());
        assertNull(result.get(1).path());
        assertEquals("Implement", result.get(0).title());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getWorkflows_noWorkflows_skipsBlobQuery() {
        WorkflowQueryRequest request = new WorkflowQueryRequest();
        request.setVersionId(List.of(versionId));
        when(workflowRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        List<WorkflowListResponse> result = workflowQueryService.getWorkflows(organizationId, request);

        assertEquals(0, result.size());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    void getWorkflow_workflowOfOrganization_returnsContentAsUtf8() {
        Workflow workflow = workflow("implement.md");
        byte[] content = "---\ntitle: Implement\n---\n# Implement\n".getBytes(StandardCharsets.UTF_8);
        Blob blob = Blob.builder().id(workflow.getContentBlobId()).content(content).size(content.length).build();
        when(workflowRepository.findByIdAndOrganizationId(workflow.getId(), organizationId)).thenReturn(Optional.of(workflow));
        when(blobQueryService.getEntityById(organizationId, workflow.getContentBlobId())).thenReturn(blob);

        WorkflowDetailResponse result = workflowQueryService.getWorkflow(organizationId, workflow.getId());

        assertEquals("---\ntitle: Implement\n---\n# Implement\n", result.content());
        assertEquals("implement.md", result.path());
        assertEquals(content.length, result.sizeBytes());
    }

    @Test
    void getWorkflow_unknownOrOtherOrganization_throwsNotFound() {
        UUID workflowId = UUID.randomUUID();
        when(workflowRepository.findByIdAndOrganizationId(workflowId, organizationId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> workflowQueryService.getWorkflow(organizationId, workflowId));

        assertEquals("KAY_NF_001", exception.getErrorCode());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceVersion_indexRows_deletesFirstAndWritesWorkflows() {
        UUID blobId = UUID.randomUUID();
        WorkflowIndex index = new WorkflowIndex(null, blobId, "Skill-only");
        when(workflowRepository.saveAll(any(List.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Workflow> result = workflowService.replaceVersion(organizationId, versionId, List.of(index));

        InOrder order = inOrder(workflowRepository);
        order.verify(workflowRepository).deleteByVersion(organizationId, versionId);
        order.verify(workflowRepository).saveAll(any(List.class));
        assertEquals(1, result.size());
        assertEquals(organizationId, result.get(0).getOrganizationId());
        assertEquals(versionId, result.get(0).getVersionId());
        assertEquals(blobId, result.get(0).getContentBlobId());
        assertNull(result.get(0).getRelativePath());
        assertEquals("Skill-only", result.get(0).getTitle());
    }

    @Test
    void saveFile_index_deletesTheOldRowFirstAndWritesOne() {
        UUID blobId = UUID.randomUUID();
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Workflow result = workflowService.saveFile(organizationId, versionId, new WorkflowIndex("plan.md", blobId, "Plan"));

        InOrder order = inOrder(workflowRepository);
        order.verify(workflowRepository).deleteByPath(organizationId, versionId, "plan.md");
        order.verify(workflowRepository).save(any(Workflow.class));
        assertEquals(organizationId, result.getOrganizationId());
        assertEquals(versionId, result.getVersionId());
        assertEquals(blobId, result.getContentBlobId());
    }

    @Test
    void deleteFile_noRowAtPath_returnsFalse() {
        when(workflowRepository.deleteByPath(organizationId, versionId, "plan.md")).thenReturn(0);

        assertFalse(workflowService.deleteFile(organizationId, versionId, "plan.md"));
    }

    @Test
    void deleteFile_rowAtPath_returnsTrue() {
        when(workflowRepository.deleteByPath(organizationId, versionId, "plan.md")).thenReturn(1);

        assertTrue(workflowService.deleteFile(organizationId, versionId, "plan.md"));
    }

    @Test
    void savePart_index_addsOneRowWithoutDeleting() {
        UUID blobId = UUID.randomUUID();
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Workflow result = workflowService.savePart(organizationId, versionId, new WorkflowIndex("plan.md", blobId, "Plan"));

        assertEquals(blobId, result.getContentBlobId());
        verify(workflowRepository, never()).deleteByVersion(any(), any());
    }

    @Test
    void deleteParts_noIds_deletesNothing() {
        assertEquals(0, workflowService.deleteParts(organizationId, versionId, List.of()));

        verify(workflowRepository, never()).deleteParts(any(), any(), anyCollection());
    }

    @Test
    void deleteParts_ids_deletesThem() {
        List<UUID> partIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(workflowRepository.deleteParts(organizationId, versionId, partIds)).thenReturn(2);

        assertEquals(2, workflowService.deleteParts(organizationId, versionId, partIds));
    }
}

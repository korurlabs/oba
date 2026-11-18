package com.korur.oba.apps.kaya.subagent.service.impl;

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
import com.korur.oba.apps.kaya.subagent.dto.embedded.SubagentIndex;
import com.korur.oba.apps.kaya.subagent.dto.request.SubagentQueryRequest;
import com.korur.oba.apps.kaya.subagent.dto.response.SubagentDetailResponse;
import com.korur.oba.apps.kaya.subagent.dto.response.SubagentListResponse;
import com.korur.oba.apps.kaya.subagent.entity.Subagent;
import com.korur.oba.apps.kaya.subagent.mapper.SubagentMapper;
import com.korur.oba.apps.kaya.subagent.repository.SubagentRepository;

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
class SubagentServicesUnitTest {

    @Mock
    private SubagentRepository subagentRepository;
    @Mock
    private BlobQueryService blobQueryService;
    @Mock
    private LoggingService loggingService;

    private SubagentQueryServiceImpl subagentQueryService;
    private SubagentServiceImpl subagentService;
    private UUID organizationId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        subagentQueryService = new SubagentQueryServiceImpl(subagentRepository, blobQueryService,
                Mappers.getMapper(SubagentMapper.class), loggingService);
        subagentService = new SubagentServiceImpl(subagentRepository, loggingService);
        organizationId = UUID.randomUUID();
        versionId = UUID.randomUUID();
    }

    private Subagent subagent(String relativePath, String name) {
        return Subagent.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(UUID.randomUUID())
                .relativePath(relativePath)
                .name(name)
                .model("opus")
                .maxTurns(50)
                .tools(List.of("Read", "Bash"))
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSubagents_subagentsFound_returnsPathsAndSizes() {
        Subagent first = subagent("coding-engineer.md", "coding-engineer");
        Subagent second = subagent("reviewer.md", "reviewer");
        SubagentQueryRequest request = new SubagentQueryRequest();
        request.setVersionId(List.of(versionId));
        when(subagentRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(first, second));
        when(blobQueryService.getSizesByIds(organizationId, Set.of(first.getContentBlobId(), second.getContentBlobId())))
                .thenReturn(Map.of(first.getContentBlobId(), 900, second.getContentBlobId(), 50));

        List<SubagentListResponse> result = subagentQueryService.getSubagents(organizationId, request);

        assertEquals(2, result.size());
        assertEquals("coding-engineer.md", result.get(0).path());
        assertEquals("coding-engineer", result.get(0).name());
        assertEquals(List.of("Read", "Bash"), result.get(0).tools());
        assertEquals(50, result.get(0).maxTurns());
        assertEquals(900, result.get(0).sizeBytes());
        assertEquals("reviewer.md", result.get(1).path());
        assertEquals(50, result.get(1).sizeBytes());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSubagents_noSubagents_readsNoSizes() {
        SubagentQueryRequest request = new SubagentQueryRequest();
        request.setVersionId(List.of(versionId));
        when(subagentRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        List<SubagentListResponse> result = subagentQueryService.getSubagents(organizationId, request);

        assertEquals(0, result.size());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    void getSubagent_subagentOfOrganization_returnsContentAsUtf8() {
        Subagent subagent = subagent("coding-engineer.md", "coding-engineer");
        byte[] content = "---\nname: coding-engineer\n---\nYou implement one scoped change.\n".getBytes(StandardCharsets.UTF_8);
        Blob blob = Blob.builder().id(subagent.getContentBlobId()).content(content).size(content.length).build();
        when(subagentRepository.findByIdAndOrganizationId(subagent.getId(), organizationId)).thenReturn(Optional.of(subagent));
        when(blobQueryService.getEntityById(organizationId, subagent.getContentBlobId())).thenReturn(blob);

        SubagentDetailResponse result = subagentQueryService.getSubagent(organizationId, subagent.getId());

        assertEquals("---\nname: coding-engineer\n---\nYou implement one scoped change.\n", result.content());
        assertEquals("coding-engineer.md", result.path());
        assertEquals(content.length, result.sizeBytes());
    }

    @Test
    void getSubagent_unknownOrOtherOrganization_throwsNotFound() {
        UUID subagentId = UUID.randomUUID();
        when(subagentRepository.findByIdAndOrganizationId(subagentId, organizationId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> subagentQueryService.getSubagent(organizationId, subagentId));

        assertEquals("KAY_NF_001", exception.getErrorCode());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceVersion_indexRows_deletesFirstAndWritesSubagents() {
        UUID blobId = UUID.randomUUID();
        SubagentIndex index = new SubagentIndex("coding-engineer.md", blobId, "coding-engineer", null, null, null, null);
        when(subagentRepository.saveAll(any(List.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Subagent> result = subagentService.replaceVersion(organizationId, versionId, List.of(index));

        InOrder order = inOrder(subagentRepository);
        order.verify(subagentRepository).deleteByVersion(organizationId, versionId);
        order.verify(subagentRepository).saveAll(any(List.class));
        assertEquals(1, result.size());
        assertEquals(organizationId, result.get(0).getOrganizationId());
        assertEquals(versionId, result.get(0).getVersionId());
        assertEquals(blobId, result.get(0).getContentBlobId());
        assertEquals("coding-engineer.md", result.get(0).getRelativePath());
        assertEquals("coding-engineer", result.get(0).getName());
        assertNull(result.get(0).getTools());
    }

    @Test
    void saveFile_index_deletesTheOldRowFirstAndWritesOne() {
        UUID blobId = UUID.randomUUID();
        when(subagentRepository.save(any(Subagent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Subagent result = subagentService.saveFile(organizationId, versionId, new SubagentIndex("reviewer.md", blobId, "reviewer", null, null, 5, List.of("Read")));

        InOrder order = inOrder(subagentRepository);
        order.verify(subagentRepository).deleteByPath(organizationId, versionId, "reviewer.md");
        order.verify(subagentRepository).save(any(Subagent.class));
        assertEquals(organizationId, result.getOrganizationId());
        assertEquals(versionId, result.getVersionId());
        assertEquals(blobId, result.getContentBlobId());
    }

    @Test
    void deleteFile_noRowAtPath_returnsFalse() {
        when(subagentRepository.deleteByPath(organizationId, versionId, "reviewer.md")).thenReturn(0);

        assertFalse(subagentService.deleteFile(organizationId, versionId, "reviewer.md"));
    }

    @Test
    void deleteFile_rowAtPath_returnsTrue() {
        when(subagentRepository.deleteByPath(organizationId, versionId, "reviewer.md")).thenReturn(1);

        assertTrue(subagentService.deleteFile(organizationId, versionId, "reviewer.md"));
    }
}

package com.korur.oba.apps.kaya.agentsfile.service.impl;

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
import com.korur.oba.apps.kaya.agentsfile.dto.embedded.AgentsFileIndex;
import com.korur.oba.apps.kaya.agentsfile.dto.request.AgentsFileQueryRequest;
import com.korur.oba.apps.kaya.agentsfile.dto.response.AgentsFileDetailResponse;
import com.korur.oba.apps.kaya.agentsfile.dto.response.AgentsFileListResponse;
import com.korur.oba.apps.kaya.agentsfile.entity.AgentsFile;
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;
import com.korur.oba.apps.kaya.agentsfile.mapper.AgentsFileMapper;
import com.korur.oba.apps.kaya.agentsfile.repository.AgentsFileRepository;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentsFileServicesUnitTest {

    @Mock
    private AgentsFileRepository agentsFileRepository;
    @Mock
    private BlobQueryService blobQueryService;
    @Mock
    private LoggingService loggingService;

    private AgentsFileQueryServiceImpl agentsFileQueryService;
    private AgentsFileServiceImpl agentsFileService;
    private UUID organizationId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        agentsFileQueryService = new AgentsFileQueryServiceImpl(agentsFileRepository, blobQueryService,
                Mappers.getMapper(AgentsFileMapper.class), loggingService);
        agentsFileService = new AgentsFileServiceImpl(agentsFileRepository, loggingService);
        organizationId = UUID.randomUUID();
        versionId = UUID.randomUUID();
    }

    private AgentsFile agentsFile(AgentsFilePlacement placement) {
        return AgentsFile.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(UUID.randomUUID())
                .placement(placement)
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAgentsFiles_agentsFilesFound_returnsPlacementsAndSizes() {
        AgentsFile first = agentsFile(AgentsFilePlacement.ROOT);
        AgentsFile second = agentsFile(AgentsFilePlacement.AGENTS_DIR);
        AgentsFileQueryRequest request = new AgentsFileQueryRequest();
        request.setVersionId(List.of(versionId));
        when(agentsFileRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(first, second));
        when(blobQueryService.getSizesByIds(organizationId, Set.of(first.getContentBlobId(), second.getContentBlobId())))
                .thenReturn(Map.of(first.getContentBlobId(), 900, second.getContentBlobId(), 50));

        List<AgentsFileListResponse> result = agentsFileQueryService.getAgentsFiles(organizationId, request);

        assertEquals(2, result.size());
        assertEquals(AgentsFilePlacement.ROOT, result.get(0).placement());
        assertEquals(900, result.get(0).sizeBytes());
        assertEquals(AgentsFilePlacement.AGENTS_DIR, result.get(1).placement());
        assertEquals(50, result.get(1).sizeBytes());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAgentsFiles_noAgentsFiles_readsNoSizes() {
        AgentsFileQueryRequest request = new AgentsFileQueryRequest();
        request.setVersionId(List.of(versionId));
        when(agentsFileRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        List<AgentsFileListResponse> result = agentsFileQueryService.getAgentsFiles(organizationId, request);

        assertEquals(0, result.size());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    void getAgentsFile_agentsFileOfOrganization_returnsContentAsUtf8() {
        AgentsFile agentsFile = agentsFile(AgentsFilePlacement.MOUNT_ROOT);
        byte[] content = "# Agent instructions\n".getBytes(StandardCharsets.UTF_8);
        Blob blob = Blob.builder().id(agentsFile.getContentBlobId()).content(content).size(content.length).build();
        when(agentsFileRepository.findByIdAndOrganizationId(agentsFile.getId(), organizationId)).thenReturn(Optional.of(agentsFile));
        when(blobQueryService.getEntityById(organizationId, agentsFile.getContentBlobId())).thenReturn(blob);

        AgentsFileDetailResponse result = agentsFileQueryService.getAgentsFile(organizationId, agentsFile.getId());

        assertEquals("# Agent instructions\n", result.content());
        assertEquals(AgentsFilePlacement.MOUNT_ROOT, result.placement());
        assertEquals(content.length, result.sizeBytes());
    }

    @Test
    void getAgentsFile_unknownOrOtherOrganization_throwsNotFound() {
        UUID agentsFileId = UUID.randomUUID();
        when(agentsFileRepository.findByIdAndOrganizationId(agentsFileId, organizationId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> agentsFileQueryService.getAgentsFile(organizationId, agentsFileId));

        assertEquals("KAY_NF_001", exception.getErrorCode());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceVersion_indexRows_deletesFirstAndWritesAgentsFiles() {
        UUID blobId = UUID.randomUUID();
        AgentsFileIndex index = new AgentsFileIndex(AgentsFilePlacement.ROOT, blobId);
        when(agentsFileRepository.saveAll(any(List.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<AgentsFile> result = agentsFileService.replaceVersion(organizationId, versionId, List.of(index));

        InOrder order = inOrder(agentsFileRepository);
        order.verify(agentsFileRepository).deleteByVersion(organizationId, versionId);
        order.verify(agentsFileRepository).saveAll(any(List.class));
        assertEquals(1, result.size());
        assertEquals(organizationId, result.get(0).getOrganizationId());
        assertEquals(versionId, result.get(0).getVersionId());
        assertEquals(blobId, result.get(0).getContentBlobId());
        assertEquals(AgentsFilePlacement.ROOT, result.get(0).getPlacement());
    }

    @Test
    void saveFile_index_deletesTheOldRowFirstAndWritesOne() {
        UUID blobId = UUID.randomUUID();
        when(agentsFileRepository.save(any(AgentsFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentsFile result = agentsFileService.saveFile(organizationId, versionId, new AgentsFileIndex(AgentsFilePlacement.ROOT, blobId));

        InOrder order = inOrder(agentsFileRepository);
        order.verify(agentsFileRepository).deleteByPlacement(organizationId, versionId, AgentsFilePlacement.ROOT);
        order.verify(agentsFileRepository).save(any(AgentsFile.class));
        assertEquals(organizationId, result.getOrganizationId());
        assertEquals(versionId, result.getVersionId());
        assertEquals(blobId, result.getContentBlobId());
    }

    @Test
    void deleteFile_noRowAtPath_returnsFalse() {
        when(agentsFileRepository.deleteByPlacement(organizationId, versionId, AgentsFilePlacement.ROOT)).thenReturn(0);

        assertFalse(agentsFileService.deleteFile(organizationId, versionId, AgentsFilePlacement.ROOT));
    }

    @Test
    void deleteFile_rowAtPath_returnsTrue() {
        when(agentsFileRepository.deleteByPlacement(organizationId, versionId, AgentsFilePlacement.ROOT)).thenReturn(1);

        assertTrue(agentsFileService.deleteFile(organizationId, versionId, AgentsFilePlacement.ROOT));
    }
}

package com.korur.oba.apps.kaya.convention.service.impl;

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
import com.korur.oba.apps.kaya.convention.dto.embedded.ConventionIndex;
import com.korur.oba.apps.kaya.convention.dto.request.ConventionQueryRequest;
import com.korur.oba.apps.kaya.convention.dto.response.ConventionDetailResponse;
import com.korur.oba.apps.kaya.convention.dto.response.ConventionListResponse;
import com.korur.oba.apps.kaya.convention.entity.Convention;
import com.korur.oba.apps.kaya.convention.mapper.ConventionMapper;
import com.korur.oba.apps.kaya.convention.repository.ConventionRepository;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConventionServicesUnitTest {

    @Mock
    private ConventionRepository conventionRepository;
    @Mock
    private BlobQueryService blobQueryService;
    @Mock
    private LoggingService loggingService;

    private ConventionQueryServiceImpl conventionQueryService;
    private ConventionServiceImpl conventionService;
    private UUID organizationId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        conventionQueryService = new ConventionQueryServiceImpl(conventionRepository, blobQueryService,
                Mappers.getMapper(ConventionMapper.class), loggingService);
        conventionService = new ConventionServiceImpl(conventionRepository, loggingService);
        organizationId = UUID.randomUUID();
        versionId = UUID.randomUUID();
    }

    private Convention convention(String relativePath) {
        return Convention.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(UUID.randomUUID())
                .relativePath(relativePath)
                .title("Logging")
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getConventions_conventionsFound_returnsSizesAndNullPathForSkillOnly() {
        Convention delivered = convention("backend/logging.md");
        Convention skillOnly = convention(null);
        ConventionQueryRequest request = new ConventionQueryRequest();
        request.setVersionId(List.of(versionId));
        when(conventionRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(delivered, skillOnly));
        when(blobQueryService.getSizesByIds(organizationId,
                Set.of(delivered.getContentBlobId(), skillOnly.getContentBlobId())))
                .thenReturn(Map.of(delivered.getContentBlobId(), 900, skillOnly.getContentBlobId(), 50));

        List<ConventionListResponse> result = conventionQueryService.getConventions(organizationId, request);

        assertEquals(2, result.size());
        assertEquals("backend/logging.md", result.get(0).path());
        assertEquals(900, result.get(0).sizeBytes());
        assertNull(result.get(1).path());
        assertEquals(50, result.get(1).sizeBytes());
    }

    @Test
    void getConvention_conventionOfOrganization_returnsContentAsUtf8() {
        Convention convention = convention("backend/logging.md");
        byte[] content = "---\ntitle: Logging\n---\n# Logging\n".getBytes(StandardCharsets.UTF_8);
        Blob blob = Blob.builder().id(convention.getContentBlobId()).content(content).size(content.length).build();
        when(conventionRepository.findByIdAndOrganizationId(convention.getId(), organizationId))
                .thenReturn(Optional.of(convention));
        when(blobQueryService.getEntityById(organizationId, convention.getContentBlobId())).thenReturn(blob);

        ConventionDetailResponse result = conventionQueryService.getConvention(organizationId, convention.getId());

        assertEquals("---\ntitle: Logging\n---\n# Logging\n", result.content());
        assertEquals("Logging", result.title());
        assertEquals(content.length, result.sizeBytes());
    }

    @Test
    void getConvention_unknownOrOtherOrganization_throwsNotFound() {
        UUID conventionId = UUID.randomUUID();
        when(conventionRepository.findByIdAndOrganizationId(conventionId, organizationId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> conventionQueryService.getConvention(organizationId, conventionId));

        assertEquals("KAY_NF_001", exception.getErrorCode());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceVersion_indexRows_deletesFirstAndWritesConventions() {
        UUID blobId = UUID.randomUUID();
        ConventionIndex index = new ConventionIndex(null, blobId, "Skill-only", null);
        when(conventionRepository.saveAll(any(List.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Convention> result = conventionService.replaceVersion(organizationId, versionId, List.of(index));

        InOrder order = inOrder(conventionRepository);
        order.verify(conventionRepository).deleteByVersion(organizationId, versionId);
        order.verify(conventionRepository).saveAll(any(List.class));
        assertEquals(1, result.size());
        assertEquals(organizationId, result.get(0).getOrganizationId());
        assertEquals(versionId, result.get(0).getVersionId());
        assertEquals(blobId, result.get(0).getContentBlobId());
        assertNull(result.get(0).getRelativePath());
        assertTrue(result.get(0).getTitle().startsWith("Skill"));
    }

    @Test
    void saveFile_index_deletesTheOldRowFirstAndWritesOne() {
        UUID blobId = UUID.randomUUID();
        when(conventionRepository.save(any(Convention.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Convention result = conventionService.saveFile(organizationId, versionId, new ConventionIndex("backend/logging.md", blobId, "Logging", null));

        InOrder order = inOrder(conventionRepository);
        order.verify(conventionRepository).deleteByPath(organizationId, versionId, "backend/logging.md");
        order.verify(conventionRepository).save(any(Convention.class));
        assertEquals(organizationId, result.getOrganizationId());
        assertEquals(versionId, result.getVersionId());
        assertEquals(blobId, result.getContentBlobId());
    }

    @Test
    void deleteFile_noRowAtPath_returnsFalse() {
        when(conventionRepository.deleteByPath(organizationId, versionId, "backend/logging.md")).thenReturn(0);

        assertFalse(conventionService.deleteFile(organizationId, versionId, "backend/logging.md"));
    }

    @Test
    void deleteFile_rowAtPath_returnsTrue() {
        when(conventionRepository.deleteByPath(organizationId, versionId, "backend/logging.md")).thenReturn(1);

        assertTrue(conventionService.deleteFile(organizationId, versionId, "backend/logging.md"));
    }

    @Test
    void savePart_index_addsOneRowWithoutDeleting() {
        UUID blobId = UUID.randomUUID();
        when(conventionRepository.save(any(Convention.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Convention result = conventionService.savePart(organizationId, versionId, new ConventionIndex("backend/logging.md", blobId, "Logging", null));

        assertEquals(blobId, result.getContentBlobId());
        verify(conventionRepository, never()).deleteByVersion(any(), any());
    }

    @Test
    void deleteParts_noIds_deletesNothing() {
        assertEquals(0, conventionService.deleteParts(organizationId, versionId, List.of()));

        verify(conventionRepository, never()).deleteParts(any(), any(), anyCollection());
    }

    @Test
    void deleteParts_ids_deletesThem() {
        List<UUID> partIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(conventionRepository.deleteParts(organizationId, versionId, partIds)).thenReturn(2);

        assertEquals(2, conventionService.deleteParts(organizationId, versionId, partIds));
    }
}

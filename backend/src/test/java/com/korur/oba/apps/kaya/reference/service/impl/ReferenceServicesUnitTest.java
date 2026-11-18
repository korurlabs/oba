package com.korur.oba.apps.kaya.reference.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2. Third-party
import org.mapstruct.factory.Mappers;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.reference.dto.embedded.ReferenceIndex;
import com.korur.oba.apps.kaya.reference.dto.response.ReferenceDetailResponse;
import com.korur.oba.apps.kaya.reference.entity.Reference;
import com.korur.oba.apps.kaya.reference.mapper.ReferenceMapper;
import com.korur.oba.apps.kaya.reference.repository.ReferenceRepository;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceServicesUnitTest {

    @Mock
    private ReferenceRepository referenceRepository;
    @Mock
    private BlobQueryService blobQueryService;
    @Mock
    private LoggingService loggingService;

    private ReferenceQueryServiceImpl referenceQueryService;
    private ReferenceServiceImpl referenceService;
    private UUID organizationId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        referenceQueryService = new ReferenceQueryServiceImpl(referenceRepository, blobQueryService,
                Mappers.getMapper(ReferenceMapper.class), loggingService);
        referenceService = new ReferenceServiceImpl(referenceRepository, loggingService);
        organizationId = UUID.randomUUID();
        versionId = UUID.randomUUID();
    }

    private Reference reference(String name, String mediaType) {
        return Reference.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(UUID.randomUUID())
                .name(name)
                .mediaType(mediaType)
                .build();
    }

    private void stubFile(Reference reference, byte[] content) {
        Blob blob = Blob.builder().id(reference.getContentBlobId()).content(content).size(content.length).build();
        when(referenceRepository.findByIdAndOrganizationId(reference.getId(), organizationId))
                .thenReturn(Optional.of(reference));
        when(blobQueryService.getEntityById(organizationId, reference.getContentBlobId())).thenReturn(blob);
    }

    @Test
    void getReference_textFile_returnsContentWithoutBase64() {
        Reference reference = reference("api-routes.md", "text/markdown");
        byte[] content = "# API routes\nÜmlaut\n".getBytes(StandardCharsets.UTF_8);
        stubFile(reference, content);

        ReferenceDetailResponse result = referenceQueryService.getReference(organizationId, reference.getId());

        assertEquals("# API routes\nÜmlaut\n", result.content());
        assertNull(result.contentBase64());
        assertEquals("text/markdown", result.mediaType());
        assertEquals(content.length, result.sizeBytes());
    }

    @Test
    void getReference_binaryFile_returnsBase64WithoutContent() {
        Reference reference = reference("logo.png", "image/png");
        byte[] content = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, (byte) 0xFF, 0x00};
        stubFile(reference, content);

        ReferenceDetailResponse result = referenceQueryService.getReference(organizationId, reference.getId());

        assertNull(result.content());
        assertArrayEquals(content, Base64.getDecoder().decode(result.contentBase64()));
        assertEquals(content.length, result.sizeBytes());
    }

    @Test
    void getReference_unknownOrOtherOrganization_throwsNotFound() {
        UUID referenceId = UUID.randomUUID();
        when(referenceRepository.findByIdAndOrganizationId(referenceId, organizationId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> referenceQueryService.getReference(organizationId, referenceId));

        assertEquals("KAY_NF_001", exception.getErrorCode());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceVersion_indexRows_deletesFirstAndWritesInGivenOrder() {
        ReferenceIndex markdown = new ReferenceIndex("api-routes.md", UUID.randomUUID(), "text/markdown");
        ReferenceIndex image = new ReferenceIndex("logo.png", UUID.randomUUID(), "image/png");
        when(referenceRepository.saveAll(any(List.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Reference> result = referenceService.replaceVersion(organizationId, versionId, List.of(markdown, image));

        InOrder order = inOrder(referenceRepository);
        order.verify(referenceRepository).deleteByVersion(organizationId, versionId);
        order.verify(referenceRepository).saveAll(any(List.class));
        assertEquals(2, result.size());
        assertEquals("api-routes.md", result.get(0).getName());
        assertEquals("image/png", result.get(1).getMediaType());
        assertEquals(organizationId, result.get(1).getOrganizationId());
        assertEquals(versionId, result.get(1).getVersionId());
        assertEquals(image.contentBlobId(), result.get(1).getContentBlobId());
    }

    @Test
    void savePart_index_addsOneRowWithoutDeleting() {
        UUID blobId = UUID.randomUUID();
        when(referenceRepository.save(any(Reference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reference result = referenceService.savePart(organizationId, versionId, new ReferenceIndex("guide.md", blobId, "text/markdown"));

        assertEquals(blobId, result.getContentBlobId());
        verify(referenceRepository, never()).deleteByVersion(any(), any());
    }

    @Test
    void deleteParts_noIds_deletesNothing() {
        assertEquals(0, referenceService.deleteParts(organizationId, versionId, List.of()));

        verify(referenceRepository, never()).deleteParts(any(), any(), anyCollection());
    }

    @Test
    void deleteParts_ids_deletesThem() {
        List<UUID> partIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(referenceRepository.deleteParts(organizationId, versionId, partIds)).thenReturn(2);

        assertEquals(2, referenceService.deleteParts(organizationId, versionId, partIds));
    }
}

package com.korur.oba.apps.kaya.blob.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.repository.BlobRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlobQueryServiceImplUnitTest {

    @Mock
    private BlobRepository blobRepository;
    @Mock
    private LoggingService loggingService;

    private BlobQueryServiceImpl blobQueryService;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        blobQueryService = new BlobQueryServiceImpl(blobRepository, loggingService);
        organizationId = UUID.randomUUID();
    }

    private static BlobRepository.BlobSize size(UUID id, int size) {
        return new BlobRepository.BlobSize() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public Integer getSize() {
                return size;
            }
        };
    }

    @Test
    void getEntityById_blobOfOrganization_returnsBlob() {
        UUID blobId = UUID.randomUUID();
        Blob blob = Blob.builder().id(blobId).organizationId(organizationId).build();
        when(blobRepository.findByIdAndOrganizationId(blobId, organizationId)).thenReturn(Optional.of(blob));

        Blob result = blobQueryService.getEntityById(organizationId, blobId);

        assertSame(blob, result);
    }

    @Test
    void getEntityById_unknownOrOtherOrganization_throwsNotFound() {
        UUID blobId = UUID.randomUUID();
        when(blobRepository.findByIdAndOrganizationId(blobId, organizationId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> blobQueryService.getEntityById(organizationId, blobId));

        assertEquals("KAY_NF_001", exception.getErrorCode());
    }

    @Test
    void getEntitiesByIds_noIds_returnsEmptyWithoutQuery() {
        Map<UUID, Blob> result = blobQueryService.getEntitiesByIds(organizationId, Set.of());

        assertTrue(result.isEmpty());
        verifyNoInteractions(blobRepository);
    }

    @Test
    void getEntitiesByIds_storedBlobs_returnsBlobById() {
        Blob first = Blob.builder().id(UUID.randomUUID()).organizationId(organizationId).build();
        Blob second = Blob.builder().id(UUID.randomUUID()).organizationId(organizationId).build();
        UUID missing = UUID.randomUUID();
        Set<UUID> ids = Set.of(first.getId(), second.getId(), missing);
        when(blobRepository.findByOrganizationIdAndIdIn(organizationId, ids)).thenReturn(List.of(first, second));

        Map<UUID, Blob> result = blobQueryService.getEntitiesByIds(organizationId, ids);

        assertEquals(Map.of(first.getId(), first, second.getId(), second), result);
    }

    @Test
    void getSizesByIds_noIds_returnsEmptyWithoutQuery() {
        Map<UUID, Integer> result = blobQueryService.getSizesByIds(organizationId, Set.of());

        assertTrue(result.isEmpty());
        verify(blobRepository, never()).findSizes(any(), anyCollection());
    }

    @Test
    void getSizesByIds_storedBlobs_returnsSizeById() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        Set<UUID> ids = Set.of(first, second, missing);
        when(blobRepository.findSizes(organizationId, ids)).thenReturn(List.of(size(first, 10), size(second, 20)));

        Map<UUID, Integer> result = blobQueryService.getSizesByIds(organizationId, ids);

        assertEquals(Map.of(first, 10, second, 20), result);
    }
}

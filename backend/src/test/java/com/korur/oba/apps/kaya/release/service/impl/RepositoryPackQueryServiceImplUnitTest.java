package com.korur.oba.apps.kaya.release.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.mapper.PackMapper;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.release.mapper.ReleaseMapper;
import com.korur.oba.apps.kaya.release.repository.ReleaseRepository;
import com.korur.oba.apps.kaya.release.repository.RepositoryPackRepository;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryPackQueryServiceImplUnitTest {

    @Mock
    private RepositoryPackRepository repositoryPackRepository;
    @Mock
    private ReleaseRepository releaseRepository;
    @Mock
    private PackQueryService packQueryService;
    @Mock
    private ReleaseQueryService releaseQueryService;
    @Mock
    private RepositoryQueryService repositoryQueryService;
    @Mock
    private ReleaseMapper releaseMapper;
    @Mock
    private PackMapper packMapper;
    @Mock
    private LoggingService loggingService;

    private RepositoryPackQueryServiceImpl service;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        service = new RepositoryPackQueryServiceImpl(repositoryPackRepository, releaseRepository, packQueryService,
                releaseQueryService, repositoryQueryService, releaseMapper, packMapper, loggingService);
        organizationId = UUID.randomUUID();
    }

    @Test
    void getRepositoryIdsOfPack_listedPack_returnsListingRepositories() {
        Pack pack = Pack.builder().id(UUID.randomUUID()).organizationId(organizationId).name("listed").build();
        List<UUID> listing = List.of(UUID.randomUUID());
        when(packQueryService.getEntityById(organizationId, pack.getId())).thenReturn(pack);
        when(repositoryPackRepository.findRepositoryIdsByPackId(pack.getId())).thenReturn(listing);

        List<UUID> result = service.getRepositoryIdsOfPack(organizationId, pack.getId());

        assertEquals(listing, result);
        verify(releaseQueryService, never()).getActiveRepositoryIds(organizationId);
    }

    @Test
    void getRepositoryIdsOfPack_requiredPack_returnsEveryActiveRepository() {
        Pack pack = Pack.builder().id(UUID.randomUUID()).organizationId(organizationId).name("required")
                .required(true).build();
        List<UUID> active = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(packQueryService.getEntityById(organizationId, pack.getId())).thenReturn(pack);
        when(releaseQueryService.getActiveRepositoryIds(organizationId)).thenReturn(active);

        List<UUID> result = service.getRepositoryIdsOfPack(organizationId, pack.getId());

        assertEquals(active, result);
        verify(repositoryPackRepository, never()).findRepositoryIdsByPackId(pack.getId());
    }
}

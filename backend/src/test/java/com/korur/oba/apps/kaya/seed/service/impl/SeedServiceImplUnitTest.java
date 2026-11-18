package com.korur.oba.apps.kaya.seed.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.pack.service.PackService;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.release.service.ReleaseService;
import com.korur.oba.apps.kaya.release.service.RepositoryPackQueryService;
import com.korur.oba.apps.kaya.release.service.RepositoryPackService;
import com.korur.oba.apps.kaya.render.dto.embedded.FileRefusal;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.EntityKind;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.service.RenderQueryService;
import com.korur.oba.apps.kaya.repository.service.RepositoryService;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedRefusedFile;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedReport;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedSetReport;
import com.korur.oba.apps.kaya.seed.dto.request.SeedFileRequest;
import com.korur.oba.apps.kaya.seed.dto.request.SeedRequest;
import com.korur.oba.apps.kaya.seed.dto.request.SeedSetRequest;
import com.korur.oba.apps.kaya.seed.dto.response.SeedRunResponse;
import com.korur.oba.apps.kaya.seed.entity.SeedRun;
import com.korur.oba.apps.kaya.seed.enums.SeedRunStatus;
import com.korur.oba.apps.kaya.seed.enums.SeedSetKind;
import com.korur.oba.apps.kaya.seed.enums.SeedSetOutcome;
import com.korur.oba.apps.kaya.seed.helper.SeedRunRecorder;
import com.korur.oba.apps.kaya.seed.mapper.SeedMapperImpl;
import com.korur.oba.apps.kaya.seed.repository.SeedRunRepository;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.service.VersionService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeedServiceImplUnitTest {

    private static final String SOURCE_REF = "main@4f2a9c1";
    private static final String PACK_NAME = "shared";
    private static final String RULE_PATH = ".agents/rules/backend.md";
    private static final String STRAY_PATH = "notes/todo.md";
    private static final String RULE_SHA = "a".repeat(64);
    private static final String STRAY_SHA = "b".repeat(64);
    private static final String NOT_DELIVERABLE_CODE = "KAY_CFG_028";

    @Mock
    private SeedRunRepository seedRunRepository;
    @Mock
    private SeedRunRecorder seedRunRecorder;
    @Mock
    private BlobQueryService blobQueryService;
    @Mock
    private PackQueryService packQueryService;
    @Mock
    private PackService packService;
    @Mock
    private RepositoryPackQueryService repositoryPackQueryService;
    @Mock
    private RepositoryPackService repositoryPackService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private RenderQueryService renderQueryService;
    @Mock
    private ValidationQueryService validationQueryService;
    @Mock
    private VersionService versionService;
    @Mock
    private ReleaseQueryService releaseQueryService;
    @Mock
    private ReleaseService releaseService;
    @Mock
    private LoggingService loggingService;

    private SeedServiceImpl service;
    private UUID organizationId;
    private User owner;
    private Pack pack;

    @BeforeEach
    void setUp() {
        service = new SeedServiceImpl(seedRunRepository, seedRunRecorder, new SeedMapperImpl(), blobQueryService,
                packQueryService, packService, repositoryPackQueryService, repositoryPackService, repositoryService, renderQueryService, validationQueryService,
                versionService, releaseQueryService, releaseService, loggingService);
        organizationId = UUID.randomUUID();
        owner = User.builder().id(UUID.randomUUID()).build();
        pack = Pack.builder().id(UUID.randomUUID()).organizationId(organizationId).name(PACK_NAME)
                .currentVersionId(UUID.randomUUID()).build();

        when(blobQueryService.sumSizes(eq(organizationId), any())).thenReturn(20L);
        when(blobQueryService.getContents(eq(organizationId), any())).thenReturn(Map.of(
                RULE_SHA, "# Backend\n".getBytes(StandardCharsets.UTF_8),
                STRAY_SHA, "todo\n".getBytes(StandardCharsets.UTF_8)));
        when(validationQueryService.validateFile(any())).thenReturn(List.of());
        when(validationQueryService.validateLinks(anyList())).thenReturn(List.of());
        when(packQueryService.findByName(organizationId, PACK_NAME)).thenReturn(Optional.of(pack));
        when(repositoryPackQueryService.getRepositoryIdsOfPack(organizationId, pack.getId())).thenReturn(List.of());
        when(seedRunRepository.save(any(SeedRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static SeedRequest packRequest() {
        SeedSetRequest set = new SeedSetRequest(SeedSetKind.PACK, PACK_NAME, null, List.of(
                new SeedFileRequest(RULE_PATH, RULE_SHA, FileMode.FILE, null),
                new SeedFileRequest(STRAY_PATH, STRAY_SHA, FileMode.FILE, null)));
        return new SeedRequest(SOURCE_REF, List.of(set), null, null, null);
    }

    private void strayPathNotDeliverable() {
        when(renderQueryService.screen(anyList())).thenReturn(List.of(
                new FileRefusal(STRAY_PATH, NOT_DELIVERABLE_CODE, "No file kind accepts this path")));
        when(renderQueryService.countEntities(anyList())).thenReturn(Map.of(EntityKind.RULE, 1));
    }

    @SuppressWarnings("unchecked")
    private List<SourceFile> seededFiles() {
        ArgumentCaptor<List<SourceFile>> files = ArgumentCaptor.forClass(List.class);
        verify(versionService).seed(eq(owner.getId()), eq(organizationId), eq(pack.getId()), files.capture(),
                anyString());
        return files.getValue();
    }

    @Nested
    class Seed {

        @Test
        void fileNoKindAccepts_reportsItAsRefusedAndSeedsTheRest() {
            strayPathNotDeliverable();
            Version version = Version.builder().id(UUID.randomUUID()).organizationId(organizationId)
                    .packId(pack.getId()).number(2).baseVersion(Version.builder().id(pack.getCurrentVersionId()).build())
                    .build();
            when(versionService.seed(any(), any(), any(), anyList(), anyString()))
                    .thenReturn(Optional.of(version));

            SeedRunResponse response = service.seed(owner, organizationId, packRequest());

            assertEquals(List.of(RULE_PATH), seededFiles().stream().map(SourceFile::path).toList());
            SeedReport report = response.report();
            assertEquals(SeedRunStatus.PASSED, response.status());
            assertEquals(1, report.refused().size());
            SeedRefusedFile refused = report.refused().get(0);
            assertEquals(STRAY_PATH, refused.path());
            assertEquals(NOT_DELIVERABLE_CODE, refused.errorCode());
            assertEquals(Map.of(EntityKind.RULE, 1), report.entities());
            SeedSetReport set = report.sets().get(0);
            assertEquals(SeedSetOutcome.UPDATED, set.outcome());
            assertEquals(version.getId(), set.versionId());
        }

        @Test
        void inputUnchangedSinceTheLastRun_createsNoVersionAndNoRelease() {
            strayPathNotDeliverable();
            when(versionService.seed(any(), any(), any(), anyList(), anyString())).thenReturn(Optional.empty());

            SeedRunResponse response = service.seed(owner, organizationId, packRequest());

            SeedSetReport set = response.report().sets().get(0);
            assertEquals(SeedSetOutcome.UNCHANGED, set.outcome());
            assertNull(set.versionId());
            assertEquals(List.of(), response.report().releases());
            verify(packService, never()).createPack(any(), any(), any());
            verify(releaseService, never()).releaseRepository(any(), any(), any());
            verify(releaseQueryService, never()).previewComposition(any(), any(), anyMap());
        }
    }
}

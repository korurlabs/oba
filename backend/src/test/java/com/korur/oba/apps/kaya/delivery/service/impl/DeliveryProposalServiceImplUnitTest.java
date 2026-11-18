package com.korur.oba.apps.kaya.delivery.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.delivery.DeliveryProposalProperties;
import com.korur.oba.apps.kaya.delivery.dto.request.DeliveryProposalRequest;
import com.korur.oba.apps.kaya.delivery.mapper.DeliveryMapper;
import com.korur.oba.apps.kaya.delivery.service.DeliveryQueryService;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.release.dto.embedded.PackVersionEntry;
import com.korur.oba.apps.kaya.release.dto.embedded.ReleaseEntry;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.render.enums.FileEncoding;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.version.dto.request.ProposalFileRequest;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;
import com.korur.oba.apps.kaya.version.service.VersionService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryProposalServiceImplUnitTest {

    private static final String REMOTE_URL = "git@github.com:acme/app.git";
    private static final String REASON = "Allow the board CLI";
    private static final int RELEASE_NUMBER = 4;

    @Mock
    private DeliveryQueryService deliveryQueryService;
    @Mock
    private ReleaseQueryService releaseQueryService;
    @Mock
    private PackQueryService packQueryService;
    @Mock
    private VersionService versionService;

    @Mock
    private VersionQueryService versionQueryService;

    @Mock
    private ValidationQueryService validationQueryService;
    @Mock
    private DeliveryMapper deliveryMapper;
    @Mock
    private LoggingService loggingService;

    private DeliveryProposalServiceImpl service;
    private UUID organizationId;
    private UUID userId;
    private UUID repositoryId;
    private MachinePrincipal principal;

    @BeforeEach
    void setUp() {
        service = new DeliveryProposalServiceImpl(deliveryQueryService, releaseQueryService, packQueryService,
                versionService, versionQueryService, validationQueryService, deliveryMapper,
                new DeliveryProposalProperties("https://kaya.test/"), loggingService);
        organizationId = UUID.randomUUID();
        userId = UUID.randomUUID();
        repositoryId = UUID.randomUUID();
        principal = new MachinePrincipal(organizationId, userId, UUID.randomUUID(), UUID.randomUUID(), "laptop");

        when(deliveryQueryService.getRepository(organizationId, REMOTE_URL)).thenReturn(new RepositoryResponse(
                repositoryId, "app", REMOTE_URL, null, null, null, null));
    }

    private void releaseDelivering(ReleaseEntry... entries) {
        Release release = Release.builder().id(UUID.randomUUID()).repositoryId(repositoryId).number(RELEASE_NUMBER)
                .manifest(List.of(entries)).build();
        when(releaseQueryService.getEntityByNumber(organizationId, repositoryId, RELEASE_NUMBER)).thenReturn(release);
    }

    /** A release that delivered nothing yet and composes the packs at the repository root. */
    private void releaseComposing(UUID... packIds) {
        Release release = Release.builder().id(UUID.randomUUID()).repositoryId(repositoryId).number(RELEASE_NUMBER)
                .manifest(List.of())
                .packVersions(List.of(packIds).stream().map(packId -> new PackVersionEntry(packId, UUID.randomUUID(), null)).toList())
                .build();
        when(releaseQueryService.getEntityByNumber(organizationId, repositoryId, RELEASE_NUMBER)).thenReturn(release);
    }

    private static ReleaseEntry entry(String path, UUID ownerId) {
        return new ReleaseEntry(path, "a".repeat(64), FileMode.FILE, null, 10, true, ownerId, UUID.randomUUID(), null,
                null, null);
    }

    private static ProposalFileRequest file(String path) {
        return new ProposalFileRequest(path, FileMode.FILE, FileEncoding.UTF_8, "# Rule\n", null, null);
    }

    private static DeliveryProposalRequest request(String... paths) {
        return new DeliveryProposalRequest(REMOTE_URL, RELEASE_NUMBER, REASON,
                List.of(paths).stream().map(DeliveryProposalServiceImplUnitTest::file).toList());
    }

    private VersionResponse version() {
        VersionResponse version = mock(VersionResponse.class);
        when(version.id()).thenReturn(UUID.randomUUID());
        return version;
    }

    @Nested
    class Propose {

        @Test
        void deliveredPathOfAPack_proposesToThePack() {
            UUID packId = UUID.randomUUID();
            releaseDelivering(entry(".agents/rules/backend.md", packId));
            when(packQueryService.getEntityById(organizationId, packId)).thenReturn(
                    Pack.builder().id(packId).organizationId(organizationId).name("shared").build());
            VersionResponse version = version();
            when(versionService.propose(eq(userId), eq(organizationId), eq(packId), anyMap(), anyList(),
                    eq(REASON))).thenReturn(version);

            String reviewUrl = "https://kaya.test/changes/" + version.id();

            service.propose(principal, request(".agents/rules/backend.md"));

            verify(deliveryMapper).toProposalResponse(eq(version), eq("shared"), eq(RELEASE_NUMBER), eq(reviewUrl));
        }

        @Test
        void newPathWithTwoRootPacksOfTheSameDepth_throwsWithBothAsCandidates() {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            releaseComposing(first, second);
            when(packQueryService.getChain(eq(organizationId), any())).thenReturn(List.of(Pack.builder().build()));

            ValidationException exception = assertThrows(ValidationException.class,
                    () -> service.propose(principal, request(".agents/rules/new.md")));

            assertEquals("KAY_CFG_019", exception.getErrorCode());
            assertEquals(List.of(first.toString(), second.toString()), exception.getErrorValues().get("candidates"));
            verify(versionService, never()).propose(any(), any(), any(), anyMap(), anyList(), any());
        }

        @Test
        void newPath_proposesToTheDeepestRootPack() {
            UUID parent = UUID.randomUUID();
            UUID child = UUID.randomUUID();
            releaseComposing(parent, child);
            when(packQueryService.getChain(organizationId, parent)).thenReturn(List.of(Pack.builder().id(parent).build()));
            when(packQueryService.getChain(organizationId, child)).thenReturn(List.of(Pack.builder().id(parent).build(),
                    Pack.builder().id(child).build()));
            when(packQueryService.getEntityById(organizationId, child)).thenReturn(
                    Pack.builder().id(child).organizationId(organizationId).name("child").build());
            VersionResponse version = version();
            when(versionService.propose(eq(userId), eq(organizationId), eq(child), anyMap(), anyList(),
                    eq(REASON))).thenReturn(version);

            service.propose(principal, request(".agents/rules/new.md"));

            verify(versionService).propose(eq(userId), eq(organizationId), eq(child), anyMap(), anyList(), eq(REASON));
        }

        @Test
        void pathsOfTwoOwners_throwsProposalSpansSets() {
            releaseDelivering(entry(".agents/rules/a.md", UUID.randomUUID()), entry(".agents/rules/b.md",
                    UUID.randomUUID()));

            ValidationException exception = assertThrows(ValidationException.class,
                    () -> service.propose(principal, request(".agents/rules/a.md", ".agents/rules/b.md")));

            assertEquals("KAY_CFG_019", exception.getErrorCode());
            verify(versionService, never()).propose(any(), any(), any(), anyMap(), anyList(), any());
        }
    }
}

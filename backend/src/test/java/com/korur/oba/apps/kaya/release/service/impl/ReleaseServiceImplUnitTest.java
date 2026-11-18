package com.korur.oba.apps.kaya.release.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.blob.service.BlobService;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.helper.Composer;
import com.korur.oba.apps.kaya.release.helper.Composition;
import com.korur.oba.apps.kaya.release.mapper.ReleaseMapper;
import com.korur.oba.apps.kaya.release.repository.ReleaseRepository;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.apps.kaya.signing.dto.embedded.Signature;
import com.korur.oba.apps.kaya.signing.dto.embedded.SignedManifest;
import com.korur.oba.apps.kaya.signing.service.SigningService;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.enums.ViolationRule;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.version.entity.Version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReleaseServiceImplUnitTest {

    private static final Instant NOW = Instant.parse("2026-10-20T09:00:00.123456Z");

    @Mock
    private ReleaseRepository releaseRepository;
    @Mock
    private Composer composer;
    @Mock
    private ReleaseQueryService releaseQueryService;
    @Mock
    private ValidationQueryService validationQueryService;
    @Mock
    private RepositoryQueryService repositoryQueryService;
    @Mock
    private BlobService blobService;
    @Mock
    private BlobQueryService blobQueryService;
    @Mock
    private SigningService signingService;
    @Mock
    private ReleaseMapper releaseMapper;
    @Mock
    private LoggingService loggingService;

    private ReleaseServiceImpl service;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        service = new ReleaseServiceImpl(releaseRepository, composer, releaseQueryService, validationQueryService,
                repositoryQueryService, blobService, blobQueryService, signingService, releaseMapper, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC), loggingService);
        organizationId = UUID.randomUUID();
        lenient().doThrow(new ValidationException(ExceptionValidationError.VALIDATION))
                .when(validationQueryService).requireValid(argThat(violations -> violations != null && !violations.isEmpty()));
    }

    private Composition composition(UUID repositoryId, List<Version> versions, Map<String, byte[]> contents) {
        return new Composition(repositoryId, List.of(), List.of(), versions, null, List.of(), List.of(), List.of(), contents);
    }

    private Version version() {
        return Version.builder().id(UUID.randomUUID()).organizationId(organizationId).packId(UUID.randomUUID()).build();
    }

    private void stubSigning(UUID repositoryId) {
        when(releaseRepository.findMaxNumber(repositoryId)).thenReturn(4);
        when(repositoryQueryService.getEntityById(organizationId, repositoryId))
                .thenReturn(Repository.builder().id(repositoryId).remoteUrl("git@example:village.git").build());
        // Shared by every repository a test releases, so a second call only repeats the stubs.
        lenient().when(releaseMapper.toEntries(any())).thenReturn(List.of());
        lenient().when(signingService.sign(nullable(SignedManifest.class))).thenReturn(new Signature("{}", "abc", "sig", "key-1"));
        lenient().when(blobQueryService.getStoredSha256s(eq(organizationId), any())).thenReturn(Set.of());
    }

    @Nested
    class ReleaseOfRepository {

        @Test
        void composedVersions_insertsTheNextNumberSignedAtTheMillisecond() {
            UUID repositoryId = UUID.randomUUID();
            UUID causeVersionId = UUID.randomUUID();
            when(composer.compose(organizationId, repositoryId, Map.of()))
                    .thenReturn(composition(repositoryId, List.of(version()), Map.of()));
            stubSigning(repositoryId);
            Release release = Release.builder().id(UUID.randomUUID()).repositoryId(repositoryId).number(5).build();
            when(releaseRepository.findById(any())).thenReturn(Optional.of(release));

            Optional<Release> result = service.releaseRepository(organizationId, repositoryId, causeVersionId);

            assertSame(release, result.orElseThrow());
            InOrder order = inOrder(releaseRepository, composer);
            order.verify(releaseRepository).lockReleases(repositoryId.toString());
            order.verify(composer).compose(organizationId, repositoryId, Map.of());
            verify(releaseRepository).insertSigned(any(), eq(repositoryId), eq(5), eq(causeVersionId), eq("[]"), eq("[]"),
                    eq("abc"), eq("sig"), eq("key-1"), eq(Instant.parse("2026-10-20T09:00:00.123Z")));
        }

        @Test
        void noComposedVersion_releasesNothing() {
            UUID repositoryId = UUID.randomUUID();
            when(composer.compose(organizationId, repositoryId, Map.of()))
                    .thenReturn(composition(repositoryId, List.of(), Map.of()));

            Optional<Release> result = service.releaseRepository(organizationId, repositoryId, UUID.randomUUID());

            assertTrue(result.isEmpty());
            verify(releaseRepository, never()).insertSigned(any(), any(), anyInt(), any(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), any());
        }

        @Test
        void contentNotYetStored_storesItAsABlob() {
            UUID repositoryId = UUID.randomUUID();
            byte[] stored = "stored".getBytes(StandardCharsets.UTF_8);
            byte[] narrowed = "narrowed".getBytes(StandardCharsets.UTF_8);
            when(composer.compose(organizationId, repositoryId, Map.of()))
                    .thenReturn(composition(repositoryId, List.of(version()), Map.of("a", stored, "b", narrowed)));
            stubSigning(repositoryId);
            when(blobQueryService.getStoredSha256s(eq(organizationId), any())).thenReturn(Set.of("a"));
            when(releaseRepository.findById(any())).thenReturn(Optional.of(Release.builder().build()));

            service.releaseRepository(organizationId, repositoryId, null);

            verify(blobService).storeContent(organizationId, narrowed);
            verify(blobService, never()).storeContent(organizationId, stored);
        }
    }

    @Nested
    class ReleaseVersion {

        @Test
        void twoComposingRepositories_locksInIdOrderThenReleasesEach() {
            Version approved = version();
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            when(releaseQueryService.getComposingRepositoryIds(organizationId, approved)).thenReturn(List.of(first, second));
            when(composer.compose(organizationId, first, Map.of())).thenReturn(composition(first, List.of(approved), Map.of()));
            when(composer.compose(organizationId, second, Map.of())).thenReturn(composition(second, List.of(approved), Map.of()));
            when(validationQueryService.validateComposition(any())).thenReturn(List.of());
            stubSigning(first);
            stubSigning(second);
            when(releaseRepository.findById(any())).thenReturn(Optional.of(Release.builder().build()));

            List<Release> releases = service.releaseVersion(organizationId, approved);

            assertEquals(2, releases.size());
            InOrder order = inOrder(releaseRepository);
            order.verify(releaseRepository).lockReleases(first.toString());
            order.verify(releaseRepository).lockReleases(second.toString());
            order.verify(releaseRepository, times(2)).insertSigned(any(), any(), anyInt(),
                    eq(approved.getId()), anyString(), anyString(), anyString(), anyString(), anyString(), any());
        }

        @Test
        void versionWithBase_validatesTheCurrentCompositionAlone() {
            Version base = version();
            Version approved = Version.builder().id(UUID.randomUUID()).packId(base.getPackId()).baseVersion(base).build();
            UUID repositoryId = UUID.randomUUID();
            Composition after = composition(repositoryId, List.of(approved), Map.of());
            when(releaseQueryService.getComposingRepositoryIds(organizationId, approved)).thenReturn(List.of(repositoryId));
            when(composer.compose(organizationId, repositoryId, Map.of())).thenReturn(after);
            when(validationQueryService.validateComposition(after.toCandidate())).thenReturn(List.of());
            stubSigning(repositoryId);
            when(releaseRepository.findById(any())).thenReturn(Optional.of(Release.builder().build()));

            List<Release> releases = service.releaseVersion(organizationId, approved);

            assertEquals(1, releases.size());
            verify(validationQueryService).validateComposition(after.toCandidate());
            verify(composer, never()).compose(organizationId, repositoryId, Map.of(base.getPackId(), base.getId()));
        }

        @Test
        void violationInOneRepository_releasesNone() {
            Version approved = version();
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            when(releaseQueryService.getComposingRepositoryIds(organizationId, approved)).thenReturn(List.of(first, second));
            Composition broken = composition(second, List.of(approved), Map.of());
            when(composer.compose(organizationId, first, Map.of())).thenReturn(composition(first, List.of(approved), Map.of()));
            when(composer.compose(organizationId, second, Map.of())).thenReturn(broken);
            when(validationQueryService.validateComposition(any())).thenReturn(List.of())
                    .thenReturn(List.of(Violation.ofRepository(second, "AGENTS.md", ViolationRule.SYMLINK_DANGLING, "dangling")));

            assertThrows(ValidationException.class, () -> service.releaseVersion(organizationId, approved));

            verify(releaseRepository, never()).insertSigned(any(), any(), anyInt(), any(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), any());
        }
    }
}

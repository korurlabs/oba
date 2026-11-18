package com.korur.oba.apps.kaya.release.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionInternalError;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.blob.service.BlobService;
import com.korur.oba.apps.kaya.release.constant.service.ReleaseServiceLogConstant;
import com.korur.oba.apps.kaya.release.dto.embedded.ReleaseEntry;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.helper.Composer;
import com.korur.oba.apps.kaya.release.helper.Composition;
import com.korur.oba.apps.kaya.release.mapper.ReleaseMapper;
import com.korur.oba.apps.kaya.release.repository.ReleaseRepository;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.release.service.ReleaseService;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.apps.kaya.signing.dto.embedded.Signature;
import com.korur.oba.apps.kaya.signing.dto.embedded.SignedManifest;
import com.korur.oba.apps.kaya.signing.helper.SignedManifestWriter;
import com.korur.oba.apps.kaya.signing.service.SigningService;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.version.entity.Version;

/**
 * Implements {@link ReleaseService}: composes a repository with
 * {@link com.korur.oba.apps.kaya.release.helper.Composer}, stores the content no blob holds yet,
 * signs the manifest and numbers the release after the latest one, under the repository's release
 * lock so two releases of one repository never take the same number.
 */
@Service
@RequiredArgsConstructor
public class ReleaseServiceImpl implements ReleaseService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ReleaseServiceImpl.class);

    private final ReleaseRepository releaseRepository;
    private final Composer composer;
    private final ReleaseQueryService releaseQueryService;
    private final ValidationQueryService validationQueryService;
    private final RepositoryQueryService repositoryQueryService;
    private final BlobService blobService;
    private final BlobQueryService blobQueryService;
    private final SigningService signingService;
    private final ReleaseMapper releaseMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public List<Release> releaseVersion(UUID organizationId, Version approved) {
        List<UUID> repositoryIds = releaseQueryService.getComposingRepositoryIds(organizationId, approved);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.RELEASING_VERSION,
                approved.getId(), organizationId, repositoryIds.size());
        // Locked in repository id order, so two approvals that reach the same repositories cannot
        // wait on each other's locks. Each composition below is read under its lock, so an approval
        // of another owner of the same repository that committed first is composed as well.
        repositoryIds.forEach(this::lockReleases);

        List<Composition> compositions = new ArrayList<>();
        List<Violation> violations = new ArrayList<>();
        for (UUID repositoryId : repositoryIds) {
            Composition composition = composer.compose(organizationId, repositoryId, Map.of());
            violations.addAll(validationQueryService.validateComposition(composition.toCandidate()));
            compositions.add(composition);
        }
        validationQueryService.requireValid(violations);

        List<Release> releases = new ArrayList<>();
        for (Composition composition : compositions) {
            release(organizationId, composition, approved.getId()).ifPresent(releases::add);
        }
        return releases;
    }

    @Override
    @Transactional
    public Optional<Release> releaseRepository(UUID organizationId, UUID repositoryId, UUID causeVersionId) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.RELEASING_REPOSITORY,
                repositoryId, organizationId, causeVersionId);
        lockReleases(repositoryId);
        return release(organizationId, composer.compose(organizationId, repositoryId, Map.of()), causeVersionId);
    }

    @Override
    @Transactional
    public void lockReleases(UUID repositoryId) {
        releaseRepository.lockReleases(repositoryId.toString());
    }

    @Override
    @Transactional
    public void lockOwner(UUID ownerId) {
        releaseRepository.lockOwner(ownerId.toString());
    }

    /**
     * Signs and stores a release of a composition read under the repository's release lock. The
     * creation time is part of the signed document, so the row is inserted with that exact time.
     */
    private Optional<Release> release(UUID organizationId, Composition composition, UUID causeVersionId) {
        UUID repositoryId = composition.repositoryId();
        if (composition.versions().isEmpty()) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.RELEASE_SKIPPED,
                    repositoryId, organizationId);
            return Optional.empty();
        }
        int number = releaseRepository.findMaxNumber(repositoryId) + 1;
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        String remoteUrl = repositoryQueryService.getEntityById(organizationId, repositoryId).getRemoteUrl();
        List<ReleaseEntry> entries = releaseMapper.toEntries(composition.files());
        SignedManifest manifest = releaseMapper.toSignedManifest(organizationId.toString(), remoteUrl, number,
                SignedManifestWriter.formatCreatedAt(createdAt), entries);
        Signature signature = signingService.sign(manifest);
        storeContents(organizationId, composition);

        UUID id = UUID.randomUUID();
        releaseRepository.insertSigned(id, repositoryId, number, causeVersionId, writeJson(repositoryId, number, entries),
                writeJson(repositoryId, number, composition.packVersions()), signature.manifestSha256(), signature.signature(),
                signature.keyId(), createdAt);
        Release release = releaseRepository.findById(id).orElseThrow(() -> {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.RELEASE_INSERT_LOST, number, repositoryId);
            return new InternalServerErrorException(ExceptionInternalError.UNEXPECTED);
        });
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.RELEASE_CREATED,
                number, repositoryId, organizationId, entries.size(), signature.keyId());
        return Optional.of(release);
    }

    /**
     * Every delivered byte must be a blob before the release that names it, since the CLI downloads
     * each file by its sha256. Version files already are; a narrowed rule copy exists in no
     * version, so it is stored here, in the same transaction. Blobs are addressed by their hash, so
     * a copy an earlier release stored is kept.
     */
    private void storeContents(UUID organizationId, Composition composition) {
        Set<String> stored = blobQueryService.getStoredSha256s(organizationId, composition.contents().keySet());
        int added = 0;
        for (Map.Entry<String, byte[]> content : composition.contents().entrySet()) {
            if (!stored.contains(content.getKey())) {
                blobService.storeContent(organizationId, content.getValue());
                added++;
            }
        }
        if (added > 0) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.BLOBS_STORED,
                    added, composition.repositoryId(), organizationId);
        }
    }

    private String writeJson(UUID repositoryId, int number, List<?> rows) {
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.RELEASE_MANIFEST_WRITE_FAILED, e,
                    number, repositoryId);
            throw new InternalServerErrorException(ExceptionInternalError.JSON_SERIALIZATION);
        }
    }
}

package com.korur.oba.apps.kaya.delivery.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionApiError;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.BadRequestException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.delivery.constant.DeliveryConstant;
import com.korur.oba.apps.kaya.delivery.constant.service.DeliveryServiceLogConstant;
import com.korur.oba.apps.kaya.delivery.dto.derived.DeliveryBlob;
import com.korur.oba.apps.kaya.delivery.dto.derived.DeliveryCurrent;
import com.korur.oba.apps.kaya.delivery.dto.response.DeliveryCurrentResponse;
import com.korur.oba.apps.kaya.delivery.dto.response.DeliveryReleaseResponse;
import com.korur.oba.apps.kaya.delivery.dto.response.DeliveryRepositoriesResponse;
import com.korur.oba.apps.kaya.delivery.dto.response.embedded.DeliveryRepositoryResponse;
import com.korur.oba.apps.kaya.delivery.helper.DeliveryEtagHelper;
import com.korur.oba.apps.kaya.delivery.mapper.DeliveryMapper;
import com.korur.oba.apps.kaya.delivery.service.DeliveryQueryService;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;
import com.korur.oba.apps.kaya.repository.helper.RemoteUrlHelper;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;

/**
 * Implements {@link DeliveryQueryService}: resolves the token's organization and the checkout's
 * repository first, then serves releases and blobs of that organization only.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryQueryServiceImpl implements DeliveryQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DeliveryQueryServiceImpl.class);

    private final RepositoryQueryService repositoryQueryService;
    private final ReleaseQueryService releaseQueryService;
    private final BlobQueryService blobQueryService;
    private final DeliveryMapper deliveryMapper;
    private final LoggingService loggingService;

    @Override
    public DeliveryRepositoriesResponse getRepositories(MachinePrincipal principal) {
        UUID organizationId = principal.organizationId();
        List<DeliveryRepositoryResponse> repositories = repositoryQueryService.getRepositories(organizationId).stream()
                .filter(repository -> repository.status() == RepositoryStatus.ACTIVE)
                .sorted(Comparator.comparing(RepositoryResponse::remoteUrl))
                .map(deliveryMapper::toRepositoryResponse)
                .toList();
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, DeliveryServiceLogConstant.REPOSITORIES_RESOLVED,
                repositories.size(), organizationId);
        return deliveryMapper.toRepositoriesResponse(organizationId, repositories);
    }

    @Override
    public DeliveryCurrent getCurrent(MachinePrincipal principal, String remoteUrl) {
        UUID organizationId = principal.organizationId();
        RepositoryResponse repository = getRepository(organizationId, remoteUrl);
        Release release = releaseQueryService.findLatest(organizationId, repository.id()).orElse(null);

        DeliveryCurrentResponse response = deliveryMapper.toCurrentResponse(repository.id(), release);
        String etag = DeliveryEtagHelper.etagOf(response.manifestSha256());
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, DeliveryServiceLogConstant.CURRENT_RESOLVED,
                repository.id(), organizationId, response.number());
        return deliveryMapper.toCurrent(response, etag);
    }

    @Override
    public DeliveryReleaseResponse getRelease(MachinePrincipal principal, UUID releaseId) {
        Release release = releaseQueryService.getEntityById(principal.organizationId(), releaseId);
        return toSignedResponse(principal.organizationId(), release);
    }

    @Override
    public DeliveryReleaseResponse getReleaseByNumber(MachinePrincipal principal, String remoteUrl, int number) {
        RepositoryResponse repository = getRepository(principal.organizationId(), remoteUrl);
        Release release = releaseQueryService.getEntityByNumber(principal.organizationId(),
                repository.id(), number);
        return toSignedResponse(principal.organizationId(), release);
    }

    @Override
    public DeliveryBlob getBlob(MachinePrincipal principal, String sha256) {
        UUID organizationId = principal.organizationId();
        // A blob is stored as soon as a draft or seed uploads it; only content a release names has
        // been approved, so anything else answers as unknown.
        if (!releaseQueryService.isDelivered(organizationId, sha256)) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, DeliveryServiceLogConstant.BLOB_NOT_DELIVERED,
                    sha256, organizationId);
            throw new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(DeliveryConstant.FIELD_SHA256, sha256));
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, DeliveryServiceLogConstant.BLOB_RESOLVED, sha256, organizationId);
        return deliveryMapper.toBlob(blobQueryService.getEntityBySha256(organizationId, sha256));
    }

    /**
     * The release with the exact text its signature covers. The text is rebuilt from the stored
     * release and the repository's current remote URL; when the remote changed after the release,
     * the rebuilt text no longer has the signed hash, and serving it would hand the CLI a manifest
     * whose signature fails, so the release is refused until a new one is made.
     */
    private DeliveryReleaseResponse toSignedResponse(UUID organizationId, Release release) {
        String signedManifest = releaseQueryService.toCanonicalJson(organizationId, release);
        if (!DeliveryEtagHelper.sha256Hex(signedManifest).equals(release.getManifestSha256())) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, DeliveryServiceLogConstant.RELEASE_REMOTE_CHANGED,
                    release.getId(), release.getRepositoryId(), release.getManifestSha256());
            throw new ConflictException(ExceptionDomainKayaError.RELEASE_REMOTE_CHANGED,
                    Map.of(DeliveryConstant.FIELD_RELEASE_ID, release.getId().toString()));
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, DeliveryServiceLogConstant.RELEASE_RESOLVED,
                release.getId(), release.getNumber(), release.getRepositoryId(), organizationId);
        return deliveryMapper.toReleaseResponse(release, signedManifest);
    }

    /**
     * Delivery never connects a repository: only ingest does, and a repository without a release
     * has nothing to deliver.
     */
    @Override
    public RepositoryResponse getRepository(UUID organizationId, String remoteUrl) {
        String normalizedRemoteUrl = RemoteUrlHelper.normalize(remoteUrl);
        if (normalizedRemoteUrl == null) {
            // The raw remote is neither logged nor echoed: it may carry an access token.
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, DeliveryServiceLogConstant.REMOTE_URL_INVALID, organizationId);
            throw new BadRequestException(ExceptionApiError.BAD_REQUEST,
                    Map.of(DeliveryConstant.FIELD_REMOTE_URL, DeliveryConstant.REMOTE_URL_INVALID_MESSAGE));
        }
        Optional<RepositoryResponse> repository = repositoryQueryService.getRepositories(organizationId).stream()
                .filter(candidate -> candidate.remoteUrl().equals(normalizedRemoteUrl))
                .findFirst();
        if (repository.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, DeliveryServiceLogConstant.REPOSITORY_NOT_FOUND,
                    normalizedRemoteUrl, organizationId);
            throw new NotFoundException(ExceptionDomainKayaError.NOT_RESOURCE_FOUND,
                    Map.of(DeliveryConstant.FIELD_REMOTE_URL, normalizedRemoteUrl));
        }
        if (repository.get().status() == RepositoryStatus.ARCHIVED) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, DeliveryServiceLogConstant.REPOSITORY_ARCHIVED,
                    repository.get().id(), organizationId);
            throw new ValidationException(ExceptionDomainKayaError.VAL_REPOSITORY_ARCHIVED,
                    Map.of(DeliveryConstant.FIELD_REPOSITORY_ID, repository.get().id().toString()));
        }
        return repository.get();
    }
}

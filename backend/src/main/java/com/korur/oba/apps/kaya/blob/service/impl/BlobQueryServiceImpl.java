package com.korur.oba.apps.kaya.blob.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.constant.BlobConstant;
import com.korur.oba.apps.kaya.blob.constant.service.BlobServiceLogConstant;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.repository.BlobRepository;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;

/**
 * The {@link BlobQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlobQueryServiceImpl implements BlobQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BlobQueryServiceImpl.class);

    private final BlobRepository blobRepository;
    private final LoggingService loggingService;

    @Override
    public Blob getEntityBySha256(UUID organizationId, String sha256) {
        return blobRepository.findByOrganizationIdAndSha256(organizationId, sha256).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, BlobServiceLogConstant.BLOB_NOT_FOUND, sha256, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(BlobConstant.FIELD_SHA256, sha256));
        });
    }

    @Override
    public Blob getEntityById(UUID organizationId, UUID blobId) {
        // Looked up within the organization, so a blob of another organization answers the same
        // 404 as an unknown id.
        return blobRepository.findByIdAndOrganizationId(blobId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, BlobServiceLogConstant.BLOB_NOT_FOUND, blobId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(CommonConstant.ID, blobId.toString()));
        });
    }

    @Override
    public Map<UUID, Integer> getSizesByIds(UUID organizationId, Collection<UUID> blobIds) {
        if (blobIds.isEmpty()) {
            return Map.of();
        }
        return blobRepository.findSizes(organizationId, blobIds).stream()
                .collect(Collectors.toMap(BlobRepository.BlobSize::getId, BlobRepository.BlobSize::getSize));
    }

    @Override
    public Map<UUID, Blob> getEntitiesByIds(UUID organizationId, Collection<UUID> blobIds) {
        if (blobIds.isEmpty()) {
            return Map.of();
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, BlobServiceLogConstant.LOADING_BLOBS, blobIds.size(), organizationId);
        return blobRepository.findByOrganizationIdAndIdIn(organizationId, blobIds).stream()
                .collect(Collectors.toMap(Blob::getId, Function.identity()));
    }

    @Override
    public List<Blob> getEntitiesBySha256s(UUID organizationId, Collection<String> sha256s) {
        if (sha256s.isEmpty()) {
            return List.of();
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, BlobServiceLogConstant.LOADING_BLOBS, sha256s.size(), organizationId);
        return blobRepository.findByOrganizationIdAndSha256In(organizationId, sha256s);
    }

    @Override
    public Map<String, byte[]> getContents(UUID organizationId, Collection<String> sha256s) {
        return getEntitiesBySha256s(organizationId, sha256s).stream()
                .collect(Collectors.toMap(Blob::getSha256, Blob::getContent, (first, second) -> first));
    }

    @Override
    public Set<String> getStoredSha256s(UUID organizationId, Collection<String> sha256s) {
        if (sha256s.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(blobRepository.findStoredSha256s(organizationId, sha256s));
    }

    @Override
    public long sumSizes(UUID organizationId, Collection<String> sha256s) {
        if (sha256s.isEmpty()) {
            return 0L;
        }
        return blobRepository.sumSizes(organizationId, sha256s);
    }
}

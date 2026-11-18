package com.korur.oba.apps.kaya.blob.service.impl;

// 1. Framework-level
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionApiError;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.BadRequestException;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.constant.BlobConstant;
import com.korur.oba.apps.kaya.blob.constant.service.BlobServiceLogConstant;
import com.korur.oba.apps.kaya.blob.dto.response.BlobUploadResponse;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.mapper.BlobMapper;
import com.korur.oba.apps.kaya.blob.repository.BlobRepository;
import com.korur.oba.apps.kaya.blob.service.BlobService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * The {@link BlobService} implementation.
 */
@Service
@RequiredArgsConstructor
public class BlobServiceImpl implements BlobService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BlobServiceImpl.class);

    private final BlobRepository blobRepository;
    private final BlobMapper blobMapper;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public BlobUploadResponse uploadBlob(User actor, UUID organizationId, String sha256, InputStream content) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, BlobServiceLogConstant.UPLOADING_BLOB,
                actor.getId(), sha256, organizationId);
        byte[] bytes = readWithinLimit(organizationId, sha256, content);
        String actual = sha256Of(bytes);
        if (!actual.equals(sha256)) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, BlobServiceLogConstant.BLOB_HASH_MISMATCH,
                    organizationId, actual, sha256);
            throw new BadRequestException(ExceptionDomainKayaError.BLOB_HASH_MISMATCH, Map.of(
                    BlobConstant.FIELD_EXPECTED_SHA256, sha256,
                    BlobConstant.FIELD_ACTUAL_SHA256, actual));
        }
        boolean stored = blobRepository.insertIfAbsent(organizationId, sha256, bytes, bytes.length) > 0;
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, BlobServiceLogConstant.BLOB_STORED,
                sha256, bytes.length, organizationId, stored);
        return blobMapper.toUploadResponse(sha256, bytes.length, stored);
    }

    @Override
    @Transactional
    public Blob storeContent(UUID organizationId, byte[] content) {
        String sha256 = sha256Of(content);
        storeIfAbsent(organizationId, sha256, content);
        // Present now: either inserted above or already there, and blobs are never deleted while
        // the caller's transaction runs.
        return blobRepository.findByOrganizationIdAndSha256(organizationId, sha256)
                .orElseThrow(() -> new IllegalStateException(sha256));
    }

    @Override
    @Transactional
    public boolean storeIfAbsent(UUID organizationId, String sha256, byte[] content) {
        boolean stored = blobRepository.insertIfAbsent(organizationId, sha256, content, content.length) > 0;
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, BlobServiceLogConstant.BLOB_STORED,
                sha256, content.length, organizationId, stored);
        return stored;
    }

    /**
     * Reads one byte past the limit at most, so an oversized or endless body is refused without
     * the server buffering more than the limit.
     */
    private byte[] readWithinLimit(UUID organizationId, String sha256, InputStream content) {
        byte[] bytes;
        try {
            bytes = content.readNBytes(BlobConstant.CONTENT_MAX_BYTES + 1);
        } catch (IOException e) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, BlobServiceLogConstant.BLOB_READ_FAILED,
                    sha256, organizationId, e.getMessage());
            throw new BadRequestException(ExceptionApiError.BAD_REQUEST);
        }
        if (bytes.length > BlobConstant.CONTENT_MAX_BYTES) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, BlobServiceLogConstant.BLOB_TOO_LARGE,
                    sha256, organizationId, BlobConstant.CONTENT_MAX_BYTES);
            throw new ApplicationException(HttpStatus.PAYLOAD_TOO_LARGE, ExceptionDomainKayaError.VAL_BODY_TOO_LARGE,
                    Map.of(BlobConstant.FIELD_LIMIT_BYTES, BlobConstant.CONTENT_MAX_BYTES));
        }
        return bytes;
    }

    private static String sha256Of(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(BlobConstant.SHA256_ALGORITHM).digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            // Every Java platform must provide SHA-256, so this means a broken runtime.
            throw new IllegalStateException(exception);
        }
    }
}

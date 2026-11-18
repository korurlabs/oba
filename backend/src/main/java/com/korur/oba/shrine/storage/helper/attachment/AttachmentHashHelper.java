package com.korur.oba.shrine.storage.helper.attachment;

// 1. Framework-level
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionInternalError;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.util.FileHashUtil;
import com.korur.oba.shrine.storage.constant.service.attachment.AttachmentServiceLogConstant;

/**
 * Centralizes SHA-256 content hashing on the attachment path, for
 * multipart uploads and for content the server holds in memory, so the
 * upload, store and exists-check services share one implementation.
 *
 * Wraps FileHashUtil.sha256Hex with the storage domain's logging and
 * error-translation contract: emits an observe log at the SERVICE layer
 * before hashing, an error log if hashing fails, and converts any
 * thrown checked or runtime failure into InternalServerErrorException
 * with ExceptionInternalError.HASH_COMPUTATION_ERROR so the controller
 * layer sees a single, well-known exception type.
 */
@Component
@RequiredArgsConstructor
public class AttachmentHashHelper {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AttachmentHashHelper.class);

    private final LoggingService loggingService;

    /**
     * Computes the SHA-256 hex digest of the given multipart file.
     *
     * Logs ATTACHMENT_HASH_COMPUTING with the original file name and
     * size at the SERVICE layer before hashing. On failure logs the
     * same message at error level with the throwable attached.
     *
     * @param file the multipart upload to hash
     * @return the 64-character lowercase hexadecimal SHA-256 digest
     * @throws InternalServerErrorException if reading or hashing the
     *         file content fails for any reason
     */
    public String computeHash(MultipartFile file) {
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                AttachmentServiceLogConstant.ATTACHMENT_HASH_COMPUTING,
                file.getOriginalFilename(), file.getSize());
        try {
            return FileHashUtil.sha256Hex(file);
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                    AttachmentServiceLogConstant.ATTACHMENT_HASH_COMPUTING,
                    e, file.getOriginalFilename(), file.getSize());
            throw new InternalServerErrorException(ExceptionInternalError.HASH_COMPUTATION_ERROR);
        }
    }

    /**
     * Computes the SHA-256 hex digest of content held in memory, for an
     * attachment the server makes itself rather than receives as an upload.
     *
     * Logs ATTACHMENT_CONTENT_HASH_COMPUTING with the size at the SERVICE layer
     * before hashing, and the same message at error level if hashing fails.
     *
     * @param content the bytes to hash; must not be null or empty
     * @return the 64-character lowercase hexadecimal SHA-256 digest
     * @throws InternalServerErrorException if the content is null or empty, or hashing fails
     */
    public String computeHash(byte[] content) {
        int size = content == null ? 0 : content.length;
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE,
                AttachmentServiceLogConstant.ATTACHMENT_CONTENT_HASH_COMPUTING, size);
        try {
            return FileHashUtil.sha256Hex(content);
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                    AttachmentServiceLogConstant.ATTACHMENT_CONTENT_HASH_COMPUTING, e, size);
            throw new InternalServerErrorException(ExceptionInternalError.HASH_COMPUTATION_ERROR);
        }
    }
}

package com.korur.oba.dojo.util;

// 1. Framework-level
import org.springframework.web.multipart.MultipartFile;

// 2. Third-party
import org.apache.commons.codec.digest.DigestUtils;

// 3. Java SDK
import java.io.IOException;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionInternalError;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;

/**
 * Utility for computing cryptographic content hashes of binary payloads.
 *
 * Kept separate from AuthenticationUtil (which also wraps DigestUtils.sha256Hex)
 * because the two have different concerns: AuthenticationUtil hashes credential
 * Strings and signals failure with a security-domain error code, whereas this
 * utility hashes arbitrary file bytes and signals failure with a generic
 * internal error code. Sharing the helper would force one caller to understand
 * the other's exception semantics, so we trade a small duplication for
 * domain isolation.
 *
 * @see com.korur.oba.dojo.exception.enums.ExceptionInternalError#HASH_COMPUTATION_ERROR
 */
public final class FileHashUtil {

    private FileHashUtil() {
        // Static-only utility; instantiation is meaningless.
    }

    /**
     * Computes the SHA-256 hex digest of the given byte content.
     *
     * Empty input is treated as a programming error rather than a domain
     * validation failure: callers are expected to short-circuit empty
     * uploads before hashing (the storage validator does this via
     * ensureFileNotEmpty), so reaching this method with no bytes signals
     * a bug, not a user-facing problem.
     *
     * @param content the raw file content to hash; must be non-null and non-empty
     * @return the 64-character lowercase hexadecimal SHA-256 digest
     * @throws InternalServerErrorException if the content is null or empty
     */
    public static String sha256Hex(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InternalServerErrorException(ExceptionInternalError.HASH_COMPUTATION_ERROR);
        }
        return DigestUtils.sha256Hex(content);
    }

    /**
     * Computes the SHA-256 hex digest of the contents of the given multipart upload.
     *
     * Convenience wrapper around the byte-array overload; declared to throw
     * IOException so callers reading the multipart stream do not have to
     * unwrap a checked exception they cannot meaningfully recover from.
     *
     * @param file the uploaded multipart file; must contain non-empty bytes
     * @return the 64-character lowercase hexadecimal SHA-256 digest
     * @throws IOException if reading the multipart bytes fails
     * @throws InternalServerErrorException if the file content resolves to null or empty
     */
    public static String sha256Hex(MultipartFile file) throws IOException {
        return sha256Hex(file.getBytes());
    }
}

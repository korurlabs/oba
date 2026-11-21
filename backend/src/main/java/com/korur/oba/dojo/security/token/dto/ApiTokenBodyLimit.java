package com.korur.oba.dojo.security.token.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionError;

/**
 * The largest request body a token kind may send, checked in the token filter before any controller
 * buffers it.
 *
 * @param maxBytes      the largest body in bytes
 * @param tooLargeError the error answered with 413 when the body is larger
 */
public record ApiTokenBodyLimit(
        int maxBytes,
        ExceptionError tooLargeError
) {
}

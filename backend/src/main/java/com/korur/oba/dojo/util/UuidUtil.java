package com.korur.oba.dojo.util;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal

/**
 * Parses identifiers that arrive as untrusted text, such as request and STOMP headers.
 *
 * Every caller refuses a missing or malformed value in its own transport (an HTTP error
 * body, a STOMP error frame, a placeholder in the logging context), so the parse reports
 * the problem as an empty result instead of an exception.
 */
public final class UuidUtil {

    private UuidUtil() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Parses a UUID, ignoring surrounding whitespace.
     *
     * @param value the raw text, may be null
     * @return the UUID, or empty when the value is null, blank or not a valid UUID
     */
    public static Optional<UUID> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException e) {
            // rule-10-no-op -- reason: the empty result is the refusal signal of every caller
            return Optional.empty();
        }
    }
}

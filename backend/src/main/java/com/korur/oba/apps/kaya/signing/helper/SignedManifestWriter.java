package com.korur.oba.apps.kaya.signing.helper;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

// 3. Java SDK
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionInternalError;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.apps.kaya.signing.dto.embedded.SignedManifest;

/**
 * Writes the canonical form of a signed document: compact JSON, properties in the order the
 * records declare, absent values left out, UTF-8. A private mapper is used, so a change to the
 * application's Jackson settings can never change signed bytes. The same document always gives
 * the same bytes, which lets delivery rebuild the signed text from the stored release.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SignedManifestWriter {

    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .disable(SerializationFeature.INDENT_OUTPUT);

    private SignedManifestWriter() {
    }

    /**
     * The canonical text of a document.
     *
     * @param manifest the document
     * @return compact JSON
     * @throws InternalServerErrorException when the document cannot be written, which only a
     *         programming error causes
     */
    public static String toCanonicalJson(SignedManifest manifest) {
        try {
            return CANONICAL_MAPPER.writeValueAsString(manifest);
        } catch (JsonProcessingException e) {
            throw new InternalServerErrorException(ExceptionInternalError.JSON_SERIALIZATION);
        }
    }

    /**
     * The creation time as signed: UTC, millisecond precision. The database keeps microseconds,
     * so the truncation makes the stored and the signed time the same instant.
     *
     * @param createdAt the release's creation time
     * @return for example 2026-10-23T09:15:02.123Z
     */
    public static String formatCreatedAt(Instant createdAt) {
        return DateTimeFormatter.ISO_INSTANT.format(createdAt.truncatedTo(ChronoUnit.MILLIS));
    }
}

package com.korur.oba.apps.kaya.reference.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The index of one reference file as the render domain extracts it on parse.
 *
 * @param name          the file name
 * @param contentBlobId the blob holding the whole file
 * @param mediaType     the media type of the file, such as text/markdown or image/png
 */
public record ReferenceIndex(
        String name,
        UUID contentBlobId,
        String mediaType
) {
}

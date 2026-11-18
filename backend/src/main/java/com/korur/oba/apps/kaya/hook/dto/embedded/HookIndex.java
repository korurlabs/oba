package com.korur.oba.apps.kaya.hook.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The index of one hook file as the render domain extracts it on parse.
 *
 * @param relativePath  the path below .agents/hooks/
 * @param contentBlobId the blob holding the whole file
 * @param executable    whether the file carries the executable bit; null is stored as false
 */
public record HookIndex(
        String relativePath,
        UUID contentBlobId,
        Boolean executable
) {
}

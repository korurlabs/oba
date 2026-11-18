package com.korur.oba.apps.kaya.script.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The index of one script file as the render domain extracts it on parse.
 *
 * @param name          the file name of the script
 * @param contentBlobId the blob holding the whole file
 * @param executable    whether the file carries the executable bit
 */
public record ScriptIndex(
        String name,
        UUID contentBlobId,
        boolean executable
) {
}

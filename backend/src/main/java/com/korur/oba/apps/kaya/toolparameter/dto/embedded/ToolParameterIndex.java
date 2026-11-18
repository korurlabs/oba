package com.korur.oba.apps.kaya.toolparameter.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.toolparameter.enums.ToolParameterFormat;

/**
 * The index of one tool parameter file as the render domain extracts it on parse.
 *
 * @param name          the file name
 * @param contentBlobId the blob holding the whole file
 * @param format        the syntax of the file, from its extension
 */
public record ToolParameterIndex(
        String name,
        UUID contentBlobId,
        ToolParameterFormat format
) {
}

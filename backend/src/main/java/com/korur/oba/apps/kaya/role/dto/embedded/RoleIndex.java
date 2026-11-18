package com.korur.oba.apps.kaya.role.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The index of one role file as the render domain extracts it on parse.
 *
 * @param relativePath  the path below .agents/roles/
 * @param contentBlobId the blob holding the whole file, frontmatter included
 * @param title         the frontmatter title, or null
 */
public record RoleIndex(
        String relativePath,
        UUID contentBlobId,
        String title
) {
}

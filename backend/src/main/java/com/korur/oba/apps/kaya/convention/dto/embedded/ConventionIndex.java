package com.korur.oba.apps.kaya.convention.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The index of one convention file as the render domain extracts it on parse.
 *
 * @param relativePath  the path below docs/private/conventions/, or null for a convention delivered
 *                      only inside skills
 * @param contentBlobId the blob holding the whole file, frontmatter included
 * @param title         the frontmatter title, or null
 * @param overridesPath the path of the pack convention this convention replaces, or null
 */
public record ConventionIndex(
        String relativePath,
        UUID contentBlobId,
        String title,
        String overridesPath
) {
}

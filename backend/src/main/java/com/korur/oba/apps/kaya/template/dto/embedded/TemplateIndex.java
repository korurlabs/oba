package com.korur.oba.apps.kaya.template.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The index of one template file as the render domain extracts it on parse.
 *
 * @param relativePath  the path below .agents/templates/
 * @param contentBlobId the blob holding the whole file, frontmatter included
 * @param title         the frontmatter title, or null
 */
public record TemplateIndex(
        String relativePath,
        UUID contentBlobId,
        String title
) {
}

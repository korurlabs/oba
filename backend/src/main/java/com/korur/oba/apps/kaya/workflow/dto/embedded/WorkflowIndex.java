package com.korur.oba.apps.kaya.workflow.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The index of one workflow file as the render domain extracts it on parse.
 *
 * @param relativePath  the path below .agents/workflows/, or null for a workflow delivered
 *                      only inside skills
 * @param contentBlobId the blob holding the whole file
 * @param title         the frontmatter title, or null
 */
public record WorkflowIndex(
        String relativePath,
        UUID contentBlobId,
        String title
) {
}

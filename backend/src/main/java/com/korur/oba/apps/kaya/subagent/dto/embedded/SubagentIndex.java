package com.korur.oba.apps.kaya.subagent.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal

/**
 * The index of one subagent file as the render domain extracts it on parse.
 *
 * @param relativePath  the path below .claude/agents/
 * @param contentBlobId the blob holding the whole file, frontmatter included
 * @param name          the frontmatter name, unique within the version
 * @param description   the frontmatter description, or null
 * @param model         the frontmatter model, or null
 * @param maxTurns      the frontmatter turn limit, at least 1, or null
 * @param tools         the frontmatter tools, or null when the frontmatter names none
 */
public record SubagentIndex(
        String relativePath,
        UUID contentBlobId,
        String name,
        String description,
        String model,
        Integer maxTurns,
        List<String> tools
) {
}

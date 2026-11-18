package com.korur.oba.apps.kaya.rule.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal

/**
 * The index of one rule file as the render domain extracts it on parse: the stored blob, the
 * frontmatter fields and the convention paths the body links to. Always-on is not part of it,
 * because it follows from an empty paths list.
 *
 * @param relativePath    the path below .agents/rules/
 * @param contentBlobId   the blob holding the whole file, frontmatter included
 * @param description     the frontmatter description, or null
 * @param paths           the frontmatter globs; empty for an always-on rule
 * @param overridesPath   the path of the pack rule this rule narrows, or null
 * @param conventionPaths the convention paths the body links to, from the repository root
 */
public record RuleIndex(
        String relativePath,
        UUID contentBlobId,
        String description,
        List<String> paths,
        String overridesPath,
        List<String> conventionPaths
) {
}

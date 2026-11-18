package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Set;

// 4. Internal

/**
 * What the skill rules of {@link FileRules} read from the frontmatter of a SKILL.md, read by the
 * caller, so the validation domain parses no file format itself.
 *
 * @param error       why the frontmatter cannot be read; null when it can
 * @param keys        its top-level keys; empty when absent or unreadable
 * @param description the description as text; null when absent or not a scalar
 * @param whenToUse   the when_to_use text; null when absent or not a scalar
 */
public record FrontmatterFacts(
        String error,
        Set<String> keys,
        String description,
        String whenToUse
) {

    /** A file without frontmatter, or one whose frontmatter the skill rules do not read. */
    public static final FrontmatterFacts NONE = new FrontmatterFacts(null, Set.of(), null, null);
}

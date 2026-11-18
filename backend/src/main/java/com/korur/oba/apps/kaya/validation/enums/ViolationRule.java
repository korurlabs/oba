package com.korur.oba.apps.kaya.validation.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Set;

// 4. Internal

/**
 * The rule a delivered file or a repository composition breaks, as {@link com.korur.oba.apps.kaya.validation.helper.FileRules} and
 * {@link com.korur.oba.apps.kaya.validation.helper.CompositionRules} report it. These are the checks the policy check
 * of the agents repository ran before Kaya held these files; the CLI repeats the path and symlink
 * rules on delivery, so a file Kaya accepts is one the CLI writes.
 */
public enum ViolationRule {

    // --- One file ---
    PATH_NOT_ALLOWED,
    MODE_INVALID,
    SYMLINK_TARGET_INVALID,
    SYMLINK_OUTSIDE_REPOSITORY,
    SYMLINK_CONTENT_REQUIRED,
    FILE_TOO_LARGE,
    SKILL_SYMLINK,
    HOOK_SYMLINK,
    SKILL_TOO_LONG,
    FRONTMATTER_INVALID,
    FRONTMATTER_KEY_UNKNOWN,
    SKILL_DESCRIPTION_TOO_LONG,

    // --- A repository composition; PATH_IN_TWO_SETS means two packs not on one chain give the path ---
    PATH_IN_TWO_SETS,
    SYMLINK_DANGLING,
    SYMLINK_TARGET_NOT_ALLOWED,
    PATH_THROUGH_SYMLINK,
    PATH_CASE_CONFLICT,
    SETTINGS_DISPATCHER_MISSING,
    // A guardrail's deny rules are merged into .claude/settings.json, so the composition must deliver one.
    GUARDRAIL_SETTINGS_MISSING,

    // --- A pack mounted in a folder of a repository ---
    MOUNT_PATH_OUTSIDE_NAMESPACE,
    MOUNT_RULE_SCOPE,
    OVERRIDE_NOT_ALLOWED,
    RULE_OVERRIDE_ALWAYS_ON,
    RULE_OVERRIDE_SPANS_MOUNT,
    // A guardrail belongs to a pack composed at the root, so it applies to the whole repository.
    GUARDRAIL_SCOPE,
    // A mounted pack is also an ancestor of a pack at the root, or composed there, so it would be delivered twice.
    PACK_MOUNT_INHERITED,
    // A pack gives an item a required ancestor, or an ancestor's guardrail, already gives; no descendant may replace it.
    OVERRIDE_LOCKED,

    // --- A pack list ---
    NOT_A_PACK,
    PACK_DUPLICATE;

    private static final Set<ViolationRule> SIZE_LIMITS =
            Set.of(FILE_TOO_LARGE, SKILL_TOO_LONG, SKILL_DESCRIPTION_TOO_LONG);

    /**
     * Whether the rule limits a size rather than what may be delivered. A seed mirrors the
     * repository as it is today, so it imports such a file and reports the rule as a warning; the
     * limits govern the changes made through Kaya, where they refuse.
     */
    public boolean isSizeLimit() {
        return SIZE_LIMITS.contains(this);
    }
}

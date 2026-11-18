package com.korur.oba.apps.kaya.validation.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Set;

// 4. Internal

/**
 * Delivered paths, file and composition limits, error value keys and violation messages of the validation domain.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ValidationConstant {

    private ValidationConstant() {
    }

    // --- Error value keys ---
    public static final String FIELD_VIOLATIONS = "violations";
    public static final String FIELD_RULE = "rule";
    public static final String FIELD_PROBLEM = "problem";
    public static final String FIELD_LIST = "list";
    public static final String FIELD_FOLDER = "folder";
    public static final String FIELD_PACK = "pack";
    public static final String FIELD_PATH = "path";

    // --- Permission rules: the syntax as the allowlist parses a rule, a tool name optionally followed
    // by a specifier in parentheses; tool names start upper case, or are MCP tools ---
    public static final String RULE_PATTERN = "^(?:[A-Z][A-Za-z0-9]*|mcp__[A-Za-z0-9_-]+)(?:\\((.+)\\))?$";
    public static final String LIST_ALLOW = "allow";
    public static final String LIST_ASK = "ask";

    // --- Why a permission rule was refused, in the problem of each refused rule ---
    public static final String RULE_PROBLEM_UNPARSABLE = "UNPARSABLE";
    public static final String RULE_PROBLEM_DUPLICATE = "DUPLICATE";
    public static final String RULE_PROBLEM_NOT_DENY = "NOT_DENY";

    // --- Delivered paths ---
    public static final String AGENTS_FILE = "AGENTS.md";
    public static final String AGENTS_DIRECTORY_FILE = ".agents/AGENTS.md";
    public static final String RULES_PREFIX = ".agents/rules/";
    public static final String GUARDRAILS_PREFIX = ".agents/rules/guardrails/";
    public static final String SKILLS_PREFIX = ".agents/skills/";
    public static final String SKILL_FILE_SUFFIX = "/SKILL.md";
    public static final String CLAUDE_SKILLS_PREFIX = ".claude/skills/";
    public static final String CLAUDE_AGENTS_PREFIX = ".claude/agents/";
    public static final String CONVENTIONS_PREFIX = "docs/private/conventions/";
    public static final String MOUNT_CONVENTIONS_PREFIX = "docs/private/conventions/projects/";
    public static final List<String> MOUNT_DIRECTORY_PREFIXES = List.of(".agents/roles/", ".agents/workflows/", ".agents/templates/");
    public static final String MOUNT_NAME_SEPARATOR = "-";
    public static final String HOOKS_PREFIX = ".agents/hooks/";
    public static final String DISPATCHER_PATH = ".agents/hooks/dispatch.sh";
    public static final List<String> DISPATCHER_EVENTS = List.of("PreToolUse", "SessionStart");
    public static final String SETTINGS_PATH = ".claude/settings.json";
    public static final String MARKDOWN_SUFFIX = ".md";
    public static final String PATH_SEPARATOR = "/";

    // --- Frontmatter ---
    public static final String FRONTMATTER_DESCRIPTION = "description";
    public static final String FRONTMATTER_WHEN_TO_USE = "when_to_use";
    public static final Set<String> SKILL_FRONTMATTER_KEYS = Set.of(
            "name", "description", "when_to_use", "argument-hint", "arguments", "disable-model-invocation",
            "user-invocable", "allowed-tools", "model", "effort", "context", "agent", "hooks", "paths", "license",
            "compatibility", "metadata", "version", "globs", "alwaysApply", "user_invocable");

    // --- File and composition limits ---
    public static final int PATH_MAX_LENGTH = 500;
    public static final int FILE_MAX_BYTES = 5_242_880;
    public static final int SKILL_MAX_LINES = 1500;
    public static final int SKILL_LISTING_MAX_CHARACTERS = 1536;
    public static final int SYMLINK_MAX_HOPS = 8;
    public static final int BRACE_EXPANSIONS_MAX = 64;

    // --- Violation messages ---
    public static final String ALWAYS_ON_LINK_TARGET_MESSAGE =
            "The symlink resolves to %s, which is not a delivered regular file; an always-on link must lead to one";
    public static final String FILE_TOO_LARGE_MESSAGE = "The file has %d bytes; the limit is %d";
    public static final String FILE_WITHOUT_CONTENT_MESSAGE = "A file or executable carries content and no symlink target";
    public static final String FRONTMATTER_INVALID_MESSAGE = "The frontmatter cannot be read: %s";
    public static final String FRONTMATTER_KEY_UNKNOWN_MESSAGE = "The frontmatter key %s is not a skill key";
    public static final String GUARDRAIL_SETTINGS_MISSING_MESSAGE =
            "The guardrail has deny rules, but the repository delivers no .claude/settings.json to merge them into";
    public static final String GUARDRAIL_SCOPE_MESSAGE =
            "A guardrail applies to the whole repository, so it belongs to a pack composed at the root, not to pack %s mounted in a folder";
    public static final String HOOK_SYMLINK_MESSAGE =
            "A hook must be a regular file, so that every change to the code it runs needs an owner's approval";
    public static final String PATH_CASE_CONFLICT_MESSAGE =
            "The path differs from %s only in letter case; a case-insensitive file system writes both to one place";
    public static final String PATH_NOT_ALLOWED_MESSAGE = "The path is outside the paths Kaya delivers";
    public static final String PATH_THROUGH_SYMLINK_MESSAGE =
            "The path lies below %s, a delivered symlink, so it would be written wherever that link points";
    public static final String MOUNT_PATH_OUTSIDE_NAMESPACE_MESSAGE =
            "The path is outside the namespace of pack %s, which is mounted in a folder; its files go under .agents/rules/%s/, .agents/skills/%s-*, "
                    + "docs/private/conventions/projects/%s/ or %s";
    public static final String SETTINGS_DISPATCHER_MISSING_MESSAGE =
            "The settings do not register .agents/hooks/dispatch.sh for %s";
    public static final String SKILL_DESCRIPTION_TOO_LONG_MESSAGE =
            "The description and when_to_use have %d characters together; the skill listing shows %d";
    public static final String SKILL_SYMLINK_MESSAGE = "A SKILL.md must be a regular file; Claude Code does not follow a symlinked skill";
    public static final String SKILL_TOO_LONG_MESSAGE = "The skill has %d lines; it must stay under %d";
    public static final String SYMLINK_CONTENT_REQUIRED_MESSAGE =
            "This path must be a regular file: Kaya checks its content";
    public static final String SYMLINK_DANGLING_MESSAGE = "The symlink points at %s, which the repository does not deliver";
    public static final String SYMLINK_OUTSIDE_REPOSITORY_MESSAGE = "The symlink target leaves the repository";
    public static final String SYMLINK_TARGET_INVALID_MESSAGE = "A symlink target must be a relative path without a backslash";
    public static final String SYMLINK_TARGET_NOT_ALLOWED_MESSAGE =
            "The symlink resolves to %s, which is outside the paths Kaya delivers";
    public static final String SYMLINK_WITH_CONTENT_MESSAGE = "A symlink carries a target, not content";
}

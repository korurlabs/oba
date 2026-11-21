package com.korur.oba.apps.kaya.render.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Map;
import java.util.Set;

// 4. Internal
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;

/**
 * Delivery folders, frontmatter keys, skill folder names, error value keys and refusal reasons of the
 * render domain.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RenderConstant {

    private RenderConstant() {
    }

    // --- Error value keys ---
    public static final String FIELD_PATH = "path";
    public static final String FIELD_REASON = "reason";
    public static final String FIELD_VIOLATIONS = "violations";

    // --- Delivery folders and files, from the repository root ---
    public static final String AGENTS_FILE = "AGENTS.md";
    public static final String AGENTS_DIRECTORY_FILE = ".agents/AGENTS.md";
    public static final String RULES_FOLDER = ".agents/rules/";
    public static final String GUARDRAILS_FOLDER = ".agents/rules/guardrails/";
    public static final String ROLES_FOLDER = ".agents/roles/";
    public static final String WORKFLOWS_FOLDER = ".agents/workflows/";
    public static final String TEMPLATES_FOLDER = ".agents/templates/";
    public static final String HOOKS_FOLDER = ".agents/hooks/";
    public static final String SKILLS_FOLDER = ".agents/skills/";
    public static final String SUBAGENTS_FOLDER = ".claude/agents/";
    public static final String CLAUDE_SKILLS_FOLDER = ".claude/skills/";
    public static final String SKILL_LINK_TARGET_PREFIX = "../../.agents/skills/";
    public static final String SETTINGS_FILE = ".claude/settings.json";
    public static final String CONVENTIONS_FOLDER = "docs/private/conventions/";
    public static final String MOUNT_CONVENTIONS_FOLDER = "docs/private/conventions/projects/";
    public static final String SKILL_FILE = "SKILL.md";
    public static final String PATH_SEPARATOR = "/";
    public static final String MARKDOWN_SUFFIX = ".md";

    // --- Tool links: the fixed path and target of each, relative to the link's directory ---
    public static final String CLAUDE_RULES_LINK = ".claude/rules";
    public static final String CLAUDE_TEMPLATES_LINK = ".claude/templates";
    public static final Map<ToolLinkName, String> TOOL_LINK_PATHS = Map.of(
            ToolLinkName.ROOT_AGENTS_FILE, AGENTS_FILE,
            ToolLinkName.CLAUDE_RULES, CLAUDE_RULES_LINK,
            ToolLinkName.CLAUDE_TEMPLATES, CLAUDE_TEMPLATES_LINK
    );
    public static final Map<ToolLinkName, String> TOOL_LINK_TARGETS = Map.of(
            ToolLinkName.ROOT_AGENTS_FILE, AGENTS_DIRECTORY_FILE,
            ToolLinkName.CLAUDE_RULES, "../.agents/rules",
            ToolLinkName.CLAUDE_TEMPLATES, "../.agents/templates"
    );

    // --- Skill folder mapping ---
    public static final String SKILL_WORKFLOWS_FOLDER = "workflows/";
    public static final String SKILL_SCRIPTS_FOLDER = "scripts/";
    public static final String SKILL_RULES_FOLDER = "rules/";
    public static final String SKILL_CONVENTIONS_FOLDER = "conventions/";
    public static final Set<String> TOOL_PARAMETER_JSON_SUFFIXES = Set.of(".json");
    public static final Set<String> TOOL_PARAMETER_YAML_SUFFIXES = Set.of(".yaml", ".yml");
    public static final String MEDIA_TYPE_FALLBACK = "application/octet-stream";
    public static final String MEDIA_TYPE_MARKDOWN = "text/markdown";

    // --- Path mapping: placeholders and notes of the rows ---
    public static final String PATTERN_PATH = "<path>";
    public static final String PATTERN_NAME = "<name>";
    public static final String PATTERN_ROOT_PATH = "<rootPath>";
    public static final String PATTERN_PACK = "<pack>";
    public static final String MOUNT_NAME_SEPARATOR = "-";
    public static final String PATTERN_SKILL_CONVENTION_FOLDERS = "{rules,conventions}/";
    public static final String PATTERN_TOOL_PARAMETER_SUFFIXES = ".{json,yaml,yml}";
    public static final String NOTE_MOUNT_AGENTS_FILE = "AGENTS.md of a pack mounted in a folder, stored as AGENTS.md";
    public static final String NOTE_SKILL_SCRIPT = "also any file of the skill with the executable bit";
    public static final String NOTE_SKILL_TOOL_PARAMETER = "outside workflows/, scripts/, rules/ and conventions/";
    public static final String NOTE_SKILL_REFERENCE = "every other file of the skill directory";
    public static final String NOTE_SKILL_LINK = "a link to ../../.agents/skills/<name>, rendered from the skill and never stored";
    public static final String NOTE_GUARDRAIL = "always-on; its deny rules go into .claude/settings.json";
    public static final String NOTE_TOOL_LINK = "a link to %s that a tool reads; it comes from a seed or oba kaya propose";

    // --- Tool view: names, summaries and notes of what each AI tool reads ---
    public static final String TOOL_NAME_CLAUDE_CODE = "Claude Code";
    public static final String TOOL_NAME_CODEX = "OpenAI Codex";
    public static final String TOOL_NAME_CURSOR = "Cursor";
    public static final String TOOL_NAME_GEMINI_CLI = "Gemini CLI";
    public static final String TOOL_SUMMARY_CLAUDE_CODE = "Reads AGENTS.md and its own .claude folder; the links .claude/rules, "
            + ".claude/templates and .claude/skills/<name> lead it to the sources under .agents/";
    public static final String TOOL_SUMMARY_CODEX = "Reads AGENTS.md and the skills under .agents/skills; other files under "
            + ".agents/ only when AGENTS.md names them";
    public static final String TOOL_SUMMARY_CURSOR = "Reads AGENTS.md and the skills under .agents/skills; other files under "
            + ".agents/ only when AGENTS.md names them";
    public static final String TOOL_SUMMARY_GEMINI_CLI = "Reads AGENTS.md only when its settings name it, and the skills under "
            + ".agents/skills; other files under .agents/ only when AGENTS.md names them";
    public static final String TOOL_NOTE_CLAUDE_RULES = "a rule with paths in its frontmatter loads when Claude Code reads a "
            + "matching file";
    public static final String TOOL_NOTE_GUARDRAIL = "its deny rules are enforced through .claude/settings.json";
    public static final String TOOL_NOTE_GUARDRAIL_NAMED = "only when AGENTS.md names it; the deny rules apply in Claude Code only";
    public static final String TOOL_NOTE_SKILL = "the description loads at start, the body when the tool picks the skill";
    public static final String TOOL_NOTE_SUBAGENT = "when Claude Code delegates a task to the subagent";
    public static final String TOOL_NOTE_HOOK = "runs when .claude/settings.json registers it";
    public static final String TOOL_NOTE_NAMED = "read when AGENTS.md, a rule or a skill names it";
    public static final String TOOL_NOTE_NAMED_BY_AGENTS_FILE = "read when AGENTS.md names it";
    public static final String TOOL_NOTE_MOUNT_AGENTS_FILE = "AGENTS.md of a pack mounted in a folder; read when the tool works on files below <rootPath>";
    public static final String TOOL_NOTE_MOUNT_AGENTS_FILE_MERGED = "AGENTS.md of a pack mounted in a folder; only when Codex starts in or below <rootPath>; merged after "
            + "the root AGENTS.md";
    public static final String TOOL_NOTE_GEMINI_AGENTS_FILE = "only when context.fileName in .gemini/settings.json or "
            + "~/.gemini/settings.json names AGENTS.md (a name or a list); GEMINI.md otherwise";
    public static final String TOOL_GAP_CLAUDE_FILE = "CLAUDE.md";
    public static final String TOOL_GAP_CLAUDE_FILE_NOTE = "Claude Code's own instructions file; when one exists Claude Code reads it "
            + "instead of AGENTS.md";
    public static final String TOOL_GAP_CLAUDE_LOCAL_FILE = "CLAUDE.local.md";
    public static final String TOOL_GAP_CLAUDE_LOCAL_FILE_NOTE = "personal instructions; like CLAUDE.md, its presence stops "
            + "AGENTS.md from loading";
    public static final String TOOL_GAP_CLAUDE_LOCAL_SETTINGS = ".claude/settings.local.json";
    public static final String TOOL_GAP_CLAUDE_LOCAL_SETTINGS_NOTE = "personal settings of one developer, kept out of version control";
    public static final String TOOL_GAP_CODEX_CONFIG = "~/.codex/config.toml";
    public static final String TOOL_GAP_CODEX_CONFIG_NOTE = "MCP servers and settings, in the user's home";
    public static final String TOOL_GAP_CODEX_PROJECT_CONFIG = ".codex/config.toml";
    public static final String TOOL_GAP_CODEX_PROJECT_CONFIG_NOTE = "project settings, loaded only in a trusted project";
    public static final String TOOL_GAP_CURSOR_RULES = ".cursor/rules/";
    public static final String TOOL_GAP_CURSOR_RULES_NOTE = "Cursor's own rule files";
    public static final String TOOL_GAP_CURSOR_MCP = ".cursor/mcp.json";
    public static final String TOOL_GAP_CURSOR_MCP_NOTE = "Cursor's MCP servers";
    public static final String TOOL_GAP_GEMINI_FILE = "GEMINI.md";
    public static final String TOOL_GAP_GEMINI_FILE_NOTE = "Gemini CLI's default instructions file";
    public static final String TOOL_GAP_GEMINI_SETTINGS = ".gemini/settings.json";
    public static final String TOOL_GAP_GEMINI_SETTINGS_NOTE = "settings, among them context.fileName, which decides whether "
            + "AGENTS.md is read";

    // --- Path mapping request ---
    public static final int PATH_MAPPING_VERSION_IDS_MAX = 20;
    public static final String PATH_MAPPING_VERSION_IDS_SIZE_MESSAGE = "must name at most 20 version ids";

    // --- Frontmatter keys ---
    public static final String FRONTMATTER_NAME = "name";
    public static final String FRONTMATTER_TITLE = "title";
    public static final String FRONTMATTER_DESCRIPTION = "description";
    public static final String FRONTMATTER_PATHS = "paths";
    public static final String FRONTMATTER_OVERRIDES = "overrides";
    public static final String FRONTMATTER_MODEL = "model";
    public static final String FRONTMATTER_MAX_TURNS = "maxTurns";
    public static final String FRONTMATTER_TOOLS = "tools";
    public static final String FRONTMATTER_WHEN_TO_USE = "when_to_use";
    public static final String FRONTMATTER_CATEGORY = "category";
    public static final String FRONTMATTER_DENY = "deny";
    // The only keys a guardrail may hold: paths and overrides would make it a scoped or narrowing rule.
    public static final Set<String> GUARDRAIL_FRONTMATTER_KEYS = Set.of(FRONTMATTER_CATEGORY, FRONTMATTER_DESCRIPTION, FRONTMATTER_DENY);

    public static final String MARKDOWN_HEADING_PREFIX = "# ";

    // --- Frontmatter reader messages ---
    public static final String FRONTMATTER_NOT_CLOSED_MESSAGE = "the frontmatter block is not closed by a line of ---";
    public static final String FRONTMATTER_NOT_MAPPING_MESSAGE = "the frontmatter is not a mapping of keys";

    // --- Refusal reasons of INVALID_FILE and NOT_DELIVERABLE ---
    public static final String NOT_DELIVERABLE_REASON = "no file kind is delivered at this path";
    public static final String SYMLINK_NOT_STORED_REASON = "only the link .claude/skills/<name> to its skill directory and the "
            + "tool links AGENTS.md, .claude/rules and .claude/templates are kept as symlinks";
    public static final String TOOL_LINK_TARGET_REASON = "the link %s must point at %s";
    public static final String SKILL_LINK_TARGET_REASON = "the link of a skill must point at ../../.agents/skills/%s";
    public static final String SKILL_LINK_DELETE_REASON = "the link of a skill follows its SKILL.md; delete the SKILL.md instead";
    public static final String NOT_UTF8_REASON = "the file is not valid UTF-8 text";
    public static final String FRONTMATTER_INVALID_REASON = "the frontmatter cannot be read: %s";
    public static final String FIELD_NOT_TEXT_REASON = "the frontmatter field %s must be a text value";
    public static final String FIELD_NOT_TEXT_LIST_REASON = "the frontmatter field %s must be a text value or a list of text values";
    public static final String FIELD_NOT_INTEGER_REASON = "the frontmatter field %s must be a whole number";
    public static final String FIELD_REQUIRED_REASON = "the frontmatter field %s is required";
    public static final String FIELD_TOO_LONG_REASON = "the frontmatter field %s is longer than %d characters";
    public static final String MAX_TURNS_REASON = "maxTurns must be 1 or more";
    public static final String SUBAGENT_NAME_TAKEN_REASON = "the subagent name %s is already used by %s";
    public static final String SETTINGS_NOT_OBJECT_REASON = "the settings file is not a JSON object";
    public static final String TOOL_PARAMETER_INVALID_REASON = "the %s file cannot be read: %s";
    public static final String SKILL_WITHOUT_SKILL_FILE_REASON = "the skill directory %s has no SKILL.md";
    public static final String PATH_TWICE_REASON = "the path is given twice";
    public static final String PATH_TOO_LONG_REASON = "the path inside the skill is longer than %d characters";
    public static final String VALIDATION_FAILED_REASON = "the file breaks a delivery rule";
    public static final String GUARDRAIL_NAME_REASON = "a guardrail is .agents/rules/guardrails/<name>.md with a name of 1 to 60 "
            + "lower case letters, digits and hyphens that starts with a letter or digit";
    public static final String GUARDRAIL_KEY_UNKNOWN_REASON = "the frontmatter key %s is not allowed in a guardrail; it takes "
            + "category, description and deny";
    public static final String GUARDRAIL_CATEGORY_UNKNOWN_REASON = "the category %s is not one of %s";
    public static final String GUARDRAIL_BODY_EMPTY_REASON = "the guardrail has no instruction text after its frontmatter";
    public static final String GUARDRAIL_BODY_TOO_LONG_REASON = "the instruction text has %d characters; a guardrail holds at most %d";
    public static final String GUARDRAIL_DENY_TOO_MANY_REASON = "the guardrail has %d deny rules; it holds at most %d";
    public static final String GUARDRAIL_DENY_TOO_LONG_REASON = "a deny rule is longer than %d characters";
    public static final String GUARDRAIL_DENY_REFUSED_REASON = "the deny rule %s is refused: %s";
    public static final String REMOVAL_IN_TREE_REASON = "a whole file tree holds files only; a removal is a single-file change";

    // --- Column limits of the kind tables ---
    public static final int RELATIVE_PATH_MAX = 500;
    public static final int SKILL_PART_PATH_MAX = 300;
    public static final int PART_NAME_MAX = 300;
    public static final int TITLE_MAX = 300;
    public static final int DESCRIPTION_MAX = 4000;
    public static final int NAME_MAX = 100;
    public static final int MODEL_MAX = 100;
}

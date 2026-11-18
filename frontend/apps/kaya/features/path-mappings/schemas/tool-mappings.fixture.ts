import type { ToolGapResponse, ToolMappingResponse, ToolPathResponse } from ".";

type Source = Pick<ToolPathResponse, "sourcePattern" | "kind" | "part" | "placement">;

function source(
  sourcePattern: string,
  kind: ToolPathResponse["kind"],
  placement: ToolPathResponse["placement"] = null
): Source {
  return { sourcePattern, kind, part: null, placement };
}

const ROOT_AGENTS = source(".agents/AGENTS.md", "AGENTS_FILE", "AGENTS_DIR");
const PROJECT_AGENTS = source("<rootPath>/AGENTS.md", "AGENTS_FILE", "MOUNT_ROOT");
const RULE = source(".agents/rules/<path>.md", "RULE");
const SKILL = source(".agents/skills/<name>/SKILL.md", "SKILL");
const TEMPLATE = source(".agents/templates/<path>.md", "TEMPLATE");

/** A read of the source itself, without a link on the way. */
function direct(
  from: Source,
  loading: ToolPathResponse["loading"],
  note: string | null = null
): ToolPathResponse {
  return {
    readPath: from.sourcePattern,
    loading,
    linkPath: null,
    linkTarget: null,
    ...from,
    note,
    state: null,
    count: null,
  };
}

/** A read that passes a link before it reaches its source. */
function linked(
  readPath: string,
  loading: ToolPathResponse["loading"],
  linkPath: string,
  linkTarget: string,
  from: Source,
  note: string | null = null
): ToolPathResponse {
  return { readPath, loading, linkPath, linkTarget, ...from, note, state: null, count: null };
}

const NAMED = "read when AGENTS.md names it";
const SKILL_NOTE = "the description loads at start, the body when the tool picks the skill";
const PROJECT_NOTE = "when the tool works on files below <rootPath>";
const ROOT_AGENTS_READ = (note: string | null = null) =>
  linked("AGENTS.md", "ALWAYS", "AGENTS.md", ".agents/AGENTS.md", ROOT_AGENTS, note);

const namedByAgentsFile = (): ToolPathResponse[] => [
  direct(RULE, "ON_REFERENCE", NAMED),
  direct(source(".agents/roles/<path>.md", "ROLE"), "ON_REFERENCE", NAMED),
  direct(source(".agents/workflows/<path>.md", "WORKFLOW"), "ON_REFERENCE", NAMED),
  direct(TEMPLATE, "ON_REFERENCE", NAMED),
  direct(source("docs/private/conventions/<path>.md", "CONVENTION"), "ON_REFERENCE", NAMED),
];

const gap = (path: string, note: string): ToolGapResponse => ({ path, note });

/** What `GET /path-mappings/tools` answers without a version, one entry per tool in its order. */
export const TOOL_MAPPINGS: ToolMappingResponse[] = [
  {
    tool: "CLAUDE_CODE",
    name: "Claude Code",
    summary:
      "Reads AGENTS.md and its own .claude folder; the links .claude/rules, .claude/templates and .claude/skills/<name> lead it to the sources under .agents/",
    reads: [
      ROOT_AGENTS_READ(),
      linked(
        ".claude/rules/<path>.md",
        "ALWAYS",
        ".claude/rules",
        "../.agents/rules",
        RULE,
        "a rule with paths in its frontmatter loads when Claude Code reads a matching file"
      ),
      direct(source(".claude/settings.json", "SETTINGS"), "ALWAYS"),
      linked(
        ".claude/skills/<name>/SKILL.md",
        "ON_DEMAND",
        ".claude/skills/<name>",
        "../../.agents/skills/<name>",
        SKILL,
        SKILL_NOTE
      ),
      direct(
        source(".claude/agents/<path>.md", "SUBAGENT"),
        "ON_DEMAND",
        "when Claude Code delegates a task to the subagent"
      ),
      direct(PROJECT_AGENTS, "ON_DEMAND", PROJECT_NOTE),
      direct(
        source(".agents/hooks/<path>", "HOOK"),
        "ON_REFERENCE",
        "runs when .claude/settings.json registers it"
      ),
      linked(
        ".claude/templates/<path>.md",
        "ON_REFERENCE",
        ".claude/templates",
        "../.agents/templates",
        TEMPLATE,
        "read when AGENTS.md, a rule or a skill names it"
      ),
      direct(source(".agents/roles/<path>.md", "ROLE"), "ON_REFERENCE"),
      direct(source(".agents/workflows/<path>.md", "WORKFLOW"), "ON_REFERENCE"),
      direct(source("docs/private/conventions/<path>.md", "CONVENTION"), "ON_REFERENCE"),
    ],
    notDelivered: [
      gap("CLAUDE.md", "Claude Code's own instructions file"),
      gap("CLAUDE.local.md", "personal instructions"),
      gap(".claude/settings.local.json", "personal settings of one developer"),
    ],
  },
  {
    tool: "CODEX",
    name: "OpenAI Codex",
    summary:
      "Reads AGENTS.md and the skills under .agents/skills; other files under .agents/ only when AGENTS.md names them",
    reads: [
      ROOT_AGENTS_READ(),
      direct(PROJECT_AGENTS, "ALWAYS", "only when Codex starts in or below <rootPath>"),
      direct(SKILL, "ON_DEMAND", SKILL_NOTE),
      ...namedByAgentsFile(),
    ],
    notDelivered: [
      gap("~/.codex/config.toml", "MCP servers and settings, in the user's home"),
      gap(".codex/config.toml", "project settings, loaded only in a trusted project"),
    ],
  },
  {
    tool: "CURSOR",
    name: "Cursor",
    summary:
      "Reads AGENTS.md and the skills under .agents/skills; other files under .agents/ only when AGENTS.md names them",
    reads: [
      ROOT_AGENTS_READ(),
      direct(PROJECT_AGENTS, "ON_DEMAND", PROJECT_NOTE),
      direct(SKILL, "ON_DEMAND", SKILL_NOTE),
      ...namedByAgentsFile(),
    ],
    notDelivered: [
      gap(".cursor/rules/", "Cursor's own rule files"),
      gap(".cursor/mcp.json", "Cursor's MCP servers"),
    ],
  },
  {
    tool: "GEMINI_CLI",
    name: "Gemini CLI",
    summary:
      "Reads AGENTS.md only when its settings name it, and the skills under .agents/skills; other files under .agents/ only when AGENTS.md names them",
    reads: [
      ROOT_AGENTS_READ("only when context.fileName in .gemini/settings.json names AGENTS.md"),
      direct(PROJECT_AGENTS, "ON_DEMAND", PROJECT_NOTE),
      direct(SKILL, "ON_DEMAND", SKILL_NOTE),
      ...namedByAgentsFile(),
    ],
    notDelivered: [
      gap("GEMINI.md", "Gemini CLI's default instructions file"),
      gap(".gemini/settings.json", "settings, among them context.fileName"),
    ],
  },
];

/** One tool's entry with the given state and count on every read. */
export function withStates(
  mapping: ToolMappingResponse,
  states: ReadonlyArray<Pick<ToolPathResponse, "state" | "count">>
): ToolMappingResponse {
  return {
    ...mapping,
    reads: mapping.reads.map((read: ToolPathResponse, index: number) => ({
      ...read,
      ...(states[index] ?? {}),
    })),
  };
}

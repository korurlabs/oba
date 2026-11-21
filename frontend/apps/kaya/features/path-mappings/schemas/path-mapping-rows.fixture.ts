import type { FileFormat, PathMappingResponse } from ".";

/** A row of the server's answer, with no count unless one is given. */
function row(
  kind: PathMappingResponse["kind"],
  pathPattern: string,
  formats: FileFormat[],
  extra: Partial<PathMappingResponse> = {}
): PathMappingResponse {
  return {
    kind,
    part: null,
    placement: null,
    link: null,
    pathPattern,
    mountedPattern: null,
    formats,
    executable: false,
    note: null,
    count: null,
    ...extra,
  };
}

/** The 23 rows `GET /path-mappings` answers, in its order, without counts. */
export const PATH_MAPPING_ROWS: PathMappingResponse[] = [
  row("AGENTS_FILE", "AGENTS.md", ["MARKDOWN"], {
    placement: "ROOT",
    mountedPattern: "<rootPath>/AGENTS.md",
  }),
  row("AGENTS_FILE", ".agents/AGENTS.md", ["MARKDOWN"], { placement: "AGENTS_DIR" }),
  row("AGENTS_FILE", "<rootPath>/AGENTS.md", ["MARKDOWN"], {
    placement: "MOUNT_ROOT",
    mountedPattern: "<rootPath>/AGENTS.md",
    note: "for a pack mounted in a folder, at that folder",
  }),
  row("RULE", ".agents/rules/<path>.md", ["MARKDOWN"], {
    mountedPattern: ".agents/rules/<pack>/<path>.md",
  }),
  row("CONVENTION", "docs/private/conventions/<path>.md", ["MARKDOWN"], {
    mountedPattern: "docs/private/conventions/projects/<pack>/<path>.md",
  }),
  row("ROLE", ".agents/roles/<path>.md", ["MARKDOWN"], {
    mountedPattern: ".agents/roles/<pack>/<path>.md",
  }),
  row("WORKFLOW", ".agents/workflows/<path>.md", ["MARKDOWN"], {
    mountedPattern: ".agents/workflows/<pack>/<path>.md",
  }),
  row("TEMPLATE", ".agents/templates/<path>.md", ["MARKDOWN"], {
    mountedPattern: ".agents/templates/<pack>/<path>.md",
  }),
  row("HOOK", ".agents/hooks/<path>", ["ANY"], { executable: true }),
  row("SUBAGENT", ".claude/agents/<path>.md", ["MARKDOWN"], {
    mountedPattern: ".claude/agents/<pack>-<name>.md",
  }),
  row("SETTINGS", ".claude/settings.json", ["JSON"], { executable: true }),
  row("SKILL", ".agents/skills/<name>/SKILL.md", ["MARKDOWN"], {
    mountedPattern: ".agents/skills/<pack>-<name>/SKILL.md",
  }),
  row("SKILL", ".agents/skills/<name>/workflows/<path>", ["MARKDOWN"], {
    part: "WORKFLOW",
    mountedPattern: ".agents/skills/<pack>-<name>/workflows/<path>",
  }),
  row("SKILL", ".agents/skills/<name>/scripts/<path>", ["ANY"], {
    part: "SCRIPT",
    mountedPattern: ".agents/skills/<pack>-<name>/scripts/<path>",
    executable: true,
    note: "also any file of the skill with the executable bit",
  }),
  row("SKILL", ".agents/skills/<name>/{rules,conventions}/<path>", ["MARKDOWN"], {
    part: "CONVENTION",
    mountedPattern: ".agents/skills/<pack>-<name>/{rules,conventions}/<path>",
  }),
  row("SKILL", ".agents/skills/<name>/<path>.{json,yaml,yml}", ["JSON", "YAML"], {
    part: "TOOL_PARAMETER",
    mountedPattern: ".agents/skills/<pack>-<name>/<path>.{json,yaml,yml}",
    note: "outside workflows/, scripts/, rules/ and conventions/",
  }),
  row("SKILL", ".agents/skills/<name>/<path>", ["ANY"], {
    part: "REFERENCE",
    mountedPattern: ".agents/skills/<pack>-<name>/<path>",
    note: "every other file of the skill directory",
  }),
  row("SKILL_LINK", ".claude/skills/<name>", ["SYMLINK"], {
    mountedPattern: ".claude/skills/<pack>-<name>",
    note: "a link to ../../.agents/skills/<name>, rendered from the skill and never stored",
  }),
  row("TOOL_LINK", "AGENTS.md", ["SYMLINK"], {
    link: "ROOT_AGENTS_FILE",
    note: "a link to .agents/AGENTS.md that a tool reads; it comes from a seed or oba kaya propose",
  }),
  row("TOOL_LINK", ".claude/rules", ["SYMLINK"], {
    link: "CLAUDE_RULES",
    note: "a link to ../.agents/rules that a tool reads; it comes from a seed or oba kaya propose",
  }),
  row("TOOL_LINK", ".claude/templates", ["SYMLINK"], {
    link: "CLAUDE_TEMPLATES",
    note: "a link to ../.agents/templates that a tool reads; it comes from a seed or oba kaya propose",
  }),
];

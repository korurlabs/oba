/**
 * The grouping, filtering and sorting of the allowlist's permission rules by
 * tool and program, kept as pure functions over the rules so a page can feed
 * them from any source.
 *
 * A Bash rule is filed under the program its pattern runs, read past the
 * wrappers Claude Code strips before matching (`timeout`, `time`, `nice`,
 * `nohup`, `stdbuf`, `command`, `builtin`, `noglob` and a bare `xargs`) and
 * past leading variable assignments, so `timeout 30 npm test *` sits with the
 * other `npm` rules.
 */

import type { AllowRule, PermissionEffect } from "@/types/run";

/** The order in which effects decide in Claude Code, and in which rules are listed. */
export const EFFECT_ORDER: readonly PermissionEffect[] = ["deny", "ask", "allow"];

const WRAPPERS: ReadonlySet<string> = new Set([
  "time",
  "nice",
  "nohup",
  "stdbuf",
  "builtin",
  "noglob",
]);
const ASSIGNMENT = /^[A-Za-z_][A-Za-z0-9_]*=/;

/** The words of a command, split on whitespace; enough to strip wrappers. */
function words(command: string): string[] {
  return command.trim().split(/\s+/).filter(Boolean);
}

/** The command without the wrappers Claude Code strips before matching. */
function stripWrappers(command: string): string {
  let rest = words(command);
  for (;;) {
    const [first, second] = rest;
    if (first === undefined) break;
    if (first === "timeout" && second !== undefined) {
      rest = rest.slice(2);
    } else if (first === "command" && second !== "-v" && second !== "-V") {
      rest = rest.slice(1);
    } else if (WRAPPERS.has(first) || (first === "xargs" && !second?.startsWith("-"))) {
      rest = rest.slice(1);
    } else {
      break;
    }
  }
  return rest.join(" ");
}

/** The command without its leading variable assignments. */
function stripAssignments(command: string): string {
  const rest = words(command);
  while (rest.length > 1 && ASSIGNMENT.test(rest[0] ?? "")) rest.shift();
  return rest.join(" ");
}

/** A rule as it is written in a settings file, such as `Bash(git push *)`. */
export function ruleText(rule: AllowRule): string {
  return rule.pattern === null ? rule.tool : `${rule.tool}(${rule.pattern})`;
}

/**
 * The program a Bash pattern runs: its first word after variable assignments
 * and wrappers, without a directory. Null for a pattern that matches every
 * command.
 */
export function programOf(rule: AllowRule): string | null {
  if (rule.tool !== "Bash" || rule.pattern === null) return null;
  const pattern = rule.pattern.endsWith(":*") ? rule.pattern.slice(0, -2) : rule.pattern;
  const word = words(stripWrappers(stripAssignments(pattern)))[0];
  if (word === undefined || word === "*") return null;
  // A script named by a wildcard, such as `./scripts/*`, keeps its directory as its name.
  const name = word.split("/").pop();
  return name && !name.includes("*") ? name : word;
}

/** The settings files a rule can come from, for the source filter. */
export type SourceKind = "user" | "project" | "local";

export const SOURCE_KINDS: readonly SourceKind[] = ["user", "project", "local"];

export const SOURCE_KIND_LABELS: Readonly<Record<SourceKind, string>> = {
  user: "User settings",
  project: "Project settings",
  local: "Local settings",
};

/** The settings file a source names, or null for a source that names none. */
export function sourceKind(source: string): SourceKind | null {
  return source === "user" || source === "project" || source === "local" ? source : null;
}

/** The label of a rule's source badge, such as "Project settings"; an unknown source shows as is. */
export function sourceLabel(source: string): string {
  const kind = sourceKind(source);
  return kind === null ? source : SOURCE_KIND_LABELS[kind];
}

export type RuleFilter = {
  /** One source kind, or null for every source. */
  source: SourceKind | null;
  /** Words that must all appear in the rule text or its source, in any case. */
  text: string;
};

export function filterRules(rules: readonly AllowRule[], filter: RuleFilter): AllowRule[] {
  const terms = filter.text.toLowerCase().split(/\s+/).filter(Boolean);
  return rules.filter((rule: AllowRule) => {
    if (filter.source !== null && sourceKind(rule.source) !== filter.source) return false;
    const haystack = `${ruleText(rule)} ${rule.source} ${sourceLabel(rule.source)}`.toLowerCase();
    return terms.every((term: string) => haystack.includes(term));
  });
}

/** The card title for Bash rules that match every command. */
export const EVERY_COMMAND = "Every command";
/** The section that gathers the tools of every MCP server. */
export const MCP_SECTION = "MCP";

/** The tools whose sections come first, in this order; the others follow by name. */
export const TOOL_ORDER: readonly string[] = [
  "Bash",
  "Edit",
  "Read",
  "Write",
  "WebFetch",
  "Skill",
  MCP_SECTION,
];

export type RuleCard = {
  /** The program for Bash, the server for MCP, else the tool. */
  title: string;
  rules: AllowRule[];
};

export type RuleSection = {
  /** The tool name, or "MCP" for the tools of every MCP server. */
  tool: string;
  cards: RuleCard[];
  count: number;
};

function sectionOf(rule: AllowRule): string {
  return rule.tool.startsWith("mcp__") ? MCP_SECTION : rule.tool;
}

function cardOf(rule: AllowRule): string {
  if (rule.tool === "Bash") return programOf(rule) ?? EVERY_COMMAND;
  if (rule.tool.startsWith("mcp__")) return rule.tool.split("__")[1] || rule.tool;
  return rule.tool;
}

function compareRules(left: AllowRule, right: AllowRule): number {
  const effect = EFFECT_ORDER.indexOf(left.effect) - EFFECT_ORDER.indexOf(right.effect);
  return effect !== 0 ? effect : ruleText(left).localeCompare(ruleText(right));
}

/**
 * Groups rules by tool, in `TOOL_ORDER` and then by name, with every MCP
 * server's tools in one section. Bash is split into one card per program, so
 * the list reads as what Claude can run; MCP into one card per server; every
 * other tool is one card. Cards are sorted by title, "Every command" first,
 * and their rules deny first, then ask, then allow, then by text.
 */
export function groupRules(rules: readonly AllowRule[]): RuleSection[] {
  const sections = new Map<string, Map<string, AllowRule[]>>();
  for (const rule of rules) {
    const section = sectionOf(rule);
    const cards = sections.get(section) ?? new Map<string, AllowRule[]>();
    const card = cardOf(rule);
    cards.set(card, [...(cards.get(card) ?? []), rule]);
    sections.set(section, cards);
  }
  const others = [...sections.keys()]
    .filter((tool: string) => !TOOL_ORDER.includes(tool))
    .sort((left: string, right: string) => left.localeCompare(right));
  return [...TOOL_ORDER, ...others]
    .filter((tool: string) => sections.has(tool))
    .map((tool: string) => {
      const cards = [...(sections.get(tool) ?? new Map<string, AllowRule[]>()).entries()]
        .sort(([left]: [string, AllowRule[]], [right]: [string, AllowRule[]]) =>
          left === EVERY_COMMAND ? -1 : right === EVERY_COMMAND ? 1 : left.localeCompare(right)
        )
        .map(([title, cardRules]: [string, AllowRule[]]) => ({
          title,
          rules: [...cardRules].sort(compareRules),
        }));
      const count = cards.reduce((sum: number, card: RuleCard) => sum + card.rules.length, 0);
      return { tool, cards, count };
    });
}

/**
 * What a path mapping row is called and which page edits its files. Routes
 * are a web concern, so this table lives here and not on the server; a row
 * the table does not know keeps its kind name and links to no page.
 */

import type { AgentsFilePlacement } from "@/features/rules/schemas";
import type { RuleTab } from "@/features/scope/components/scope-files";
import type { AllowlistTab } from "@/features/allowlist/components/allowlist-page";

import type { FileKind, PathMappingResponse, SkillPartKind } from "../schemas";

/** The page that edits a row's files, and the tab it opens on; null for a page without tabs. */
export type KindPage = {
  label: string;
  path: string;
  tab: RuleTab | AllowlistTab | null;
};

export type KindRow = {
  /** The kind as a person reads it, such as "Skill: script". */
  label: string;
  /** Null for a row no page edits. */
  page: KindPage | null;
};

const rulesTab = (label: string, tab: RuleTab): KindPage => ({ label, path: "/rules", tab });
const allowlistTab = (label: string, tab: AllowlistTab): KindPage => ({
  label,
  path: "/allowlist",
  tab,
});
const SKILLS: KindPage = { label: "Skills", path: "/skills", tab: null };

const KIND_ROWS: Readonly<Record<Exclude<FileKind, "AGENTS_FILE">, KindRow>> = {
  RULE: { label: "Rule", page: rulesTab("Rules", "rules") },
  CONVENTION: {
    label: "Convention",
    page: { label: "Conventions", path: "/conventions", tab: null },
  },
  ROLE: { label: "Role", page: rulesTab("Rules: Roles", "roles") },
  WORKFLOW: { label: "Workflow", page: rulesTab("Rules: Workflows", "workflows") },
  TEMPLATE: { label: "Template", page: rulesTab("Rules: Templates", "templates") },
  HOOK: { label: "Hook", page: rulesTab("Rules: Hooks", "hooks") },
  SUBAGENT: { label: "Subagent", page: rulesTab("Rules: Subagents", "subagents") },
  SETTINGS: { label: "Settings", page: allowlistTab("Allowlist: Settings file", "content") },
  SKILL: { label: "Skill", page: SKILLS },
  SKILL_LINK: { label: "Skill link", page: SKILLS },
  // A tool link comes from a seed or oba kaya propose; no page edits it.
  TOOL_LINK: { label: "Tool link", page: null },
};

const PLACEMENT_LABELS: Readonly<Record<AgentsFilePlacement, string>> = {
  ROOT: "AGENTS.md: repository root",
  AGENTS_DIR: "AGENTS.md: .agents/",
  MOUNT_ROOT: "AGENTS.md: mounted pack",
};

const PART_LABELS: Readonly<Record<SkillPartKind, string>> = {
  WORKFLOW: "Skill: workflow",
  SCRIPT: "Skill: script",
  CONVENTION: "Skill: convention",
  REFERENCE: "Skill: reference",
  TOOL_PARAMETER: "Skill: tool parameter",
};

export function kindRowOf(row: Pick<PathMappingResponse, "kind" | "part" | "placement">): KindRow {
  if (row.kind === "AGENTS_FILE") {
    return {
      label: (row.placement && PLACEMENT_LABELS[row.placement]) ?? "AGENTS.md",
      page: rulesTab("Rules: AGENTS.md", "agents-md"),
    };
  }
  const known: KindRow | undefined = KIND_ROWS[row.kind];
  if (!known) return { label: row.kind, page: null };
  if (row.kind === "SKILL" && row.part) {
    return { label: PART_LABELS[row.part] ?? `Skill: ${row.part}`, page: known.page };
  }
  return known;
}

/**
 * The rows a search keeps: every term is found in the row's path pattern,
 * its kind or the page that edits it, ignoring case. An empty search keeps
 * every row.
 */
export function filterMappingRows<T extends PathMappingResponse>(
  rows: readonly T[],
  search: string
): T[] {
  const terms = search.toLowerCase().split(/\s+/).filter(Boolean);
  return rows.filter((row: T) => {
    const kind = kindRowOf(row);
    const haystack = `${row.pathPattern} ${kind.label} ${kind.page?.label ?? ""}`.toLowerCase();
    return terms.every((term: string) => haystack.includes(term));
  });
}

/**
 * The link to a row's page: the scope in view, so the page opens on the
 * files the count counted, and the row's tab.
 */
export function kindPageHref(page: KindPage, scope: string | null): string {
  const params = new URLSearchParams();
  if (scope !== null) params.set("scope", scope);
  if (page.tab !== null) params.set("tab", page.tab);
  const query = params.toString();
  return query === "" ? page.path : `${page.path}?${query}`;
}

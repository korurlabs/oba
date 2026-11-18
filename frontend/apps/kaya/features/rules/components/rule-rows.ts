/**
 * The rows of a Rules tab: one per file the scope delivers in the tab,
 * described by the server's list of that kind for the versions that hold the
 * files. The list names every row a version holds, also the ones another
 * pack shadows, so the delivered files decide what is listed and the kind
 * rows only describe them. A file the list does not name keeps its path.
 */

import {
  filesForTab,
  formatBytes,
  type RuleTab,
  type ScopeFile,
} from "@/features/scope/components/scope-files";

import type {
  AgentsFileListResponse,
  AgentsFilePlacement,
  HookListResponse,
  RoleListResponse,
  RuleListResponse,
  SubagentListResponse,
  TemplateListResponse,
  WorkflowListResponse,
} from "../schemas";

/** The kind rows of one tab, tagged with the tab they belong to. */
export type RuleKindRows =
  | { tab: "rules"; rows: readonly RuleListResponse[] }
  | { tab: "agents-md"; rows: readonly AgentsFileListResponse[] }
  | { tab: "roles"; rows: readonly RoleListResponse[] }
  | { tab: "subagents"; rows: readonly SubagentListResponse[] }
  | { tab: "workflows"; rows: readonly WorkflowListResponse[] }
  | { tab: "templates"; rows: readonly TemplateListResponse[] }
  | { tab: "hooks"; rows: readonly HookListResponse[] };

/** What a kind row says about a file, beyond its path. */
export type KindSummary = {
  /** The title, description or name from the frontmatter; null when it has none. */
  title: string | null;
  /** Short facts such as the paths a rule applies to or the model of a subagent. */
  details: string[];
  /** Whether Claude Code loads the file at every start; null when the kind has no such flag. */
  alwaysOn: boolean | null;
  sizeBytes: number;
  /** The paths a rule applies to; empty for every other kind. */
  paths: string[];
  /** A subagent's description, which tells Claude when to use it; null for every other kind. */
  description: string | null;
};

export type RuleTabRow = {
  file: ScopeFile;
  /** Null when the kind list does not name the file, or has not loaded yet. */
  summary: KindSummary | null;
};

const KIND_FOLDERS: Readonly<Record<Exclude<RuleTab, "agents-md">, string>> = {
  rules: ".agents/rules/",
  roles: ".agents/roles/",
  subagents: ".claude/agents/",
  workflows: ".agents/workflows/",
  templates: ".agents/templates/",
  hooks: ".agents/hooks/",
};

/**
 * Where an `AGENTS.md` path sits, the way the server stores it. A mounted
 * pack stores its agents file as `AGENTS.md` and a release delivers it in the
 * mount folder, so a folder's `AGENTS.md` is a stored ROOT file.
 */
export function placementOf(path: string): AgentsFilePlacement {
  return path === ".agents/AGENTS.md" ? "AGENTS_DIR" : "ROOT";
}

function keyOf(versionId: string, where: string): string {
  return `${versionId}\n${where}`;
}

/** The key a scope file of a tab is described under. */
function fileKey(tab: RuleTab, file: ScopeFile): string {
  return keyOf(file.versionId, tab === "agents-md" ? placementOf(file.path) : file.path);
}

function toolsText(tools: readonly string[] | null | undefined): string | null {
  return tools && tools.length > 0 ? `Tools: ${tools.join(", ")}` : null;
}

function present(...values: (string | null | undefined)[]): string[] {
  return values.filter((value): value is string => typeof value === "string" && value !== "");
}

/** The summary of every kind row, by version and repository path (by placement for `AGENTS.md`). */
export function kindSummaries(kind: RuleKindRows): Map<string, KindSummary> {
  const summaries = new Map<string, KindSummary>();
  const put = (versionId: string, where: string, summary: KindSummary) =>
    summaries.set(keyOf(versionId, where), summary);
  switch (kind.tab) {
    case "rules":
      for (const row of kind.rows) {
        put(row.versionId, `${KIND_FOLDERS.rules}${row.path}`, {
          title: row.description ?? null,
          details: present(
            row.alwaysOn ? null : `Applies to ${row.paths.join(", ")}`,
            row.conventions.length === 0
              ? null
              : row.conventions.length === 1
                ? "Links 1 convention doc"
                : `Links ${row.conventions.length} convention docs`
          ),
          alwaysOn: row.alwaysOn,
          sizeBytes: row.sizeBytes,
          paths: row.paths,
          description: null,
        });
      }
      break;
    case "agents-md":
      for (const row of kind.rows) {
        put(row.versionId, row.placement, {
          title: null,
          details: [],
          alwaysOn: true,
          sizeBytes: row.sizeBytes,
          paths: [],
          description: null,
        });
      }
      break;
    case "subagents":
      for (const row of kind.rows) {
        put(row.versionId, `${KIND_FOLDERS.subagents}${row.path}`, {
          title: row.name,
          details: present(
            row.model ? `Model ${row.model}` : null,
            typeof row.maxTurns === "number" ? `At most ${row.maxTurns} turns` : null,
            toolsText(row.tools)
          ),
          alwaysOn: null,
          sizeBytes: row.sizeBytes,
          paths: [],
          description: row.description ?? null,
        });
      }
      break;
    case "hooks":
      for (const row of kind.rows) {
        put(row.versionId, `${KIND_FOLDERS.hooks}${row.path}`, {
          title: null,
          details: present(row.executable ? null : "Not executable, so it never runs"),
          alwaysOn: null,
          sizeBytes: row.sizeBytes,
          paths: [],
          description: null,
        });
      }
      break;
    case "roles":
    case "workflows":
    case "templates":
      for (const row of kind.rows) {
        // A workflow only a skill holds has no path and no file in this tab.
        if (!row.path) continue;
        put(row.versionId, `${KIND_FOLDERS[kind.tab]}${row.path}`, {
          title: row.title ?? null,
          details: [],
          alwaysOn: null,
          sizeBytes: row.sizeBytes,
          paths: [],
          description: null,
        });
      }
      break;
  }
  return summaries;
}

/** The versions whose kind rows a tab needs: those that hold its delivered files. */
export function tabVersionIds(files: readonly ScopeFile[], tab: RuleTab): string[] {
  return [...new Set(filesForTab(files, tab).map((file: ScopeFile) => file.versionId))];
}

/** The rows of a tab: its delivered files, each with the summary its kind row gives. */
export function ruleTabRows(
  files: readonly ScopeFile[],
  tab: RuleTab,
  kind: RuleKindRows | null
): RuleTabRow[] {
  const summaries = kind !== null && kind.tab === tab ? kindSummaries(kind) : new Map();
  return filesForTab(files, tab).map((file: ScopeFile) => ({
    file,
    summary: summaries.get(fileKey(tab, file)) ?? null,
  }));
}

/**
 * The rows a search keeps: every term is found in the row's path, title or
 * description, ignoring case. An empty search keeps every row.
 */
export function filterRuleRows(rows: readonly RuleTabRow[], search: string): RuleTabRow[] {
  const terms = search.toLowerCase().split(/\s+/).filter(Boolean);
  return rows.filter(({ file, summary }: RuleTabRow) => {
    const haystack =
      `${file.path} ${summary?.title ?? ""} ${summary?.description ?? ""}`.toLowerCase();
    return terms.every((term: string) => haystack.includes(term));
  });
}

/** The column header of a tab's kind field. */
export const ROW_FIELD_LABELS: Readonly<Record<RuleTab, string>> = {
  rules: "Loaded",
  "agents-md": "Loaded",
  roles: "Size",
  subagents: "Description",
  workflows: "Size",
  templates: "Size",
  hooks: "Events",
};

/**
 * The one fact a list row shows beyond path and title, the one a reader
 * scans the tab for: when a rule or agents file loads and what it costs, a
 * subagent's description, the events that run a hook, else the size. Null
 * when the row has nothing to say, such as before its kind list loads. The
 * hook events come from the delivered settings, since a hook script does not
 * name its own event.
 */
export function rowField(
  tab: RuleTab,
  { file, summary }: RuleTabRow,
  hookEvents: readonly string[]
): string | null {
  const bytes = summary?.sizeBytes ?? file.size;
  const size = bytes === null ? null : formatBytes(bytes);
  const alwaysOn = size === null ? "Always on" : `Always on, ${size}`;
  switch (tab) {
    case "rules": {
      const flag = summary?.alwaysOn ?? file.alwaysOn;
      if (flag === true) return alwaysOn;
      if (flag === false && summary !== null) return `Applies to ${summary.paths.join(", ")}`;
      return size;
    }
    case "agents-md":
      return alwaysOn;
    case "subagents":
      return summary?.description ?? null;
    case "hooks":
      return hookEvents.length === 0 ? "Not registered" : hookEvents.join(", ");
    case "roles":
    case "workflows":
    case "templates":
      return size;
  }
}

/**
 * The addresses of the Rules list and of one file's detail page. A file is
 * addressed by its repository path, not by its kind row, because a change
 * copies every file into new rows: the path stays the same while the change
 * starts, saves and is approved. The scope, release and inherited setting
 * travel with every address, so the target shows the same files.
 */

import { mountTabStart, type MountRef } from "@/features/scope/components/mount-paths";
import {
  RULE_TAB_IDS,
  RULE_TABS,
  ruleTabOf,
  type RuleTab,
  type ScopeFile,
} from "@/features/scope/components/scope-files";

/** The query parameters that keep the scope, release and inherited setting. */
const KEPT_PARAMS = ["scope", "release", "inherited"] as const;

/** The tab the list opens without a `tab` parameter. */
const DEFAULT_TAB: RuleTab = "rules";

/** The Rules tab a route segment names, or null for any other segment. */
export function kindOfSegment(segment: string): RuleTab | null {
  return RULE_TAB_IDS.find((tab: RuleTab) => tab === segment) ?? null;
}

/** A page's address with the scope, release and inherited setting of the current one. */
export function withKept(
  page: string,
  current: URLSearchParams,
  own: [string, string][] = []
): string {
  const query = new URLSearchParams();
  for (const key of KEPT_PARAMS) {
    const value = current.get(key);
    if (value !== null) query.set(key, value);
  }
  for (const [key, value] of own) query.set(key, value);
  const text = query.toString();
  return text === "" ? page : `${page}?${text}`;
}

/** The detail page of a file in a tab; the path is one URL segment. */
export function ruleDetailHref(tab: RuleTab, path: string, current: URLSearchParams): string {
  return withKept(`/rules/${tab}/${encodeURIComponent(path)}`, current);
}

/** The page that starts a new file in a tab. */
export function ruleNewHref(tab: RuleTab, current: URLSearchParams): string {
  return withKept(`/rules/${tab}/new`, current);
}

/** The Rules list at a tab. */
export function rulesListHref(tab: RuleTab, current: URLSearchParams): string {
  return withKept("/rules", current, tab === DEFAULT_TAB ? [] : [["tab", tab]]);
}

/**
 * Where an older `/rules?file=<path>` link now leads: the file's detail page,
 * in the tab its path fits or else the link's `tab`, with every other
 * parameter kept; the list without `file` when neither names a tab. Null
 * when the link names no file.
 */
export function legacyFileRedirect(query: URLSearchParams): string | null {
  const file = query.get("file");
  if (file === null) return null;
  const tab = ruleTabOf(file) ?? kindOfSegment(query.get("tab") ?? "");
  const rest = new URLSearchParams(query);
  rest.delete("file");
  if (tab === null) {
    const text = rest.toString();
    return text === "" ? "/rules" : `/rules?${text}`;
  }
  rest.delete("tab");
  const text = rest.toString();
  const page = `/rules/${tab}/${encodeURIComponent(file)}`;
  return text === "" ? page : `${page}?${text}`;
}

/**
 * Where a new file of a tab starts, and whether it starts executable: hook
 * scripts run, so they do. In a pack mounted in a folder a file starts in
 * the pack's namespace, and null means the tab holds nothing a mounted pack
 * may add (hooks).
 */
export function newFileStart(
  tab: RuleTab,
  mount: MountRef | null
): { path: string; executable: boolean } | null {
  if (mount !== null) {
    const path = mountTabStart(tab, mount);
    return path === null ? null : { path, executable: false };
  }
  if (tab === "agents-md") return { path: "AGENTS.md", executable: false };
  const prefix = RULE_TABS.find((entry) => entry.id === tab)?.prefixes[0] ?? "";
  return { path: prefix, executable: tab === "hooks" };
}

/**
 * A list of rule files with a detail page per file: the Rules tabs, and lists
 * that show a part of one tab, such as the guardrails.
 */
export type RuleSection = {
  /** The tab whose kind list describes the files. */
  tab: RuleTab;
  title: string;
  description: string;
  /** The back link's text, such as "Back to Rules". */
  back: string;
  /** The heading of the page that starts a new file. */
  newTitle: string;
  /** The files the list shows, of every file the scope delivers. */
  files: (files: readonly ScopeFile[]) => readonly ScopeFile[];
  listHref: (current: URLSearchParams) => string;
  detailHref: (path: string, current: URLSearchParams) => string;
  /** Where a file shows after a save, which may move it out of the list. */
  savedHref: (path: string, current: URLSearchParams) => string;
  newStart: (mount: MountRef | null) => { path: string; executable: boolean } | null;
  /** Why a pack mounted in a folder cannot add a file here. */
  mountedRefusal: string;
};

/**
 * What each Rules tab creates: the text of its create button and the heading
 * of the page that button opens, so both name the same thing.
 */
export const RULE_NEW_LABELS: Readonly<Record<RuleTab, string>> = {
  rules: "New rule",
  "agents-md": "New AGENTS.md",
  roles: "New role",
  subagents: "New subagent",
  workflows: "New workflow",
  templates: "New template",
  hooks: "New hook",
};

/** A Rules tab as a section; every file of the scope is offered to the tab. */
export function rulesSection(tab: RuleTab): RuleSection {
  return {
    tab,
    title: "Rules",
    description: "The rules and agent files a repository's release or a pack delivers.",
    back: "Back to Rules",
    newTitle: RULE_NEW_LABELS[tab],
    files: (files: readonly ScopeFile[]) => files,
    listHref: (current: URLSearchParams) => rulesListHref(tab, current),
    detailHref: (path: string, current: URLSearchParams) => ruleDetailHref(tab, path, current),
    savedHref: (path: string, current: URLSearchParams) =>
      ruleDetailHref(ruleTabOf(path) ?? tab, path, current),
    newStart: (mount: MountRef | null) => newFileStart(tab, mount),
    mountedRefusal:
      "A pack mounted in a folder cannot add hooks: they run on every developer's machine, so they stay with the packs at the repository root.",
  };
}

/**
 * The repository path a route segment names. The path is one segment, its
 * slashes encoded, and the segment arrives still encoded, so it is decoded
 * once here; null for a segment that is no valid encoding or names nothing.
 */
export function pathOfSegment(segment: string): string | null {
  try {
    const path = decodeURIComponent(segment);
    return path === "" ? null : path;
  } catch (failure: unknown) {
    if (failure instanceof URIError) return null;
    throw failure;
  }
}

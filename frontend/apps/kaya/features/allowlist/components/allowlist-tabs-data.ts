import { scopeValue, type Scope } from "@/features/scope/components/scope-data";
import type { ScopeFile } from "@/features/scope/components/scope-files";

/**
 * The tabs of the allowlist, in order: the rules and hooks the settings file
 * sets, then the file itself as every document page shows one.
 */
export const ALLOWLIST_TABS = ["rules", "hooks", "content", "links", "history", "checks"] as const;
export type AllowlistTab = (typeof ALLOWLIST_TABS)[number];

/**
 * The tabs a link may name: every tab, and "settings", the older name of
 * Content that existing links still carry.
 */
export const ALLOWLIST_LINK_TABS = [...ALLOWLIST_TABS, "settings"] as const;
export type AllowlistLinkTab = (typeof ALLOWLIST_LINK_TABS)[number];

/** The tabs that read the settings file itself; a scope without the file has nothing to show there. */
const FILE_TABS: ReadonlySet<AllowlistTab> = new Set(["content", "links", "history"]);

/** The tabs shown for a scope with or without a settings file. */
export function allowlistTabsOf(hasFile: boolean): AllowlistTab[] {
  return ALLOWLIST_TABS.filter((tab: AllowlistTab) => hasFile || !FILE_TABS.has(tab));
}

/** The tab the page opens: the one the URL names when it is shown, else Rules. */
export function shownAllowlistTab(
  tab: AllowlistLinkTab,
  tabs: readonly AllowlistTab[]
): AllowlistTab {
  const named: AllowlistTab = tab === "settings" ? "content" : tab;
  return tabs.includes(named) ? named : "rules";
}

/**
 * The tab to open when the member picks `value`, or null to stay: the open
 * tab's editor closes on the switch, so unsaved text there asks first.
 */
export function allowlistTabAfter(value: string, confirmLeave: () => boolean): AllowlistTab | null {
  const next = ALLOWLIST_TABS.find((id: AllowlistTab) => id === value);
  if (!next || !confirmLeave()) return null;
  return next;
}

/**
 * Whether the viewer may edit the settings file: only the pack an edit
 * changes, only its own file (or none yet, which the first rule creates),
 * never a link and never while an older version is shown.
 */
export function mayEditSettings(
  file: ScopeFile | null,
  targetId: string | null,
  at: string | null
): boolean {
  if (targetId === null || at !== null) return false;
  return file === null || (file.ownerId === targetId && file.mode !== "SYMLINK");
}

/** The Rules page on its Hooks tab for the same scope, where the hook scripts live. */
export function hookScriptsHref(scope: Scope): string {
  return `/rules?scope=${encodeURIComponent(scopeValue(scope))}&tab=hooks`;
}

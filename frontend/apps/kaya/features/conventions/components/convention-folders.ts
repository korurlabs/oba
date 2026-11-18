/**
 * The folder rows of the Conventions list: one row per folder that holds
 * docs, itself or below it, with the number of docs it holds, and which rows
 * show for the folders that are open. The open folders live in the URL as
 * `open`, a comma separated list of folder keys, so a reload, a shared link
 * and the detail page's back link show the same folders open.
 */

import { CONVENTIONS_PREFIX } from "@/features/links/components/links";
import type { ScopeFile } from "@/features/scope/components/scope-files";

import type { ConventionGroup } from "./convention-files";

/** The query parameter that holds the open folders. */
export const OPEN_PARAM = "open";

/** The key of the docs root; a folder below it is keyed by its path below the prefix. */
export const ROOT_KEY = ".";

/**
 * A folder's key in the URL: its path below the docs prefix without the
 * trailing slash, such as `backend` or `backend/api`, and `.` for the root.
 */
export function folderKey(folder: string): string {
  if (folder === CONVENTIONS_PREFIX || !folder.startsWith(CONVENTIONS_PREFIX)) return ROOT_KEY;
  return folder.slice(CONVENTIONS_PREFIX.length).replace(/\/$/, "");
}

/** The open folder keys an `open` value names, each once, in sorted order. */
export function parseOpenFolders(value: string | null): string[] {
  if (value === null) return [];
  const keys = value
    .split(",")
    .map((key: string) => key.trim().replace(/^\/+|\/+$/g, ""))
    .filter((key: string) => key !== "");
  return [...new Set(keys)].sort();
}

/** The `open` value for a set of folder keys, sorted so equal sets give equal addresses. */
export function serializeOpenFolders(keys: readonly string[]): string {
  return [...new Set(keys)].sort().join(",");
}

/** Whether two lists of folder keys name the same folders. */
export function sameOpenFolders(left: readonly string[], right: readonly string[]): boolean {
  return serializeOpenFolders(left) === serializeOpenFolders(right);
}

/** The keys of a folder and of every folder above it below the root, so the folder shows. */
export function folderWithParents(key: string): string[] {
  if (key === ROOT_KEY) return [ROOT_KEY];
  const segments = key.split("/");
  return segments.map((_: string, at: number) => segments.slice(0, at + 1).join("/"));
}

/** The open keys with a folder and the folders above it added. */
export function openingFolder(open: readonly string[], folder: string): string[] {
  return parseOpenFolders(serializeOpenFolders([...open, ...folderWithParents(folderKey(folder))]));
}

/** The open keys with one folder opened or closed. */
export function toggledFolder(open: readonly string[], key: string): string[] {
  return open.includes(key)
    ? open.filter((entry: string) => entry !== key)
    : parseOpenFolders(serializeOpenFolders([...open, key]));
}

/** A folder row of the list. */
export type FolderRow = {
  /** The folder's repository path, ending with `/`. */
  folder: string;
  key: string;
  /** The folder's last segment with `/`, such as `api/`; the prefix for the root. */
  label: string;
  /** 0 for the root and the folders right below it, one more per level below that. */
  depth: number;
  /** The key of the folder above it in the list; null for the root and the folders right below it. */
  parentKey: string | null;
  /** The folder's own docs, in path order. */
  docs: ScopeFile[];
  /** The docs the row stands for: the root's own, any other folder's own and nested. */
  count: number;
};

function segmentsOf(key: string): string[] {
  return key === ROOT_KEY ? [] : key.split("/");
}

/** Folders in tree order: the root first, then each folder before the folders inside it. */
export function compareFolders(left: string, right: string): number {
  const a = segmentsOf(folderKey(left));
  const b = segmentsOf(folderKey(right));
  for (let at = 0; at < Math.min(a.length, b.length); at += 1) {
    const one = a[at] ?? "";
    const other = b[at] ?? "";
    if (one !== other) return one < other ? -1 : 1;
  }
  return a.length - b.length;
}

/**
 * The folder rows for the docs groups, in tree order. The root lists its
 * subfolders beside itself, not inside, so the page opens as a list of the
 * folders; a folder that holds only nested folders still gets a row.
 */
export function folderRows(groups: readonly ConventionGroup[]): FolderRow[] {
  const own = new Map(groups.map((group: ConventionGroup) => [folderKey(group.folder), group]));
  const keys = new Set<string>();
  for (const key of own.keys()) for (const entry of folderWithParents(key)) keys.add(entry);
  const rows = [...keys].map((key: string): FolderRow => {
    const segments = segmentsOf(key);
    const folder = key === ROOT_KEY ? CONVENTIONS_PREFIX : `${CONVENTIONS_PREFIX}${key}/`;
    const nested = groups
      .filter((group: ConventionGroup) => key !== ROOT_KEY && group.folder.startsWith(folder))
      .reduce((sum: number, group: ConventionGroup) => sum + group.docs.length, 0);
    const docs = own.get(key)?.docs ?? [];
    return {
      folder,
      key,
      label: key === ROOT_KEY ? CONVENTIONS_PREFIX : `${segments[segments.length - 1]}/`,
      depth: Math.max(segments.length - 1, 0),
      parentKey: segments.length > 1 ? segments.slice(0, -1).join("/") : null,
      docs,
      count: key === ROOT_KEY ? docs.length : nested,
    };
  });
  return rows.sort((left: FolderRow, right: FolderRow) =>
    compareFolders(left.folder, right.folder)
  );
}

/** A row the list shows: a folder's header row or one of its docs. */
export type VisibleRow =
  | { kind: "folder"; row: FolderRow; open: boolean }
  | { kind: "doc"; row: FolderRow; file: ScopeFile };

/**
 * The rows that show: a folder row while every folder above it is open, and
 * a folder's docs right below it while it is open, before its subfolders.
 */
export function visibleRows(rows: readonly FolderRow[], open: readonly string[]): VisibleRow[] {
  const opened = new Set(open);
  const shown = new Set<string>();
  const visible: VisibleRow[] = [];
  for (const row of rows) {
    if (row.parentKey !== null && !(shown.has(row.parentKey) && opened.has(row.parentKey))) {
      continue;
    }
    shown.add(row.key);
    const isOpen = opened.has(row.key);
    visible.push({ kind: "folder", row, open: isOpen });
    if (isOpen) {
      for (const file of row.docs) visible.push({ kind: "doc", row, file });
    }
  }
  return visible;
}

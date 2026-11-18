/**
 * The convention docs of a scope: every delivered file under
 * `docs/private/conventions/`, grouped by folder, folders and files in path
 * order.
 */

import type { ScopeFile } from "@/features/scope/components/scope-files";
import type { ConventionListResponse } from "../schemas";
import { CONVENTIONS_PREFIX } from "@/features/links/components/links";
import { mountDocsFolder, type MountRef } from "@/features/scope/components/mount-paths";
import { compareFolders } from "./convention-folders";

/** The docs of one folder, the folder's repository path ending with `/`. */
export type ConventionGroup = {
  folder: string;
  /** The folder below the docs prefix, such as `backend/`; the prefix itself for its own docs. */
  label: string;
  docs: ScopeFile[];
};

/** The scope's files under the docs prefix, in path order. */
export function conventionFiles(files: readonly ScopeFile[]): ScopeFile[] {
  return files
    .filter((file: ScopeFile) => file.path.startsWith(CONVENTIONS_PREFIX))
    .sort((left: ScopeFile, right: ScopeFile) =>
      left.path < right.path ? -1 : left.path > right.path ? 1 : 0
    );
}

/**
 * The docs grouped by folder, each folder holding only its own docs (a
 * nested folder is a group of its own), each folder before the folders inside
 * it and docs in path order, so the list reads like the folder tree of the
 * signed manifest.
 */
export function conventionGroups(files: readonly ScopeFile[]): ConventionGroup[] {
  const groups: ConventionGroup[] = [];
  for (const file of conventionFiles(files)) {
    const folder = folderOf(file.path);
    let group = groups.find((entry: ConventionGroup) => entry.folder === folder);
    if (!group) {
      const label =
        folder === CONVENTIONS_PREFIX
          ? CONVENTIONS_PREFIX
          : folder.slice(CONVENTIONS_PREFIX.length);
      group = { folder, label, docs: [] };
      groups.push(group);
    }
    group.docs.push(file);
  }
  return groups.sort((left: ConventionGroup, right: ConventionGroup) =>
    compareFolders(left.folder, right.folder)
  );
}

/** The folder of a doc, where "New file" starts; the docs root when no doc is open. */
export function folderOf(path: string | null): string {
  if (path === null || !path.startsWith(CONVENTIONS_PREFIX)) return CONVENTIONS_PREFIX;
  return path.slice(0, path.lastIndexOf("/") + 1);
}

/**
 * Where "New file" starts: the chosen folder while the scope still delivers
 * docs in it, else the open doc's folder.
 */
export function newFileFolder(
  selectedFolder: string | null,
  openPath: string | null,
  docs: readonly ScopeFile[]
): string {
  if (
    selectedFolder !== null &&
    selectedFolder.startsWith(CONVENTIONS_PREFIX) &&
    docs.some((file: ScopeFile) => file.path.startsWith(selectedFolder))
  ) {
    return selectedFolder;
  }
  return folderOf(openPath);
}

/**
 * Where "New file" starts in a draft of a pack mounted in a folder: the
 * folder `newFileFolder` picked while it lies in the pack's docs, else the
 * pack's docs folder, since a mounted pack may add docs only there.
 */
export function mountNewFileFolder(folder: string, mount: MountRef | null): string {
  if (mount === null) return folder;
  const own = mountDocsFolder(mount);
  return folder.startsWith(own) ? folder : own;
}

/** The versions whose convention list the docs need: those that hold them. */
export function conventionVersionIds(docs: readonly ScopeFile[]): string[] {
  return [...new Set(docs.map((file: ScopeFile) => file.versionId))];
}

/**
 * The frontmatter title of each delivered doc, by path, from the server's
 * convention list. The list also names docs a mounted pack overrides, so a row
 * counts only for the version that delivers the path.
 */
export function conventionTitles(
  docs: readonly ScopeFile[],
  rows: readonly ConventionListResponse[]
): Map<string, string> {
  const delivered = new Map(docs.map((file: ScopeFile) => [file.path, file.versionId]));
  const titles = new Map<string, string>();
  for (const row of rows) {
    if (!row.path || !row.title) continue;
    const path = `${CONVENTIONS_PREFIX}${row.path}`;
    if (delivered.get(path) === row.versionId) titles.set(path, row.title);
  }
  return titles;
}

/**
 * The rows of the skills table: one per `SKILL.md` a scope delivers, named
 * and described by the server's skill list of the versions that hold them,
 * or, when that list cannot cover them, by the files read and parsed here.
 */

import {
  skillsFromFiles,
  type ScopeFile,
  type SkillLocation,
} from "@/features/scope/components/scope-files";
import type { Owner } from "@/features/scope/schemas";
import {
  frontmatterList,
  frontmatterText,
  parseFrontmatter,
} from "@/features/versions/components/file-parsers";

import type { SkillListResponse } from "../schemas";

export type SkillRow = {
  name: string;
  /** Null when the frontmatter has none; undefined while its `SKILL.md` loads. */
  description: string | null | undefined;
  /** Such as `.agents/skills/kaizen`; unique within a scope. */
  directory: string;
  /** The name of the pack that delivers the skill. */
  originName: string;
  /** The pack that delivers the skill, the one an edit changes. */
  originOwnerId: string;
  /** The frontmatter `paths`; null when unknown or unset. */
  paths: string[] | null;
  fileCount: number;
  /** Why the `SKILL.md` could not be read; null when it was. */
  error: string | null;
};

type OwnerOf = (ownerId: string) => Owner | undefined;

function originOf(location: SkillLocation, ownerOf: OwnerOf) {
  const owner = ownerOf(location.skillFile.ownerId);
  return {
    originName: owner?.name ?? "",
    originOwnerId: location.skillFile.ownerId,
  };
}

/**
 * The rows of every skill the files hold, named from the server's list of
 * the versions that hold their `SKILL.md`. A skill the list does not name has
 * no frontmatter the server could read, so it keeps its directory name.
 */
export function serverSkillRows(
  files: readonly ScopeFile[],
  ownerOf: OwnerOf,
  skills: readonly SkillListResponse[]
): SkillRow[] {
  return skillsFromFiles(files)
    .map((location: SkillLocation) => {
      const skill = skills.find(
        (entry: SkillListResponse) =>
          entry.versionId === location.skillFile.versionId && entry.path === location.directoryName
      );
      return {
        name: skill?.name ?? location.directoryName,
        description: skill ? (skill.description ?? null) : null,
        directory: location.directory,
        ...originOf(location, ownerOf),
        paths: null,
        fileCount: location.fileCount,
        error: null,
      };
    })
    .sort(byName);
}

/** What a `SKILL.md` read gave: its text, an error, or nothing yet. */
export type SkillFileRead = { content: string } | { error: string } | null;

/**
 * The rows of every skill the files hold, each named by its `SKILL.md`
 * frontmatter read here, for a scope whose versions are too many for one
 * server list.
 */
export function fileSkillRows(
  files: readonly ScopeFile[],
  ownerOf: OwnerOf,
  readOf: (skillFile: ScopeFile) => SkillFileRead
): SkillRow[] {
  return skillsFromFiles(files)
    .map((location: SkillLocation) => {
      const read = readOf(location.skillFile);
      const frontmatter = read && "content" in read ? parseFrontmatter(read.content) : {};
      return {
        name: frontmatterText(frontmatter, "name") ?? location.directoryName,
        description:
          read === null
            ? undefined
            : "error" in read
              ? null
              : frontmatterText(frontmatter, "description"),
        directory: location.directory,
        ...originOf(location, ownerOf),
        paths: frontmatterList(frontmatter, "paths"),
        fileCount: location.fileCount,
        error: read && "error" in read ? read.error : null,
      };
    })
    .sort(byName);
}

function byName(left: SkillRow, right: SkillRow): number {
  return left.name.localeCompare(right.name) || left.directory.localeCompare(right.directory);
}

/**
 * The frontmatter `paths` of a skill: the row's own when it has them, else
 * those of its `SKILL.md` content once read; null while unread or unset.
 */
export function skillPaths(
  row: Pick<SkillRow, "paths">,
  skillFileContent: string | null | undefined
): string[] | null {
  if (row.paths !== null) return row.paths;
  return typeof skillFileContent === "string"
    ? frontmatterList(parseFrontmatter(skillFileContent), "paths")
    : null;
}

/** Rows whose name, description or origin hold every word of the search, in any case. */
export function filterSkillRows(rows: readonly SkillRow[], search: string): SkillRow[] {
  const terms = search.toLowerCase().split(/\s+/).filter(Boolean);
  return rows.filter((row: SkillRow) => {
    const haystack = `${row.name} ${row.description ?? ""} ${row.originName}`.toLowerCase();
    return terms.every((term: string) => haystack.includes(term));
  });
}

/** The origin badge of a skill: the pack that delivers it. */
export function skillOrigin(row: Pick<SkillRow, "originName">): {
  text: string;
  variant: "info";
} {
  return { text: `Pack ${row.originName}`, variant: "info" };
}

/** The first sentence of a description, for the table cell. */
export function firstSentence(text: string): string {
  const found = /^[\s\S]*?[.!?](\s|$)/.exec(text);
  return (found?.[0] ?? text).trim();
}

/** The file of a skill a page shows: the one `path` names, else the first, its `SKILL.md`. */
export function selectedSkillFile(
  own: readonly ScopeFile[],
  path: string | null
): ScopeFile | null {
  return own.find((file: ScopeFile) => file.path === path) ?? own[0] ?? null;
}

/** A skill's file named within the skill's folder, such as `SKILL.md`. */
export function skillFileLabel(directory: string, path: string): string {
  return path.startsWith(`${directory}/`) ? path.slice(directory.length + 1) : path;
}

/** Whether a skill's page offers a choice of file: only a skill with more than one has one. */
export function hasFileChoice(own: readonly ScopeFile[]): boolean {
  return own.length > 1;
}

/** One file of a skill in the file list: its full path and its name within its folder. */
export type SkillFileEntry = { path: string; name: string };

/** The files of one folder of a skill; the folder is null for the skill's own root. */
export type SkillFileGroup = { folder: string | null; files: SkillFileEntry[] };

/**
 * The files of a skill grouped by their folder within it: the root first, its
 * `SKILL.md` leading, then every folder in alphabetical order, each holding
 * its files by name in alphabetical order.
 */
export function groupSkillFiles(directory: string, own: readonly ScopeFile[]): SkillFileGroup[] {
  const groups = new Map<string | null, SkillFileEntry[]>();
  for (const file of own) {
    const label = skillFileLabel(directory, file.path);
    const slash = label.lastIndexOf("/");
    const folder = slash === -1 ? null : label.slice(0, slash);
    const entries = groups.get(folder) ?? [];
    entries.push({ path: file.path, name: label.slice(slash + 1) });
    groups.set(folder, entries);
  }
  const leads = (folder: string | null, entry: SkillFileEntry) =>
    Number(folder === null && entry.name === "SKILL.md");
  return [...groups.entries()]
    .map(([folder, files]: [string | null, SkillFileEntry[]]) => ({
      folder,
      files: files.sort(
        (left: SkillFileEntry, right: SkillFileEntry) =>
          leads(folder, right) - leads(folder, left) || left.name.localeCompare(right.name)
      ),
    }))
    .sort(
      (left: SkillFileGroup, right: SkillFileGroup) =>
        Number(left.folder !== null) - Number(right.folder !== null) ||
        (left.folder ?? "").localeCompare(right.folder ?? "")
    );
}

/**
 * The groups with only the files whose path within the skill holds every word
 * of the search, in any case; a group left without files is dropped.
 */
export function filterSkillFileGroups(
  groups: readonly SkillFileGroup[],
  search: string
): SkillFileGroup[] {
  const terms = search.toLowerCase().split(/\s+/).filter(Boolean);
  return groups
    .map((group: SkillFileGroup) => ({
      folder: group.folder,
      files: group.files.filter((entry: SkillFileEntry) => {
        const label = (
          group.folder === null ? entry.name : `${group.folder}/${entry.name}`
        ).toLowerCase();
        return terms.every((term: string) => label.includes(term));
      }),
    }))
    .filter((group: SkillFileGroup) => group.files.length > 0);
}

/**
 * The label of the button that opens the file list: the count while the
 * skill's `SKILL.md` is open, else the open file's path within the skill.
 */
export function fileListLabel(
  directory: string,
  own: readonly ScopeFile[],
  selected: ScopeFile | null
): string {
  return selected === null || selected.path === own[0]?.path
    ? `Files (${own.length})`
    : skillFileLabel(directory, selected.path);
}

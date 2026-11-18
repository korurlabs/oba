/**
 * The files of a scope in one shape, whether they come from a repository
 * release or a pack version, and how the pages pick theirs: skills by their
 * `SKILL.md`, rules and agent files by tab.
 */

import type { SettingsHookResponse } from "@/features/allowlist/schemas";
import type { ReleaseDetailResponse, ReleaseEntry } from "@/features/releases/schemas";
import {
  type FileMode,
  type VersionDetailResponse,
  type VersionFileResponse,
} from "@/features/versions/schemas";

import type { Owner } from "../schemas";

export type ScopeFile = {
  path: string;
  mode: FileMode;
  sha256: string | null;
  size: number | null;
  /** The relative target of a symlink. */
  target: string | null;
  /** Whether Claude Code loads it at every start; null when only a release can tell (a pack). */
  alwaysOn: boolean | null;
  /** The version whose file read returns the content. */
  versionId: string;
  /** The pack that delivers the path. */
  ownerId: string;
  /** A mounted pack's override marker: the root path it replaces in the mount folder. */
  overridesPath?: string | null;
  /** The mounted pack that narrowed this root rule; only a release tells. */
  narrowedBy?: string | null;
};

export function filesFromRelease(release: ReleaseDetailResponse): ScopeFile[] {
  return release.files.map((entry: ReleaseEntry) => ({
    path: entry.path,
    mode: entry.mode,
    sha256: entry.sha256 ?? null,
    size: entry.size ?? null,
    target: entry.target ?? null,
    alwaysOn: entry.alwaysOn,
    versionId: entry.versionId,
    ownerId: entry.ownerId,
    overridesPath: entry.overridesPath ?? null,
    narrowedBy: entry.narrowedBy ?? null,
  }));
}

export function filesFromVersion(version: VersionDetailResponse): ScopeFile[] {
  const ownerId = version.packId;
  return version.files.map((file: VersionFileResponse) => ({
    path: file.path,
    mode: file.mode,
    sha256: file.sha256 ?? null,
    size: file.size ?? null,
    target: file.symlinkTarget ?? null,
    alwaysOn: null,
    versionId: version.id,
    ownerId,
    overridesPath: file.overridesPath ?? null,
    narrowedBy: null,
  }));
}

/** Code point order, the order of the signed manifest. */
function byPath(left: ScopeFile, right: ScopeFile): number {
  return left.path < right.path ? -1 : left.path > right.path ? 1 : 0;
}

/**
 * A scope's files with a draft in place of its owner's files: what the scope
 * would deliver once the draft is approved. A draft file unchanged from the
 * shown release keeps the release's always-on flag.
 */
export function composeDraftFiles(
  files: readonly ScopeFile[],
  ownerId: string,
  draft: VersionDetailResponse
): ScopeFile[] {
  const flags = new Map(
    files
      .filter((file: ScopeFile) => file.ownerId === ownerId)
      .map((file: ScopeFile) => [`${file.path}\u0000${file.sha256 ?? ""}`, file.alwaysOn] as const)
  );
  const own = filesFromVersion(draft).map((file: ScopeFile) => ({
    ...file,
    alwaysOn: flags.get(`${file.path}\u0000${file.sha256 ?? ""}`) ?? null,
  }));
  return [...files.filter((file: ScopeFile) => file.ownerId !== ownerId), ...own].sort(byPath);
}

// ── Skills ──────────────────────────────────────────────────────────
const SKILL_FILE = /^\.(agents|claude)\/skills\/([^/]+)\/SKILL\.md$/;

export type SkillLocation = {
  /** The directory name, the skill's name when its frontmatter gives none. */
  directoryName: string;
  /** Such as `.agents/skills/kazan`. */
  directory: string;
  skillFile: ScopeFile;
  fileCount: number;
};

/** The skill directory of a `SKILL.md` path, or null when the path is no skill file. */
export function skillDirectoryOf(path: string): string | null {
  return SKILL_FILE.test(path) ? path.slice(0, -"/SKILL.md".length) : null;
}

/** The files of a skill's directory, its `SKILL.md` first. */
export function skillFiles(files: readonly ScopeFile[], directory: string): ScopeFile[] {
  const prefix = `${directory}/`;
  const inside = files.filter((file: ScopeFile) => file.path.startsWith(prefix));
  const skillFile = `${prefix}SKILL.md`;
  return [
    ...inside.filter((file: ScopeFile) => file.path === skillFile),
    ...inside.filter((file: ScopeFile) => file.path !== skillFile),
  ];
}

/** One skill per `SKILL.md` that is not a symlink, as the server lists a release's skills. */
export function skillsFromFiles(files: readonly ScopeFile[]): SkillLocation[] {
  return files
    .filter((file: ScopeFile) => file.mode !== "SYMLINK" && SKILL_FILE.test(file.path))
    .map((file: ScopeFile) => {
      const directory = skillDirectoryOf(file.path) as string;
      return {
        directoryName: directory.split("/").pop() ?? directory,
        directory,
        skillFile: file,
        fileCount: skillFiles(files, directory).length,
      };
    });
}

// ── Rules ───────────────────────────────────────────────────────────
export const RULE_TABS = [
  { id: "rules", label: "Rules", prefixes: [".agents/rules/"] },
  { id: "agents-md", label: "AGENTS.md", prefixes: [] },
  { id: "roles", label: "Roles", prefixes: [".agents/roles/"] },
  { id: "subagents", label: "Subagents", prefixes: [".claude/agents/"] },
  { id: "workflows", label: "Workflows", prefixes: [".agents/workflows/"] },
  { id: "templates", label: "Templates", prefixes: [".agents/templates/"] },
  { id: "hooks", label: "Hooks", prefixes: [".agents/hooks/"] },
] as const;

export type RuleTab = (typeof RULE_TABS)[number]["id"];
export const RULE_TAB_IDS: RuleTab[] = RULE_TABS.map((tab) => tab.id);

const AGENTS_FILES: ReadonlySet<string> = new Set(["AGENTS.md", ".agents/AGENTS.md"]);
/** A mounted pack's `<folder>/AGENTS.md` as a release delivers it. */
const FOLDER_AGENTS_FILE = /^([A-Za-z0-9_][A-Za-z0-9._-]*\/){1,4}AGENTS\.md$/;

/** Whether a Rules tab lists the file at a path. */
function fitsTab(path: string, tab: RuleTab): boolean {
  if (tab === "agents-md") return AGENTS_FILES.has(path) || FOLDER_AGENTS_FILE.test(path);
  const prefixes: readonly string[] = RULE_TABS.find((entry) => entry.id === tab)?.prefixes ?? [];
  return prefixes.some((prefix: string) => path.startsWith(prefix));
}

/** The files a Rules tab lists, in the scope's path order. */
export function filesForTab(files: readonly ScopeFile[], tab: RuleTab): ScopeFile[] {
  return files.filter((file: ScopeFile) => fitsTab(file.path, tab));
}

/** The Rules tab that lists a path, or null when no tab does. */
export function ruleTabOf(path: string): RuleTab | null {
  return RULE_TAB_IDS.find((tab: RuleTab) => fitsTab(path, tab)) ?? null;
}

// ── Origins ─────────────────────────────────────────────────────────

/**
 * What a file's override markers say about it: a file of a mounted pack that
 * replaces a root file in the mount folder, or a root rule narrowed for a
 * mounted pack. Every other file has no origin badge.
 */
export type FileOrigin =
  | { kind: "OVERRIDES"; label: string; target: string }
  | { kind: "NARROWED"; label: string };

/** The origin of each marked file of a scope, by path; unmarked files are left out. */
export function fileOrigins(
  files: readonly ScopeFile[],
  owners: readonly Owner[]
): Map<string, FileOrigin> {
  return new Map(
    files.flatMap((file: ScopeFile): [string, FileOrigin][] => {
      if (file.overridesPath) {
        const target = file.overridesPath;
        return [[file.path, { kind: "OVERRIDES", label: `Overrides ${target}`, target }]];
      }
      if (file.narrowedBy) {
        const by = owners.find((owner: Owner) => owner.id === file.narrowedBy);
        return [[file.path, { kind: "NARROWED", label: `Narrowed for ${by?.name ?? "a mounted pack"}` }]];
      }
      return [];
    })
  );
}

// ── Guards ──────────────────────────────────────────────────────────
const GUARD_FILE = /^\.agents\/hooks\/(pre-[^/]+?)(\.[a-z]+)?$/;
const DISPATCHER = ".agents/hooks/dispatch.sh";

/**
 * The hook events that run a script: those of the delivered settings' hooks
 * whose command names the script, and PreToolUse for a guard script when a
 * PreToolUse hook runs the dispatcher, which runs the guards. Empty when the
 * settings register the script nowhere.
 */
export function hookEventsOf(
  file: ScopeFile,
  hooks: readonly SettingsHookResponse[]
): string[] {
  const events = new Set<string>();
  const guard = GUARD_FILE.test(file.path);
  for (const hook of hooks) {
    if (!hook.command) continue;
    if (hook.command.includes(file.path)) events.add(hook.event);
    else if (guard && hook.event === "PreToolUse" && hook.command.includes(DISPATCHER)) {
      events.add(hook.event);
    }
  }
  return [...events];
}

// ── Display ─────────────────────────────────────────────────────────
export function formatBytes(bytes: number): string {
  return `${bytes.toLocaleString("en-US")} bytes`;
}

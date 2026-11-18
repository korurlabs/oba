/**
 * The checks the editors run while a member types, mirroring the server's
 * file rules (`PathRules`, `FileRules`) so a save is not sent
 * only to be refused. The server stays the authority: it also checks what
 * only a whole composition shows (two owners giving one path, dangling links,
 * the dispatcher registration).
 */

import type { FileMode } from "../schemas";
import { parseFrontmatter, frontmatterText } from "./file-parsers";
import {
  isInMountNamespace,
  mountNamespaceMessage,
  mountRuleRefusal,
  type MountRef,
} from "@/features/scope/components/mount-paths";

export const FILE_LIMITS = {
  pathMax: 500,
  /** 5 MiB. */
  fileBytesMax: 5_242_880,
  /** A `SKILL.md` must stay under this many lines. */
  skillLinesMax: 1500,
  /** `description` plus `when_to_use`, in code points. */
  skillListingMax: 1536,
} as const;

const ALLOWED_PATH =
  /^(AGENTS\.md|\.agents\/AGENTS\.md|\.claude\/settings\.json|\.agents\/(rules|skills|roles|workflows|templates|hooks)\/.+|\.claude\/(agents|skills)\/.+|docs\/private\/conventions\/.+\.md|([A-Za-z0-9_][A-Za-z0-9._-]*\/){1,4}AGENTS\.md)$/;
const SKILL_FILE = /^\.(agents|claude)\/skills\/([^/]+)\/SKILL\.md$/;

export const SETTINGS_PATH = ".claude/settings.json";
const HOOKS_PREFIX = ".agents/hooks/";

/** The frontmatter keys a skill may use, as the server lists them. */
export const SKILL_FRONTMATTER_KEYS: ReadonlySet<string> = new Set([
  "name",
  "description",
  "when_to_use",
  "argument-hint",
  "arguments",
  "disable-model-invocation",
  "user-invocable",
  "allowed-tools",
  "model",
  "effort",
  "context",
  "agent",
  "hooks",
  "paths",
  "license",
  "compatibility",
  "metadata",
  "version",
  "globs",
  "alwaysApply",
  "user_invocable",
]);

/**
 * Whether Kaya delivers a path: on the allow-list (the same expression as
 * the server's `PathRules.ALLOWED_PATH`, whose last alternative is a
 * mounted pack's `<folder>/AGENTS.md` of at most four folders), at most 500 characters,
 * relative, without an empty, "." or ".." segment and without a backslash.
 */
export function isAllowedPath(path: string): boolean {
  if (path === "" || path.length > FILE_LIMITS.pathMax || path.includes("\\")) return false;
  if (!ALLOWED_PATH.test(path)) return false;
  return path
    .split("/")
    .every((segment: string) => segment !== "" && segment !== "." && segment !== "..");
}

export function isSkillFile(path: string): boolean {
  return SKILL_FILE.test(path);
}

export function isHookPath(path: string): boolean {
  return path.startsWith(HOOKS_PREFIX);
}

/** Bytes of a text once encoded as UTF-8, as the server stores it. */
export function textBytes(text: string): number {
  return new TextEncoder().encode(text).length;
}

/** Lines as an editor counts them: a final newline does not start another line. */
export function lineCount(text: string): number {
  if (text === "") return 0;
  const newlines = text.split("\n").length - 1;
  return text.endsWith("\n") ? newlines : newlines + 1;
}

function codePoints(text: string | null): number {
  return text === null ? 0 : [...text].length;
}

/** The length the skill listing shows: `description` plus `when_to_use`, in code points. */
export function listingLength(text: string): number {
  const frontmatter = parseFrontmatter(text);
  return (
    codePoints(frontmatterText(frontmatter, "description")) +
    codePoints(frontmatterText(frontmatter, "when_to_use"))
  );
}

/** Whether the text opens a frontmatter block that no `---` line closes. */
function frontmatterNotClosed(text: string): boolean {
  const lines = text.replace(/^﻿/, "").split(/\r?\n/);
  if ((lines[0] ?? "").trim() !== "---") return false;
  return !lines.slice(1).some((line: string) => line.trim() === "---");
}

/** The top-level keys of a frontmatter block, in order. */
function frontmatterKeys(text: string): string[] {
  return Object.keys(parseFrontmatter(text.replace(/^﻿/, "")));
}

export type FileIssue = { rule: string; message: string };

/** The measures of the base file at the same path, for the size waiver; null when unknown. */
export type BaseMeasure = { bytes: number; lines: number | null; listing: number | null };

export function measureOf(text: string): BaseMeasure {
  return { bytes: textBytes(text), lines: lineCount(text), listing: listingLength(text) };
}

/**
 * Whether a size limit refuses the file. A file the base already held over
 * the same limit may be edited as long as its measure does not grow, as the
 * server waives it; an unknown base measure is given the benefit of the doubt.
 */
function overLimit(
  measure: number,
  broken: (value: number) => boolean,
  base: number | null | undefined,
  hasBase: boolean
): boolean {
  if (!broken(measure)) return false;
  if (!hasBase) return true;
  if (base === null || base === undefined) return false;
  return !(broken(base) && measure <= base);
}

export type FileCheckInput = {
  path: string;
  mode: FileMode;
  content: string;
};

/**
 * The rules one text file breaks, as a save would be refused. `base` is the
 * measure of the base version's file at the same path, null when the base
 * has no such file and undefined while it loads.
 */
export function checkFile(
  file: FileCheckInput,
  base: BaseMeasure | null | undefined,
  mount: MountRef | null = null
): FileIssue[] {
  const path = file.path.trim();
  if (!isAllowedPath(path)) {
    return [
      {
        rule: "PATH_NOT_ALLOWED",
        message:
          "Kaya delivers only AGENTS.md, .claude/settings.json, files under .agents/{rules,skills,roles,workflows,templates,hooks}/ and .claude/{agents,skills}/, Markdown files under docs/private/conventions/, and the AGENTS.md of a folder at most four levels deep, with no empty, . or .. segment.",
      },
    ];
  }
  if (mount !== null && !isInMountNamespace(path, mount.name)) {
    return [{ rule: "MOUNT_PATH_OUTSIDE_NAMESPACE", message: mountNamespaceMessage(mount) }];
  }
  const issues: FileIssue[] = [];
  const unscoped = mount === null ? null : mountRuleRefusal(path, file.content, mount);
  if (unscoped !== null) issues.push({ rule: "MOUNT_RULE_SCOPE", message: unscoped });
  const hasBase = base !== null;
  const bytes = textBytes(file.content);
  if (overLimit(bytes, (value) => value > FILE_LIMITS.fileBytesMax, base?.bytes, hasBase)) {
    issues.push({
      rule: "FILE_TOO_LARGE",
      message: `The file has ${bytes.toLocaleString("en-US")} bytes; the limit is ${FILE_LIMITS.fileBytesMax.toLocaleString("en-US")}.`,
    });
    return issues;
  }
  if (isSkillFile(path)) {
    const lines = lineCount(file.content);
    if (overLimit(lines, (value) => value >= FILE_LIMITS.skillLinesMax, base?.lines, hasBase)) {
      issues.push({
        rule: "SKILL_TOO_LONG",
        message: `The skill has ${lines} lines; it must stay under ${FILE_LIMITS.skillLinesMax}.`,
      });
    }
    if (frontmatterNotClosed(file.content)) {
      issues.push({
        rule: "FRONTMATTER_INVALID",
        message: "The frontmatter block is not closed by a line of ---.",
      });
    } else {
      for (const key of frontmatterKeys(file.content)) {
        if (!SKILL_FRONTMATTER_KEYS.has(key)) {
          issues.push({
            rule: "FRONTMATTER_KEY_UNKNOWN",
            message: `The frontmatter key ${key} is not a skill key.`,
          });
        }
      }
      const listing = listingLength(file.content);
      if (
        overLimit(listing, (value) => value > FILE_LIMITS.skillListingMax, base?.listing, hasBase)
      ) {
        issues.push({
          rule: "SKILL_DESCRIPTION_TOO_LONG",
          message: `The description and when_to_use have ${listing} characters; the limit is ${FILE_LIMITS.skillListingMax}.`,
        });
      }
    }
  }
  if (path === SETTINGS_PATH && !isJsonObject(file.content)) {
    issues.push({
      rule: "SETTINGS_INVALID_JSON",
      message: "The settings file is not a JSON object.",
    });
  }
  return issues;
}

function isJsonObject(text: string): boolean {
  try {
    const value: unknown = JSON.parse(text);
    return typeof value === "object" && value !== null && !Array.isArray(value);
  } catch (error: unknown) {
    // Invalid JSON is the answer here, not a failure: the editor lists it as an issue.
    if (error instanceof SyntaxError) return false;
    throw error;
  }
}

/**
 * The directory name of a new skill: one path segment Kaya can deliver, and
 * in a pack mounted in a folder one that starts with the pack's name and a
 * hyphen.
 */
export function skillNameIssue(
  name: string,
  taken: readonly string[],
  mountedName: string | null = null
): string | null {
  const trimmed = name.trim();
  if (trimmed === "") return "Enter the skill's name.";
  if (trimmed.includes("/") || trimmed.includes("\\") || trimmed === "." || trimmed === "..") {
    return "A skill name is one folder name, without a slash.";
  }
  if (
    mountedName !== null &&
    !(trimmed.startsWith(`${mountedName}-`) && trimmed.length > mountedName.length + 1)
  ) {
    return `A skill of pack ${mountedName}, which is mounted in a folder, is named ${mountedName}-<name>.`;
  }
  if (!isAllowedPath(`.agents/skills/${trimmed}/SKILL.md`))
    return "This name gives a path Kaya does not deliver.";
  if (taken.includes(trimmed)) return "A skill of this name exists in this scope.";
  return null;
}

/** The `SKILL.md` a new skill starts with. */
export function newSkillContent(name: string): string {
  return `---\nname: ${name.trim()}\ndescription: Say what the skill does and when to use it.\n---\n\n# ${name.trim()}\n`;
}

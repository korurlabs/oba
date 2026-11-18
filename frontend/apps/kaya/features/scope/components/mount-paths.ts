/**
 * The path rules of a pack mounted in a folder of a repository, mirroring
 * the server's `MountPaths`, so the editor names a refusal before a save or
 * a submit is sent. A mounted pack's files sit at fixed namespace paths at
 * the repository root, because Claude Code loads rules, skills and agents
 * only from the root's folders; the namespace keeps them apart from the
 * files of the packs at the root. Only its `AGENTS.md` moves, into the mount
 * folder. The server stays the authority.
 */

import type { Owner } from "../schemas";
import type { RuleTab } from "./scope-files";

/** The pack fields the mount rules read: its name and every folder it is mounted at. */
export type MountRef = { name: string; mountPaths: readonly string[] };

/** The stored path of a pack's agents file; a mount delivers it in the mount folder. */
export const AGENTS_FILE = "AGENTS.md";

const RULES_PREFIX = ".agents/rules/";
const SKILLS_PREFIX = ".agents/skills/";
const CLAUDE_SKILLS_PREFIX = ".claude/skills/";
const CLAUDE_AGENTS_PREFIX = ".claude/agents/";
const MOUNT_CONVENTIONS_PREFIX = "docs/private/conventions/projects/";
const DIRECTORY_PREFIXES = [
  ".agents/roles/",
  ".agents/workflows/",
  ".agents/templates/",
] as const;
const MARKDOWN = ".md";
const BRACE_EXPANSIONS_MAX = 64;
const GLOB_CHARACTERS = /[*?[\]{}]/;

/** A pack as the mount rules read it; null for a pack mounted nowhere, which has no namespace. */
export function mountRefOf(owner: Owner | null | undefined): MountRef | null {
  if (!owner || owner.mountPaths.length === 0) return null;
  return { name: owner.name, mountPaths: owner.mountPaths };
}

export function isMountRule(path: string, name: string): boolean {
  return path.startsWith(`${RULES_PREFIX}${name}/`) && path.endsWith(MARKDOWN);
}

export function isMountDoc(path: string, name: string): boolean {
  return path.startsWith(`${MOUNT_CONVENTIONS_PREFIX}${name}/`) && path.endsWith(MARKDOWN);
}

/**
 * Whether a stored path lies in a mounted pack's namespace. Settings, hooks
 * and guardrails are never in it, so a mounted pack can never change what
 * runs on a developer's machine.
 */
export function isInMountNamespace(path: string, name: string): boolean {
  if (path === AGENTS_FILE) return true;
  if (isMountRule(path, name) || isMountDoc(path, name)) return true;
  const rest = (prefix: string): string | null =>
    path.startsWith(`${prefix}${name}-`) ? path.slice(`${prefix}${name}-`.length) : null;
  const skill = rest(SKILLS_PREFIX);
  if (skill !== null && /^[^/]+\/.+$/.test(skill)) return true;
  const link = rest(CLAUDE_SKILLS_PREFIX);
  if (link !== null && /^[^/]+$/.test(link)) return true;
  const agent = rest(CLAUDE_AGENTS_PREFIX);
  if (agent !== null && /^[^/]+\.md$/.test(agent)) return true;
  return DIRECTORY_PREFIXES.some((prefix: string) => {
    const folder = `${prefix}${name}/`;
    return path.startsWith(folder) && path.length > folder.length;
  });
}

/**
 * The server's message for a path outside the namespace. The server names
 * the agents file in the mount folder; a draft stores it as `AGENTS.md`.
 */
export function mountNamespaceMessage(mount: MountRef): string {
  const { name } = mount;
  return `The path is outside the namespace of pack ${name}, which is mounted in a folder; its files go under .agents/rules/${name}/, .agents/skills/${name}-*, docs/private/conventions/projects/${name}/ or ${AGENTS_FILE}`;
}

/**
 * Where a new file of a Rules tab starts in a mounted pack, inside the
 * namespace; null for a tab a mounted pack cannot hold (hooks).
 */
export function mountTabStart(tab: RuleTab, mount: MountRef): string | null {
  const { name } = mount;
  switch (tab) {
    case "rules":
      return `${RULES_PREFIX}${name}/`;
    case "agents-md":
      return AGENTS_FILE;
    case "roles":
      return `.agents/roles/${name}/`;
    case "workflows":
      return `.agents/workflows/${name}/`;
    case "templates":
      return `.agents/templates/${name}/`;
    case "subagents":
      return `${CLAUDE_AGENTS_PREFIX}${name}-`;
    case "hooks":
      return null;
  }
}

/** Where a new convention doc of a mounted pack starts. */
export function mountDocsFolder(mount: MountRef): string {
  return `${MOUNT_CONVENTIONS_PREFIX}${mount.name}/`;
}

// ── Patterns ────────────────────────────────────────────────────────

export type PatternClass = "INSIDE" | "OUTSIDE" | "SPANNING";

function matchingClose(pattern: string, open: number): number {
  let depth = 0;
  for (let index = open; index < pattern.length; index += 1) {
    if (pattern[index] === "{") depth += 1;
    else if (pattern[index] === "}") {
      depth -= 1;
      if (depth === 0) return index;
    }
  }
  return -1;
}

/** Splits at the commas outside brace groups and quotes, as the server reads a list. */
function splitTopLevel(text: string): string[] {
  const parts: string[] = [];
  let depth = 0;
  let quote: string | null = null;
  let start = 0;
  for (let index = 0; index < text.length; index += 1) {
    const character = text[index];
    if (quote !== null) {
      if (character === quote) quote = null;
    } else if (character === '"' || character === "'") quote = character;
    else if (character === "{") depth += 1;
    else if (character === "}") depth -= 1;
    else if (character === "," && depth === 0) {
      parts.push(text.slice(start, index));
      start = index + 1;
    }
  }
  parts.push(text.slice(start));
  return parts;
}

function expandInto(pattern: string, from: number, expansions: string[]): boolean {
  let open = pattern.indexOf("{", from);
  while (open >= 0) {
    const close = matchingClose(pattern, open);
    if (close < 0) break;
    const alternatives = splitTopLevel(pattern.slice(open + 1, close));
    if (alternatives.length > 1) {
      const prefix = pattern.slice(0, open);
      const suffix = pattern.slice(close + 1);
      return alternatives.every((alternative: string) =>
        expandInto(`${prefix}${alternative}${suffix}`, open, expansions)
      );
    }
    open = pattern.indexOf("{", open + 1);
  }
  expansions.push(pattern);
  return expansions.length <= BRACE_EXPANSIONS_MAX;
}

/**
 * The brace expansions of a pattern, as Claude Code expands them; null when
 * there are more than the server expands, which then counts the pattern as
 * spanning.
 */
export function expandBraces(pattern: string): string[] | null {
  const expansions: string[] = [];
  return expandInto(pattern, 0, expansions) ? expansions : null;
}

/** A glob segment as a regular expression; null when it cannot be read. */
function segmentExpression(glob: string): RegExp | null {
  let source = "";
  for (let index = 0; index < glob.length; index += 1) {
    const character = glob[index] as string;
    if (character === "*") source += "[^/]*";
    else if (character === "?") source += "[^/]";
    else if (character === "[") {
      const close = glob.indexOf("]", index + 1);
      if (close < 0) return null;
      let body = glob.slice(index + 1, close);
      if (body.startsWith("!")) body = `^${body.slice(1)}`;
      source += `[${body.replace(/\\/g, "\\\\")}]`;
      index = close;
    } else source += character.replace(/[.+^$()|\\]/g, "\\$&");
  }
  try {
    return new RegExp(`^${source}$`);
  } catch (error: unknown) {
    // A glob that is no valid expression may still match for Claude Code; the caller counts it as matching.
    if (error instanceof SyntaxError) return null;
    throw error;
  }
}

function classifyExpansion(expansion: string, rootPath: string): PatternClass {
  const pattern = expansion.startsWith("./") ? expansion.slice(2) : expansion;
  const segments = pattern.split("/");
  const rootSegments = rootPath.split("/");
  for (let index = 0; index < rootSegments.length; index += 1) {
    const segment = segments[index];
    const rootSegment = rootSegments[index] as string;
    if (segment === undefined) return "OUTSIDE";
    if (segment === "**") return "SPANNING";
    if (!GLOB_CHARACTERS.test(segment)) {
      if (segment !== rootSegment) return "OUTSIDE";
      continue;
    }
    const expression = segmentExpression(segment);
    return expression === null || expression.test(rootSegment) ? "SPANNING" : "OUTSIDE";
  }
  return "INSIDE";
}

/**
 * Each brace expansion of a pattern with its class against a mount folder:
 * INSIDE when its first segments are the folder's, OUTSIDE when they cannot
 * match it, SPANNING when `**` or a glob matching the folder comes first.
 */
export function classifyPattern(pattern: string, rootPath: string): [string, PatternClass][] {
  const expansions = expandBraces(pattern);
  if (expansions === null) return [[pattern, "SPANNING"]];
  return expansions.map((expansion: string) => [expansion, classifyExpansion(expansion, rootPath)]);
}

function unquote(value: string): string {
  const text = value.trim();
  if (text.length >= 2 && (text[0] === '"' || text[0] === "'") && text.at(-1) === text[0]) {
    return text.slice(1, -1);
  }
  return text;
}

/**
 * The patterns of a rule's `paths` key in the three forms Claude Code reads:
 * an inline list, a block list, or one string of comma-separated patterns.
 * Null when the rule has no frontmatter or no `paths` key: it is always on.
 */
export function rulePatterns(content: string): string[] | null {
  const lines = content.replace(/^﻿/, "").replace(/\r\n/g, "\n").split("\n");
  if ((lines[0] ?? "").trim() !== "---") return null;
  const end = lines.findIndex((line: string, index: number) => index > 0 && line.trim() === "---");
  if (end < 0) return null;
  const at = lines.findIndex((line: string, index: number) => index < end && /^paths:/.test(line));
  if (at < 1) return null;
  const raw = (lines[at] as string).slice("paths:".length).trim();
  if (raw.startsWith("[") && raw.endsWith("]")) {
    return splitTopLevel(raw.slice(1, -1))
      .map(unquote)
      .filter((item: string) => item !== "");
  }
  if (raw !== "") {
    return splitTopLevel(unquote(raw))
      .map((item: string) => item.trim())
      .filter((item: string) => item !== "");
  }
  const items: string[] = [];
  for (const line of lines.slice(at + 1, end)) {
    const item = /^\s+-\s+(.*)$/.exec(line);
    if (!item) break;
    items.push(unquote(item[1] ?? ""));
  }
  return items.filter((item: string) => item !== "");
}

/**
 * The server's refusal (KAY_CFG_034) of a rule of a pack mounted in a folder
 * whose `paths` frontmatter is missing or has a pattern not under every
 * folder the pack is mounted at: such a rule would load for files outside
 * the folder. Null for a scoped rule and for any other file. The check reads
 * the text only; Kaya never adds or rewrites `paths`.
 */
export function mountRuleRefusal(path: string, content: string, mount: MountRef): string | null {
  const trimmed = path.trim();
  if (!isMountRule(trimmed, mount.name)) return null;
  const patterns = rulePatterns(content) ?? [];
  const folder = mount.mountPaths.find(
    (root: string) =>
      patterns.length === 0 ||
      patterns.some((pattern: string) =>
        classifyPattern(pattern, root).some(([, kind]) => kind !== "INSIDE")
      )
  );
  if (folder === undefined) return null;
  return `The rule ${trimmed} of pack ${mount.name} needs paths frontmatter whose every pattern lies under ${folder}/`;
}

/**
 * The allowlist's rules in the shape the grouping takes,
 * from the three permission lists of `.claude/settings.json`.
 */

import { programOf } from "@/lib/allowlist";
import type { AllowRule, PermissionEffect } from "@/types/run";

import { isParsableRule } from "../schemas";

/** The settings file every delivered rule is written in. */
export const SETTINGS_FILE = ".claude/settings.json";

const RULE_TEXT = /^([^()\s]+)\(([\s\S]*)\)$/;

export type PermissionLists = {
  allow: readonly string[];
  ask: readonly string[];
  deny: readonly string[];
};

/** One rule from its settings text, such as `Bash(git push *)` or a bare `WebFetch`. */
export function toAllowRule(text: string, effect: PermissionEffect, line: number): AllowRule {
  const trimmed = text.trim();
  const found = RULE_TEXT.exec(trimmed);
  const tool = found?.[1] ?? trimmed;
  const pattern = found ? (found[2] ?? "") : null;
  const rule: AllowRule = {
    tool,
    pattern,
    program: null,
    effect,
    source: "project",
    file: SETTINGS_FILE,
    line,
  };
  return { ...rule, program: programOf(rule) };
}

/** Every rule of the three lists, deny first, then ask, then allow, each in file order. */
export function toAllowRules(lists: PermissionLists): AllowRule[] {
  const effects: readonly [PermissionEffect, readonly string[]][] = [
    ["deny", lists.deny],
    ["ask", lists.ask],
    ["allow", lists.allow],
  ];
  return effects.flatMap(([effect, texts]: [PermissionEffect, readonly string[]]) =>
    texts.map((text: string, index: number) => toAllowRule(text, effect, index + 1))
  );
}

export type PermissionList = keyof PermissionLists;

export const PERMISSION_LISTS: readonly PermissionList[] = ["deny", "ask", "allow"];

/**
 * Why a rule cannot be added to a list, or null when it can: Claude Code
 * reads a rule only as a tool name with an optional non-empty pattern in
 * parentheses, written without surrounding spaces, as the server parses one.
 */
export function ruleIssue(text: string, list: readonly string[]): string | null {
  if (text === "") return "Enter a rule.";
  if (!isParsableRule(text)) {
    return "Write a tool name with an optional pattern in parentheses, such as Bash(git status *) or WebFetch, with no space around it.";
  }
  if (list.includes(text)) return "This list already holds the rule.";
  return null;
}

function objectOf(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

/**
 * The settings file with new permission lists and every other key kept as
 * it was. A list that was absent and stays empty is not added.
 *
 * @throws SyntaxError when the current content is not JSON; the editor then offers the file as text.
 */
export function settingsWithPermissions(content: string | null, lists: PermissionLists): string {
  const root = content === null ? {} : objectOf(JSON.parse(content));
  const permissions = objectOf(root.permissions);
  const next: Record<string, unknown> = { ...permissions };
  for (const key of PERMISSION_LISTS) {
    if (lists[key].length > 0 || key in permissions) next[key] = [...lists[key]];
  }
  return `${JSON.stringify({ ...root, permissions: next }, null, 2)}\n`;
}

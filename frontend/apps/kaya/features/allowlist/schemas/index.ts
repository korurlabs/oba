import { z } from "zod";

// ── Request Schemas ─────────────────────────────────────────────────
/** A tool name, optionally with a non-empty argument in parentheses, as the server parses a rule. */
const RULE_PATTERN = /^(?:[A-Z][A-Za-z0-9]*|mcp__[A-Za-z0-9_-]+)(?:\(([\s\S]+)\))?$/;

/** Whether a character is a control character as Java's `Character.isISOControl` counts one. */
function isControl(code: number): boolean {
  return code <= 0x1f || (code >= 0x7f && code <= 0x9f);
}

/**
 * Whether Claude Code reads the text as a permission rule exactly as written.
 * The rule is delivered in `.claude/settings.json` byte for byte, so a rule
 * with surrounding spaces or a line break would match nothing.
 */
export function isParsableRule(rule: string): boolean {
  if (rule === "" || rule !== rule.trim()) return false;
  for (let index = 0; index < rule.length; index += 1) {
    if (isControl(rule.charCodeAt(index))) return false;
  }
  return RULE_PATTERN.test(rule);
}

/** `GET /settings`: the settings file of each version, one to 20 version ids. */
export const settingsQueryRequestSchema = z.object({
  versionId: z.array(z.string().min(1)).min(1).max(20),
});

// ── Response Schemas ────────────────────────────────────────────────
/** One hook registration of `.claude/settings.json`. */
export const settingsHookResponseSchema = z.object({
  event: z.string(),
  matcher: z.string().nullish(),
  type: z.string().nullish(),
  command: z.string().nullish(),
});

/**
 * The permission rules and hooks of one version's `.claude/settings.json`,
 * parsed by the server; a file that is not a JSON object gives empty lists.
 */
export const settingsResponseSchema = z.object({
  id: z.string(),
  versionId: z.string(),
  sizeBytes: z.number(),
  allow: z.array(z.string()),
  ask: z.array(z.string()),
  deny: z.array(z.string()),
  hooks: z.array(settingsHookResponseSchema),
});

// ── Inferred Types ──────────────────────────────────────────────────
export type SettingsQueryRequest = z.infer<typeof settingsQueryRequestSchema>;
export type SettingsHookResponse = z.infer<typeof settingsHookResponseSchema>;
export type SettingsResponse = z.infer<typeof settingsResponseSchema>;

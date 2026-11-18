/**
 * Readers for the two file kinds a page interprets itself while it edits
 * them, before the server lists them: the frontmatter of a `SKILL.md` and
 * the permissions and hooks of `.claude/settings.json`.
 */

import type { SettingsResponse, SettingsHookResponse } from "@/features/allowlist/schemas";

export type Frontmatter = Record<string, string | string[]>;

const FENCE = /^---\s*$/;
const KEY_LINE = /^([A-Za-z_][\w-]*):\s*(.*)$/;
const LIST_ITEM = /^\s+-\s+(.*)$/;

function unquote(value: string): string {
  const text = value.trim();
  if (text.length >= 2 && (text[0] === '"' || text[0] === "'") && text.at(-1) === text[0]) {
    return text.slice(1, -1);
  }
  return text;
}

/**
 * Where the indented lines under a key end. A blank line belongs to them when
 * an indented line follows it, as in a folded or literal block with
 * paragraphs; blank lines after the last indented one do not.
 */
function blockEnd(lines: readonly string[], from: number, end: number): number {
  let stop = from;
  for (let index = from; index < end; index += 1) {
    const line = lines[index] ?? "";
    if (line.trim() === "") continue;
    if (!/^\s/.test(line)) break;
    stop = index + 1;
  }
  return stop;
}

/** A folded (`>`) block: lines join with spaces, a blank line is a line break. */
function foldBlock(block: readonly string[]): string {
  const paragraphs: string[][] = [[]];
  for (const line of block) {
    if (line.trim() === "") paragraphs.push([]);
    else paragraphs[paragraphs.length - 1]?.push(line.trim());
  }
  return paragraphs
    .map((words: string[]) => words.join(" "))
    .join("\n")
    .trim();
}

/** A literal (`|`) block: lines kept as written, less the block's indentation. */
function literalBlock(block: readonly string[]): string {
  const indents = block
    .filter((line: string) => line.trim() !== "")
    .map((line: string) => line.length - line.trimStart().length);
  const indent = indents.length === 0 ? 0 : Math.min(...indents);
  return block
    .map((line: string) => (line.trim() === "" ? "" : line.slice(indent).trimEnd()))
    .join("\n")
    .trim();
}

/**
 * The top-level keys of a YAML frontmatter block: plain and quoted scalars,
 * folded (`>`) and literal (`|`) blocks, block lists and inline `[a, b]`
 * lists. That covers the keys a skill uses; nested maps are skipped.
 * Content without a frontmatter block gives an empty record.
 */
export function parseFrontmatter(content: string): Frontmatter {
  const lines = content.replace(/\r\n/g, "\n").split("\n");
  if (!FENCE.test(lines[0] ?? "")) return {};
  const end = lines.findIndex((line: string, index: number) => index > 0 && FENCE.test(line));
  if (end < 0) return {};
  const result: Frontmatter = {};
  let index = 1;
  while (index < end) {
    const found = KEY_LINE.exec(lines[index] ?? "");
    index += 1;
    if (!found?.[1]) continue;
    const key = found[1];
    const raw = (found[2] ?? "").trim();
    const stop = blockEnd(lines, index, end);
    const block = lines.slice(index, stop);
    index = stop;
    const content = block.filter((line: string) => line.trim() !== "");
    if (raw.startsWith(">") || raw.startsWith("|")) {
      result[key] = raw.startsWith(">") ? foldBlock(block) : literalBlock(block);
    } else if (raw.startsWith("[") && raw.endsWith("]")) {
      result[key] = raw
        .slice(1, -1)
        .split(",")
        .map(unquote)
        .filter((item: string) => item !== "");
    } else if (raw === "" && content.length > 0 && content.every((line) => LIST_ITEM.test(line))) {
      result[key] = content.map((line: string) => unquote(LIST_ITEM.exec(line)?.[1] ?? ""));
    } else if (raw !== "") {
      result[key] = unquote([raw, ...content.map((line: string) => line.trim())].join(" "));
    }
  }
  return result;
}

export function frontmatterText(frontmatter: Frontmatter, key: string): string | null {
  const value = frontmatter[key];
  return typeof value === "string" && value !== "" ? value : null;
}

export function frontmatterList(frontmatter: Frontmatter, key: string): string[] | null {
  const value = frontmatter[key];
  if (Array.isArray(value)) return value;
  return typeof value === "string" && value !== "" ? [value] : null;
}

function stringList(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item: unknown): item is string => typeof item === "string")
    : [];
}

function record(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function optionalText(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

/**
 * The permission lists and registered hooks of a settings file, in the shape
 * the settings endpoint answers.
 *
 * @throws SyntaxError when the content is not JSON; the page shows that.
 */
export function parseSettings(
  content: string
): Pick<SettingsResponse, "allow" | "ask" | "deny" | "hooks"> {
  const settings = record(JSON.parse(content));
  const permissions = record(settings.permissions);
  const hooks: SettingsHookResponse[] = Object.entries(record(settings.hooks)).flatMap(
    ([event, groups]: [string, unknown]) =>
      (Array.isArray(groups) ? groups : []).flatMap((group: unknown) => {
        const entry = record(group);
        const matcher = optionalText(entry.matcher);
        return (Array.isArray(entry.hooks) ? entry.hooks : []).map((hook: unknown) => {
          const fields = record(hook);
          return {
            event,
            matcher,
            type: optionalText(fields.type),
            command: optionalText(fields.command),
          };
        });
      })
  );
  return {
    allow: stringList(permissions.allow),
    ask: stringList(permissions.ask),
    deny: stringList(permissions.deny),
    hooks,
  };
}

/**
 * Splits a Markdown file into its YAML frontmatter and its body for the
 * preview. Only the top-level keys are read, each with the text after its
 * colon, or its indented lines for a list or a map; the preview shows them
 * as written and never re-serialises them.
 */

export type FrontmatterField = { key: string; value: string };

export type SplitMarkdown = {
  /** Null when the file opens with no closed frontmatter block. */
  fields: FrontmatterField[] | null;
  body: string;
};

const FENCE = /^---[ \t]*$/;
const KEY = /^([A-Za-z0-9_.-]+)\s*:\s?(.*)$/;

export function splitFrontmatter(text: string): SplitMarkdown {
  const lines = text.replace(/\r\n/g, "\n").split("\n");
  if (!FENCE.test(lines[0] ?? "")) return { fields: null, body: text };
  const close = lines.findIndex((line: string, index: number) => index > 0 && FENCE.test(line));
  if (close < 0) return { fields: null, body: text };

  const fields: FrontmatterField[] = [];
  for (const line of lines.slice(1, close)) {
    // A comment line at the top level belongs to no key.
    if (/^#/.test(line)) continue;
    const key = /^\s/.test(line) ? null : KEY.exec(line);
    if (key) {
      fields.push({ key: key[1] ?? "", value: (key[2] ?? "").trim() });
      continue;
    }
    const last = fields[fields.length - 1];
    if (last && line.trim() !== "") {
      last.value = last.value === "" ? line.trim() : `${last.value}\n${line.trim()}`;
    }
  }
  return { fields, body: lines.slice(close + 1).join("\n") };
}

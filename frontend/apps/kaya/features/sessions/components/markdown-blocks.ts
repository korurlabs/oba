/**
 * A small markdown reader for handoff files: headings, lists, paragraphs and
 * code fences, with inline code and bold text.
 *
 * Handoffs are written by agents and may quote anything, HTML included, so
 * the result is plain data that the renderer turns into React text nodes;
 * nothing in a handoff is ever interpreted as markup.
 */

export type InlinePart = { kind: "text" | "code" | "strong"; text: string };

export type MarkdownBlock =
  | { kind: "heading"; level: number; text: string }
  | { kind: "paragraph"; text: string }
  | { kind: "list"; ordered: boolean; items: { text: string; depth: number }[] }
  | { kind: "code"; language: string; text: string };

const HEADING = /^(#{1,6})\s+(.*)$/;
const LIST_ITEM = /^(\s*)([-*+]|\d+[.)])\s+(.*)$/;
const FENCE = /^\s*(```|~~~)\s*([\w-]*)/;
const INDENT_PER_LEVEL = 2;

/** Splits markdown into blocks. An unclosed fence runs to the end of the text. */
export function parseMarkdown(markdown: string): MarkdownBlock[] {
  const blocks: MarkdownBlock[] = [];
  const lines = markdown.replace(/\r\n/g, "\n").split("\n");
  let paragraph: string[] = [];

  const flushParagraph = (): void => {
    if (paragraph.length > 0) {
      blocks.push({ kind: "paragraph", text: paragraph.join("\n") });
      paragraph = [];
    }
  };

  for (let at = 0; at < lines.length; at += 1) {
    const line = lines[at] ?? "";
    const fence = FENCE.exec(line);
    if (fence) {
      flushParagraph();
      const marker = fence[1] ?? "```";
      const body: string[] = [];
      at += 1;
      while (at < lines.length && !(lines[at] ?? "").trim().startsWith(marker)) {
        body.push(lines[at] ?? "");
        at += 1;
      }
      blocks.push({ kind: "code", language: fence[2] ?? "", text: body.join("\n") });
      continue;
    }
    if (line.trim() === "") {
      flushParagraph();
      continue;
    }
    const heading = HEADING.exec(line);
    if (heading) {
      flushParagraph();
      blocks.push({
        kind: "heading",
        level: (heading[1] ?? "#").length,
        text: (heading[2] ?? "").trim(),
      });
      continue;
    }
    const item = LIST_ITEM.exec(line);
    if (item) {
      flushParagraph();
      const ordered = /\d/.test(item[2] ?? "");
      const depth = Math.floor((item[1] ?? "").replace(/\t/g, "  ").length / INDENT_PER_LEVEL);
      const last = blocks[blocks.length - 1];
      const entry = { text: item[3] ?? "", depth };
      if (last?.kind === "list" && (depth > 0 || last.ordered === ordered)) {
        last.items.push(entry);
      } else {
        blocks.push({ kind: "list", ordered, items: [entry] });
      }
      continue;
    }
    const last = blocks[blocks.length - 1];
    if (paragraph.length === 0 && last?.kind === "list" && /^\s+\S/.test(line)) {
      // An indented line under a list item continues that item.
      const lastItem = last.items[last.items.length - 1];
      if (lastItem !== undefined) {
        lastItem.text = `${lastItem.text} ${line.trim()}`;
        continue;
      }
    }
    paragraph.push(line.trim());
  }
  flushParagraph();
  return blocks;
}

const INLINE = /`([^`]+)`|\*\*([^*]+)\*\*/g;

/** Splits one line of text into plain text, inline code and bold parts. */
export function parseInline(text: string): InlinePart[] {
  const parts: InlinePart[] = [];
  let from = 0;
  for (const match of text.matchAll(INLINE)) {
    const start = match.index ?? 0;
    if (start > from) {
      parts.push({ kind: "text", text: text.slice(from, start) });
    }
    if (match[1] !== undefined) {
      parts.push({ kind: "code", text: match[1] });
    } else {
      parts.push({ kind: "strong", text: match[2] ?? "" });
    }
    from = start + match[0].length;
  }
  if (from < text.length) {
    parts.push({ kind: "text", text: text.slice(from) });
  }
  return parts;
}

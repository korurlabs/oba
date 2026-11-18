/**
 * A small Markdown reader for handoffs and config files: headings, lists,
 * paragraphs, code fences, block quotes and pipe tables, with inline code,
 * bold text and links.
 *
 * The text comes from agents and members and may quote anything, HTML
 * included, so the result is plain data that the renderer turns into React
 * text nodes; nothing in it is ever interpreted as markup.
 */

export type InlinePart =
  | { kind: "text" | "code" | "strong"; text: string }
  | { kind: "link"; text: string; href: string };

export type TableAlign = "left" | "center" | "right" | null;

export type MarkdownBlock =
  | { kind: "heading"; level: number; text: string }
  | { kind: "paragraph"; text: string }
  | { kind: "list"; ordered: boolean; items: { text: string; depth: number }[] }
  | { kind: "code"; language: string; text: string }
  | { kind: "quote"; blocks: MarkdownBlock[] }
  | { kind: "table"; header: string[]; align: TableAlign[]; rows: string[][] };

const HEADING = /^(#{1,6})\s+(.*)$/;
const LIST_ITEM = /^(\s*)([-*+]|\d+[.)])\s+(.*)$/;
const FENCE = /^\s*(```|~~~)\s*([\w-]*)/;
const QUOTE = /^\s{0,3}>\s?(.*)$/;
const TABLE_SEPARATOR = /^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$/;
const INDENT_PER_LEVEL = 2;

/** The cells of one pipe table row; an escaped pipe stays in its cell. */
export function tableCells(line: string): string[] {
  let row = line.trim();
  if (row.startsWith("|")) row = row.slice(1);
  if (row.endsWith("|") && !row.endsWith("\\|")) row = row.slice(0, -1);
  return row.split(/(?<!\\)\|/).map((cell: string) => cell.trim().replace(/\\\|/g, "|"));
}

function alignOf(cell: string): TableAlign {
  const left = cell.startsWith(":");
  const right = cell.endsWith(":");
  if (left && right) return "center";
  if (right) return "right";
  if (left) return "left";
  return null;
}

/** Whether a line and the next open a pipe table: a row with a pipe, then a separator row. */
function opensTable(line: string, next: string | undefined): boolean {
  return line.includes("|") && next !== undefined && TABLE_SEPARATOR.test(next) && next.includes("-");
}

/** Splits Markdown into blocks. An unclosed fence runs to the end of the text. */
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
    if (QUOTE.test(line)) {
      flushParagraph();
      const quoted: string[] = [];
      while (at < lines.length) {
        const match = QUOTE.exec(lines[at] ?? "");
        if (!match) break;
        quoted.push(match[1] ?? "");
        at += 1;
      }
      at -= 1;
      blocks.push({ kind: "quote", blocks: parseMarkdown(quoted.join("\n")) });
      continue;
    }
    if (paragraph.length === 0 && opensTable(line, lines[at + 1])) {
      const header = tableCells(line);
      const align = tableCells(lines[at + 1] ?? "").map(alignOf);
      const rows: string[][] = [];
      at += 2;
      while (at < lines.length && (lines[at] ?? "").includes("|") && (lines[at] ?? "").trim()) {
        const cells = tableCells(lines[at] ?? "");
        // Every row has the header's width, so a short row is padded and a long one cut.
        rows.push(header.map((_cell: string, index: number) => cells[index] ?? ""));
        at += 1;
      }
      at -= 1;
      blocks.push({ kind: "table", header, align, rows });
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

const INLINE = /`([^`]+)`|\*\*([^*]+)\*\*|\[([^\]]+)\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g;

/** Splits one line of text into plain text, inline code, bold and link parts. */
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
    } else if (match[2] !== undefined) {
      parts.push({ kind: "strong", text: match[2] });
    } else {
      parts.push({ kind: "link", text: match[3] ?? "", href: match[4] ?? "" });
    }
    from = start + match[0].length;
  }
  if (from < text.length) {
    parts.push({ kind: "text", text: text.slice(from) });
  }
  return parts;
}

/**
 * The address a link may open: a web or mail address as written. A relative
 * link points into the repository the file is delivered to, not into Kaya,
 * and any other scheme (`javascript:`, `data:`) could run code, so neither
 * gets an address.
 */
export function safeHref(href: string): string | null {
  return /^(https?:\/\/|mailto:)/i.test(href.trim()) ? href.trim() : null;
}

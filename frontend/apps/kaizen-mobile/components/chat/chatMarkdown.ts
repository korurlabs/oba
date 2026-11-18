import { CHAT_MENTION_EVERYONE, CHAT_MENTION_HERE } from "@/lib/schemas/chat";

/*
 * The chat's markdown subset as a tree, without React, so the web app's
 * rules (which text becomes a link, a chip or code) can be tested alone:
 * fenced code, quotes, lists and paragraphs; inline code, bold, italic,
 * strike, links, autolinks, mention tokens and ticket tokens.
 */

export type ChatInline =
  | { type: "text"; text: string }
  | { type: "code"; text: string }
  | { type: "bold" | "italic" | "strike"; children: ChatInline[] }
  | { type: "link"; url: string; children: ChatInline[] }
  /** `everyone`, `here` or a user ID. */
  | { type: "mention"; target: string }
  | { type: "ticket"; ticketId: string };

export type ChatBlock =
  | { type: "paragraph"; inlines: ChatInline[] }
  | { type: "code"; language: string | null; code: string }
  | { type: "quote"; blocks: ChatBlock[] }
  | { type: "list"; ordered: boolean; start: number; items: ChatInline[][] };

const UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
/** An unclosed fence runs to the end of the text, as the server masks code. */
const FENCED_CODE_PATTERN = /```[\s\S]*?(?:```|$(?![\s\S]))/g;
const USER_TOKEN_STICKY = new RegExp(
  `<@(${CHAT_MENTION_EVERYONE}|${CHAT_MENTION_HERE}|${UUID})>`,
  "y"
);
const TICKET_TOKEN_STICKY = new RegExp(`<#(${UUID})>`, "y");
const INLINE_CODE_STICKY = /`([^`\n]+)`/y;
const LINK_STICKY = /\[([^\]\n]+)\]\(([^()\s]+)\)/y;
const AUTOLINK_STICKY = /https?:\/\/[^\s<>]*[^\s<>.,:;"'!?)\]]/y;
const BRACKETED_AUTOLINK_STICKY = /<(https?:\/\/[^\s<>]+)>/y;
/** Only these schemes become links; anything else (`javascript:`, `data:`) stays text. */
export const SAFE_URL_PATTERN = /^(https?:\/\/|mailto:)/i;
const ESCAPABLE: ReadonlySet<string> = new Set([
  "\\",
  "`",
  "*",
  "_",
  "~",
  "<",
  ">",
  "[",
  "]",
  "(",
  ")",
  "#",
  "-",
]);
const QUOTE_LINE = /^>\s?(.*)$/;
const UNORDERED_ITEM = /^\s*[-*+]\s+(.*)$/;
const ORDERED_ITEM = /^\s*(\d{1,9})[.)]\s+(.*)$/;
const LANGUAGE_LINE_PATTERN = /^[\w+#.-]+$/;

export function isSafeChatUrl(url: string): boolean {
  return SAFE_URL_PATTERN.test(url);
}

// ── Blocks ──────────────────────────────────────────────────────────

/** What stands between the fences: a single word on the first line is the language. */
function parseCodeFence(raw: string): ChatBlock {
  const closed: boolean = raw.length >= 6 && raw.endsWith("```");
  const inner: string = raw.slice(3, closed ? -3 : undefined);
  const newline: number = inner.indexOf("\n");
  if (newline >= 0) {
    const firstLine: string = inner.slice(0, newline).trim();
    if (firstLine.length === 0 || LANGUAGE_LINE_PATTERN.test(firstLine)) {
      return {
        type: "code",
        language: firstLine || null,
        code: inner.slice(newline + 1).replace(/\n$/, ""),
      };
    }
  }
  return { type: "code", language: null, code: inner.replace(/\n$/, "") };
}

/** Quotes, lists and paragraphs of text without code fences. A quote holds no nested quote. */
function parseTextBlocks(text: string, allowQuotes: boolean): ChatBlock[] {
  const lines: string[] = text.split("\n");
  const blocks: ChatBlock[] = [];
  let paragraph: string[] | null = null;
  const flush = (): void => {
    if (paragraph)
      blocks.push({ type: "paragraph", inlines: parseChatInline(paragraph.join("\n")) });
    paragraph = null;
  };

  let index = 0;
  while (index < lines.length) {
    const line: string = lines[index] ?? "";
    if (allowQuotes && QUOTE_LINE.test(line)) {
      flush();
      const quoted: string[] = [];
      while (index < lines.length) {
        const match: RegExpExecArray | null = QUOTE_LINE.exec(lines[index] ?? "");
        if (!match) break;
        quoted.push(match[1] ?? "");
        index++;
      }
      blocks.push({ type: "quote", blocks: parseTextBlocks(quoted.join("\n"), false) });
      continue;
    }
    const unordered: RegExpExecArray | null = UNORDERED_ITEM.exec(line);
    const ordered: RegExpExecArray | null = unordered ? null : ORDERED_ITEM.exec(line);
    if (unordered || ordered) {
      flush();
      const isOrdered: boolean = !!ordered;
      const items: ChatInline[][] = [];
      const start: number = ordered ? Number(ordered[1]) : 1;
      while (index < lines.length) {
        const current: string = lines[index] ?? "";
        const match: RegExpExecArray | null = isOrdered
          ? ORDERED_ITEM.exec(current)
          : UNORDERED_ITEM.exec(current);
        if (!match) break;
        items.push(parseChatInline((isOrdered ? match[2] : match[1]) ?? ""));
        index++;
      }
      blocks.push({ type: "list", ordered: isOrdered, start, items });
      continue;
    }
    if (line.trim().length === 0) {
      flush();
    } else {
      paragraph = paragraph ?? [];
      paragraph.push(line);
    }
    index++;
  }
  flush();
  return blocks;
}

/** The blocks of a message's content. */
export function parseChatMarkdown(content: string): ChatBlock[] {
  const text: string = content.replace(/\r\n?/g, "\n");
  const blocks: ChatBlock[] = [];
  let last = 0;
  for (const match of text.matchAll(FENCED_CODE_PATTERN)) {
    const at: number = match.index ?? 0;
    if (match[0].length === 0) continue;
    blocks.push(...parseTextBlocks(text.slice(last, at), true));
    blocks.push(parseCodeFence(match[0]));
    last = at + match[0].length;
  }
  blocks.push(...parseTextBlocks(text.slice(last), true));
  return blocks;
}

// ── Inline ──────────────────────────────────────────────────────────

function isWordChar(char: string | undefined): boolean {
  return !!char && /[\p{L}\p{N}]/u.test(char);
}

function matchAt(pattern: RegExp, text: string, index: number): RegExpExecArray | null {
  pattern.lastIndex = index;
  return pattern.exec(text);
}

/**
 * The closing delimiter of an emphasis run that starts at `index`, or -1.
 * The emphasised text is not empty and neither starts nor ends with a space.
 */
function findClosing(text: string, index: number, delimiter: string): number {
  const from: number = index + delimiter.length;
  const first: string | undefined = text[from];
  if (first === undefined || /\s/.test(first)) return -1;
  let close: number = text.indexOf(delimiter, from + 1);
  while (close >= 0) {
    const lastInner: string | undefined = text[close - 1];
    const tripled: boolean = delimiter.length === 1 && text[close + 1] === delimiter;
    if (lastInner !== undefined && !/\s/.test(lastInner) && !tripled) {
      // `_` emphasis never closes inside a word, so snake_case stays text.
      if (delimiter !== "_" || !isWordChar(text[close + 1])) return close;
    }
    close = text.indexOf(delimiter, close + 1);
  }
  return -1;
}

const EMPHASIS: readonly { delimiter: string; type: "bold" | "italic" | "strike" }[] = [
  { delimiter: "**", type: "bold" },
  { delimiter: "__", type: "bold" },
  { delimiter: "~~", type: "strike" },
  { delimiter: "*", type: "italic" },
  { delimiter: "_", type: "italic" },
];

/** The inline nodes of one paragraph, list item or link label. */
export function parseChatInline(text: string, inLink: boolean = false): ChatInline[] {
  const nodes: ChatInline[] = [];
  let buffer = "";
  const flush = (): void => {
    if (buffer.length > 0) nodes.push({ type: "text", text: buffer });
    buffer = "";
  };

  let index = 0;
  outer: while (index < text.length) {
    const char: string = text[index] ?? "";

    if (char === "\\" && ESCAPABLE.has(text[index + 1] ?? "")) {
      buffer += text[index + 1];
      index += 2;
      continue;
    }

    if (char === "`") {
      const code: RegExpExecArray | null = matchAt(INLINE_CODE_STICKY, text, index);
      if (code) {
        flush();
        nodes.push({ type: "code", text: code[1] ?? "" });
        index += code[0].length;
        continue;
      }
    }

    if (char === "<") {
      const user: RegExpExecArray | null = matchAt(USER_TOKEN_STICKY, text, index);
      if (user?.[1]) {
        flush();
        nodes.push({ type: "mention", target: user[1] });
        index += user[0].length;
        continue;
      }
      const ticket: RegExpExecArray | null = matchAt(TICKET_TOKEN_STICKY, text, index);
      if (ticket?.[1]) {
        flush();
        nodes.push({ type: "ticket", ticketId: ticket[1] });
        index += ticket[0].length;
        continue;
      }
      const bracketed: RegExpExecArray | null = inLink
        ? null
        : matchAt(BRACKETED_AUTOLINK_STICKY, text, index);
      if (bracketed?.[1]) {
        flush();
        nodes.push({
          type: "link",
          url: bracketed[1],
          children: [{ type: "text", text: bracketed[1] }],
        });
        index += bracketed[0].length;
        continue;
      }
    }

    if (char === "[" && !inLink) {
      const link: RegExpExecArray | null = matchAt(LINK_STICKY, text, index);
      if (link?.[1] && link[2] && isSafeChatUrl(link[2])) {
        flush();
        nodes.push({ type: "link", url: link[2], children: parseChatInline(link[1], true) });
        index += link[0].length;
        continue;
      }
    }

    if (char === "h" && !inLink && !isWordChar(text[index - 1])) {
      const url: RegExpExecArray | null = matchAt(AUTOLINK_STICKY, text, index);
      if (url) {
        flush();
        nodes.push({ type: "link", url: url[0], children: [{ type: "text", text: url[0] }] });
        index += url[0].length;
        continue;
      }
    }

    for (const { delimiter, type } of EMPHASIS) {
      if (!text.startsWith(delimiter, index)) continue;
      // `_` emphasis never opens inside a word.
      if (delimiter === "_" && isWordChar(text[index - 1])) continue;
      const close: number = findClosing(text, index, delimiter);
      if (close < 0) continue;
      flush();
      nodes.push({
        type,
        children: parseChatInline(text.slice(index + delimiter.length, close), inLink),
      });
      index = close + delimiter.length;
      continue outer;
    }

    buffer += char;
    index++;
  }
  flush();
  return nodes;
}

/** A code block longer than this many lines shows only its start until the reader expands it. */
export const CHAT_CODE_COLLAPSED_LINES = 20;

/** What a code block shows: its first lines while collapsed, and how long it is in all. */
export interface ChatCodeView {
  text: string;
  lineCount: number;
  /** True when the block is longer than the collapsed view, so it offers "Show more". */
  collapsible: boolean;
}

/**
 * The text of a code block as shown: a long block keeps the bubble short
 * until the reader asks for the rest, as the web app does.
 */
export function chatCodeView(code: string, expanded: boolean): ChatCodeView {
  const lines: string[] = code.split("\n");
  const collapsible: boolean = lines.length > CHAT_CODE_COLLAPSED_LINES;
  return {
    text: collapsible && !expanded ? lines.slice(0, CHAT_CODE_COLLAPSED_LINES).join("\n") : code,
    lineCount: lines.length,
    collapsible,
  };
}

/** How the plain text of a message names its tokens. */
export interface ChatPlainTokens {
  /** A mention, by its target: `everyone`, `here` or a user ID. */
  mention: (target: string) => string;
  /** A ticket token, by its ticket ID. */
  ticket: (ticketId: string) => string;
}

function inlinePlainText(inlines: readonly ChatInline[], tokens: ChatPlainTokens): string {
  return inlines
    .map((inline: ChatInline): string => {
      switch (inline.type) {
        case "text":
        case "code":
          return inline.text;
        case "bold":
        case "italic":
        case "strike":
        case "link":
          return inlinePlainText(inline.children, tokens);
        case "mention":
          return tokens.mention(inline.target);
        case "ticket":
          return tokens.ticket(inline.ticketId);
      }
    })
    .join("");
}

/**
 * The words of parsed blocks without their markdown, one block per line:
 * what the reader sees in the bubble, for a one-line preview.
 */
export function chatMarkdownPlainText(
  blocks: readonly ChatBlock[],
  tokens: ChatPlainTokens
): string {
  return blocks
    .map((block: ChatBlock): string => {
      switch (block.type) {
        case "paragraph":
          return inlinePlainText(block.inlines, tokens);
        case "code":
          return block.code;
        case "quote":
          return chatMarkdownPlainText(block.blocks, tokens);
        case "list":
          return block.items.map((item) => inlinePlainText(item, tokens)).join("\n");
      }
    })
    .join("\n");
}

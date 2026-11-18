/**
 * Renders a chat message's markdown as React elements.
 *
 * Kaizen has no read-only markdown renderer, and a Tiptap instance per
 * message would be heavy, so this is a small parser for the subset the
 * composer writes: bold, italic, strike, inline code, fenced code blocks,
 * links, lists and quotes, plus the chat tokens. It builds elements directly
 * and never an HTML string, so no text of a message is ever parsed as HTML.
 *
 * Code wins first, with the same fence rule as the token parser
 * (`maskChatCode`): nothing inside a fenced block or an inline code span is a
 * mention, a ticket reference or a ticket key, so what renders as a chip is
 * exactly what the server counted.
 */
import type { ReactNode } from "react";
import { cn } from "@oba/ui/lib/utils";
import { getDisplayName } from "@/components/ui/user-avatar";
import { TicketKeyLink } from "@/features/tickets/components/ticket-key-link";
import { ChatCodeBlock } from "../components/chat-code-block";
import { splitChatCodeFence } from "./chat-code-fence";
import { parseChatMessageLink, type ChatMessageLink } from "./chat-message-link";
import {
  CHAT_MENTION_EVERYONE,
  CHAT_MENTION_HERE,
  parseChatTokens,
  type ChatLinkedTicketResponse,
  type ChatTicketRefResponse,
  type ChatUserResponse,
} from "../schemas";

export interface ChatMarkdownLabels {
  /** Shown as `@{label}` for a user who is no longer a member, or is not known to the page. */
  unknownMember: string;
  /** Shown for a ticket reference without its snapshot. */
  unknownTicket: string;
}

export interface ChatMarkdownContext {
  /** Users of the message by user ID (`ChatMessageResponse.users`). */
  users: Record<string, ChatUserResponse>;
  /** One entry per `<#ticketId>` token, in order of first reference. */
  ticketRefs: readonly ChatTicketRefResponse[];
  /** Plain ticket keys of the text that the reader can open; other keys stay text. */
  linkedTickets: readonly ChatLinkedTicketResponse[];
  /** The reader's Oba user ID; a mention of the reader is marked. */
  meId: string | undefined;
  /** True when the message mentions the reader, directly or through `@everyone` or `@here`. */
  mentionsMe: boolean;
  labels: ChatMarkdownLabels;
  /**
   * Renders a link to a chat message (see `parseChatMessageLink`), e.g. as a
   * chip with a preview; without it the link renders as any other link.
   */
  renderMessageLink?: (link: ChatMessageLink, key: number) => ReactNode;
}

/** The name a mention or a quote shows for a user of a page. */
export function chatUserDisplayName(user: ChatUserResponse | undefined, unknownLabel: string): string {
  if (!user || !user.member) return unknownLabel;
  return (
    getDisplayName({
      firstName: user.firstName ?? undefined,
      lastName: user.lastName ?? undefined,
      email: user.email ?? undefined,
    }) || unknownLabel
  );
}

// ── Patterns ────────────────────────────────────────────────────────

const UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
/** The same fence rule as `maskChatCode`: an unclosed fence runs to the end of the text. */
const FENCED_CODE_PATTERN = /```[\s\S]*?(?:```|$(?![\s\S]))/g;
const USER_TOKEN_STICKY = new RegExp(`<@(${CHAT_MENTION_EVERYONE}|${CHAT_MENTION_HERE}|${UUID})>`, "y");
const TICKET_TOKEN_STICKY = new RegExp(`<#(${UUID})>`, "y");
const INLINE_CODE_STICKY = /`([^`\n]+)`/y;
const LINK_STICKY = /\[([^\]\n]+)\]\(([^()\s]+)\)/y;
const AUTOLINK_STICKY = /https?:\/\/[^\s<>]*[^\s<>.,:;"'!?)\]]/y;
/** A URL in angle brackets, markdown's autolink, as the composer writes a pasted link; the brackets are not shown. */
const BRACKETED_AUTOLINK_STICKY = /<(https?:\/\/[^\s<>]+)>/y;
/** A ticket key as the server finds plain keys: a board prefix of at most 10 characters, `-`, a number. */
const TICKET_KEY_PATTERN = /\b[A-Za-z][A-Za-z0-9]{0,9}-\d{1,9}\b/g;
/** Only these schemes become links; anything else (`javascript:`, `data:`) stays text. */
const SAFE_URL_PATTERN = /^(https?:\/\/|mailto:)/i;
const ESCAPABLE = new Set(["\\", "`", "*", "_", "~", "<", ">", "[", "]", "(", ")", "#", "-"]);
const QUOTE_LINE = /^>\s?(.*)$/;
const UNORDERED_ITEM = /^\s*[-*+]\s+(.*)$/;
const ORDERED_ITEM = /^\s*(\d{1,9})[.)]\s+(.*)$/;

// ── Prepared context ────────────────────────────────────────────────

interface RenderContext extends ChatMarkdownContext {
  usersById: Map<string, ChatUserResponse>;
  refsById: Map<string, ChatTicketRefResponse>;
  /** Distinct ticket IDs of the tokens outside code, in order: the position of a ref without an ID. */
  ticketTokenOrder: string[];
  linkedByKey: Map<string, ChatLinkedTicketResponse>;
}

function prepare(content: string, context: ChatMarkdownContext): RenderContext {
  const usersById = new Map<string, ChatUserResponse>();
  for (const [userId, user] of Object.entries(context.users)) usersById.set(userId.toLowerCase(), user);
  const refsById = new Map<string, ChatTicketRefResponse>();
  for (const ref of context.ticketRefs) {
    if (ref.ticketId) refsById.set(ref.ticketId.toLowerCase(), ref);
  }
  const linkedByKey = new Map<string, ChatLinkedTicketResponse>();
  for (const linked of context.linkedTickets) linkedByKey.set(linked.ticketKey, linked);
  return {
    ...context,
    usersById,
    refsById,
    ticketTokenOrder: parseChatTokens(content).ticketIds,
    linkedByKey,
  };
}

// ── Blocks ──────────────────────────────────────────────────────────

type Block =
  | { type: "paragraph"; lines: string[] }
  | { type: "code"; language: string | null; code: string }
  | { type: "quote"; blocks: Block[] }
  | { type: "list"; ordered: boolean; start: number; items: string[] };

/** A fence, closed or running to the end of the text; one written on a single line is a block too. */
function parseCodeFence(raw: string): Block {
  const closed = raw.length >= 6 && raw.endsWith("```");
  return { type: "code", ...splitChatCodeFence(raw.slice(3, closed ? -3 : undefined)) };
}

/** Quotes, lists and paragraphs of text without code fences. A quote holds no nested quote. */
function parseTextBlocks(text: string, allowQuotes: boolean): Block[] {
  const lines = text.split("\n");
  const blocks: Block[] = [];
  let paragraph: string[] | null = null;
  const flush = () => {
    if (paragraph) blocks.push({ type: "paragraph", lines: paragraph });
    paragraph = null;
  };

  let index = 0;
  while (index < lines.length) {
    const line = lines[index] ?? "";
    if (allowQuotes && QUOTE_LINE.test(line)) {
      flush();
      const quoted: string[] = [];
      while (index < lines.length) {
        const match = QUOTE_LINE.exec(lines[index] ?? "");
        if (!match) break;
        quoted.push(match[1] ?? "");
        index++;
      }
      blocks.push({ type: "quote", blocks: parseTextBlocks(quoted.join("\n"), false) });
      continue;
    }
    const unordered = UNORDERED_ITEM.exec(line);
    const ordered = unordered ? null : ORDERED_ITEM.exec(line);
    if (unordered || ordered) {
      flush();
      const isOrdered = !!ordered;
      const items: string[] = [];
      const start = ordered ? Number(ordered[1]) : 1;
      while (index < lines.length) {
        const current = lines[index] ?? "";
        const match = isOrdered ? ORDERED_ITEM.exec(current) : UNORDERED_ITEM.exec(current);
        if (!match) break;
        items.push((isOrdered ? match[2] : match[1]) ?? "");
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

function parseBlocks(content: string): Block[] {
  const text = content.replace(/\r\n?/g, "\n");
  const blocks: Block[] = [];
  let last = 0;
  for (const match of text.matchAll(FENCED_CODE_PATTERN)) {
    const at = match.index ?? 0;
    if (match[0].length === 0) continue;
    blocks.push(...parseTextBlocks(text.slice(last, at), true));
    blocks.push(parseCodeFence(match[0]));
    last = at + match[0].length;
  }
  blocks.push(...parseTextBlocks(text.slice(last), true));
  return blocks;
}

// ── Inline ──────────────────────────────────────────────────────────

const chipBase = "rounded px-1 py-px font-medium whitespace-nowrap";

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
  const from = index + delimiter.length;
  const first = text[from];
  if (first === undefined || /\s/.test(first)) return -1;
  let close = text.indexOf(delimiter, from + 1);
  while (close >= 0) {
    const lastInner = text[close - 1];
    const tripled = delimiter.length === 1 && text[close + 1] === delimiter;
    if (lastInner !== undefined && !/\s/.test(lastInner) && !tripled) {
      // `_` emphasis never closes inside a word, so snake_case stays text.
      if (delimiter !== "_" || !isWordChar(text[close + 1])) return close;
    }
    close = text.indexOf(delimiter, close + 1);
  }
  return -1;
}

function mentionChip(target: string, ctx: RenderContext, key: number): ReactNode {
  if (target === CHAT_MENTION_EVERYONE || target === CHAT_MENTION_HERE) {
    return (
      <span
        key={key}
        className={cn(
          chipBase,
          ctx.mentionsMe ? "bg-amber-400/30 text-amber-900 dark:text-amber-100" : "bg-primary/15 text-primary",
        )}
      >
        @{target}
      </span>
    );
  }
  const userId = target.toLowerCase();
  const user = ctx.usersById.get(userId);
  const isMe = !!ctx.meId && ctx.meId.toLowerCase() === userId;
  const known = !!user?.member;
  return (
    <span
      key={key}
      className={cn(
        chipBase,
        !known
          ? "bg-muted text-muted-foreground"
          : isMe
            ? "bg-amber-400/30 text-amber-900 dark:text-amber-100"
            : "bg-primary/15 text-primary",
      )}
    >
      @{chatUserDisplayName(user, ctx.labels.unknownMember)}
    </span>
  );
}

function findTicketRef(ticketId: string, ctx: RenderContext): ChatTicketRefResponse | undefined {
  const id = ticketId.toLowerCase();
  const byId = ctx.refsById.get(id);
  if (byId) return byId;
  // A ref whose ticket row was removed has no ID; it keeps its token's position.
  const position = ctx.ticketTokenOrder.indexOf(id);
  const byPosition = position >= 0 ? ctx.ticketRefs[position] : undefined;
  return byPosition && !byPosition.ticketId ? byPosition : undefined;
}

function ticketChip(ticketId: string, ctx: RenderContext, key: number): ReactNode {
  const ref = findTicketRef(ticketId, ctx);
  const chip = "inline-flex max-w-full items-baseline gap-1 rounded border px-1 py-px align-baseline text-[13px]";
  if (!ref) {
    return (
      <span key={key} className={cn(chip, "text-muted-foreground")}>
        #{ctx.labels.unknownTicket}
      </span>
    );
  }
  const body = (
    <>
      <span className="font-mono text-xs font-semibold whitespace-nowrap">{ref.ticketKey}</span>
      <span className="truncate">{ref.ticketTitle}</span>
    </>
  );
  if (ref.canOpen && ref.ticketId) {
    return (
      <TicketKeyLink
        key={key}
        ticketKey={ref.ticketKey}
        className={cn(
          chip,
          "border-primary/30 bg-primary/5 text-primary focus-visible:ring-ring hover:bg-primary/10 focus-visible:ring-2 focus-visible:outline-none",
        )}
      >
        {body}
      </TicketKeyLink>
    );
  }
  return (
    <span key={key} className={cn(chip, "bg-muted/60 text-foreground")}>
      {body}
    </span>
  );
}

/** A URL found in the text: a chat message link as its chip, any other as a link. */
function urlNode(href: string, ctx: RenderContext, key: number): ReactNode {
  const messageLink = ctx.renderMessageLink ? parseChatMessageLink(href) : null;
  if (messageLink && ctx.renderMessageLink) return ctx.renderMessageLink(messageLink, key);
  return externalLink(href, href, key);
}

/** Plain text, with the ticket keys the reader can open as links. */
function renderPlain(text: string, ctx: RenderContext, inLink: boolean, nodes: ReactNode[]): void {
  if (text.length === 0) return;
  if (inLink || ctx.linkedByKey.size === 0) {
    nodes.push(text);
    return;
  }
  let last = 0;
  for (const match of text.matchAll(TICKET_KEY_PATTERN)) {
    const linked = ctx.linkedByKey.get(match[0]);
    if (!linked) continue;
    const at = match.index ?? 0;
    if (at > last) nodes.push(text.slice(last, at));
    nodes.push(
      <TicketKeyLink
        key={nodes.length}
        ticketKey={linked.ticketKey}
        className="text-primary focus-visible:ring-ring rounded-sm font-mono text-[13px] font-semibold underline-offset-2 hover:underline focus-visible:ring-2 focus-visible:outline-none"
      >
        {match[0]}
      </TicketKeyLink>,
    );
    last = at + match[0].length;
  }
  if (last < text.length) nodes.push(text.slice(last));
}

function externalLink(href: string, children: ReactNode, key: number): ReactNode {
  return (
    <a
      key={key}
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="text-primary break-all underline-offset-2 hover:underline"
    >
      {children}
    </a>
  );
}

const EMPHASIS: ReadonlyArray<{ delimiter: string; wrap: (children: ReactNode[], key: number) => ReactNode }> = [
  { delimiter: "**", wrap: (children, key) => <strong key={key}>{children}</strong> },
  { delimiter: "__", wrap: (children, key) => <strong key={key}>{children}</strong> },
  { delimiter: "~~", wrap: (children, key) => <del key={key}>{children}</del> },
  { delimiter: "*", wrap: (children, key) => <em key={key}>{children}</em> },
  { delimiter: "_", wrap: (children, key) => <em key={key}>{children}</em> },
];

function parseInline(text: string, ctx: RenderContext, inLink: boolean): ReactNode[] {
  const nodes: ReactNode[] = [];
  let buffer = "";
  const flush = () => {
    renderPlain(buffer, ctx, inLink, nodes);
    buffer = "";
  };

  let index = 0;
  outer: while (index < text.length) {
    const char = text[index] ?? "";

    if (char === "\\" && ESCAPABLE.has(text[index + 1] ?? "")) {
      buffer += text[index + 1];
      index += 2;
      continue;
    }

    if (char === "`") {
      const code = matchAt(INLINE_CODE_STICKY, text, index);
      if (code) {
        flush();
        nodes.push(
          <code key={nodes.length} className="bg-muted rounded px-1 py-px font-mono text-[0.85em]">
            {code[1]}
          </code>,
        );
        index += code[0].length;
        continue;
      }
    }

    if (char === "<") {
      const user = matchAt(USER_TOKEN_STICKY, text, index);
      if (user?.[1]) {
        flush();
        nodes.push(mentionChip(user[1], ctx, nodes.length));
        index += user[0].length;
        continue;
      }
      const ticket = matchAt(TICKET_TOKEN_STICKY, text, index);
      if (ticket?.[1]) {
        flush();
        nodes.push(ticketChip(ticket[1], ctx, nodes.length));
        index += ticket[0].length;
        continue;
      }
      const bracketed = inLink ? null : matchAt(BRACKETED_AUTOLINK_STICKY, text, index);
      if (bracketed?.[1]) {
        flush();
        nodes.push(urlNode(bracketed[1], ctx, nodes.length));
        index += bracketed[0].length;
        continue;
      }
    }

    if (char === "[" && !inLink) {
      const link = matchAt(LINK_STICKY, text, index);
      if (link?.[1] && link[2] && SAFE_URL_PATTERN.test(link[2])) {
        flush();
        nodes.push(externalLink(link[2], parseInline(link[1], ctx, true), nodes.length));
        index += link[0].length;
        continue;
      }
    }

    if ((char === "h" || char === "H") && !inLink && !isWordChar(text[index - 1])) {
      const url = matchAt(AUTOLINK_STICKY, text, index);
      if (url) {
        flush();
        nodes.push(urlNode(url[0], ctx, nodes.length));
        index += url[0].length;
        continue;
      }
    }

    for (const { delimiter, wrap } of EMPHASIS) {
      if (!text.startsWith(delimiter, index)) continue;
      // `_` emphasis opens only at a word start, so snake_case stays text.
      if (delimiter === "_" && isWordChar(text[index - 1])) continue;
      // A single delimiter is not the first half of a double one.
      if (delimiter.length === 1 && text[index + 1] === delimiter) continue;
      const close = findClosing(text, index, delimiter);
      if (close < 0) continue;
      flush();
      nodes.push(wrap(parseInline(text.slice(index + delimiter.length, close), ctx, inLink), nodes.length));
      index = close + delimiter.length;
      continue outer;
    }

    buffer += char;
    index++;
  }
  flush();
  return nodes;
}

// ── Rendering ───────────────────────────────────────────────────────

function renderLines(lines: readonly string[], ctx: RenderContext): ReactNode[] {
  const nodes: ReactNode[] = [];
  lines.forEach((line, index) => {
    if (index > 0) nodes.push(<br key={`br-${index}`} />);
    // A trailing backslash is markdown's explicit line break.
    const text = index < lines.length - 1 && line.endsWith("\\") ? line.slice(0, -1) : line;
    nodes.push(<span key={`line-${index}`}>{parseInline(text, ctx, false)}</span>);
  });
  return nodes;
}

function renderBlock(block: Block, ctx: RenderContext, key: number): ReactNode {
  switch (block.type) {
    case "paragraph":
      return (
        <p key={key} className="break-words whitespace-pre-wrap">
          {renderLines(block.lines, ctx)}
        </p>
      );
    case "code":
      return <ChatCodeBlock key={key} language={block.language} code={block.code} />;
    case "quote":
      return (
        <blockquote key={key} className="border-muted-foreground/40 text-foreground/90 space-y-1 border-l-4 pl-3">
          {block.blocks.map((inner, index) => renderBlock(inner, ctx, index))}
        </blockquote>
      );
    case "list": {
      const items = block.items.map((item, index) => (
        <li key={index} className="break-words">
          {parseInline(item, ctx, false)}
        </li>
      ));
      return block.ordered ? (
        <ol key={key} start={block.start} className="list-decimal space-y-0.5 pl-6">
          {items}
        </ol>
      ) : (
        <ul key={key} className="list-disc space-y-0.5 pl-6">
          {items}
        </ul>
      );
    }
  }
}

/**
 * The message's blocks as React elements: mentions as chips with the
 * member's current name (or "unknown member"), `<#id>` as ticket chips that
 * link only where the reader may open the ticket, plain ticket keys as
 * links only where the server listed them in `linkedTickets`, and links to a
 * chat message through `renderMessageLink`.
 */
export function renderChatMarkdown(content: string, context: ChatMarkdownContext): ReactNode[] {
  const ctx = prepare(content, context);
  return parseBlocks(content).map((block, index) => renderBlock(block, ctx, index));
}

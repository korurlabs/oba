import { chatUserDisplayName } from "./render-chat-markdown";
import {
  CHAT_MENTION_EVERYONE,
  CHAT_MENTION_HERE,
  CHAT_TICKET_TOKEN_PATTERN,
  CHAT_USER_TOKEN_PATTERN,
  maskChatCode,
  type ChatMessageResponse,
} from "../schemas";

/** A user token (group 1: `everyone`, `here` or a user ID) or a ticket token (group 2: a ticket ID). */
const CHAT_TOKEN_PATTERN = new RegExp(`${CHAT_USER_TOKEN_PATTERN.source}|${CHAT_TICKET_TOKEN_PATTERN.source}`, "g");

/**
 * True for an entry the chat renders as a card and that changes only through
 * its own screen: a daily update or a shared pull request. Such an entry has
 * no text of its own, is not grouped with other messages, and cannot be
 * edited, deleted or given files in the chat.
 */
export function isChatEntryCard(message: Pick<ChatMessageResponse, "dailyUpdateId" | "pullRequestShareId">): boolean {
  return message.dailyUpdateId != null || message.pullRequestShareId != null;
}

/**
 * The paste-ready summary of a card entry, the same for every reader; empty
 * when its daily update or share is gone. Null for any other message.
 */
export function chatEntrySummary(message: ChatMessageResponse): string | null {
  if (message.dailyUpdateId != null) return message.dailyUpdate?.summaryText ?? "";
  if (message.pullRequestShareId != null) return message.pullRequestShare?.summaryText ?? "";
  return null;
}

/**
 * The readable text of a message with its line breaks kept: mention tokens
 * as `@Name`, ticket tokens as ticket keys, a daily update or a shared pull
 * request as its summary. Tokens inside code stay literal, as they render.
 */
export function chatMessageText(message: ChatMessageResponse, unknownMember: string): string {
  const summary = chatEntrySummary(message);
  if (summary !== null) return summary;
  const usersById = new Map(Object.entries(message.users).map(([id, user]) => [id.toLowerCase(), user]));
  const refsById = new Map(
    message.ticketRefs.flatMap((ref) => (ref.ticketId ? [[ref.ticketId.toLowerCase(), ref] as const] : [])),
  );
  // Code is blanked with spaces of the same length, so a token found in the
  // content is outside code exactly when the masked text still holds it. Both
  // kinds of token are replaced in one pass, so every offset is an offset in
  // the original content that the mask was made from.
  const masked = maskChatCode(message.content);
  return message.content.replace(
    CHAT_TOKEN_PATTERN,
    (token: string, target: string | undefined, ticketId: string | undefined, offset: number) => {
      if (!masked.startsWith(token, offset)) return token;
      if (ticketId !== undefined) return refsById.get(ticketId.toLowerCase())?.ticketKey ?? token;
      if (target === undefined) return token;
      return target === CHAT_MENTION_EVERYONE || target === CHAT_MENTION_HERE
        ? `@${target}`
        : `@${chatUserDisplayName(usersById.get(target.toLowerCase()), unknownMember)}`;
    },
  );
}

export interface ChatPlainTextOptions {
  /** An attachment-only message reads as its file names; true by default. */
  withFileNames?: boolean;
}

/**
 * The readable text of a message on one line, for snippets: whitespace
 * collapsed, and an attachment-only message as its file names.
 */
export function chatMessagePlainText(
  message: ChatMessageResponse,
  unknownMember: string,
  { withFileNames = true }: ChatPlainTextOptions = {},
): string {
  const text = chatMessageText(message, unknownMember).replace(/\s+/g, " ").trim();
  if (text || !withFileNames) return text;
  return message.attachments.map((attachment) => attachment.fileName).join(", ");
}

/** An opening fence with its language line, e.g. "```ts" before a line break. */
const FENCE_OPENING = /```[\w#+.-]*\n/g;
/** A markdown backslash escape; the escaped character is kept. */
const MARKDOWN_ESCAPE = /\\([\\`*_~<>[\]()#-])/g;
/** Emphasis delimiters, which a snippet drops. */
const EMPHASIS_DELIMITERS = /\*\*|__|~~/g;

/**
 * The start of a message as plain text, for the preview of a message link:
 * mentions as names, ticket tokens as keys, code and emphasis without their
 * markdown, and `<url>` without its brackets; cut at `max` characters with
 * an ellipsis.
 */
export function chatMessageSnippet(message: ChatMessageResponse, unknownMember: string, max: number): string {
  const text = chatMessageText(message, unknownMember)
    .replace(MARKDOWN_ESCAPE, "$1")
    .replace(FENCE_OPENING, " ")
    .replace(/`+/g, "")
    .replace(EMPHASIS_DELIMITERS, "")
    .replace(/<(https?:\/\/[^\s<>]+)>/g, "$1")
    .replace(/\s+/g, " ")
    .trim();
  const readable = text || message.attachments.map((attachment) => attachment.fileName).join(", ");
  return readable.length > max ? `${readable.slice(0, max).trimEnd()}…` : readable;
}

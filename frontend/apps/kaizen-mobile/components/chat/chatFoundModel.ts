import type { ChatListItem } from "@/components/chat/chatListModel";
import { CHAT_SEARCH_MAX, CHAT_SEARCH_MIN } from "@/lib/schemas/chat";

/*
 * The pure half of message search and pins: the text a search runs with,
 * the marked snippet of a found message, where a jump to it goes, and what
 * a message list does to show it. Without React, so `bun test` can load it.
 */

/** A message the list should bring into view; `seq` lets the same message be asked for twice. */
export interface ChatJumpTarget {
  messageId: string;
  seq: number;
}

/** The text a search runs with: trimmed and capped, or null while it is too short to search. */
export function chatSearchText(raw: string): string | null {
  const text: string = raw.trim().slice(0, CHAT_SEARCH_MAX).trim();
  return text.length >= CHAT_SEARCH_MIN ? text : null;
}

/** Characters of context kept before a match, so it stays in view in a clamped snippet. */
export const CHAT_SNIPPET_LEAD = 40;

export interface ChatSnippetParts {
  before: string;
  match: string;
  after: string;
}

/**
 * A snippet split around the first case-insensitive occurrence of `query`,
 * with at most `CHAT_SNIPPET_LEAD` characters before it (an ellipsis marks
 * the cut). The server matches the stored text, so a match inside a mention
 * or ticket token is not visible as text: the snippet is then unmarked.
 */
export function chatSnippetParts(text: string, query: string | null | undefined): ChatSnippetParts {
  const needle: string = query?.trim().toLowerCase() ?? "";
  const index: number = needle ? text.toLowerCase().indexOf(needle) : -1;
  if (index < 0) return { before: text, match: "", after: "" };
  const start: number = Math.max(0, index - CHAT_SNIPPET_LEAD);
  return {
    before: `${start > 0 ? "…" : ""}${text.slice(start, index)}`,
    match: text.slice(index, index + needle.length),
    after: text.slice(index + needle.length),
  };
}

/** Where a found message opens, seen from the screen it was found on. */
export type ChatFoundDestination =
  /** The channel screen under the search or pins: it shows the message itself or opens its thread. */
  | { kind: "request"; channelId: string; threadId: string | null; messageId: string }
  /** Another channel: push it, opening the thread from there when the message is a reply. */
  | { kind: "push"; channelId: string; threadId: string | null; messageId: string };

/**
 * A message found from a channel opens through that channel's screen; one
 * found from ChatHome (every channel) pushes its channel, so a thread always
 * opens over its channel, whose screen keeps the thread's caches current.
 */
export function chatFoundDestination(
  message: { id: string; channelId: string; threadId: string | null },
  openChannelId: string | null
): ChatFoundDestination {
  const target = {
    channelId: message.channelId,
    threadId: message.threadId,
    messageId: message.id,
  };
  return openChannelId === message.channelId
    ? { kind: "request", ...target }
    : { kind: "push", ...target };
}

/** What a message list does for a jump target. */
export type ChatJumpStep =
  /** Scroll to the row at `index` of the list items. */
  | { kind: "scroll"; index: number }
  /** The thread's root, drawn above the oldest reply: scroll to the oldest end. */
  | { kind: "root" }
  /** The message is not loaded: replace the pages with the page around it. */
  | { kind: "loadAround" }
  /** Loaded around it and still not listed: it was deleted or is out of reach. */
  | { kind: "missing" };

export function chatJumpStep(
  items: readonly ChatListItem[],
  messageId: string,
  rootId: string | null,
  loadedAround: boolean
): ChatJumpStep {
  const index: number = items.findIndex(
    (item) => item.kind === "message" && item.message.id === messageId
  );
  if (index >= 0) return { kind: "scroll", index };
  if (rootId === messageId) return { kind: "root" };
  return loadedAround ? { kind: "missing" } : { kind: "loadAround" };
}

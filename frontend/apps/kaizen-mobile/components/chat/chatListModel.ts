import { format } from "date-fns";

import { isChatEntryCard } from "@/components/chat/chatDisplay";
import {
  CHAT_GROUP_WINDOW_MS,
  CHAT_UNREAD_CAP,
  type ChatMessageResponse,
  type ChatOutboxMessage,
} from "@/lib/schemas/chat";

/*
 * The rows of a channel's message list: messages, the device's unconfirmed
 * sends, a separator per calendar day and the "New messages" divider. Built
 * oldest first and returned newest first, for an inverted list.
 */

export type ChatListItem =
  | { kind: "message"; key: string; message: ChatMessageResponse; grouped: boolean }
  | { kind: "outbox"; key: string; row: ChatOutboxMessage }
  | { kind: "day"; key: string; at: string }
  | { kind: "unread"; key: string };

export interface ChatListOptions {
  /** The first message that was unread when the conversation opened; the divider goes above it. */
  firstUnreadId: string | null;
  /** The calendar day of an instant in the device zone. */
  dayOf?: (iso: string) => string;
}

/** The reader's read position and unread count, taken once when the conversation opened. */
export interface ChatUnreadMarker {
  lastReadMessageId: string | null;
  lastReadAt: string | null;
  /** Unread messages of others, capped at `CHAT_UNREAD_CAP`. */
  count: number;
}

export const NO_CHAT_UNREAD_MARKER: ChatUnreadMarker = {
  lastReadMessageId: null,
  lastReadAt: null,
  count: 0,
};

/**
 * True when the read message is unknown: before the reader's first read of
 * the channel, when the server counts from the join time, and in a thread,
 * whose counts carry no read message. The first unread message is then
 * found by counting back from the newest.
 */
export function isChatUnreadSinceJoin(marker: ChatUnreadMarker): boolean {
  return !marker.lastReadMessageId && !marker.lastReadAt;
}

/**
 * The first unread message when the read message is unknown: `count`
 * messages back from the newest, counting only the messages the server
 * counts (live ones by someone else). A capped count only says that at
 * least that many are unread, and the reader's join time is not known, so
 * the message `CHAT_UNREAD_CAP` back is the oldest one known to be unread:
 * the conversation opens there instead of at its newest message. When the
 * conversation's first message is loaded (`startLoaded`) and fewer messages
 * than the count are left (some were deleted after it was counted), all of
 * them are unread and the oldest one is first. Null when the newest messages
 * are not loaded, when fewer are loaded and older ones remain, or when none
 * counts.
 */
export function findChatFirstUnreadSinceJoin(
  messages: readonly ChatMessageResponse[],
  count: number,
  meId: string | undefined,
  atNewest: boolean,
  startLoaded: boolean
): string | null {
  if (!atNewest || count <= 0) return null;
  let remaining: number = Math.min(count, CHAT_UNREAD_CAP);
  let oldestCounted: string | null = null;
  for (let index = messages.length - 1; index >= 0; index--) {
    const message: ChatMessageResponse | undefined = messages[index];
    if (!message || message.deleted || !message.author || message.author.userId === meId) continue;
    remaining -= 1;
    oldestCounted = message.id;
    if (remaining === 0) return message.id;
  }
  return startLoaded ? oldestCounted : null;
}

/**
 * The first message after the read position that someone else wrote: the
 * divider goes above it. Null when every loaded message is read, or when the
 * read message is older than the loaded ones (`startLoaded` false) and
 * nothing loaded is known to be read.
 */
export function findChatFirstUnread(
  messages: readonly ChatMessageResponse[],
  marker: ChatUnreadMarker,
  meId: string | undefined,
  startLoaded: boolean
): string | null {
  let startIndex: number;
  const readIndex: number = marker.lastReadMessageId
    ? messages.findIndex((message) => message.id === marker.lastReadMessageId)
    : -1;
  if (readIndex >= 0) {
    startIndex = readIndex + 1;
  } else if (marker.lastReadAt) {
    const lastReadTime: number = Date.parse(marker.lastReadAt);
    startIndex = messages.findIndex((message) => Date.parse(message.createdAt) > lastReadTime);
    if (startIndex < 0) return null;
    if (startIndex === 0 && !startLoaded) return null;
  } else {
    return null;
  }
  for (let index = startIndex; index < messages.length; index++) {
    const message: ChatMessageResponse | undefined = messages[index];
    if (message && message.author?.userId !== meId) return message.id;
  }
  return null;
}

function localDay(iso: string): string {
  return format(new Date(iso), "yyyy-MM-dd");
}

/**
 * True when `message` continues `previous` under one author header: the same
 * author within `CHAT_GROUP_WINDOW_MS`, on the same day, and neither is a
 * card entry, a deleted placeholder or a reply (a reply shows its quote).
 */
export function isChatGroupedWith(
  previous: ChatMessageResponse | null,
  message: ChatMessageResponse,
  dayOf: (iso: string) => string = localDay
): boolean {
  if (!previous || !previous.author || !message.author) return false;
  if (previous.author.userId !== message.author.userId) return false;
  if (previous.deleted || message.deleted || message.replyTo) return false;
  if (isChatEntryCard(previous) || isChatEntryCard(message)) return false;
  if (dayOf(previous.createdAt) !== dayOf(message.createdAt)) return false;
  const gap: number = Date.parse(message.createdAt) - Date.parse(previous.createdAt);
  return gap >= 0 && gap <= CHAT_GROUP_WINDOW_MS;
}

export function buildChatListItems(
  messages: readonly ChatMessageResponse[],
  outbox: readonly ChatOutboxMessage[],
  { firstUnreadId, dayOf = localDay }: ChatListOptions
): ChatListItem[] {
  const items: ChatListItem[] = [];
  let currentDay: string | null = null;
  let previous: ChatMessageResponse | null = null;

  const addDay = (iso: string): void => {
    const day: string = dayOf(iso);
    if (day === currentDay) return;
    currentDay = day;
    items.push({ kind: "day", key: `day-${day}`, at: iso });
  };

  for (const message of messages) {
    addDay(message.createdAt);
    if (message.id === firstUnreadId) items.push({ kind: "unread", key: "unread" });
    const grouped: boolean =
      items[items.length - 1]?.kind === "message" && isChatGroupedWith(previous, message, dayOf);
    items.push({ kind: "message", key: message.id, message, grouped });
    previous = message;
  }
  for (const row of outbox) {
    addDay(row.createdAt);
    items.push({ kind: "outbox", key: row.clientNonce, row });
  }
  return items.reverse();
}

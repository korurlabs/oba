import { isSameDay, subDays } from "date-fns";
import { RTM_SEC_002 } from "@oba/shared/realtime";

import { chatMessageSnippet } from "@/components/chat/chatDisplay";
import { chatLinkHref, type ChatLinkHref } from "@/lib/kazanLinks";
import type { RealtimeSnapshot } from "@/lib/realtime";
import {
  ChatChannelPurpose,
  type ChatChannelResponse,
  type ChatConversationUnreadResponse,
  type ChatMessageResponse,
  type ChatUnread,
} from "@/lib/schemas/chat";

/*
 * The decisions of the chat screens that need no React: which banner the
 * connection shows, how ChatHome groups the channels, the preview line of a
 * channel, the typing line and when the reader's position moves.
 */

// ── Connection banner ───────────────────────────────────────────────

/** How long a lost connection may try again before the banner says so. */
export const CHAT_RECONNECTING_BANNER_DELAY_MS = 3000;

export type ChatConnectionBanner = "networkOffline" | "noAccess" | "offline" | "reconnecting";

/**
 * The banner over a chat screen, or null while live. The network comes
 * first, because without it every other state follows; a refusal means the
 * reader lost access, which no retry fixes; a short reconnect stays quiet
 * until `stalled` says it lasted `CHAT_RECONNECTING_BANNER_DELAY_MS`.
 */
export function chatConnectionBanner(
  snapshot: Pick<RealtimeSnapshot, "status" | "errorCode" | "networkOnline">,
  stalled: boolean
): ChatConnectionBanner | null {
  if (!snapshot.networkOnline) return "networkOffline";
  if (snapshot.status === "offline") {
    return snapshot.errorCode === RTM_SEC_002 ? "noAccess" : "offline";
  }
  if (snapshot.status === "connected") return null;
  return stalled ? "reconnecting" : null;
}

// ── ChatHome ────────────────────────────────────────────────────────

export interface ChatHomeSections {
  /** The daily updates channel, then the pull requests channel, when they exist. */
  purpose: ChatChannelResponse[];
  /** The general channels in the reader's order. */
  chats: ChatChannelResponse[];
}

const PURPOSE_ORDER: readonly string[] = [
  ChatChannelPurpose.DAILY_UPDATES,
  ChatChannelPurpose.PULL_REQUESTS,
];

/** Splits the ordered channels into the purpose rows on top and the chats below. */
export function chatHomeSections(ordered: readonly ChatChannelResponse[]): ChatHomeSections {
  const purpose: ChatChannelResponse[] = PURPOSE_ORDER.flatMap((kind) => {
    const channel: ChatChannelResponse | undefined = ordered.find((item) => item.purpose === kind);
    return channel ? [channel] : [];
  });
  const chats: ChatChannelResponse[] = ordered.filter(
    (channel) => channel.purpose === ChatChannelPurpose.GENERAL
  );
  return { purpose, chats };
}

/**
 * The channels of the switcher: the general channels in the reader's order
 * whose name holds the search text, ignoring case. Daily updates and pull
 * requests open only from ChatHome, so they are not listed.
 */
export function chatSwitcherChannels(
  ordered: readonly ChatChannelResponse[],
  search: string
): ChatChannelResponse[] {
  const text: string = search.trim().toLowerCase();
  return chatHomeSections(ordered).chats.filter(
    (channel) => !text || channel.name.toLowerCase().includes(text)
  );
}

/** The reader's unread entry of one channel itself (not its threads). */
export function chatChannelUnread(
  unread: ChatUnread | undefined,
  channelId: string | null | undefined
): ChatConversationUnreadResponse | undefined {
  if (!channelId) return undefined;
  return unread?.channels.find((entry) => entry.id === channelId);
}

/** The reader's unread entry of one thread; there is none while the reader is no member. */
export function chatThreadUnread(
  unread: ChatUnread | undefined,
  threadId: string | null | undefined
): ChatConversationUnreadResponse | undefined {
  if (!threadId) return undefined;
  return unread?.threads.find((entry) => entry.id === threadId);
}

export interface ChatPreviewLabels {
  /** Who a message of the reader is from: "You". */
  you: string;
  unknownMember: string;
}

/** Characters of a channel preview before the ellipsis. */
export const CHAT_PREVIEW_MAX = 80;

/**
 * The second line of a channel row: "Author: text", "You: text" for the
 * reader's own message, the bare text of a message without author, or null
 * when the channel has no message yet.
 */
export function chatPreviewLine(
  message: ChatMessageResponse | null | undefined,
  meId: string | undefined,
  labels: ChatPreviewLabels,
  authorName: (message: ChatMessageResponse) => string
): string | null {
  if (!message) return null;
  const text: string = chatMessageSnippet(message, labels.unknownMember, CHAT_PREVIEW_MAX);
  if (!message.author) return text;
  const author: string = meId && message.author.userId === meId ? labels.you : authorName(message);
  return `${author}: ${text}`;
}

// ── Re-purposed channels ────────────────────────────────────────────

/**
 * Where the daily updates or pull requests screen goes when the channel it
 * showed no longer holds the screen's purpose: an owner or admin gave it
 * another one, and the channel now opens where that purpose is shown. Null
 * while a channel holds the purpose (the screen shows that one), while the
 * shown channel is unknown or gone from the list, and when the reader cannot
 * read it; the screen then keeps its own empty state.
 */
export function chatPurposeScreenFollow(
  purpose: ChatChannelPurpose,
  shownChannelId: string,
  channels: readonly ChatChannelResponse[] | undefined
): ChatLinkHref | null {
  if (!channels || !shownChannelId) return null;
  if (channels.some((channel) => channel.purpose === purpose)) return null;
  const shown: ChatChannelResponse | undefined = channels.find(
    (channel) => channel.id === shownChannelId
  );
  if (!shown || !shown.member) return null;
  return chatLinkHref(shown, null, null);
}

/** The channel the plain channel screen last showed, and its purpose then. */
export interface ChatShownChannel {
  channelId: string;
  purpose: string;
}

/**
 * Where the plain channel screen goes when its channel becomes a daily
 * updates or pull requests channel, which have screens of their own: the
 * channel was given that purpose while shown, or the screen switched to a
 * channel just created with it. Null for a general channel, for a channel
 * the reader cannot read, and for the channel the screen opened on, which
 * may be a purpose channel on purpose: a link or a search result shows its
 * message here, where any message can be paged to. Null as well while the
 * screen shows such a linked message, so the reader keeps it in view when the
 * channel is given a purpose meanwhile.
 */
export function chatChannelScreenFollow(
  previous: ChatShownChannel | null,
  channel: ChatChannelResponse | undefined,
  linkedMessageId: string | null
): ChatLinkHref | null {
  if (linkedMessageId) return null;
  if (!previous || !channel || !channel.member) return null;
  if (channel.purpose === ChatChannelPurpose.GENERAL) return null;
  if (previous.channelId === channel.id && previous.purpose === channel.purpose) return null;
  return chatLinkHref(channel, null, null);
}

// ── Typing ──────────────────────────────────────────────────────────

export type ChatTypingLine =
  | { key: "chatTypingOne"; params: { name: string } }
  | { key: "chatTypingTwo"; params: { first: string; second: string } }
  | { key: "chatTypingMany"; params: Record<string, never> };

/** The translation of "who is typing" for the names known so far, or null for nobody. */
export function chatTypingLine(names: readonly string[]): ChatTypingLine | null {
  const [first, second] = names;
  if (first === undefined) return null;
  if (second === undefined) return { key: "chatTypingOne", params: { name: first } };
  if (names.length === 2) return { key: "chatTypingTwo", params: { first, second } };
  return { key: "chatTypingMany", params: {} };
}

// ── Read position ───────────────────────────────────────────────────

export interface ChatMarkReadInput {
  /** The reader sees the conversation's newest message (`isChatNewestSeen`). */
  newestSeen: boolean;
  /** The app is in the foreground and the screen is focused. */
  visible: boolean;
  /** The conversation has unread messages or notifications. */
  pending: boolean;
  newestMessageId: string | null | undefined;
  /** The message the last request moved the position to while still pending. */
  lastSentMessageId: string | null;
}

/**
 * True when the reader's position should move to the newest message: the
 * reader sees it, something is still unread, and that message was not sent
 * already, so a stuck notification is not requested in a loop.
 */
export function shouldMarkChatRead(input: ChatMarkReadInput): boolean {
  if (!input.pending || !input.newestSeen || !input.visible) return false;
  if (!input.newestMessageId) return false;
  return input.lastSentMessageId !== input.newestMessageId;
}

// ── Day separators ──────────────────────────────────────────────────

export type ChatDayKind = "today" | "yesterday" | "date";

/** Whether a day separator reads "Today", "Yesterday" or the date, in the device zone. */
export function chatDayKind(at: string, now: Date = new Date()): ChatDayKind {
  const day = new Date(at);
  if (isSameDay(day, now)) return "today";
  if (isSameDay(day, subDays(now, 1))) return "yesterday";
  return "date";
}

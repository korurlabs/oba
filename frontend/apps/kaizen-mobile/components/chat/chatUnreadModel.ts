import { isChatUnreadSinceJoin, type ChatUnreadMarker } from "@/components/chat/chatListModel";
import { BUSINESS_TIME_ZONE, dayInZone } from "@/lib/dates";
import {
  CHAT_PAGE_SIZE,
  CHAT_UNREAD_CAP,
  type ChatConversationUnreadResponse,
  type ChatMessageCursor,
  type ChatMessagePage,
  type ChatMessageResponse,
} from "@/lib/schemas/chat";

/*
 * Where a conversation with unread messages opens, and when it counts as
 * read. It opens with the "New messages" divider in view, loading older
 * messages first when the divider lies before the loaded ones. When the
 * divider lies beyond the pages a list looks through, it opens at the oldest
 * loaded message instead, which is unread too. When the conversation's first
 * message is loaded and no divider was found, nothing is unread after all
 * (the count was taken before messages were deleted), and it stays at the
 * bottom. It is read once the reader sees its newest message:
 * at the bottom of the list, whether the list opened there because
 * everything unread fits on screen or the reader scrolled down to it.
 * Opening alone reads nothing, whatever the unread count says.
 */

/**
 * Pages a list loads, counting back from the newest message, to find the
 * first unread one: enough for a count just under the cap, and below
 * `CHAT_MAX_PAGES`, so the newest page is never dropped while looking.
 */
export const CHAT_SEEK_UNREAD_MAX_PAGES: number = Math.ceil(CHAT_UNREAD_CAP / CHAT_PAGE_SIZE) + 2;

/** What a list does when it opens. */
export type ChatUnreadOpenStep =
  /** Nothing unread, a message to jump to, or the reader already scrolled: the list stays. */
  | { kind: "stay" }
  /** The divider is loaded: scroll it into view. */
  | { kind: "divider" }
  /** Load older pages until the divider is loaded. */
  | { kind: "seekOlder" }
  /** Replace the pages by the page around the read message, which the divider follows. */
  | { kind: "loadAround"; messageId: string }
  /**
   * The read message is not known, only when it was read: find a message
   * of that time (`findChatMessageNearReadTime`) and load around it.
   */
  | { kind: "locateByTime"; lastReadAt: string };

export interface ChatUnreadOpenInput {
  marker: ChatUnreadMarker;
  /** The "New messages" divider is among the list's rows. */
  dividerLoaded: boolean;
  /** The loaded messages, oldest first. */
  messages: readonly ChatMessageResponse[];
  atNewest: boolean;
  hasPreviousPage: boolean;
  /** A message named on opening is shown instead. */
  jumping: boolean;
  atBottom: boolean;
  /**
   * The read message lies before what the list can show (a daily updates
   * day after the day it was read), so loading around it cannot show the
   * divider; the divider is above the day's first unread message instead.
   */
  readBeforeList: boolean;
  /** The list can find a message by its time (a channel; a thread page has no day filter). */
  canLocateByTime: boolean;
}

export function chatUnreadOpenStep(input: ChatUnreadOpenInput): ChatUnreadOpenStep {
  const { marker, messages } = input;
  if (marker.count === 0 || input.jumping || !input.atBottom) return { kind: "stay" };
  if (input.dividerLoaded) return { kind: "divider" };
  // Everything is loaded and no divider: the unread messages are the reader's own.
  if (!input.hasPreviousPage) return { kind: "stay" };
  if (isChatUnreadSinceJoin(marker)) {
    // Counted back from the newest; a capped count leads to the oldest message known to be unread.
    return input.atNewest ? { kind: "seekOlder" } : { kind: "stay" };
  }
  const oldest: ChatMessageResponse | undefined = messages[0];
  const readBeforeLoaded: boolean =
    !!marker.lastReadAt && !!oldest && Date.parse(oldest.createdAt) > Date.parse(marker.lastReadAt);
  if (!readBeforeLoaded || !marker.lastReadAt) return { kind: "stay" };
  if (input.readBeforeList) return { kind: "seekOlder" };
  if (marker.lastReadMessageId) {
    return { kind: "loadAround", messageId: marker.lastReadMessageId };
  }
  return chatUnreadReadMessageGoneStep({
    lastReadAt: marker.lastReadAt,
    canLocateByTime: input.canLocateByTime,
  });
}

/**
 * The next move when the read message is gone (a row the server no longer
 * has, or one the read state no longer names): the read time still says
 * where the unread messages begin. A list that cannot search by time looks
 * through older pages instead.
 */
export function chatUnreadReadMessageGoneStep(input: {
  lastReadAt: string | null;
  canLocateByTime: boolean;
}): ChatUnreadOpenStep {
  if (input.lastReadAt && input.canLocateByTime) {
    return { kind: "locateByTime", lastReadAt: input.lastReadAt };
  }
  return { kind: "seekOlder" };
}

/**
 * Pages of the read day a list looks through for the read time: a day busier
 * than this falls back to looking through older pages of the list.
 */
export const CHAT_LOCATE_READ_MAX_PAGES = 10;

/**
 * Reads one page of a channel's messages posted on a business day
 * (`YYYY-MM-DD`); the request is cancelled when `signal` fires.
 */
export type ChatDayPageFetcher = (
  day: string,
  cursor: ChatMessageCursor,
  signal: AbortSignal
) => Promise<ChatMessagePage>;

/**
 * A message to load around so the divider is in view, found by the read
 * time alone: the server has no time cursor, but it filters a page by the
 * business day, so the read day is paged back from its newest message. The
 * result is the newest message at or before the read time, or, when the
 * read time comes before the whole day, the day's first message. Null when
 * the day has no messages or is too busy to page through, so the caller
 * looks another way, and when `signal` fired: the list that asked is gone,
 * so no further page is read. A failed page rejects; so does the page in
 * flight when `signal` fires, which the caller recognises by the signal.
 */
export async function findChatMessageNearReadTime(
  lastReadAt: string,
  fetchDayPage: ChatDayPageFetcher,
  signal: AbortSignal
): Promise<string | null> {
  const day: string | null = dayInZone(lastReadAt, BUSINESS_TIME_ZONE);
  if (!day) return null;
  const readTime: number = Date.parse(lastReadAt);
  let cursor: ChatMessageCursor = {};
  for (let fetched = 0; fetched < CHAT_LOCATE_READ_MAX_PAGES; fetched++) {
    if (signal.aborted) return null;
    const page: ChatMessagePage = await fetchDayPage(day, cursor, signal);
    const oldest: ChatMessageResponse | undefined = page.items[0];
    if (!oldest) return null;
    for (let index = page.items.length - 1; index >= 0; index--) {
      const message: ChatMessageResponse | undefined = page.items[index];
      if (message && Date.parse(message.createdAt) <= readTime) return message.id;
    }
    if (!page.hasOlder) return oldest.id;
    cursor = { before: oldest.id };
  }
  return null;
}

/**
 * The row a list scrolls to when it opens with unread messages: the
 * divider, or, when the page limit was reached before the divider, the
 * oldest loaded row (the top of the inverted list). Every loaded message is
 * then unread, so the reader starts above them and reads down. -1 when the
 * list has no rows.
 */
export function chatUnreadOpenIndex(input: { dividerIndex: number; rowCount: number }): number {
  return input.dividerIndex >= 0 ? input.dividerIndex : input.rowCount - 1;
}

/**
 * The next move while older pages load to find the divider.
 * - `pageLimit`: the page limit was reached without it; it lies further back,
 *   so every loaded message is unread and the list opens at its oldest loaded
 *   row (`chatUnreadOpenIndex`).
 * - `atStart`: the conversation's first message is loaded without it. The
 *   divider goes above the first unread message whenever the start is loaded
 *   (`findChatFirstUnread`, `findChatFirstUnreadSinceJoin`), so no loaded
 *   message is unread: the count was stale, and the list stays at the bottom.
 */
export type ChatUnreadSeekStep = "found" | "fetch" | "wait" | "failed" | "pageLimit" | "atStart";

export interface ChatUnreadSeekInput {
  dividerLoaded: boolean;
  pageCount: number;
  hasPreviousPage: boolean;
  isFetchingPreviousPage: boolean;
  isFetchPreviousPageError: boolean;
}

export function chatUnreadSeekStep(input: ChatUnreadSeekInput): ChatUnreadSeekStep {
  if (input.dividerLoaded) return "found";
  if (input.isFetchPreviousPageError) return "failed";
  if (!input.hasPreviousPage) return "atStart";
  if (input.pageCount >= CHAT_SEEK_UNREAD_MAX_PAGES) return "pageLimit";
  if (input.isFetchingPreviousPage) return "wait";
  return "fetch";
}

/**
 * How far the list is with placing the reader at the divider:
 * - `deciding`: the first page, the read position or the reader is not known yet;
 * - `seeking`, `loadingAround`, `aroundLoaded`: older messages load to reach the divider;
 * - `placing`: the list was scrolled to the divider, or to its oldest loaded row when the
 *   divider could not be found, and the scroll has not landed;
 * - `failed`: loading failed, and the reader has not moved since;
 * - `settled`: the reader is where the list opened, or moved on.
 */
export type ChatUnreadOpenPhase =
  | "deciding"
  | "seeking"
  | "loadingAround"
  | "aroundLoaded"
  | "placing"
  | "failed"
  | "settled";

/**
 * The phase a list enters for its open step. Only `stay` settles at once:
 * nothing is unread, or the reader is placed by a jump or by hand. Every
 * other step first reaches the divider (or the oldest loaded row), so the
 * bottom the list opened at never counts as read.
 */
export function chatUnreadPhaseOf(step: ChatUnreadOpenStep): ChatUnreadOpenPhase {
  switch (step.kind) {
    case "stay":
      return "settled";
    case "divider":
      return "placing";
    case "seekOlder":
      return "seeking";
    case "loadAround":
    case "locateByTime":
      return "loadingAround";
  }
}

export interface ChatReadGateInput {
  phase: ChatUnreadOpenPhase;
  /** The list shows the conversation's own sequence (no past day, no filter). */
  live: boolean;
  /** The list's messages are loaded. */
  loaded: boolean;
  /** The newest page is loaded. */
  atNewest: boolean;
  /** The bottom of the list, the newest message, is on screen. */
  atBottom: boolean;
}

/**
 * True when the reader sees the conversation's newest message, so it may be
 * marked read: the newest page is loaded and on screen, and the list is not
 * still reaching the divider. A failed load holds it until the reader moves,
 * so a list stuck at the newest page does not read the messages it could not
 * show; reaching the bottom again after that counts.
 */
export function isChatNewestSeen(input: ChatReadGateInput): boolean {
  if (!input.live || !input.loaded || !input.atNewest || !input.atBottom) return false;
  return input.phase === "settled";
}

/** Within this distance of the newest message the reader counts as at the bottom. */
export const CHAT_AT_BOTTOM_PX = 48;

/** The marker of a conversation entry, kept as it was when the conversation opened. */
export function chatUnreadMarkerOf(
  entry: ChatConversationUnreadResponse | undefined
): ChatUnreadMarker {
  return {
    lastReadMessageId: entry?.lastReadMessageId ?? null,
    lastReadAt: entry?.lastReadAt ?? null,
    count: entry?.unread ?? 0,
  };
}

/** The read position a conversation took when it opened. */
export interface ChatHeldUnreadMarker {
  /** The channel, or the thread, the marker belongs to. */
  conversationId: string;
  marker: ChatUnreadMarker;
}

export interface ChatUnreadStateInput {
  /** The reader's unread state has been loaded at least once. */
  loaded: boolean;
  /** The conversation's entry in it; none when nothing is unread or the reader is not on it. */
  entry: ChatConversationUnreadResponse | undefined;
}

/**
 * The marker a conversation holds: taken from the first unread state loaded
 * while it is open and kept after that, so the divider stays put while the
 * read position moves. Until the unread state is loaded, a failed load
 * included, the conversation holds none: where its unread messages begin is
 * unknown, so the list neither opens at a divider nor reads anything, and it
 * takes the real marker once a later load succeeds.
 */
export function heldChatUnreadMarker(
  held: ChatHeldUnreadMarker | null,
  conversationId: string,
  unread: ChatUnreadStateInput
): ChatHeldUnreadMarker | null {
  if (held?.conversationId === conversationId) return held;
  if (!unread.loaded) return held;
  return { conversationId, marker: chatUnreadMarkerOf(unread.entry) };
}

/**
 * True when an open conversation still waits for its read position because
 * loading the unread state failed: the reader is told, since the list holds
 * its divider and its reading until a load succeeds.
 */
export function isChatUnreadStateFailed(input: {
  markerKnown: boolean;
  isError: boolean;
}): boolean {
  return !input.markerKnown && input.isError;
}

/**
 * Whether messages added at the newest end may scroll the list to them
 * (`autoscrollToTopThreshold`): only while the newest page was already
 * loaded before they came, so they are arrivals the reader at the bottom
 * watches. A newer page loaded while the reader reads down from the divider
 * is added without the scroll, also the page that turns out to be the
 * newest, or the reader would skip its unread messages.
 */
export function chatAutoscrollThreshold(input: {
  atNewest: boolean;
  /** The newest page was loaded when the list last rendered. */
  wasAtNewest: boolean;
}): number | undefined {
  return input.atNewest && input.wasAtNewest ? CHAT_AT_BOTTOM_PX : undefined;
}

/**
 * True when messages were added below the reader's newest one while the list
 * kept its place: the earlier newest message is still loaded but no longer
 * the newest, and nothing scrolled to the new ones. The reader's distance
 * from the bottom then grows by their height, whether or not a scroll event
 * reports it (`chatBottomAfterNewerAdded`).
 */
export function isChatNewerPageAddedInPlace(input: {
  previousNewestId: string | undefined;
  messages: readonly ChatMessageResponse[];
  autoscroll: boolean;
}): boolean {
  if (input.autoscroll || !input.previousNewestId) return false;
  const index: number = input.messages.findIndex(
    (message: ChatMessageResponse) => message.id === input.previousNewestId
  );
  return index >= 0 && index < input.messages.length - 1;
}

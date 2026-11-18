/**
 * Query-key factory and keyset page parameters of the chat.
 *
 * Not marked "use client": the chat pages import these on the server
 * (`features/chat/components/chat-route-page.tsx`) to prefetch the channel
 * list, the unread counts and the newest channel page under the exact keys
 * the client hooks use. Inside a "use client" module the functions would
 * become client-reference proxies and could not be called on the server.
 *
 * Every key holds the organization ID right after the "chat" prefix: the
 * Axios client sends the session's default organization as
 * `X-Organization-ID`, so a switch of organization reads other keys and never
 * shows another organization's channels. `all(orgId)` covers every chat query
 * of one organization; the keys of one channel's content hold its ID next.
 *
 * Message lists (channel, thread, pins, search) cache `InfiniteData` of
 * `ChatMessagePage`, the unwrapped `data` of the response; the other queries
 * cache the unwrapped `data` too, so realtime signals write them directly.
 */
import type { QueryKey } from "@tanstack/react-query";
import type { ChatMessageCursor, ChatMessagePage, ChatThreadPage, ChatThreadStatus } from "../schemas";
import { resolvePullRequestStatuses } from "@/features/pull-requests/lib/pull-request-channel-filter";
import type { PullRequestShareStatus } from "@/features/pull-requests/schemas";

/**
 * What a channel list is narrowed to: when set, one day (YYYY-MM-DD in
 * Europe/Istanbul), one author, and share statuses (any of them). The daily
 * updates channel always sends a day; the pull requests channel sends no day
 * and only the fields its reader chose. Each combination is its own cache
 * entry.
 */
export interface ChatChannelFilter {
  day: string | null;
  authorId: string | null;
  /** Keeps only pull request share entries with one of these statuses; empty keeps every message. */
  pullRequestStatuses: PullRequestShareStatus[];
}

const CHANNEL_FILTER_SEGMENT = "filter";

export const CHAT_KEYS = {
  all: (orgId: string) => ["chat", orgId] as const,
  /** The `ChatChannelResponse[]` the reader sees. */
  channels: (orgId: string) => [...CHAT_KEYS.all(orgId), "channels"] as const,
  /** Every message list of every channel. */
  channelMessagesAll: (orgId: string) => [...CHAT_KEYS.all(orgId), "channel"] as const,
  /**
   * A channel from its newest page; `anchor` opens it around one message
   * instead (a jump, the unread divider), and `filter` narrows it to a day,
   * an author and share statuses. Without `anchor` and `filter` the key is
   * the prefix of every list of the channel, filtered and anchored ones
   * included. The statuses form one comma-joined segment, so the caller
   * passes them in one fixed order (`resolvePullRequestStatuses`) for the
   * server page and the client view to build the same key.
   */
  channel: (orgId: string, channelId: string, anchor?: string | null, filter?: ChatChannelFilter | null) => {
    const base = filter
      ? ([
          ...CHAT_KEYS.channelMessagesAll(orgId),
          channelId,
          CHANNEL_FILTER_SEGMENT,
          filter.day,
          filter.authorId,
          filter.pullRequestStatuses.join(","),
        ] as const)
      : ([...CHAT_KEYS.channelMessagesAll(orgId), channelId] as const);
    return anchor ? ([...base, "around", anchor] as const) : base;
  },
  /** A thread's messages, as `channel`. */
  thread: (orgId: string, threadId: string, anchor?: string | null) =>
    anchor
      ? ([...CHAT_KEYS.all(orgId), "thread-messages", threadId, "around", anchor] as const)
      : ([...CHAT_KEYS.all(orgId), "thread-messages", threadId] as const),
  /** Every message list of every thread. */
  threadMessagesAll: (orgId: string) => [...CHAT_KEYS.all(orgId), "thread-messages"] as const,
  /** One thread's `ChatThreadResponse`. */
  threadDetail: (orgId: string, threadId: string) =>
    [...CHAT_KEYS.all(orgId), "thread", threadId] as const,
  /** Every thread list of every channel (active, closed and archived). */
  threadsAll: (orgId: string) => [...CHAT_KEYS.all(orgId), "threads"] as const,
  /** Every thread list of one channel. */
  channelThreads: (orgId: string, channelId: string) => [...CHAT_KEYS.threadsAll(orgId), channelId] as const,
  threads: (orgId: string, channelId: string, status: ChatThreadStatus) =>
    [...CHAT_KEYS.channelThreads(orgId, channelId), status] as const,
  /** The pins of every channel. */
  pinsAll: (orgId: string) => [...CHAT_KEYS.all(orgId), "pins"] as const,
  pins: (orgId: string, channelId: string) => [...CHAT_KEYS.pinsAll(orgId), channelId] as const,
  /** Every search. */
  searchAll: (orgId: string) => [...CHAT_KEYS.all(orgId), "search"] as const,
  /** `channelId` null searches every readable channel. */
  search: (orgId: string, q: string, authorId: string | null, channelId: string | null = null) =>
    [...CHAT_KEYS.searchAll(orgId), channelId, q, authorId] as const,
  /** Every member list. */
  membersAll: (orgId: string) => [...CHAT_KEYS.all(orgId), "members"] as const,
  /**
   * The organization's active members (`channelId` null), a private
   * channel's members, or with `authorsOnly` a channel's message authors.
   */
  members: (orgId: string, channelId: string | null = null, authorsOnly: boolean = false) =>
    [...CHAT_KEYS.channelMembers(orgId, channelId), authorsOnly] as const,
  /** A channel's member and author lists; `channelId` null is the organization's list. */
  channelMembers: (orgId: string, channelId: string | null) => [...CHAT_KEYS.membersAll(orgId), channelId] as const,
  /** One message by ID, the preview of a message link; one entry per message however often it is linked. */
  message: (orgId: string, messageId: string) => [...CHAT_KEYS.all(orgId), "message", messageId] as const,
  unread: (orgId: string) => [...CHAT_KEYS.all(orgId), "unread"] as const,
};

/** Segments right after the organization ID whose data is `InfiniteData<ChatMessagePage>`. */
const MESSAGE_LIST_SEGMENTS: ReadonlySet<unknown> = new Set(["channel", "thread-messages", "pins", "search"]);

/** True for the keys of every message list of `orgId`: channel, threads, pins and search. */
export function isChatMessageListKey(orgId: string, queryKey: readonly unknown[]): boolean {
  return queryKey[0] === "chat" && queryKey[1] === orgId && MESSAGE_LIST_SEGMENTS.has(queryKey[2]);
}

/** The filter of a channel list key built by `CHAT_KEYS.channel`; null for an unfiltered list or any other key. */
export function chatChannelFilterOf(queryKey: QueryKey): ChatChannelFilter | null {
  if (queryKey[2] !== "channel" || queryKey[4] !== CHANNEL_FILTER_SEGMENT) return null;
  const day = queryKey[5];
  const authorId = queryKey[6];
  const pullRequestStatuses = queryKey[7];
  return {
    day: typeof day === "string" ? day : null,
    authorId: typeof authorId === "string" ? authorId : null,
    pullRequestStatuses: typeof pullRequestStatuses === "string" ? resolvePullRequestStatuses(pullRequestStatuses) : [],
  };
}

// ── Keyset page parameters ──────────────────────────────────────────
// Channel and thread pages are oldest first. The first page is the newest
// one, or the page around an anchor; older pages are fetched as previous
// pages and newer ones as next pages.

/** The first page of a message list: the newest messages, or those around `anchor`. */
export function chatFirstPageParam(anchor?: string | null): ChatMessageCursor {
  return anchor ? { around: anchor } : {};
}

export function chatOlderPageParam(firstPage: ChatMessagePage): ChatMessageCursor | undefined {
  const oldest = firstPage.items[0];
  return firstPage.hasOlder && oldest ? { before: oldest.id } : undefined;
}

export function chatNewerPageParam(lastPage: ChatMessagePage): ChatMessageCursor | undefined {
  const newest = lastPage.items[lastPage.items.length - 1];
  return lastPage.hasNewer && newest ? { after: newest.id } : undefined;
}

/** Pins and search are newest first: the next page continues with older entries. */
export function chatNewestFirstNextPageParam(lastPage: ChatMessagePage): string | undefined {
  const oldest = lastPage.items[lastPage.items.length - 1];
  return lastPage.hasOlder && oldest ? oldest.id : undefined;
}

export function chatThreadsNextPageParam(lastPage: ChatThreadPage): string | undefined {
  const last = lastPage.items[lastPage.items.length - 1];
  return lastPage.hasMore && last ? last.id : undefined;
}

/**
 * The message list is not virtualised, so rendered rows are capped at about
 * 300: an infinite message list keeps at most this many 50-message pages and
 * drops the farthest ones.
 */
export const CHAT_MAX_PAGES = 6;

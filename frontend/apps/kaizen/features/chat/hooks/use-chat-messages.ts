"use client";

import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useSession } from "next-auth/react";
import {
  useInfiniteQuery,
  useQuery,
  type InfiniteData,
  type QueryClient,
} from "@tanstack/react-query";
import { isApiError } from "@oba/shared/api/axios";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import {
  getChatMessage,
  getChatThread,
  listChannelMessages,
  listChatMembers,
  listChatPins,
  listChatThreads,
  listThreadMessages,
  requireChatData,
  searchChatMessages,
} from "../api";
import { updateDayOf, updateToday } from "@/features/updates/lib/update-day";
import { isNewerChatVersion } from "../lib/chat-realtime";
import {
  CHAT_PAGE_SIZE,
  CHAT_SEARCH_MIN,
  CHAT_SEARCH_PAGE_SIZE,
  type ChatMessageCursor,
  type ChatMessagePage,
  type ChatMessageResponse,
  type ChatOutboxMessage,
  type ChatThreadResponse,
  type ChatThreadStatus,
} from "../schemas";
import {
  CHAT_KEYS,
  CHAT_MAX_PAGES,
  chatFirstPageParam,
  chatNewerPageParam,
  chatNewestFirstNextPageParam,
  chatChannelFilterOf,
  chatOlderPageParam,
  chatThreadsNextPageParam,
  isChatMessageListKey,
  type ChatChannelFilter,
} from "./query-keys";

export { CHAT_KEYS };

/**
 * The last organization a session named in this tab. Written only in the
 * browser, after a commit, so a server render never sees another request's
 * organization.
 */
let lastChatOrganizationId: string | undefined;

function subscribeBrowserOnline(listener: () => void): () => void {
  window.addEventListener("online", listener);
  window.addEventListener("offline", listener);
  return () => {
    window.removeEventListener("online", listener);
    window.removeEventListener("offline", listener);
  };
}

/**
 * The organization of every chat request: the session's default
 * organization, which the Axios client sends as `X-Organization-ID` and the
 * realtime session connects to. Every chat query is disabled while it is
 * undefined.
 *
 * A session without an organization counts only when it was read while the
 * browser is online and has finished loading: a sign-out, or a user who
 * has no organization. While the browser is offline, or the session is
 * loading again, the session fetch can fail or lag, and the last known
 * organization is kept. Without that the chat would drop its messages, its
 * composer and the unsent messages of the outbox until the session is back.
 * A session that names another organization takes effect at once.
 */
export function useChatOrganizationId(): string | undefined {
  const { data: session, status } = useSession();
  const browserOnline = useSyncExternalStore(
    subscribeBrowserOnline,
    () => navigator.onLine,
    () => true,
  );
  const sessionOrgId = session?.user?.defaultOrganizationId || undefined;
  const confirmedWithout = !sessionOrgId && browserOnline && status !== "loading";

  useEffect(() => {
    if (sessionOrgId) lastChatOrganizationId = sessionOrgId;
    else if (confirmedWithout) lastChatOrganizationId = undefined;
  }, [sessionOrgId, confirmedWithout]);

  if (sessionOrgId) return sessionOrgId;
  return confirmedWithout ? undefined : lastChatOrganizationId;
}

// ── Cache helpers ───────────────────────────────────────────────────
// Shared by the mutations and the realtime signals, so a message changed
// here or by someone else lands in every list that shows it.

type MessageListData = InfiniteData<ChatMessagePage, unknown>;

function isMessageListData(data: unknown): data is MessageListData {
  return !!data && typeof data === "object" && Array.isArray((data as MessageListData).pages);
}

function mapPages(
  data: MessageListData,
  mapItems: (items: ChatMessageResponse[], page: ChatMessagePage, index: number) => ChatMessageResponse[],
): MessageListData {
  let changed = false;
  const pages = data.pages.map((page, index) => {
    const items = mapItems(page.items, page, index);
    if (items === page.items) return page;
    changed = true;
    return { ...page, items };
  });
  return changed ? { ...data, pages } : data;
}

function updateMessageLists(
  queryClient: QueryClient,
  orgId: string,
  update: (data: MessageListData, queryKey: readonly unknown[]) => MessageListData,
): void {
  const entries = queryClient.getQueriesData<MessageListData>({
    predicate: (query) => isChatMessageListKey(orgId, query.queryKey),
  });
  for (const [queryKey, data] of entries) {
    if (!isMessageListData(data)) continue;
    const next = update(data, queryKey);
    if (next !== data) queryClient.setQueryData(queryKey, next);
  }
}

/** The lists of one conversation: a channel (`threadId` null) or one of its threads, anchored or not. */
function conversationPrefix(orgId: string, channelId: string, threadId: string | null): readonly unknown[] {
  return threadId ? CHAT_KEYS.thread(orgId, threadId) : CHAT_KEYS.channel(orgId, channelId);
}

function startsWith(queryKey: readonly unknown[], prefix: readonly unknown[]): boolean {
  return prefix.every((segment, index) => queryKey[index] === segment);
}

/**
 * True when a list narrowed by `filter` shows `message`: the message was
 * created on the filter's day (when it has one), by its author (when it has
 * one), and, with statuses, is a share entry whose share has one of them, as
 * the server filters the list. An unfiltered list shows every message.
 */
function acceptsChatMessage(filter: ChatChannelFilter | null, message: ChatMessageResponse): boolean {
  if (!filter) return true;
  if (filter.day !== null && updateDayOf(message.createdAt) !== filter.day) return false;
  if (filter.authorId !== null && filter.authorId !== message.author?.userId) return false;
  const statuses = filter.pullRequestStatuses ?? [];
  if (statuses.length === 0) return true;
  const status = message.pullRequestShare?.status;
  return !!status && statuses.includes(status);
}

/**
 * True when a list narrowed by `filter` can take new messages: an unfiltered
 * list, one without a day, or one of today. A past day is complete, so its
 * newest page is not the conversation's newest.
 */
function isChatFilterLive(filter: ChatChannelFilter | null | undefined): boolean {
  return !filter || filter.day === null || filter.day === updateToday();
}

/** The placeholder a deleted thread root leaves in the channel, as the server returns it. */
export function toDeletedChatPlaceholder(message: ChatMessageResponse): ChatMessageResponse {
  return {
    ...message,
    deleted: true,
    author: null,
    content: "",
    editedAt: null,
    pinnedAt: null,
    replyTo: null,
    mentionedUserIds: [],
    mentionsEveryone: false,
    mentionsHere: false,
    mentionsMe: false,
    repliesToMe: false,
    ticketRefs: [],
    linkedTickets: [],
    attachments: [],
    reactions: [],
    dailyUpdateId: null,
    dailyUpdate: null,
  };
}

/** Applies `patch` to the message wherever it is loaded, the open thread's root included. */
export function patchChatMessage(
  queryClient: QueryClient,
  orgId: string,
  messageId: string,
  patch: (message: ChatMessageResponse) => ChatMessageResponse,
): void {
  updateMessageLists(queryClient, orgId, (data) =>
    mapPages(data, (items) => {
      const index = items.findIndex((item) => item.id === messageId);
      const current = items[index];
      if (!current) return items;
      const next = [...items];
      next[index] = patch(current);
      return next;
    }),
  );
  const previewKey = CHAT_KEYS.message(orgId, messageId);
  const preview = queryClient.getQueryData<ChatMessageResponse>(previewKey);
  if (preview) queryClient.setQueryData<ChatMessageResponse>(previewKey, patch(preview));
  const threadDetails = queryClient.getQueriesData<ChatThreadResponse>({
    queryKey: [...CHAT_KEYS.all(orgId), "thread"],
  });
  for (const [queryKey, thread] of threadDetails) {
    if (thread?.rootMessageId === messageId) {
      queryClient.setQueryData<ChatThreadResponse>(queryKey, { ...thread, rootMessage: patch(thread.rootMessage) });
    }
  }
}

/**
 * The copy to keep of two copies of one message: `incoming` unless `current`
 * has a strictly newer version. Responses and signals arrive out of order, so
 * an older read never replaces a newer edit; an equal version is replaced,
 * because reactions change without changing the version.
 */
function newerChatCopy(current: ChatMessageResponse, incoming: ChatMessageResponse): ChatMessageResponse {
  return isNewerChatVersion(current.version, incoming.version) ? current : incoming;
}

/** Replaces the loaded copies of a message with the server's copy, unless a loaded copy is newer. */
export function replaceChatMessage(queryClient: QueryClient, orgId: string, message: ChatMessageResponse): void {
  patchChatMessage(queryClient, orgId, message.id, (current) => newerChatCopy(current, message));
}

/**
 * Adds a new message to the lists of its conversation that hold the newest
 * page (`hasNewer` false on the last page), or replaces it where it is
 * already loaded (unless the loaded copy is newer). A list narrowed to a day,
 * an author or share statuses takes only the messages that match it. Returns true when at least one list showed it; false means
 * the reader is viewing older messages, and the caller counts it as new.
 */
export function insertChatMessage(queryClient: QueryClient, orgId: string, message: ChatMessageResponse): boolean {
  const prefix = conversationPrefix(orgId, message.channelId, message.threadId);
  let shown = false;
  updateMessageLists(queryClient, orgId, (data, queryKey) => {
    if (!startsWith(queryKey, prefix)) return data;
    // A list of another day, author or status neither shows nor counts it.
    if (!acceptsChatMessage(chatChannelFilterOf(queryKey), message)) return data;
    const loaded = data.pages.some((page) => page.items.some((item) => item.id === message.id));
    if (loaded) {
      shown = true;
      return mapPages(data, (items) =>
        items.some((item) => item.id === message.id)
          ? items.map((item) => (item.id === message.id ? newerChatCopy(item, message) : item))
          : items,
      );
    }
    const lastIndex = data.pages.length - 1;
    const lastPage = data.pages[lastIndex];
    if (!lastPage || lastPage.hasNewer) return data;
    shown = true;
    return mapPages(data, (items, _page, index) => {
      if (index !== lastIndex) return items;
      // Signals and responses can arrive out of order; keep the page oldest first.
      const next = [...items, message];
      next.sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt) || a.id.localeCompare(b.id));
      return next;
    });
  });
  return shown;
}

/**
 * Removes a deleted message from every list: a thread root becomes the
 * deleted placeholder in the channel (its thread lives on), any other
 * message is dropped; pins and search drop it too. Replies quoting it show
 * "Original message was deleted" and are no longer highlighted as replies to
 * me, as a reply to a deleted message is no notification.
 */
export function removeChatMessage(queryClient: QueryClient, orgId: string, messageId: string): void {
  // A link to the message now shows it as unavailable, without asking the server again.
  const previewKey = CHAT_KEYS.message(orgId, messageId);
  const preview = queryClient.getQueryData<ChatMessageResponse>(previewKey);
  if (preview && !preview.deleted) queryClient.setQueryData<ChatMessageResponse>(previewKey, { ...preview, deleted: true });
  updateMessageLists(queryClient, orgId, (data, queryKey) => {
    const isChannel = queryKey[2] === "channel";
    return mapPages(data, (items) => {
      let changed = false;
      const next: ChatMessageResponse[] = [];
      for (const item of items) {
        if (item.id === messageId) {
          changed = true;
          if (isChannel && item.thread) next.push(toDeletedChatPlaceholder(item));
          continue;
        }
        if (item.replyTo?.messageId === messageId && !item.replyTo.deleted) {
          changed = true;
          next.push({
            ...item,
            repliesToMe: false,
            replyTo: { ...item.replyTo, deleted: true, authorId: null, snippet: "", attachments: [] },
          });
          continue;
        }
        next.push(item);
      }
      return changed ? next : items;
    });
  });
  const threadDetails = queryClient.getQueriesData<ChatThreadResponse>({
    queryKey: [...CHAT_KEYS.all(orgId), "thread"],
  });
  for (const [queryKey, thread] of threadDetails) {
    if (thread?.rootMessageId === messageId && !thread.rootMessage.deleted) {
      queryClient.setQueryData<ChatThreadResponse>(queryKey, {
        ...thread,
        rootMessage: toDeletedChatPlaceholder(thread.rootMessage),
      });
    }
  }
}

/**
 * True when a list of the conversation (`threadId` null: the channel itself)
 * holds its newest page, so a new message can be appended live. A list of a
 * past day never does: a new message is counted for the reader instead.
 */
export function isChatConversationAtNewest(
  queryClient: QueryClient,
  orgId: string,
  channelId: string,
  threadId: string | null,
): boolean {
  const entries = queryClient.getQueriesData<MessageListData>({
    queryKey: conversationPrefix(orgId, channelId, threadId),
  });
  return entries.some(([queryKey, data]) => {
    if (!isMessageListData(data) || !isChatFilterLive(chatChannelFilterOf(queryKey))) return false;
    const lastPage = data.pages[data.pages.length - 1];
    return !!lastPage && !lastPage.hasNewer;
  });
}

/** The loaded copy of a message, from any list. */
export function findChatMessage(
  queryClient: QueryClient,
  orgId: string,
  messageId: string,
): ChatMessageResponse | undefined {
  const entries = queryClient.getQueriesData<MessageListData>({
    predicate: (query) => isChatMessageListKey(orgId, query.queryKey),
  });
  for (const [, data] of entries) {
    if (!isMessageListData(data)) continue;
    for (const page of data.pages) {
      const found = page.items.find((item) => item.id === messageId);
      if (found) return found;
    }
  }
  return undefined;
}

/** The root message ID of a thread, from its detail or from the channel row that carries its summary. */
export function findChatThreadRootId(queryClient: QueryClient, orgId: string, threadId: string): string | undefined {
  const detail = queryClient.getQueryData<ChatThreadResponse>(CHAT_KEYS.threadDetail(orgId, threadId));
  if (detail) return detail.rootMessageId;
  const entries = queryClient.getQueriesData<MessageListData>({ queryKey: CHAT_KEYS.channelMessagesAll(orgId) });
  for (const [, data] of entries) {
    if (!isMessageListData(data)) continue;
    for (const page of data.pages) {
      const root = page.items.find((item) => item.thread?.threadId === threadId);
      if (root) return root.id;
    }
  }
  return undefined;
}

export interface ChatReactionChange {
  emoji: string;
  userId: string;
  added: boolean;
  /** True when `userId` is the reader. */
  isMe: boolean;
}

/**
 * Applies one reaction change to a message's summaries. A change already
 * reflected is skipped, so the reader's optimistic toggle followed by the
 * signal of the same change, or a signal after the response that already
 * holds it, counts once. For other users this is known only while they are
 * among the first 10 listed, as the server lists no more.
 */
export function applyChatReaction(message: ChatMessageResponse, change: ChatReactionChange): ChatMessageResponse {
  const index = message.reactions.findIndex((reaction) => reaction.emoji === change.emoji);
  const current = index >= 0 ? message.reactions[index] : undefined;
  const listed = !!current?.userIds.includes(change.userId);
  if (change.added) {
    if (current && (change.isMe ? current.reactedByMe : listed)) return message;
    const next = current
      ? {
          ...current,
          count: current.count + 1,
          reactedByMe: current.reactedByMe || change.isMe,
          userIds: current.userIds.length < 10 ? [...current.userIds, change.userId] : current.userIds,
        }
      : { emoji: change.emoji, count: 1, reactedByMe: change.isMe, userIds: [change.userId] };
    const reactions = [...message.reactions];
    if (index >= 0) reactions[index] = next;
    else reactions.push(next);
    return { ...message, reactions };
  }
  if (!current) return message;
  if (change.isMe ? !current.reactedByMe : !listed && current.count <= current.userIds.length) return message;
  const reactions = [...message.reactions];
  if (current.count <= 1) {
    reactions.splice(index, 1);
  } else {
    reactions[index] = {
      ...current,
      count: current.count - 1,
      reactedByMe: change.isMe ? false : current.reactedByMe,
      userIds: current.userIds.filter((userId) => userId !== change.userId),
    };
  }
  return { ...message, reactions };
}

/**
 * Brings a thread's summary up to date after a message was added to or
 * removed from it: the root row in the channel (reply count, last reply,
 * avatars), the thread's detail and the thread lists. When the root cannot be
 * fetched (deleted meanwhile), the channel is refetched instead.
 */
export async function refreshChatThread(queryClient: QueryClient, orgId: string, threadId: string): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: CHAT_KEYS.threadDetail(orgId, threadId) }),
    queryClient.invalidateQueries({ queryKey: CHAT_KEYS.threadsAll(orgId) }),
  ]);
  const rootId = findChatThreadRootId(queryClient, orgId, threadId);
  if (!rootId) return;
  await refreshChatMessage(queryClient, orgId, rootId);
}

/**
 * Fetches a message as the reader sees it and replaces its loaded copies.
 * When the fetch fails, the lists that show it are refetched, so they never
 * keep a stale copy; the failure is logged.
 */
export async function refreshChatMessage(queryClient: QueryClient, orgId: string, messageId: string): Promise<void> {
  try {
    replaceChatMessage(queryClient, orgId, requireChatData(await getChatMessage(messageId)));
  } catch (error) {
    console.error("[chat] could not refresh message; refetching the lists", messageId, error);
    await queryClient.invalidateQueries({
      predicate: (query) => isChatMessageListKey(orgId, query.queryKey),
    });
  }
}

/**
 * The reader's Oba user ID, from the `me` query. Signals name Oba
 * user IDs, never the Keycloak subject, so this is what "is it me" compares
 * with. Undefined while `me` loads or after it failed.
 */
export function useChatMeId(): string | undefined {
  const { data: meResponse } = useGetMe();
  return meResponse?.success ? meResponse.data?.id : undefined;
}

// ── Outbox ──────────────────────────────────────────────────────────
// Messages of this tab the server has not confirmed. They live outside the
// query cache, so a refetch or a re-sync of the list can never drop an unsent
// text; a list shows them after its newest page.

let outboxRows: readonly ChatOutboxMessage[] = [];
const outboxListeners = new Set<() => void>();

function setOutboxRows(rows: readonly ChatOutboxMessage[]): void {
  outboxRows = rows;
  outboxListeners.forEach((listener) => listener());
}

export function getChatOutboxMessage(clientNonce: string): ChatOutboxMessage | undefined {
  return outboxRows.find((row) => row.clientNonce === clientNonce);
}

/** Every unconfirmed message of the tab, in the order it was first sent. */
export function getChatOutboxMessages(): readonly ChatOutboxMessage[] {
  return outboxRows;
}

/** Adds the row, or replaces the row with the same nonce in place. */
export function putChatOutboxMessage(row: ChatOutboxMessage): void {
  const index = outboxRows.findIndex((existing) => existing.clientNonce === row.clientNonce);
  if (index < 0) {
    setOutboxRows([...outboxRows, row]);
  } else {
    const next = [...outboxRows];
    next[index] = row;
    setOutboxRows(next);
  }
}

export function removeChatOutboxMessage(clientNonce: string): void {
  if (!outboxRows.some((row) => row.clientNonce === clientNonce)) return;
  setOutboxRows(outboxRows.filter((row) => row.clientNonce !== clientNonce));
}

function subscribeOutbox(listener: () => void): () => void {
  outboxListeners.add(listener);
  return () => {
    outboxListeners.delete(listener);
  };
}

const EMPTY_OUTBOX: readonly ChatOutboxMessage[] = [];

/** The unconfirmed messages of one conversation (`threadId` null: the channel itself), oldest first. */
export function useChatOutbox(
  orgId: string | undefined,
  channelId: string | null | undefined,
  threadId: string | null,
): ChatOutboxMessage[] {
  const rows = useSyncExternalStore(
    subscribeOutbox,
    () => outboxRows,
    () => EMPTY_OUTBOX,
  );
  return useMemo(
    () =>
      rows.filter((row) => row.organizationId === orgId && row.channelId === channelId && row.threadId === threadId),
    [rows, orgId, channelId, threadId],
  );
}

// ── Queries ─────────────────────────────────────────────────────────

/** Every loaded message of an oldest-first list, each once, oldest first. */
function flattenMessages(data: MessageListData | undefined): ChatMessageResponse[] {
  if (!data) return [];
  const seen = new Set<string>();
  const messages: ChatMessageResponse[] = [];
  for (const page of data.pages) {
    for (const item of page.items) {
      if (seen.has(item.id)) continue;
      seen.add(item.id);
      messages.push(item);
    }
  }
  return messages;
}

export interface UseChatMessagesOptions {
  /** The channel to read, or the channel of `threadId`; nothing is fetched while it is unknown. */
  channelId: string | null | undefined;
  /** The thread to read; null or undefined reads the channel itself. */
  threadId?: string | null;
  /** Open around this message (a jump, or the unread divider) instead of at the newest page. */
  anchor?: string | null;
  /** A channel page narrowed to a day, an author and share statuses; ignored for a thread. */
  filter?: ChatChannelFilter | null;
}

/**
 * A channel or thread, oldest first, paged by keyset in both directions:
 * `fetchPreviousPage` loads older messages (`before`), `fetchNextPage` newer
 * ones (`after`). At most `CHAT_MAX_PAGES` pages are kept; the farthest are
 * dropped.
 *
 * `messages` is every loaded message once; `atNewest` is true when the list
 * holds the newest page, the only case in which new messages are appended
 * live and `outbox` (this tab's unconfirmed sends) belongs below it. A list
 * of a past day is never at the newest.
 */
export function useChatMessages({ channelId, threadId = null, anchor = null, filter = null }: UseChatMessagesOptions) {
  const orgId = useChatOrganizationId();
  const channelFilter = threadId ? null : filter;
  const query = useInfiniteQuery({
    queryKey: threadId
      ? CHAT_KEYS.thread(orgId ?? "", threadId, anchor)
      : CHAT_KEYS.channel(orgId ?? "", channelId ?? "", anchor, channelFilter),
    queryFn: async ({ pageParam }: { pageParam: ChatMessageCursor }) => {
      const params = { ...pageParam, limit: CHAT_PAGE_SIZE };
      const response = threadId
        ? await listThreadMessages(threadId, params)
        : await listChannelMessages({
            ...params,
            channelId: channelId as string,
            day: channelFilter?.day ?? undefined,
            authorId: channelFilter?.authorId ?? undefined,
            pullRequestStatuses: channelFilter?.pullRequestStatuses,
          });
      return requireChatData(response);
    },
    initialPageParam: chatFirstPageParam(anchor),
    getPreviousPageParam: (firstPage: ChatMessagePage) => chatOlderPageParam(firstPage),
    getNextPageParam: (lastPage: ChatMessagePage) => chatNewerPageParam(lastPage),
    maxPages: CHAT_MAX_PAGES,
    enabled: !!orgId && !!channelId,
  });
  const outbox = useChatOutbox(orgId, channelId, threadId);
  const messages = useMemo(() => flattenMessages(query.data), [query.data]);
  const lastPage = query.data?.pages[query.data.pages.length - 1];
  const atNewest = !!lastPage && !lastPage.hasNewer && isChatFilterLive(channelFilter);
  return { ...query, messages, outbox, atNewest };
}

/** One thread with its root message, members and the reader's rights. */
export function useChatThread(threadId: string | null | undefined) {
  const orgId = useChatOrganizationId();
  return useQuery({
    queryKey: CHAT_KEYS.threadDetail(orgId ?? "", threadId ?? ""),
    queryFn: async () => requireChatData(await getChatThread(threadId as string)),
    enabled: !!orgId && !!threadId,
  });
}

/** The threads of a channel whose effective status is `status`, most recent activity first. */
export function useChatThreads(
  channelId: string | null | undefined,
  status: ChatThreadStatus,
  options?: { enabled?: boolean },
) {
  const orgId = useChatOrganizationId();
  return useInfiniteQuery({
    queryKey: CHAT_KEYS.threads(orgId ?? "", channelId ?? "", status),
    queryFn: async ({ pageParam }: { pageParam: string | null }) =>
      requireChatData(
        await listChatThreads({ channelId: channelId as string, status, before: pageParam ?? undefined }),
      ),
    initialPageParam: null as string | null,
    getNextPageParam: chatThreadsNextPageParam,
    enabled: !!orgId && !!channelId && (options?.enabled ?? true),
  });
}

/** The pinned messages of a channel, newest pin first. */
export function useChatPins(channelId: string | null | undefined, options?: { enabled?: boolean }) {
  const orgId = useChatOrganizationId();
  return useInfiniteQuery({
    queryKey: CHAT_KEYS.pins(orgId ?? "", channelId ?? ""),
    queryFn: async ({ pageParam }: { pageParam: string | null }) =>
      requireChatData(await listChatPins({ channelId: channelId as string, before: pageParam ?? undefined })),
    initialPageParam: null as string | null,
    getNextPageParam: chatNewestFirstNextPageParam,
    enabled: !!orgId && !!channelId && (options?.enabled ?? true),
  });
}

/**
 * Matches in one channel and its threads, or in every channel the reader can
 * read when `channelId` is null or missing; newest first, 25 at a time. Runs
 * only from `CHAT_SEARCH_MIN` characters; the caller debounces `q`
 * (`SenseySearchInput`).
 */
export function useChatSearch(
  q: string | null | undefined,
  authorId: string | null | undefined,
  channelId?: string | null,
) {
  const orgId = useChatOrganizationId();
  const text = (q ?? "").trim();
  return useInfiniteQuery({
    queryKey: CHAT_KEYS.search(orgId ?? "", text, authorId ?? null, channelId ?? null),
    queryFn: async ({ pageParam }: { pageParam: string | null }) =>
      requireChatData(
        await searchChatMessages({
          q: text,
          channelId: channelId ?? undefined,
          authorId: authorId ?? undefined,
          before: pageParam ?? undefined,
          limit: CHAT_SEARCH_PAGE_SIZE,
        }),
      ),
    initialPageParam: null as string | null,
    getNextPageParam: chatNewestFirstNextPageParam,
    enabled: !!orgId && text.length >= CHAT_SEARCH_MIN,
  });
}

/** True when a message cannot be shown to the reader: deleted, of another organization, or not readable (404, 403). */
function isChatMessageUnavailable(error: unknown): boolean {
  return isApiError(error) && (error.status === 404 || error.status === 403);
}

/**
 * One message as the reader sees it, for the preview of a message link. Ten
 * links to one message share one request. A message the reader cannot see
 * (404, 403) is null: an answer, cached like a message, so further links
 * to it ask the server again only once the entry is stale. Any other error
 * is the query's error and is retried twice.
 */
export function useChatMessage(messageId: string | null | undefined) {
  const orgId = useChatOrganizationId();
  return useQuery({
    queryKey: CHAT_KEYS.message(orgId ?? "", messageId ?? ""),
    queryFn: async (): Promise<ChatMessageResponse | null> => {
      try {
        return requireChatData(await getChatMessage(messageId as string));
      } catch (error) {
        if (isChatMessageUnavailable(error)) return null;
        throw error;
      }
    },
    enabled: !!orgId && !!messageId,
    retry: 2,
  });
}

export interface UseChatMembersOptions {
  enabled?: boolean;
  /** A private channel lists its members; without it, the organization's active members. */
  channelId?: string | null;
  /** With `channelId`: the distinct authors of the channel's live messages instead. */
  authorsOnly?: boolean;
}

/**
 * Active members with their online state, for the mention list and the
 * members popover, or a channel's message authors for the private channel
 * dialog. Fetched once and filtered on the client; PRESENCE signals keep
 * `online` current.
 */
export function useChatMembers(options?: UseChatMembersOptions) {
  const orgId = useChatOrganizationId();
  const channelId = options?.channelId ?? null;
  const authorsOnly = options?.authorsOnly ?? false;
  return useQuery({
    queryKey: CHAT_KEYS.members(orgId ?? "", channelId, authorsOnly),
    queryFn: async () =>
      requireChatData(await listChatMembers({ channelId: channelId ?? undefined, authorsOnly: authorsOnly || undefined })),
    enabled: !!orgId && (options?.enabled ?? true),
  });
}

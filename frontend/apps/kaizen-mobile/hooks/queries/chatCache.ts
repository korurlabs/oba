import type { InfiniteData, QueryClient } from "@tanstack/react-query";

import {
  threadRootAsMessage,
  type ChatAttachmentResponse,
  type ChatMessageCursor,
  type ChatMessagePage,
  type ChatMessageResponse,
  type ChatReactionSummaryResponse,
  type ChatThreadPage,
  type ChatThreadResponse,
  type ChatThreadStatus,
} from "@/lib/schemas/chat";

/*
 * The chat's query keys and the edits a realtime signal or a mutation makes
 * to cached message pages, without React and without the network, so the
 * same rules hold for the web app and this one and can be tested alone.
 */

/** Query keys of chat, scoped by organization; the same layout as the web app. */
export const chatKeys = {
  all: (orgId: string) => ["chat", orgId] as const,
  channels: (orgId: string) => [...chatKeys.all(orgId), "channels"] as const,
  channelMessagesAll: (orgId: string) => [...chatKeys.all(orgId), "channel"] as const,
  channel: (orgId: string, channelId: string) =>
    [...chatKeys.channelMessagesAll(orgId), channelId] as const,
  threadMessagesAll: (orgId: string) => [...chatKeys.all(orgId), "thread-messages"] as const,
  thread: (orgId: string, threadId: string) =>
    [...chatKeys.threadMessagesAll(orgId), threadId] as const,
  /** Every thread's `ChatThreadResponse`. */
  threadDetailsAll: (orgId: string) => [...chatKeys.all(orgId), "thread"] as const,
  /** One thread's `ChatThreadResponse`. */
  threadDetail: (orgId: string, threadId: string) =>
    [...chatKeys.threadDetailsAll(orgId), threadId] as const,
  threadsAll: (orgId: string) => [...chatKeys.all(orgId), "threads"] as const,
  /** Every thread list of one channel. */
  channelThreads: (orgId: string, channelId: string) =>
    [...chatKeys.threadsAll(orgId), channelId] as const,
  /** The threads of one channel with one effective status. */
  threads: (orgId: string, channelId: string, status: ChatThreadStatus) =>
    [...chatKeys.channelThreads(orgId, channelId), status] as const,
  pinsAll: (orgId: string) => [...chatKeys.all(orgId), "pins"] as const,
  pins: (orgId: string, channelId: string) => [...chatKeys.pinsAll(orgId), channelId] as const,
  searchAll: (orgId: string) => [...chatKeys.all(orgId), "search"] as const,
  /** Matches of one text, author filter and channel (null: every readable channel). */
  search: (orgId: string, q: string, authorId: string | null, channelId: string | null) =>
    [...chatKeys.searchAll(orgId), q, authorId, channelId] as const,
  membersAll: (orgId: string) => [...chatKeys.all(orgId), "members"] as const,
  members: (orgId: string) => [...chatKeys.membersAll(orgId), null, false] as const,
  /** Every member list of one channel: its members and its authors. */
  channelMemberLists: (orgId: string, channelId: string) =>
    [...chatKeys.membersAll(orgId), channelId] as const,
  /** The members of one private channel. */
  channelMembers: (orgId: string, channelId: string) =>
    [...chatKeys.channelMemberLists(orgId, channelId), false] as const,
  /** The distinct authors of one channel's live messages. */
  channelAuthors: (orgId: string, channelId: string) =>
    [...chatKeys.channelMemberLists(orgId, channelId), true] as const,
  /** The newest message of each channel, for the previews of the channel list. */
  previewsAll: (orgId: string) => [...chatKeys.all(orgId), "preview"] as const,
  preview: (orgId: string, channelId: string) =>
    [...chatKeys.previewsAll(orgId), channelId] as const,
  unread: (orgId: string) => [...chatKeys.all(orgId), "unread"] as const,
  /** One message as the preview of a link to it shows it; null when the reader cannot see it. */
  message: (orgId: string, messageId: string) =>
    [...chatKeys.all(orgId), "message", messageId] as const,
  /** The tickets the composer offers for one typed text after `#`. */
  ticketSuggestions: (orgId: string, search: string) =>
    [...chatKeys.all(orgId), "ticket-suggestions", search] as const,
};

const MESSAGE_LIST_SEGMENTS: ReadonlySet<unknown> = new Set([
  "channel",
  "thread-messages",
  "pins",
  "search",
]);

/** True for a message list of the organization: channel, thread, pins or search pages. */
export function isChatMessageListKey(orgId: string, queryKey: readonly unknown[]): boolean {
  return queryKey[0] === "chat" && queryKey[1] === orgId && MESSAGE_LIST_SEGMENTS.has(queryKey[2]);
}

/**
 * The roots of the daily updates and pull requests channels narrowed by a
 * filter (a business day, a person, share statuses). Those lists hold chat
 * messages under their own keys, so a new message is never inserted into a
 * day or a filter it may not match; a message they already hold is still
 * found, patched and removed like in any chat list.
 */
export const chatNarrowedListKeys = {
  updates: (orgId: string) => ["updates", orgId, "channel"] as const,
  pullRequests: (orgId: string) => ["pull-requests", orgId, "channel"] as const,
};

/** True for a narrowed daily updates or pull requests list of the organization. */
export function isChatNarrowedListKey(orgId: string, queryKey: readonly unknown[]): boolean {
  return (
    startsWith(queryKey, chatNarrowedListKeys.updates(orgId)) ||
    startsWith(queryKey, chatNarrowedListKeys.pullRequests(orgId))
  );
}

/** True for every cached list that can hold a message of the organization. */
function holdsChatMessages(orgId: string, queryKey: readonly unknown[]): boolean {
  return isChatMessageListKey(orgId, queryKey) || isChatNarrowedListKey(orgId, queryKey);
}

// ── Keyset page parameters ──────────────────────────────────────────
// Channel and thread pages are oldest first. The first page is the newest
// one; older pages are fetched as previous pages and newer ones as next pages.

/** At most this many pages stay loaded; scrolling further drops the far end. */
export const CHAT_MAX_PAGES = 6;

export function chatFirstPageParam(): ChatMessageCursor {
  return {};
}

export function chatOlderPageParam(firstPage: ChatMessagePage): ChatMessageCursor | undefined {
  const oldest: ChatMessageResponse | undefined = firstPage.items[0];
  return firstPage.hasOlder && oldest ? { before: oldest.id } : undefined;
}

export function chatNewerPageParam(lastPage: ChatMessagePage): ChatMessageCursor | undefined {
  const newest: ChatMessageResponse | undefined = lastPage.items[lastPage.items.length - 1];
  return lastPage.hasNewer && newest ? { after: newest.id } : undefined;
}

/**
 * The next page of a newest-first list (pins, search): the matches before
 * the oldest one listed, while the server says there are more.
 */
export function chatNewestFirstNextPageParam(lastPage: ChatMessagePage): string | undefined {
  const oldest: ChatMessageResponse | undefined = lastPage.items[lastPage.items.length - 1];
  return lastPage.hasOlder && oldest ? oldest.id : undefined;
}

/** The next page of a thread list: the threads after its last one, while the server has more. */
export function chatThreadsNextPageParam(lastPage: ChatThreadPage): string | undefined {
  const last: ChatThreadResponse | undefined = lastPage.items[lastPage.items.length - 1];
  return lastPage.hasMore && last ? last.id : undefined;
}

// ── Message versions ────────────────────────────────────────────────

const INSTANT = /^(.*T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})$/;

/** Milliseconds of the whole seconds and the nanoseconds of the fraction. */
function instantParts(version: string): [number, number] {
  const match: RegExpExecArray | null = INSTANT.exec(version);
  if (!match) return [Date.parse(version), 0];
  const seconds: string = match[1] ?? "";
  const fraction: string = match[2] ?? "";
  const zone: string = match[3] ?? "Z";
  return [Date.parse(`${seconds}${zone}`), Number(fraction.padEnd(9, "0"))];
}

/**
 * Orders two versions to the nanosecond, which `Date.parse` would round away.
 * A missing version is the oldest.
 */
export function compareChatVersions(
  a: string | null | undefined,
  b: string | null | undefined
): number {
  if (!a || !b) return (a ? 1 : 0) - (b ? 1 : 0);
  const [aSeconds, aFraction] = instantParts(a);
  const [bSeconds, bFraction] = instantParts(b);
  return aSeconds - bSeconds || aFraction - bFraction;
}

export function isNewerChatVersion(
  candidate: string | null | undefined,
  held: string | null | undefined
): boolean {
  return compareChatVersions(candidate, held) > 0;
}

// Per message, the newest version a signal announced and the ids deleted in
// this session. Both are cleared after a reconnect, when the lists reload
// from the server and every signal of the gap is lost anyway.
const signalledVersions: Map<string, string> = new Map<string, string>();
const deletedMessageIds: Set<string> = new Set<string>();

export function rememberChatMessageVersion(messageId: string, version: string): void {
  if (isNewerChatVersion(version, signalledVersions.get(messageId))) {
    signalledVersions.set(messageId, version);
  }
}

export function signalledChatMessageVersion(messageId: string): string | undefined {
  return signalledVersions.get(messageId);
}

export function rememberChatMessageDeleted(messageId: string): void {
  deletedMessageIds.add(messageId);
  signalledVersions.delete(messageId);
}

export function isChatMessageDeleted(messageId: string): boolean {
  return deletedMessageIds.has(messageId);
}

export function clearChatMessageVersions(): void {
  signalledVersions.clear();
  deletedMessageIds.clear();
}

/**
 * A signal's message as the reader sees it. The server builds it for no
 * reader; mentions, replies and reactions of the reader are filled in here.
 * A thread `@everyone` is counted only when the reader is a thread member.
 */
export function withChatReaderFields(
  message: ChatMessageResponse,
  reader: { userId: string; threadMember: boolean }
): ChatMessageResponse {
  const mine: boolean = message.author?.userId === reader.userId;
  const everyoneReachesMe: boolean =
    message.mentionsEveryone && (!message.threadId || reader.threadMember);
  const mentionsMe: boolean =
    !mine && (message.mentionedUserIds.includes(reader.userId) || everyoneReachesMe);
  const repliesToMe: boolean =
    !mine &&
    !!message.replyTo &&
    !message.replyTo.deleted &&
    message.replyTo.authorId === reader.userId;
  const reactions: ChatReactionSummaryResponse[] = message.reactions.map((reaction) => ({
    ...reaction,
    reactedByMe: reaction.userIds.includes(reader.userId),
  }));
  return { ...message, mentionsMe, repliesToMe, reactions };
}

// ── Cached message lists ────────────────────────────────────────────

export type ChatMessageListData = InfiniteData<ChatMessagePage, unknown>;

function isMessageListData(data: unknown): data is ChatMessageListData {
  return !!data && typeof data === "object" && Array.isArray((data as ChatMessageListData).pages);
}

function mapPages(
  data: ChatMessageListData,
  mapItems: (items: ChatMessageResponse[], index: number) => ChatMessageResponse[]
): ChatMessageListData {
  let changed = false;
  const pages: ChatMessagePage[] = data.pages.map((page, index) => {
    const items: ChatMessageResponse[] = mapItems(page.items, index);
    if (items === page.items) return page;
    changed = true;
    return { ...page, items };
  });
  return changed ? { ...data, pages } : data;
}

function updateMessageLists(
  queryClient: QueryClient,
  orgId: string,
  update: (data: ChatMessageListData, queryKey: readonly unknown[]) => ChatMessageListData
): void {
  const entries = queryClient.getQueriesData<ChatMessageListData>({
    predicate: (query) => holdsChatMessages(orgId, query.queryKey),
  });
  for (const [queryKey, data] of entries) {
    if (!isMessageListData(data)) continue;
    const next: ChatMessageListData = update(data, queryKey);
    if (next !== data) queryClient.setQueryData(queryKey, next);
  }
}

function conversationPrefix(
  orgId: string,
  channelId: string,
  threadId: string | null
): readonly unknown[] {
  return threadId ? chatKeys.thread(orgId, threadId) : chatKeys.channel(orgId, channelId);
}

function startsWith(queryKey: readonly unknown[], prefix: readonly unknown[]): boolean {
  return prefix.every((segment, index) => queryKey[index] === segment);
}

/** The placeholder a deleted thread root leaves in its channel. */
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

/**
 * Applies `patch` to every cached copy of the message: in the message lists
 * (the narrowed daily updates and pull requests lists too), and as the root
 * of a thread, whose screen shows it above the replies, and as the preview
 * of a link to it.
 */
export function patchChatMessage(
  queryClient: QueryClient,
  orgId: string,
  messageId: string,
  patch: (message: ChatMessageResponse) => ChatMessageResponse
): void {
  queryClient.setQueriesData<ChatThreadResponse>(
    { queryKey: chatKeys.threadDetailsAll(orgId) },
    (thread) => {
      const root: ChatMessageResponse | null = thread ? threadRootAsMessage(thread) : null;
      return thread && root?.id === messageId ? { ...thread, rootMessage: patch(root) } : thread;
    }
  );
  queryClient.setQueryData<ChatMessageResponse | null>(
    chatKeys.message(orgId, messageId),
    (current) => (current ? patch(current) : current)
  );
  updateMessageLists(queryClient, orgId, (data) =>
    mapPages(data, (items) => {
      const index: number = items.findIndex((item) => item.id === messageId);
      const current: ChatMessageResponse | undefined = items[index];
      if (!current) return items;
      const next: ChatMessageResponse[] = [...items];
      next[index] = patch(current);
      return next;
    })
  );
}

function newerChatCopy(
  current: ChatMessageResponse,
  incoming: ChatMessageResponse
): ChatMessageResponse {
  return isNewerChatVersion(current.version, incoming.version) ? current : incoming;
}

/** Replaces every cached copy with `message`, unless a copy is newer. */
export function replaceChatMessage(
  queryClient: QueryClient,
  orgId: string,
  message: ChatMessageResponse
): void {
  patchChatMessage(queryClient, orgId, message.id, (current) => newerChatCopy(current, message));
}

/**
 * Puts a new message into its conversation's loaded pages. It goes on the
 * last page only when that page is the newest one; a list scrolled into the
 * past learns about it from the jump pill. Returns whether a list shows it.
 */
export function insertChatMessage(
  queryClient: QueryClient,
  orgId: string,
  message: ChatMessageResponse
): boolean {
  const prefix: readonly unknown[] = conversationPrefix(orgId, message.channelId, message.threadId);
  let shown = false;
  updateMessageLists(queryClient, orgId, (data, queryKey) => {
    if (!startsWith(queryKey, prefix)) return data;
    const loaded: boolean = data.pages.some((page) =>
      page.items.some((item) => item.id === message.id)
    );
    if (loaded) {
      shown = true;
      return mapPages(data, (items) =>
        items.some((item) => item.id === message.id)
          ? items.map((item) => (item.id === message.id ? newerChatCopy(item, message) : item))
          : items
      );
    }
    const lastIndex: number = data.pages.length - 1;
    const lastPage: ChatMessagePage | undefined = data.pages[lastIndex];
    if (!lastPage || lastPage.hasNewer) return data;
    shown = true;
    return mapPages(data, (items, index) => {
      if (index !== lastIndex) return items;
      const next: ChatMessageResponse[] = [...items, message];
      next.sort(
        (a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt) || a.id.localeCompare(b.id)
      );
      return next;
    });
  });
  return shown;
}

/**
 * Drops a deleted message from the lists. A channel keeps a thread root as
 * the deleted placeholder, and every quote of it becomes "deleted".
 */
export function removeChatMessage(
  queryClient: QueryClient,
  orgId: string,
  messageId: string
): void {
  updateMessageLists(queryClient, orgId, (data, queryKey) => {
    // A channel list, whole or narrowed to a day or a filter, keeps the root's placeholder.
    const isChannel: boolean = queryKey[2] === "channel";
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
            replyTo: {
              ...item.replyTo,
              deleted: true,
              authorId: null,
              snippet: "",
              attachments: [],
            },
          });
          continue;
        }
        next.push(item);
      }
      return changed ? next : items;
    });
  });
}

/** True when a loaded list of the conversation reaches its newest message. */
export function isChatConversationAtNewest(
  queryClient: QueryClient,
  orgId: string,
  channelId: string,
  threadId: string | null
): boolean {
  const entries = queryClient.getQueriesData<ChatMessageListData>({
    queryKey: conversationPrefix(orgId, channelId, threadId),
  });
  return entries.some(([, data]) => {
    if (!isMessageListData(data)) return false;
    const lastPage: ChatMessagePage | undefined = data.pages[data.pages.length - 1];
    return !!lastPage && !lastPage.hasNewer;
  });
}

/** The first cached copy of the message, in any list of the organization, narrowed ones included. */
export function findChatMessage(
  queryClient: QueryClient,
  orgId: string,
  messageId: string
): ChatMessageResponse | undefined {
  return findInMessageLists(
    queryClient,
    (queryKey) => holdsChatMessages(orgId, queryKey),
    messageId
  );
}

/**
 * The cached copy of the message in its own conversation's lists, the lists
 * `insertChatMessage` writes to. A narrowed daily updates or pull requests
 * list does not count: it may have refetched a new message before the
 * message's signal arrived, and the conversation must still be given it.
 */
export function findChatConversationMessage(
  queryClient: QueryClient,
  orgId: string,
  conversation: { channelId: string; threadId: string | null },
  messageId: string
): ChatMessageResponse | undefined {
  const prefix: readonly unknown[] = conversationPrefix(
    orgId,
    conversation.channelId,
    conversation.threadId
  );
  return findInMessageLists(queryClient, (queryKey) => startsWith(queryKey, prefix), messageId);
}

function findInMessageLists(
  queryClient: QueryClient,
  holds: (queryKey: readonly unknown[]) => boolean,
  messageId: string
): ChatMessageResponse | undefined {
  const entries = queryClient.getQueriesData<ChatMessageListData>({
    predicate: (query) => holds(query.queryKey),
  });
  for (const [, data] of entries) {
    if (!isMessageListData(data)) continue;
    for (const page of data.pages) {
      const found: ChatMessageResponse | undefined = page.items.find(
        (item) => item.id === messageId
      );
      if (found) return found;
    }
  }
  return undefined;
}

/**
 * The root message of a thread: from the thread's own cache, else from a
 * loaded channel message that carries the thread's summary.
 */
export function findChatThreadRootId(
  queryClient: QueryClient,
  orgId: string,
  threadId: string
): string | undefined {
  const detail: ChatThreadResponse | undefined = queryClient.getQueryData<ChatThreadResponse>(
    chatKeys.threadDetail(orgId, threadId)
  );
  if (detail) return detail.rootMessageId;
  const entries = queryClient.getQueriesData<ChatMessageListData>({
    queryKey: chatKeys.channelMessagesAll(orgId),
  });
  for (const [, data] of entries) {
    if (!isMessageListData(data)) continue;
    for (const page of data.pages) {
      const root: ChatMessageResponse | undefined = page.items.find(
        (item) => item.thread?.threadId === threadId
      );
      if (root) return root.id;
    }
  }
  return undefined;
}

/**
 * A file of a loaded message, for the viewer: from any cached message list,
 * else from a cached thread's root, which a thread screen shows above its
 * replies without it being in a list.
 */
export function findChatAttachment(
  queryClient: QueryClient,
  orgId: string,
  messageId: string,
  attachmentId: string
): ChatAttachmentResponse | undefined {
  let message: ChatMessageResponse | undefined = findChatMessage(queryClient, orgId, messageId);
  if (!message) {
    const details = queryClient.getQueriesData<ChatThreadResponse>({
      queryKey: chatKeys.threadDetailsAll(orgId),
    });
    message =
      details
        .map(([, detail]) => (detail ? threadRootAsMessage(detail) : null))
        .find((root) => root?.id === messageId) ?? undefined;
  }
  return message?.attachments.find((attachment) => attachment.id === attachmentId);
}

/** The loaded messages oldest first, each once: neighbouring pages can overlap. */
export function flattenChatMessages(data: ChatMessageListData | undefined): ChatMessageResponse[] {
  if (!data) return [];
  const seen: Set<string> = new Set<string>();
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

// ── Reactions ───────────────────────────────────────────────────────

export interface ChatReactionChange {
  emoji: string;
  userId: string;
  added: boolean;
  /** True when the change is the reader's own. */
  isMe: boolean;
}

/**
 * A reaction added or removed. A change already reflected (the reader's own
 * optimistic one echoed back, or a user already listed) leaves the message
 * as it is, so an echo is never counted twice.
 */
export function applyChatReaction(
  message: ChatMessageResponse,
  change: ChatReactionChange
): ChatMessageResponse {
  const index: number = message.reactions.findIndex((reaction) => reaction.emoji === change.emoji);
  const current: ChatReactionSummaryResponse | undefined =
    index >= 0 ? message.reactions[index] : undefined;
  const listed: boolean = !!current?.userIds.includes(change.userId);
  if (change.added) {
    if (current && (change.isMe ? current.reactedByMe : listed)) return message;
    const next: ChatReactionSummaryResponse = current
      ? {
          ...current,
          count: current.count + 1,
          reactedByMe: current.reactedByMe || change.isMe,
          userIds:
            current.userIds.length < 10 ? [...current.userIds, change.userId] : current.userIds,
        }
      : { emoji: change.emoji, count: 1, reactedByMe: change.isMe, userIds: [change.userId] };
    const reactions: ChatReactionSummaryResponse[] = [...message.reactions];
    if (index >= 0) reactions[index] = next;
    else reactions.push(next);
    return { ...message, reactions };
  }
  if (!current) return message;
  if (change.isMe ? !current.reactedByMe : !listed && current.count <= current.userIds.length) {
    return message;
  }
  const reactions: ChatReactionSummaryResponse[] = [...message.reactions];
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

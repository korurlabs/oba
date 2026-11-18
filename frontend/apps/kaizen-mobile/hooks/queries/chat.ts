import { useInfiniteQuery, useQueries, useQuery, type UseQueryResult } from "@tanstack/react-query";
import { useMemo } from "react";

import { useMe } from "@/hooks/queries/users";
import {
  getChatMessage,
  getChatThread,
  getChatUnread,
  listChannelMessages,
  listChatChannels,
  listChatMembers,
  listChatPins,
  listChatThreads,
  listThreadMessages,
  searchChatMessages,
  searchChatTickets,
  type ChatSearchInput,
} from "@/hooks/queries/chatApi";
import {
  CHAT_MAX_PAGES,
  chatFirstPageParam,
  chatKeys,
  chatNewerPageParam,
  chatNewestFirstNextPageParam,
  chatOlderPageParam,
  chatThreadsNextPageParam,
  flattenChatMessages,
} from "@/hooks/queries/chatCache";
import { useChatOutbox } from "@/hooks/queries/chatOutbox";
import { keepPreviousWithin } from "@/hooks/queries/placeholder";
import { BADGE_REFETCH_MS } from "@/hooks/queries/tickets";
import { toApiError } from "@/lib/api";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import {
  CHAT_SEARCH_MIN,
  ChatChannelVisibility,
  type ChatChannelResponse,
  type ChatConversationUnreadResponse,
  type ChatMemberResponse,
  type ChatMessageCursor,
  type ChatMessagePage,
  type ChatMessageResponse,
  type ChatThreadPage,
  type ChatThreadResponse,
  type ChatThreadStatus,
  type ChatTicketSuggestion,
  type ChatUnread,
} from "@/lib/schemas/chat";

export { chatKeys };

/** The active organization; chat data is scoped by it. */
export function useChatOrganizationId(): string | null {
  return useActiveOrganizationId();
}

/** The signed-in user's Oba user ID, once the profile is loaded. */
export function useChatMeId(): string | undefined {
  return useMe(true).data?.id;
}

/**
 * `GET /kaizen/chat/unread`: the reader's unread state. The tab badge reads
 * `total`; the channel list reads the per-channel counts. Realtime keeps it
 * current, and the interval covers a closed realtime session.
 */
export function useChatUnread() {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<ChatUnread>({
    queryKey: chatKeys.unread(orgId ?? ""),
    queryFn: () =>
      getChatUnread().catch((error: unknown) => {
        // A badge that fails to load is left off, not toasted every minute.
        console.warn("[chat] unread counts failed", error instanceof Error ? error.message : error);
        throw error;
      }),
    enabled: !!orgId,
    refetchInterval: BADGE_REFETCH_MS,
  });
}

/** The unread entry of one channel, by channel ID. */
export function chatChannelUnreadById(
  unread: ChatUnread | undefined
): ReadonlyMap<string, ChatConversationUnreadResponse> {
  return new Map((unread?.channels ?? []).map((channel) => [channel.id, channel]));
}

/**
 * `GET /kaizen/chat/channels`: every channel the reader sees, private ones
 * included. `enabled` false only reads what is cached.
 */
export function useChatChannels(enabled: boolean = true) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<ChatChannelResponse[]>({
    queryKey: chatKeys.channels(orgId ?? ""),
    queryFn: listChatChannels,
    enabled: enabled && !!orgId,
  });
}

/**
 * `GET /kaizen/chat/members`: the organization's active members, online
 * first. `enabled` false only reads what is cached.
 */
export function useChatMembers(enabled: boolean = true) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<ChatMemberResponse[]>({
    queryKey: chatKeys.members(orgId ?? ""),
    queryFn: () => listChatMembers(),
    enabled: enabled && !!orgId,
  });
}

/**
 * `GET /kaizen/chat/members?channelId&authorsOnly=true`: everyone who wrote
 * in the channel, preselected when a public channel is made private. Read
 * only while `enabled`, so the change sheet asks once it switches to private.
 */
export function useChatChannelAuthors(channelId: string | null, enabled: boolean) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<ChatMemberResponse[]>({
    queryKey: chatKeys.channelAuthors(orgId ?? "", channelId ?? ""),
    queryFn: () => listChatMembers(channelId as string, true),
    enabled: enabled && !!orgId && !!channelId,
  });
}

/**
 * The members who can be mentioned in a channel: the organization's active
 * members in a public channel, only the channel's members in a private one,
 * because the server refuses a mention of anyone else. Waits for the channel,
 * so it never offers someone the channel would refuse.
 */
export function useChatChannelMembers(channel: ChatChannelResponse | undefined) {
  const orgId: string | null = useActiveOrganizationId();
  const privateId: string | null =
    channel?.visibility === ChatChannelVisibility.PRIVATE ? channel.id : null;
  return useQuery<ChatMemberResponse[]>({
    queryKey: privateId
      ? chatKeys.channelMembers(orgId ?? "", privateId)
      : chatKeys.members(orgId ?? ""),
    queryFn: () => listChatMembers(privateId ?? undefined),
    enabled: !!orgId && !!channel,
  });
}

/** The query key of a conversation's messages: a thread's, or the channel's own. */
export function chatConversationKey(orgId: string, channelId: string, threadId: string | null) {
  return threadId ? chatKeys.thread(orgId, threadId) : chatKeys.channel(orgId, channelId);
}

/**
 * The messages of a channel, or with `threadId` of one of its threads,
 * oldest first, with the device's unconfirmed sends. Pages load in both
 * directions from the newest page; at most `CHAT_MAX_PAGES` stay loaded.
 */
export function useChatMessages(channelId: string | null, threadId: string | null = null) {
  const orgId: string | null = useActiveOrganizationId();
  const query = useInfiniteQuery({
    queryKey: chatConversationKey(orgId ?? "", channelId ?? "", threadId),
    queryFn: ({ pageParam }: { pageParam: ChatMessageCursor }) =>
      threadId
        ? listThreadMessages(threadId, pageParam)
        : listChannelMessages(channelId as string, pageParam),
    initialPageParam: chatFirstPageParam(),
    getPreviousPageParam: (firstPage: ChatMessagePage) => chatOlderPageParam(firstPage),
    getNextPageParam: (lastPage: ChatMessagePage) => chatNewerPageParam(lastPage),
    maxPages: CHAT_MAX_PAGES,
    enabled: !!orgId && !!channelId,
  });
  const outbox = useChatOutbox(orgId, channelId, threadId);
  const messages: ChatMessageResponse[] = useMemo(
    () => flattenChatMessages(query.data),
    [query.data]
  );
  const lastPage: ChatMessagePage | undefined = query.data?.pages[query.data.pages.length - 1];
  const atNewest: boolean = !!lastPage && !lastPage.hasNewer;
  return { ...query, messages, outbox, atNewest };
}

/** How long a channel preview counts as fresh; realtime refreshes it before then. */
const PREVIEW_STALE_MS = 60_000;

/**
 * The newest message of each readable channel, for the channel list. The API
 * has no last-message field, so each preview is a one-message page; it is
 * refreshed when the channel's unread state changes and on every re-sync.
 */
export function useChatChannelPreviews(
  channels: readonly ChatChannelResponse[]
): ReadonlyMap<string, ChatMessageResponse | null> {
  const orgId: string | null = useActiveOrganizationId();
  const readable: ChatChannelResponse[] = channels.filter((channel) => channel.member);
  return useQueries({
    queries: readable.map((channel) => ({
      queryKey: chatKeys.preview(orgId ?? "", channel.id),
      queryFn: async (): Promise<ChatMessageResponse | null> => {
        const page: ChatMessagePage = await listChannelMessages(channel.id, {}, 1);
        return page.items[page.items.length - 1] ?? null;
      },
      enabled: !!orgId,
      staleTime: PREVIEW_STALE_MS,
    })),
    combine: (results: UseQueryResult<ChatMessageResponse | null>[]) => {
      const previews = new Map<string, ChatMessageResponse | null>();
      results.forEach((result, index) => {
        const channel: ChatChannelResponse | undefined = readable[index];
        if (channel && result.data !== undefined) previews.set(channel.id, result.data);
      });
      return previews as ReadonlyMap<string, ChatMessageResponse | null>;
    },
  });
}

/** How often a failed link preview is asked again before it shows its error. */
const CHAT_MESSAGE_PREVIEW_RETRIES = 2;

/**
 * `GET /kaizen/chat/messages/{id}`: one message as the reader sees it, for
 * the preview of a link to it; every link to one message shares the request.
 * A message the reader cannot see (404, 403) is null, an answer cached like
 * a message, so it is neither retried nor toasted. Any other failure is the
 * query's error, which the preview shows.
 */
export function useChatMessage(messageId: string | null) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<ChatMessageResponse | null>({
    queryKey: chatKeys.message(orgId ?? "", messageId ?? ""),
    queryFn: async (): Promise<ChatMessageResponse | null> => {
      try {
        return await getChatMessage(messageId as string, true);
      } catch (error) {
        const kind: string = toApiError(error).kind;
        if (kind === "not-found" || kind === "forbidden") return null;
        throw error;
      }
    },
    enabled: !!orgId && !!messageId,
    retry: CHAT_MESSAGE_PREVIEW_RETRIES,
  });
}

/** `GET /kaizen/chat/threads/{id}`: one thread with its root message, members and the reader's rights. */
export function useChatThread(threadId: string | null) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<ChatThreadResponse>({
    queryKey: chatKeys.threadDetail(orgId ?? "", threadId ?? ""),
    queryFn: () => getChatThread(threadId as string),
    enabled: !!orgId && !!threadId,
  });
}

/**
 * `GET /kaizen/chat/threads`: the threads of a channel whose effective status
 * is `status`, most recent activity first, a keyset page at a time. Realtime
 * marks the lists stale on thread changes and new replies; a list behind
 * another screen (`subscribed` false) only refetches once it shows again.
 */
export function useChatThreads(
  channelId: string | null,
  status: ChatThreadStatus,
  subscribed: boolean = true
) {
  const orgId: string | null = useActiveOrganizationId();
  return useInfiniteQuery({
    queryKey: chatKeys.threads(orgId ?? "", channelId ?? "", status),
    queryFn: ({ pageParam }: { pageParam: string | undefined }): Promise<ChatThreadPage> =>
      listChatThreads(channelId as string, status, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage: ChatThreadPage) => chatThreadsNextPageParam(lastPage),
    enabled: !!orgId && !!channelId,
    subscribed,
  });
}

/** Suggestions of one typed text stay this long; the composer asks again after it. */
const TICKET_SUGGESTIONS_STALE_MS = 30_000;

/**
 * The tickets the composer offers after `#`: the reader's boards searched by
 * key or title. `search` is the debounced text; null while no `#` is typed.
 */
export function useChatTicketSuggestions(search: string | null) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<ChatTicketSuggestion[]>({
    queryKey: chatKeys.ticketSuggestions(orgId ?? "", search ?? ""),
    queryFn: () =>
      searchChatTickets(search ?? "").catch((error: unknown) => {
        // The suggestion list shows the failure with a retry.
        console.warn("[chat] ticket search failed", error instanceof Error ? error.message : error);
        throw error;
      }),
    enabled: !!orgId && search !== null,
    staleTime: TICKET_SUGGESTIONS_STALE_MS,
    placeholderData: keepPreviousWithin(chatKeys.all(orgId ?? "")),
  });
}

/**
 * `GET /kaizen/chat/pins`: the pinned messages of a channel and its threads,
 * most recently pinned first. PIN_CHANGED signals of the channel refresh it.
 */
export function useChatPins(channelId: string | null) {
  const orgId: string | null = useActiveOrganizationId();
  return useInfiniteQuery({
    queryKey: chatKeys.pins(orgId ?? "", channelId ?? ""),
    queryFn: ({ pageParam }: { pageParam: string | undefined }) =>
      listChatPins(channelId as string, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage: ChatMessagePage) => chatNewestFirstNextPageParam(lastPage),
    enabled: !!orgId && !!channelId,
  });
}

/**
 * `GET /kaizen/chat/search`: messages matching the text, newest first, a
 * page at a time. Runs from `CHAT_SEARCH_MIN` characters; the caller
 * passes the debounced, trimmed text.
 */
export function useChatSearch(input: ChatSearchInput) {
  const orgId: string | null = useActiveOrganizationId();
  return useInfiniteQuery({
    queryKey: chatKeys.search(orgId ?? "", input.q, input.authorId, input.channelId),
    queryFn: ({ pageParam }: { pageParam: string | undefined }) =>
      searchChatMessages(input, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage: ChatMessagePage) => chatNewestFirstNextPageParam(lastPage),
    enabled: !!orgId && input.q.length >= CHAT_SEARCH_MIN,
  });
}

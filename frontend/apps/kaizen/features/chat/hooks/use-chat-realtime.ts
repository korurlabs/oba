"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { hashKey, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { useRealtimeResync } from "@/features/realtime/hooks/use-realtime-resync";
import { useRealtimeStatus } from "@/features/realtime/hooks/use-realtime-status";
import { useRealtimeSubscription } from "@/features/realtime/hooks/use-realtime-subscription";
import { RTM_SEC_002 } from "@oba/shared/realtime";
import { getChatMessage, requireChatData } from "../api";
import {
  CHAT_USER_DESTINATION,
  chatChannelDestination,
  chatOrganizationDestination,
  clearChatMessageVersions,
  compareChatVersions,
  isChatMessageDeleted,
  isNewerChatVersion,
  rememberChatMessageDeleted,
  rememberChatMessageVersion,
  signalledChatMessageVersion,
  withChatReaderFields,
} from "../lib/chat-realtime";
import {
  chatSignalSchema,
  chatUserSignalSchema,
  type ChatChannelSignal,
  type ChatMemberResponse,
  type ChatMessageResponse,
  type ChatMessageSignal,
  type ChatSignal,
  type ChatThreadResponse,
  type ChatUnreadResponse,
} from "../schemas";
import { CHAT_KEYS } from "./query-keys";
import { useChatOutboxResend } from "./use-chat-mutations";
import { useChatOpenChannelId } from "./use-chat-channels";
import {
  applyChatReaction,
  findChatMessage,
  insertChatMessage,
  isChatConversationAtNewest,
  patchChatMessage,
  refreshChatMessage,
  refreshChatThread,
  removeChatMessage,
  removeChatOutboxMessage,
  useChatMeId,
  useChatOrganizationId,
} from "./use-chat-messages";

const LOG_PREFIX = "[chat-realtime]";

/**
 * A signal task that failed is a defect: the cache helpers already recover
 * from a failed fetch. It goes to the browser's global error reporting, as
 * the realtime provider does with a listener that throws.
 */
function runSignalTask(task: Promise<unknown>): void {
  task.catch((error: unknown) => {
    console.error(`${LOG_PREFIX} a signal could not be applied`, error);
    if (typeof globalThis.reportError === "function") globalThis.reportError(error);
  });
}

/**
 * A frame that does not match the signal schema cannot be applied, so what it
 * changed in the open conversations is recovered by refetching their lists,
 * as after a gap. The channel list and the counts are recovered by
 * `useChatChannelListRealtime`, which reads the same organization topic and
 * queue.
 */
async function resyncAfterUnreadableSignal(
  queryClient: QueryClient,
  orgId: string,
  threadId: string | null,
): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channelMessagesAll(orgId) }),
    threadId ? queryClient.invalidateQueries({ queryKey: CHAT_KEYS.thread(orgId, threadId) }) : undefined,
  ]);
}

/**
 * The open channel was changed, deleted, or its member list changed: its
 * content is refetched. A channel the reader can no longer read answers 404
 * `KAI_NOT_015`, and the chat view switches to another channel on that error.
 * The channel list, the member lists and the counts are refetched by
 * `useChatChannelListRealtime`.
 */
async function applyOpenChannelChanged(queryClient: QueryClient, orgId: string, channelId: string): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channel(orgId, channelId) }),
    queryClient.invalidateQueries({ queryKey: CHAT_KEYS.pins(orgId, channelId) }),
    queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channelThreads(orgId, channelId) }),
  ]);
}

/**
 * A channel was created, changed, deleted, or its member list changed: the
 * channel list, the channel's member lists and the unread counts may differ.
 */
async function applyChannelListChanged(
  queryClient: QueryClient,
  orgId: string,
  signal: ChatChannelSignal,
): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channels(orgId) }),
    queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channelMembers(orgId, signal.channelId) }),
    queryClient.invalidateQueries({ queryKey: CHAT_KEYS.unread(orgId) }),
  ]);
}

/** A frame of the organization topic or the queue that cannot be read may have changed the channel list or the counts. */
async function resyncChannelList(queryClient: QueryClient, orgId: string): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channels(orgId) }),
    queryClient.invalidateQueries({ queryKey: CHAT_KEYS.unread(orgId) }),
  ]);
}

/**
 * Keeps the channel list in the sidebar and the reader's unread counts
 * current on every dashboard page, also while the chat view is not open.
 * CHANNEL_CHANGED of a public channel arrives on the organization topic,
 * that of a private channel on the reader's own queue; either refetches the
 * channel list, the channel's member lists and the unread counts.
 * UNREAD_CHANGED on the queue replaces the counts (also after a read in
 * another tab or device). After every (re)connect the channel list and the
 * counts are refetched, and after an `RTM_SEC_002` refusal the counts.
 *
 * Mounted once, by the sidebar, so that each of these triggers sends one
 * request per query; the many readers of the counts (`useChatUnread`) only
 * read the cache. The provider shares one subscription per destination with
 * `useChatRealtime`, which refetches only the open channel's content.
 * Being mounted once on every page, it also owns the resend of messages that
 * got no response (`useChatOutboxResend`).
 */
export function useChatChannelListRealtime(): void {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  const { status, errorCode } = useRealtimeStatus();
  useChatOutboxResend();

  const onSignal = (body: unknown, schema: typeof chatSignalSchema | typeof chatUserSignalSchema): void => {
    if (!orgId) return;
    const parsed = schema.safeParse(body);
    if (!parsed.success) {
      console.error(`${LOG_PREFIX} unreadable signal; refetching the channel list and the counts`, parsed.error);
      runSignalTask(resyncChannelList(queryClient, orgId));
      return;
    }
    if (parsed.data.type === "CHANNEL_CHANGED") {
      runSignalTask(applyChannelListChanged(queryClient, orgId, parsed.data));
    } else if (parsed.data.type === "UNREAD_CHANGED") {
      queryClient.setQueryData<ChatUnreadResponse>(CHAT_KEYS.unread(orgId), parsed.data.unread);
    }
  };

  useRealtimeSubscription(orgId ? chatOrganizationDestination(orgId) : null, (body: unknown) =>
    onSignal(body, chatSignalSchema),
  );
  useRealtimeSubscription(orgId ? CHAT_USER_DESTINATION : null, (body: unknown) =>
    onSignal(body, chatUserSignalSchema),
  );

  useRealtimeResync(() => {
    if (orgId) runSignalTask(resyncChannelList(queryClient, orgId));
  });

  // A refused socket means the REST calls are refused too: refetch so the
  // badge shows that, not the counts from before.
  const refused = status === "offline" && errorCode === RTM_SEC_002;
  useEffect(() => {
    if (refused && orgId) runSignalTask(queryClient.invalidateQueries({ queryKey: CHAT_KEYS.unread(orgId) }));
  }, [refused, orgId, queryClient]);
}

/**
 * Refetches the cached content of a channel that was cached before its topic
 * was subscribed: signals sent while the reader was in another channel never
 * reached it. Content first fetched after `since` is current and kept.
 */
async function refreshChannelAfterSubscribe(
  queryClient: QueryClient,
  orgId: string,
  channelId: string,
  since: number,
): Promise<void> {
  const cachedBefore = (query: { state: { data: unknown; dataUpdatedAt: number } }): boolean =>
    query.state.data !== undefined && query.state.dataUpdatedAt < since;
  await Promise.all(
    [
      CHAT_KEYS.channel(orgId, channelId),
      CHAT_KEYS.pins(orgId, channelId),
      CHAT_KEYS.channelThreads(orgId, channelId),
    ].map((queryKey) => queryClient.invalidateQueries({ queryKey, predicate: cachedBefore })),
  );
}

/**
 * What to do with a MESSAGE_CREATED or MESSAGE_UPDATED signal:
 * - `drop`: the message was deleted in this session, or the loaded copy is
 *   as new as the payload or newer (an out-of-order frame);
 * - `apply`: use the payload, with the reader's own fields filled in;
 * - `fetch`: read the message for the reader with `GET /messages/{id}`. The
 *   signal has no payload, the reader's ID is not known yet, or a newer
 *   change was announced whose content no loaded copy holds yet.
 */
type ChatPayloadDecision =
  | { kind: "drop" }
  | { kind: "apply"; message: ChatMessageResponse }
  | { kind: "fetch" };

function decideMessagePayload(
  queryClient: QueryClient,
  orgId: string,
  signal: ChatMessageSignal,
  meId: string | undefined,
): ChatPayloadDecision {
  if (isChatMessageDeleted(signal.messageId)) return { kind: "drop" };
  const payload = signal.message;
  if (!payload || !meId) return { kind: "fetch" };
  const loaded = findChatMessage(queryClient, orgId, signal.messageId);
  if (loaded && !isNewerChatVersion(payload.version, loaded.version)) return { kind: "drop" };
  const signalled = signalledChatMessageVersion(signal.messageId);
  if (signalled && compareChatVersions(signalled, payload.version) > 0) return { kind: "fetch" };
  const thread = payload.threadId
    ? queryClient.getQueryData<ChatThreadResponse>(CHAT_KEYS.threadDetail(orgId, payload.threadId))
    : undefined;
  return { kind: "apply", message: withChatReaderFields(payload, { userId: meId, threadMember: !!thread?.member }) };
}

async function applyMessageCreated(
  queryClient: QueryClient,
  orgId: string,
  channelId: string,
  signal: ChatMessageSignal,
  meId: string | undefined,
  countNew: (threadId: string | null) => void,
): Promise<void> {
  const decision = decideMessagePayload(queryClient, orgId, signal, meId);
  if (signal.version) rememberChatMessageVersion(signal.messageId, signal.version);
  const isMine = !!meId && signal.authorId === meId;
  if (decision.kind === "drop") {
    // The server holds the message, so the sender's pending row is obsolete either way.
    if (isMine && signal.clientNonce) removeChatOutboxMessage(signal.clientNonce);
    return;
  }
  const loaded = !!findChatMessage(queryClient, orgId, signal.messageId);
  // The signal came on the channel's own topic, so it belongs to `channelId`.
  const atNewest = isChatConversationAtNewest(queryClient, orgId, channelId, signal.threadId);
  if (loaded || atNewest) {
    try {
      const message =
        decision.kind === "apply" ? decision.message : requireChatData(await getChatMessage(signal.messageId));
      insertChatMessage(queryClient, orgId, message);
      // The sender's own message replaces its pending row, whichever of the
      // response and the signal arrives first.
      if (isMine && signal.clientNonce) removeChatOutboxMessage(signal.clientNonce);
    } catch (error) {
      console.error(`${LOG_PREFIX} could not fetch a new message; refetching`, signal.messageId, error);
      await queryClient.invalidateQueries({
        queryKey: signal.threadId ? CHAT_KEYS.thread(orgId, signal.threadId) : CHAT_KEYS.channelMessagesAll(orgId),
      });
    }
  } else if (!isMine) {
    countNew(signal.threadId);
  }
  if (signal.threadId) await refreshChatThread(queryClient, orgId, signal.threadId);
}

/**
 * An edit replaces every loaded copy that is older than the payload. The
 * loaded reactions are kept: they change only through REACTION_CHANGED,
 * which patches them, and the payload's carry no `reactedByMe`. Without a
 * payload a loaded message is fetched for the reader.
 */
async function applyMessageUpdated(
  queryClient: QueryClient,
  orgId: string,
  signal: ChatMessageSignal,
  meId: string | undefined,
): Promise<void> {
  const decision = decideMessagePayload(queryClient, orgId, signal, meId);
  if (signal.version) rememberChatMessageVersion(signal.messageId, signal.version);
  if (decision.kind === "apply") {
    const edited = decision.message;
    patchChatMessage(queryClient, orgId, signal.messageId, (current) =>
      isNewerChatVersion(edited.version, current.version) ? { ...edited, reactions: current.reactions } : current,
    );
  } else if (decision.kind === "fetch" && findChatMessage(queryClient, orgId, signal.messageId)) {
    await refreshChatMessage(queryClient, orgId, signal.messageId);
  }
}

export interface UseChatRealtimeOptions {
  /** The thread open in the panel; its list is re-synced and its new messages counted. */
  threadId?: string | null;
  /**
   * Every valid signal of the open channel's topic after the cache was
   * updated, except the reader's own TYPING, with the channel the topic
   * belongs to; the typing indicator listens here for TYPING and
   * MESSAGE_CREATED.
   */
  onSignal?: (signal: ChatSignal, channelId: string) => void;
}

/**
 * Keeps the chat caches of the active organization live. Mounted once by the
 * chat view. It subscribes three destinations: the open channel's topic, the
 * organization topic and the reader's own queue. Opening another channel
 * unsubscribes the previous channel's topic and subscribes the new one.
 *
 * Open channel topic:
 * - MESSAGE_CREATED / MESSAGE_UPDATED: takes the message from the signal's
 *   payload, with `mentionsMe`, `repliesToMe` and `reactedByMe` computed for
 *   the reader, or fetches it for the reader when the signal has none. A
 *   payload not newer than the loaded copy, or of a message deleted in this
 *   session, is dropped. A new message is upserted into the lists that hold
 *   the conversation's newest page; when none does (the reader is viewing
 *   older messages) a message of someone else is counted in
 *   `newMessageCount` instead. The sender's pending row is removed by its
 *   `clientNonce`. An edit replaces loaded copies only.
 * - MESSAGE_DELETED: the row is dropped, or becomes the deleted placeholder
 *   when it carries a thread; later payloads of it are dropped.
 * - REACTION_CHANGED: patches counts; "mine" compares the signal's Oba
 *   user ID with the `me` query, never with the Keycloak subject.
 * - PIN_CHANGED, THREAD_*: refetch the message or root and the pins and
 *   thread queries. TYPING goes to `onSignal` only.
 *
 * Organization topic: PRESENCE patches every member list (the organization's
 * and each channel's); CHANNEL_CHANGED of the open channel (public) refetches
 * its content. Own queue: CHANNEL_CHANGED of the open channel (private) does
 * the same; THREAD_MEMBERSHIP_CHANGED refetches the thread queries. The
 * channel list follows CHANNEL_CHANGED in `useChatChannelListRealtime`, which
 * the sidebar mounts, and so does UNREAD_CHANGED.
 *
 * After every (re)connect it refetches the message, thread and pin lists, the
 * open thread and the members, which recovers any signal missed during the
 * gap (the channel list and the counts are refetched by the sidebar's
 * hooks); after an
 * `RTM_SEC_002` refusal it refetches every chat query, so the REST error state
 * of a lost membership shows.
 */
export function useChatRealtime({ threadId = null, onSignal }: UseChatRealtimeOptions = {}) {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  const channelId = useChatOpenChannelId();
  const meId = useChatMeId();
  const { status, errorCode } = useRealtimeStatus();

  const [newCounts, setNewCounts] = useState<Record<string, number>>({});
  const conversationKey = (id: string | null) => id ?? "channel";

  // The counters belong to the channel they were counted in.
  const [countsChannelId, setCountsChannelId] = useState(channelId);
  if (countsChannelId !== channelId) {
    setCountsChannelId(channelId);
    setNewCounts({});
  }

  const countNew = useCallback(
    (signalThreadId: string | null) => {
      // A thread's new messages count only while it is open.
      if (signalThreadId && signalThreadId !== threadId) return;
      const key = signalThreadId ?? "channel";
      setNewCounts((counts) => ({ ...counts, [key]: (counts[key] ?? 0) + 1 }));
    },
    [threadId],
  );

  /** Resets the counter of a conversation (`null`: the channel) once its newest page is shown. */
  const clearNewMessageCount = useCallback((id: string | null) => {
    const key = id ?? "channel";
    setNewCounts((counts) => (counts[key] ? { ...counts, [key]: 0 } : counts));
  }, []);

  const onChannelChanged = (signal: ChatChannelSignal): void => {
    if (!orgId || signal.channelId !== channelId) return;
    runSignalTask(applyOpenChannelChanged(queryClient, orgId, signal.channelId));
  };

  useRealtimeSubscription(
    orgId && channelId ? chatChannelDestination(orgId, channelId) : null,
    (body: unknown) => {
      if (!orgId || !channelId) return;
      const parsed = chatSignalSchema.safeParse(body);
      if (!parsed.success) {
        console.error(`${LOG_PREFIX} unreadable channel signal; re-syncing`, parsed.error);
        runSignalTask(resyncAfterUnreadableSignal(queryClient, orgId, threadId));
        return;
      }
      const signal = parsed.data;
      switch (signal.type) {
        case "MESSAGE_CREATED":
          runSignalTask(applyMessageCreated(queryClient, orgId, channelId, signal, meId, countNew));
          break;
        case "MESSAGE_UPDATED":
          runSignalTask(applyMessageUpdated(queryClient, orgId, signal, meId));
          break;
        case "MESSAGE_DELETED": {
          const wasPinned = !!findChatMessage(queryClient, orgId, signal.messageId)?.pinnedAt;
          rememberChatMessageDeleted(signal.messageId);
          removeChatMessage(queryClient, orgId, signal.messageId);
          runSignalTask(
            Promise.all([
              wasPinned ? queryClient.invalidateQueries({ queryKey: CHAT_KEYS.pins(orgId, channelId) }) : undefined,
              queryClient.invalidateQueries({ queryKey: CHAT_KEYS.searchAll(orgId) }),
              signal.threadId ? refreshChatThread(queryClient, orgId, signal.threadId) : undefined,
            ]),
          );
          break;
        }
        case "REACTION_CHANGED":
          patchChatMessage(queryClient, orgId, signal.messageId, (message) =>
            applyChatReaction(message, {
              emoji: signal.emoji,
              userId: signal.userId,
              added: signal.added,
              isMe: !!meId && signal.userId === meId,
            }),
          );
          break;
        case "PIN_CHANGED":
          runSignalTask(
            Promise.all([
              findChatMessage(queryClient, orgId, signal.messageId)
                ? refreshChatMessage(queryClient, orgId, signal.messageId)
                : undefined,
              queryClient.invalidateQueries({ queryKey: CHAT_KEYS.pins(orgId, channelId) }),
            ]),
          );
          break;
        case "THREAD_CREATED":
        case "THREAD_UPDATED":
          runSignalTask(
            Promise.all([
              queryClient.invalidateQueries({ queryKey: CHAT_KEYS.threadDetail(orgId, signal.threadId) }),
              queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channelThreads(orgId, channelId) }),
              findChatMessage(queryClient, orgId, signal.rootMessageId)
                ? refreshChatMessage(queryClient, orgId, signal.rootMessageId)
                : undefined,
            ]),
          );
          break;
        case "TYPING":
          if (meId && signal.userId === meId) return;
          break;
        case "PRESENCE":
        case "CHANNEL_CHANGED":
          console.warn(`${LOG_PREFIX} ignored a ${signal.type} signal on a channel topic`);
          return;
      }
      onSignal?.(signal, channelId);
    },
  );

  useRealtimeSubscription(
    orgId ? chatOrganizationDestination(orgId) : null,
    (body: unknown) => {
      if (!orgId) return;
      const parsed = chatSignalSchema.safeParse(body);
      if (!parsed.success) {
        console.error(`${LOG_PREFIX} unreadable organization signal; re-syncing`, parsed.error);
        runSignalTask(resyncAfterUnreadableSignal(queryClient, orgId, threadId));
        return;
      }
      const signal = parsed.data;
      switch (signal.type) {
        case "PRESENCE":
          queryClient.setQueriesData<ChatMemberResponse[]>({ queryKey: CHAT_KEYS.membersAll(orgId) }, (members) =>
            members?.map((member) =>
              member.userId === signal.userId ? { ...member, online: signal.online } : member,
            ),
          );
          break;
        case "CHANNEL_CHANGED":
          onChannelChanged(signal);
          break;
        default:
          // The content signals of a channel arrive on its own topic only.
          console.warn(`${LOG_PREFIX} ignored a ${signal.type} signal on the organization topic`);
      }
    },
  );

  useRealtimeSubscription(
    orgId ? CHAT_USER_DESTINATION : null,
    (body: unknown) => {
      if (!orgId) return;
      const parsed = chatUserSignalSchema.safeParse(body);
      if (!parsed.success) {
        console.error(`${LOG_PREFIX} unreadable user signal; re-syncing`, parsed.error);
        runSignalTask(resyncAfterUnreadableSignal(queryClient, orgId, threadId));
        return;
      }
      const signal = parsed.data;
      if (signal.type === "THREAD_MEMBERSHIP_CHANGED") {
        runSignalTask(
          Promise.all([
            queryClient.invalidateQueries({ queryKey: CHAT_KEYS.threadDetail(orgId, signal.threadId) }),
            queryClient.invalidateQueries({ queryKey: CHAT_KEYS.threadsAll(orgId) }),
          ]),
        );
      } else if (signal.type === "CHANNEL_CHANGED") {
        onChannelChanged(signal);
      }
    },
  );

  // A channel opened again may hold content cached while its topic was not
  // subscribed. The first channel is skipped: it was fetched for this mount.
  const subscribedChannelRef = useRef<string | undefined>(channelId);
  useEffect(() => {
    const previous = subscribedChannelRef.current;
    subscribedChannelRef.current = channelId;
    if (!orgId || !channelId || previous === undefined || previous === channelId) return;
    runSignalTask(refreshChannelAfterSubscribe(queryClient, orgId, channelId, Date.now()));
  }, [orgId, channelId, queryClient]);

  // Re-sync after every (re)connect: signals of the gap are lost. Lists of
  // channels not open are only marked stale and refetch when opened.
  useRealtimeResync(() => {
    if (!orgId) return;
    // The lists reload as the server holds them now; versions and deletions
    // seen before the gap say nothing about the frames that follow.
    clearChatMessageVersions();
    runSignalTask(
      Promise.all([
        queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channelMessagesAll(orgId) }),
        queryClient.invalidateQueries({ queryKey: CHAT_KEYS.threadsAll(orgId) }),
        queryClient.invalidateQueries({ queryKey: CHAT_KEYS.pinsAll(orgId) }),
        threadId ? queryClient.invalidateQueries({ queryKey: CHAT_KEYS.thread(orgId, threadId) }) : undefined,
        threadId ? queryClient.invalidateQueries({ queryKey: CHAT_KEYS.threadDetail(orgId, threadId) }) : undefined,
        queryClient.invalidateQueries({ queryKey: CHAT_KEYS.membersAll(orgId) }),
      ]),
    );
    setNewCounts({});
  });

  // A refused socket (membership removed or disabled, organization disabled)
  // means the REST calls fail too: refetch so the views show that error. The
  // counts are refetched by `useChatChannelListRealtime`, which reacts to the
  // same refusal; refetching them here too would send a second request.
  const refused = status === "offline" && errorCode === RTM_SEC_002;
  useEffect(() => {
    if (!refused || !orgId) return;
    const unreadHash = hashKey(CHAT_KEYS.unread(orgId));
    runSignalTask(
      queryClient.invalidateQueries({
        queryKey: CHAT_KEYS.all(orgId),
        predicate: (query) => query.queryHash !== unreadHash,
      }),
    );
  }, [refused, orgId, queryClient]);

  return {
    /** Messages of others that arrived while the open channel's list was not at its newest page. */
    channelNewMessageCount: newCounts[conversationKey(null)] ?? 0,
    /** The same for the open thread. */
    threadNewMessageCount: threadId ? (newCounts[conversationKey(threadId)] ?? 0) : 0,
    clearNewMessageCount,
  };
}

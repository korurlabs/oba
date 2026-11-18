import { hashKey, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { realtimeOrganizationTopic, RTM_SEC_002 } from "@oba/shared/realtime";

import { useChatMeId } from "@/hooks/queries/chat";
import { getChatMessage } from "@/hooks/queries/chatApi";
import {
  applyChatReaction,
  chatKeys,
  clearChatMessageVersions,
  findChatMessage,
  findChatThreadRootId,
  insertChatMessage,
  isNewerChatVersion,
  patchChatMessage,
  rememberChatMessageDeleted,
  rememberChatMessageVersion,
  removeChatMessage,
  replaceChatMessage,
} from "@/hooks/queries/chatCache";
import {
  decideChatMessagePayload,
  planChatMessageCreated,
  type ChatMessageCreatedPlan,
  type ChatPayloadDecision,
} from "@/hooks/queries/chatMessageSignals";
import { createChatOutboxResender, removeChatOutboxMessage } from "@/hooks/queries/chatOutbox";
import { useSendChatMessage } from "@/hooks/queries/chatMutations";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import {
  realtime,
  useRealtimeResync,
  useRealtimeStatus,
  useRealtimeSubscription,
} from "@/lib/realtime";
import {
  CHAT_TYPING_INTERVAL_MS,
  CHAT_TYPING_TIMEOUT_MS,
  chatSignalSchema,
  chatUserSignalSchema,
  type ChatConversationUnreadResponse,
  type ChatMemberResponse,
  type ChatMessageResponse,
  type ChatMessageSignal,
  type ChatSignal,
  type ChatUnread,
} from "@/lib/schemas/chat";

const LOG_PREFIX = "[chat-realtime]";

/** The organization's chat signals: presence, and CHANNEL_CHANGED of public channels. */
export function chatOrganizationDestination(organizationId: string): string {
  return realtimeOrganizationTopic(organizationId, "/chat");
}

/** One channel's signals: messages, reactions, pins, threads and typing. */
export function chatChannelDestination(organizationId: string, channelId: string): string {
  return realtimeOrganizationTopic(organizationId, `/chat/channels/${channelId}`);
}

/** The reader's own signals: unread state, thread membership, private channel changes. */
export const CHAT_USER_DESTINATION = "/user/queue/chat";

/** Where the composer sends the typing signal; the server relays it to the channel topic. */
export const CHAT_TYPING_DESTINATION = "/app/chat/typing";

/**
 * A signal task that failed is a defect: the cache helpers already recover
 * from a failed fetch. It is logged as an error, which the dev overlay shows
 * in a development build; a release build only writes it to the device log.
 */
function runSignalTask(task: Promise<unknown>): void {
  task.catch((error: unknown) => {
    console.error(`${LOG_PREFIX} a signal could not be applied`, error);
  });
}

/**
 * The channels whose newest message may have changed: their unread count or
 * read position moved. Their list previews are refetched.
 */
export function changedUnreadChannelIds(
  previous: ChatUnread | undefined,
  next: ChatUnread
): string[] {
  const before = new Map<string, ChatConversationUnreadResponse>(
    (previous?.channels ?? []).map((channel) => [channel.id, channel])
  );
  return next.channels
    .filter((channel) => {
      const old: ChatConversationUnreadResponse | undefined = before.get(channel.id);
      return (
        !old || old.unread !== channel.unread || old.lastReadMessageId !== channel.lastReadMessageId
      );
    })
    .map((channel) => channel.id);
}

async function resyncChannelList(queryClient: QueryClient, orgId: string): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: chatKeys.channels(orgId) }),
    queryClient.invalidateQueries({ queryKey: chatKeys.unread(orgId) }),
    queryClient.invalidateQueries({ queryKey: chatKeys.previewsAll(orgId) }),
  ]);
}

/**
 * Keeps the channel list, its previews and the unread state current while
 * the Chat tab is mounted. CHANNEL_CHANGED (organization topic for public
 * channels, own queue for private ones) refetches the list, the members and
 * the counts; UNREAD_CHANGED replaces the counts and refreshes the previews
 * of the channels it moved. It also owns the resend of messages that got no
 * response, when the session connects and when the network comes back.
 */
export function useChatChannelListRealtime(): void {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const { status, errorCode, networkOnline } = useRealtimeStatus();
  const { resend } = useSendChatMessage();

  const onSignal = (body: unknown): void => {
    if (!orgId) return;
    const parsed = chatUserSignalSchema.or(chatSignalSchema).safeParse(body);
    if (!parsed.success) {
      console.error(`${LOG_PREFIX} unreadable signal; refetching the channel list`, parsed.error);
      runSignalTask(resyncChannelList(queryClient, orgId));
      return;
    }
    const signal = parsed.data;
    if (signal.type === "CHANNEL_CHANGED") {
      runSignalTask(
        Promise.all([
          queryClient.invalidateQueries({ queryKey: chatKeys.channels(orgId) }),
          queryClient.invalidateQueries({ queryKey: chatKeys.membersAll(orgId) }),
          queryClient.invalidateQueries({ queryKey: chatKeys.unread(orgId) }),
          queryClient.invalidateQueries({ queryKey: chatKeys.preview(orgId, signal.channelId) }),
        ])
      );
    } else if (signal.type === "THREAD_MEMBERSHIP_CHANGED") {
      runSignalTask(
        Promise.all([
          queryClient.invalidateQueries({
            queryKey: chatKeys.threadDetail(orgId, signal.threadId),
          }),
          queryClient.invalidateQueries({ queryKey: chatKeys.threadsAll(orgId) }),
        ])
      );
    } else if (signal.type === "UNREAD_CHANGED") {
      const previous: ChatUnread | undefined = queryClient.getQueryData<ChatUnread>(
        chatKeys.unread(orgId)
      );
      queryClient.setQueryData<ChatUnread>(chatKeys.unread(orgId), signal.unread);
      const changed: string[] = changedUnreadChannelIds(previous, signal.unread);
      runSignalTask(
        Promise.all(
          changed.map((channelId) =>
            queryClient.invalidateQueries({ queryKey: chatKeys.preview(orgId, channelId) })
          )
        )
      );
    }
  };

  useRealtimeSubscription(orgId ? chatOrganizationDestination(orgId) : null, onSignal);
  useRealtimeSubscription(orgId ? CHAT_USER_DESTINATION : null, onSignal);

  useRealtimeResync(() => {
    if (orgId) runSignalTask(resyncChannelList(queryClient, orgId));
  });

  // A refused session means the REST calls are refused too: refetch so the
  // views show that, not the data from before.
  const refused: boolean = status === "offline" && errorCode === RTM_SEC_002;
  useEffect(() => {
    if (!refused || !orgId) return;
    const unreadHash: string = hashKey(chatKeys.unread(orgId));
    runSignalTask(
      Promise.all([
        queryClient.invalidateQueries({ queryKey: chatKeys.unread(orgId) }),
        queryClient.invalidateQueries({
          queryKey: chatKeys.all(orgId),
          predicate: (query) => query.queryHash !== unreadHash,
        }),
      ])
    );
  }, [refused, orgId, queryClient]);

  const resender = useMemo(() => createChatOutboxResender(resend), [resend]);
  useEffect(() => {
    if (status === "connected" && networkOnline && orgId) resender(orgId);
  }, [status, networkOnline, orgId, resender]);
}

/** Reads one message for the reader and replaces its loaded copies. */
async function refreshChatMessage(
  queryClient: QueryClient,
  orgId: string,
  messageId: string
): Promise<void> {
  try {
    const message: ChatMessageResponse = await getChatMessage(messageId);
    replaceChatMessage(queryClient, orgId, message);
  } catch (error) {
    console.warn(`${LOG_PREFIX} could not refresh a message; refetching the lists`, error);
    await queryClient.invalidateQueries({ queryKey: chatKeys.channelMessagesAll(orgId) });
  }
}

async function applyMessageCreated(
  queryClient: QueryClient,
  orgId: string,
  conversation: { channelId: string; threadId: string | null },
  signal: ChatMessageSignal,
  meId: string | undefined,
  countNew: () => void
): Promise<void> {
  const { channelId, threadId } = conversation;
  const { decision, insert }: ChatMessageCreatedPlan = planChatMessageCreated(
    queryClient,
    orgId,
    conversation,
    signal,
    meId
  );
  if (signal.version) rememberChatMessageVersion(signal.messageId, signal.version);
  const isMine: boolean = !!meId && signal.authorId === meId;
  if (decision.kind === "drop") {
    if (isMine && signal.clientNonce) removeChatOutboxMessage(signal.clientNonce);
    return;
  }
  if (insert) {
    try {
      const message: ChatMessageResponse =
        decision.kind === "apply" ? decision.message : await getChatMessage(signal.messageId);
      insertChatMessage(queryClient, orgId, message);
      // A reply is not the channel's newest message; the list preview stays.
      if (!threadId) queryClient.setQueryData(chatKeys.preview(orgId, channelId), message);
      // The sender's own message replaces its pending row, whichever of the
      // response and the signal arrives first.
      if (isMine && signal.clientNonce) removeChatOutboxMessage(signal.clientNonce);
    } catch (error) {
      console.warn(`${LOG_PREFIX} could not fetch a new message; refetching`, error);
      await queryClient.invalidateQueries({
        queryKey: threadId ? chatKeys.thread(orgId, threadId) : chatKeys.channel(orgId, channelId),
      });
    }
  } else if (!isMine) {
    countNew();
  }
}

async function applyMessageUpdated(
  queryClient: QueryClient,
  orgId: string,
  signal: ChatMessageSignal,
  meId: string | undefined
): Promise<void> {
  // An edit reaches every loaded copy, so a copy in a narrowed list counts as loaded.
  const decision: ChatPayloadDecision = decideChatMessagePayload(
    queryClient,
    orgId,
    signal,
    meId,
    findChatMessage(queryClient, orgId, signal.messageId)
  );
  if (signal.version) rememberChatMessageVersion(signal.messageId, signal.version);
  if (decision.kind === "apply") {
    const edited: ChatMessageResponse = decision.message;
    // Reactions change only through REACTION_CHANGED; the payload has no `reactedByMe`.
    patchChatMessage(queryClient, orgId, signal.messageId, (current) =>
      isNewerChatVersion(edited.version, current.version)
        ? { ...edited, reactions: current.reactions }
        : current
    );
  } else if (decision.kind === "fetch" && findChatMessage(queryClient, orgId, signal.messageId)) {
    await refreshChatMessage(queryClient, orgId, signal.messageId);
  }
}

/**
 * A thread's signal seen by the channel: an open thread screen applies it
 * itself, so the channel only marks the thread's caches stale (a closed
 * thread reloads on its next visit) and, when a reply came or went,
 * refreshes the root's reply summary and the channel's thread lists, whose
 * reply counts and order follow the last reply.
 */
async function noteThreadSignalInChannel(
  queryClient: QueryClient,
  orgId: string,
  channelId: string,
  threadId: string,
  refreshRoot: boolean
): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({
      queryKey: chatKeys.thread(orgId, threadId),
      refetchType: "none",
    }),
    queryClient.invalidateQueries({
      queryKey: chatKeys.threadDetail(orgId, threadId),
      refetchType: "none",
    }),
  ]);
  if (!refreshRoot) return;
  const rootId: string | undefined = findChatThreadRootId(queryClient, orgId, threadId);
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: chatKeys.channelThreads(orgId, channelId) }),
    rootId && findChatMessage(queryClient, orgId, rootId)
      ? refreshChatMessage(queryClient, orgId, rootId)
      : undefined,
  ]);
}

/**
 * Keeps a conversation live through its channel's topic: new, edited and
 * deleted messages, reactions, pins and typing. Without `threadId` it is the
 * channel itself, which also follows thread summaries, presence and channel
 * changes; with one it is that thread, and it applies only the signals of
 * that thread. The channel screen stays mounted under an open thread, so
 * every signal is applied once. Messages of others that arrive while the
 * list is scrolled into the past are counted for the jump pill. After every
 * reconnect the lists are refetched.
 */
export function useChatChannelRealtime(
  channelId: string | null,
  onSignal?: (signal: ChatSignal) => void,
  threadId: string | null = null
) {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const meId: string | undefined = useChatMeId();
  const [newMessageCount, setNewMessageCount] = useState<number>(0);

  const countNew = useCallback(() => {
    setNewMessageCount((count) => count + 1);
  }, []);
  const clearNewMessageCount = useCallback(() => {
    setNewMessageCount((count) => (count === 0 ? count : 0));
  }, []);

  const conversationKey = (org: string, channel: string): readonly unknown[] =>
    threadId ? chatKeys.thread(org, threadId) : chatKeys.channel(org, channel);

  useRealtimeSubscription(
    orgId && channelId ? chatChannelDestination(orgId, channelId) : null,
    (body: unknown) => {
      if (!orgId || !channelId) return;
      const parsed = chatSignalSchema.safeParse(body);
      if (!parsed.success) {
        console.error(`${LOG_PREFIX} unreadable channel signal; re-syncing`, parsed.error);
        runSignalTask(
          queryClient.invalidateQueries({ queryKey: conversationKey(orgId, channelId) })
        );
        return;
      }
      const signal: ChatSignal = parsed.data;
      const signalThreadId: string | null =
        "threadId" in signal && signal.type !== "THREAD_CREATED" && signal.type !== "THREAD_UPDATED"
          ? signal.threadId
          : null;
      // A thread screen applies only its own thread's signals.
      if (threadId && signalThreadId !== threadId) return;
      if (!threadId && signalThreadId) {
        if (signal.type === "MESSAGE_DELETED") rememberChatMessageDeleted(signal.messageId);
        // The channel's pin list holds pinned replies too.
        if (signal.type === "PIN_CHANGED") {
          runSignalTask(
            queryClient.invalidateQueries({ queryKey: chatKeys.pins(orgId, channelId) })
          );
        }
        runSignalTask(
          noteThreadSignalInChannel(
            queryClient,
            orgId,
            channelId,
            signalThreadId,
            signal.type === "MESSAGE_CREATED" || signal.type === "MESSAGE_DELETED"
          )
        );
        // The channel's typing line ignores a thread's typing; others may not.
        onSignal?.(signal);
        return;
      }
      switch (signal.type) {
        case "MESSAGE_CREATED":
          runSignalTask(
            Promise.all([
              applyMessageCreated(
                queryClient,
                orgId,
                { channelId, threadId },
                signal,
                meId,
                countNew
              ),
              threadId
                ? queryClient.invalidateQueries({
                    queryKey: chatKeys.threadDetail(orgId, threadId),
                  })
                : undefined,
            ])
          );
          break;
        case "MESSAGE_UPDATED":
          runSignalTask(applyMessageUpdated(queryClient, orgId, signal, meId));
          break;
        case "MESSAGE_DELETED": {
          const wasPinned: boolean = !!findChatMessage(queryClient, orgId, signal.messageId)
            ?.pinnedAt;
          rememberChatMessageDeleted(signal.messageId);
          removeChatMessage(queryClient, orgId, signal.messageId);
          runSignalTask(
            Promise.all([
              wasPinned
                ? queryClient.invalidateQueries({ queryKey: chatKeys.pins(orgId, channelId) })
                : undefined,
              threadId
                ? queryClient.invalidateQueries({
                    queryKey: chatKeys.threadDetail(orgId, threadId),
                  })
                : queryClient.invalidateQueries({ queryKey: chatKeys.preview(orgId, channelId) }),
            ])
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
            })
          );
          break;
        case "PIN_CHANGED":
          runSignalTask(
            Promise.all([
              findChatMessage(queryClient, orgId, signal.messageId)
                ? refreshChatMessage(queryClient, orgId, signal.messageId)
                : undefined,
              queryClient.invalidateQueries({ queryKey: chatKeys.pins(orgId, channelId) }),
            ])
          );
          break;
        case "THREAD_CREATED":
        case "THREAD_UPDATED":
          // Only the channel gets here: a thread screen returned above.
          runSignalTask(
            Promise.all([
              queryClient.invalidateQueries({ queryKey: chatKeys.threadsAll(orgId) }),
              queryClient.invalidateQueries({
                queryKey: chatKeys.threadDetail(orgId, signal.threadId),
              }),
              findChatMessage(queryClient, orgId, signal.rootMessageId)
                ? refreshChatMessage(queryClient, orgId, signal.rootMessageId)
                : undefined,
            ])
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
      onSignal?.(signal);
    }
  );

  // Presence and channel changes are the channel's; a thread screen sits on top of it.
  useRealtimeSubscription(
    orgId && !threadId ? chatOrganizationDestination(orgId) : null,
    (body: unknown) => {
      if (!orgId) return;
      const parsed = chatSignalSchema.safeParse(body);
      // The channel list hook re-syncs an unreadable frame of this topic.
      if (!parsed.success) return;
      const signal: ChatSignal = parsed.data;
      if (signal.type === "PRESENCE") {
        queryClient.setQueriesData<ChatMemberResponse[]>(
          { queryKey: chatKeys.membersAll(orgId) },
          (members) =>
            members?.map((member) =>
              member.userId === signal.userId ? { ...member, online: signal.online } : member
            )
        );
      } else if (signal.type === "CHANNEL_CHANGED" && signal.channelId === channelId) {
        runSignalTask(
          Promise.all([
            queryClient.invalidateQueries({ queryKey: chatKeys.channel(orgId, signal.channelId) }),
            queryClient.invalidateQueries({ queryKey: chatKeys.pins(orgId, signal.channelId) }),
          ])
        );
      }
    }
  );

  useRealtimeResync(() => {
    if (!orgId) return;
    setNewMessageCount(0);
    if (threadId) {
      runSignalTask(
        Promise.all([
          queryClient.invalidateQueries({ queryKey: chatKeys.thread(orgId, threadId) }),
          queryClient.invalidateQueries({ queryKey: chatKeys.threadDetail(orgId, threadId) }),
        ])
      );
      return;
    }
    // The lists reload as the server holds them now; versions seen before
    // the gap say nothing about the frames that follow. An open thread
    // reloads its own lists; the others reload on their next visit.
    clearChatMessageVersions();
    runSignalTask(
      Promise.all([
        queryClient.invalidateQueries({ queryKey: chatKeys.channelMessagesAll(orgId) }),
        queryClient.invalidateQueries({
          queryKey: chatKeys.threadMessagesAll(orgId),
          refetchType: "none",
        }),
        queryClient.invalidateQueries({
          queryKey: chatKeys.threadDetailsAll(orgId),
          refetchType: "none",
        }),
        queryClient.invalidateQueries({ queryKey: chatKeys.threadsAll(orgId) }),
        queryClient.invalidateQueries({ queryKey: chatKeys.pinsAll(orgId) }),
        queryClient.invalidateQueries({ queryKey: chatKeys.membersAll(orgId) }),
      ])
    );
  });

  return { newMessageCount, clearNewMessageCount };
}

// ── Typing ──────────────────────────────────────────────────────────

const NO_TYPING: readonly string[] = [];

/**
 * Who is typing in the conversation, from its TYPING signals: the channel
 * itself without `threadId`, else that thread. A member stops typing
 * `CHAT_TYPING_TIMEOUT_MS` after the last signal, or at once when their
 * message arrives. Pass `onSignal` to `useChatChannelRealtime`.
 */
export function useChatTypingUsers(channelId: string | null, threadId: string | null = null) {
  const conversation: string = `${channelId ?? ""}/${threadId ?? ""}`;
  const [typing, setTyping] = useState<{ conversation: string; userIds: readonly string[] }>({
    conversation,
    userIds: NO_TYPING,
  });
  const timersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

  const stopTyping = useCallback((userId: string) => {
    const timer = timersRef.current.get(userId);
    if (timer !== undefined) clearTimeout(timer);
    timersRef.current.delete(userId);
    setTyping((current) =>
      current.userIds.includes(userId)
        ? { ...current, userIds: current.userIds.filter((id) => id !== userId) }
        : current
    );
  }, []);

  const onSignal = useCallback(
    (signal: ChatSignal) => {
      if (signal.type === "TYPING" && signal.threadId === threadId) {
        const previous = timersRef.current.get(signal.userId);
        if (previous !== undefined) clearTimeout(previous);
        timersRef.current.set(
          signal.userId,
          setTimeout(() => stopTyping(signal.userId), CHAT_TYPING_TIMEOUT_MS)
        );
        setTyping((current) =>
          current.userIds.includes(signal.userId)
            ? current
            : { ...current, userIds: [...current.userIds, signal.userId] }
        );
        return;
      }
      if (signal.type === "MESSAGE_CREATED" && signal.authorId && signal.threadId === threadId) {
        stopTyping(signal.authorId);
      }
    },
    [stopTyping, threadId]
  );

  useEffect(() => {
    const timers = timersRef.current;
    return () => {
      timers.forEach((timer) => clearTimeout(timer));
      timers.clear();
    };
  }, []);

  // Members typing in a conversation the reader left are not shown in the next one.
  const userIds: readonly string[] =
    typing.conversation === conversation ? typing.userIds : NO_TYPING;
  if (typing.conversation !== conversation) setTyping({ conversation, userIds: NO_TYPING });

  return { onSignal, typingUserIds: userIds };
}

/**
 * Tells the conversation (the channel, or `threadId` in it) the reader is
 * typing, at most once per `CHAT_TYPING_INTERVAL_MS`. Nothing is sent while
 * no session is connected.
 */
export function useChatTypingPublisher(
  channelId: string | null,
  threadId: string | null = null
): () => void {
  const lastSentRef = useRef<number>(0);
  return useCallback(() => {
    if (!channelId) return;
    const now: number = Date.now();
    if (now - lastSentRef.current < CHAT_TYPING_INTERVAL_MS) return;
    if (realtime.publish(CHAT_TYPING_DESTINATION, { channelId, threadId })) {
      lastSentRef.current = now;
    }
  }, [channelId, threadId]);
}

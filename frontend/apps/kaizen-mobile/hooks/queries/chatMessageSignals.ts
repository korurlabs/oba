import type { QueryClient } from "@tanstack/react-query";

import {
  chatKeys,
  compareChatVersions,
  findChatConversationMessage,
  isChatConversationAtNewest,
  isChatMessageDeleted,
  isNewerChatVersion,
  signalledChatMessageVersion,
  withChatReaderFields,
} from "@/hooks/queries/chatCache";
import type {
  ChatMessageResponse,
  ChatMessageSignal,
  ChatThreadResponse,
} from "@/lib/schemas/chat";

export type ChatPayloadDecision =
  | { kind: "drop" }
  | { kind: "apply"; message: ChatMessageResponse }
  | { kind: "fetch" };

/**
 * What to do with a MESSAGE_CREATED or MESSAGE_UPDATED signal: drop it (the
 * message was deleted here, or `loaded`, the cached copy the caller found, is
 * as new), apply its payload with the reader's own fields, or read the
 * message for the reader. A thread `@everyone` reaches the reader only as a
 * member, which the thread's loaded detail says; without it the reader
 * counts as no member.
 */
export function decideChatMessagePayload(
  queryClient: QueryClient,
  orgId: string,
  signal: ChatMessageSignal,
  meId: string | undefined,
  loaded: ChatMessageResponse | undefined
): ChatPayloadDecision {
  if (isChatMessageDeleted(signal.messageId)) return { kind: "drop" };
  const payload: ChatMessageResponse | null = signal.message;
  if (!payload || !meId) return { kind: "fetch" };
  if (loaded && !isNewerChatVersion(payload.version, loaded.version)) return { kind: "drop" };
  const signalled: string | undefined = signalledChatMessageVersion(signal.messageId);
  if (signalled && compareChatVersions(signalled, payload.version) > 0) return { kind: "fetch" };
  const threadMember: boolean = signal.threadId
    ? !!queryClient.getQueryData<ChatThreadResponse>(chatKeys.threadDetail(orgId, signal.threadId))
        ?.member
    : false;
  return { kind: "apply", message: withChatReaderFields(payload, { userId: meId, threadMember }) };
}

/** How a MESSAGE_CREATED reaches the cache, and whether its conversation shows it now. */
export interface ChatMessageCreatedPlan {
  decision: ChatPayloadDecision;
  /** True when a loaded list of the conversation holds the message or reaches its newest one. */
  insert: boolean;
}

/**
 * Plans a MESSAGE_CREATED. Only the conversation's own lists count as
 * having the message already: a narrowed daily updates or pull requests list
 * that refetched it first would otherwise keep it out of the channel's list
 * and preview.
 */
export function planChatMessageCreated(
  queryClient: QueryClient,
  orgId: string,
  conversation: { channelId: string; threadId: string | null },
  signal: ChatMessageSignal,
  meId: string | undefined
): ChatMessageCreatedPlan {
  const loaded: ChatMessageResponse | undefined = findChatConversationMessage(
    queryClient,
    orgId,
    conversation,
    signal.messageId
  );
  return {
    decision: decideChatMessagePayload(queryClient, orgId, signal, meId, loaded),
    insert:
      !!loaded ||
      isChatConversationAtNewest(queryClient, orgId, conversation.channelId, conversation.threadId),
  };
}

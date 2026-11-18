import { useMutation, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { randomUUID } from "expo-crypto";
import { useCallback } from "react";

import { userKeys } from "@/hooks/queries/users";
import { chatChannelErrorKey } from "@/components/chat/chatChannelMenuModel";
import { toast } from "@/components/ui/toast";
import { useChatMeId } from "@/hooks/queries/chat";
import {
  addChatChannelMembers,
  addChatReaction,
  changeChatThreadStatus,
  createChatChannel,
  createChatMessage,
  createChatThread,
  deleteChatChannel,
  deleteChatMessage,
  getChatMessage,
  markChatRead,
  removeChatChannelMember,
  removeChatReaction,
  setChatMessagePinned,
  setChatThreadMembership,
  updateChatChannel,
  updateChatMessage,
  updateChatThread,
} from "@/hooks/queries/chatApi";
import {
  applyChatReaction,
  chatKeys,
  findChatMessage,
  insertChatMessage,
  patchChatMessage,
  removeChatMessage,
  replaceChatMessage,
} from "@/hooks/queries/chatCache";
import {
  getChatOutboxMessage,
  putChatOutboxMessage,
  removeChatOutboxMessage,
} from "@/hooks/queries/chatOutbox";
import { translate, type TranslationKey } from "@/i18n";
import { isApiError } from "@/lib/api";
import { MEMBER_WITHOUT_KAIZEN_ACCESS_CODE } from "@/lib/kaizenAccess";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import {
  CHAT_ERROR_CODES,
  threadRootAsMessage,
  type ChatChannelCreateRequest,
  type ChatChannelResponse,
  type ChatChannelUpdateRequest,
  type ChatLocalFile,
  type ChatMessageResponse,
  type ChatOutboxMessage,
  type ChatReactionSummaryResponse,
  type ChatReadStateRequest,
  type ChatThreadResponse,
  type ChatThreadStatus,
  type ChatThreadUpdateRequest,
  type ChatUnread,
} from "@/lib/schemas/chat";

function requireOrgId(orgId: string | null): string {
  if (!orgId) throw new Error("No active organization for the chat");
  return orgId;
}

/**
 * After a change in a thread: its detail (reply count, status, members)
 * and the thread lists are read again; the root's summary in the channel
 * follows from the detail's root message.
 */
async function refreshChatThread(
  queryClient: QueryClient,
  orgId: string,
  threadId: string
): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: chatKeys.threadDetail(orgId, threadId) }),
    queryClient.invalidateQueries({ queryKey: chatKeys.threadsAll(orgId) }),
  ]);
}

/** A thread as the server answered a write: its detail, its root's summary, every thread list. */
async function syncChatThread(
  queryClient: QueryClient,
  orgId: string,
  thread: ChatThreadResponse
): Promise<void> {
  queryClient.setQueryData(chatKeys.threadDetail(orgId, thread.id), thread);
  const root: ChatMessageResponse | null = threadRootAsMessage(thread);
  if (root) replaceChatMessage(queryClient, orgId, root);
  await queryClient.invalidateQueries({ queryKey: chatKeys.threadsAll(orgId) });
}

// ── Send ────────────────────────────────────────────────────────────

/** Sends run one at a time, in the order they were written. */
const CHAT_SEND_SCOPE = "chat-send";
const CHAT_SEND_MAX_RETRIES = 5;
const CHAT_SEND_RETRY_BASE_MS = 1000;
const CHAT_SEND_RETRY_MAX_MS = 15_000;

/** Upload progress moves the outbox row in steps of this share, not on every event. */
const CHAT_PROGRESS_STEP = 0.05;

/** Records how much of a row's files went up, in steps, so the list re-renders rarely. */
function reportChatSendProgress(clientNonce: string, share: number): void {
  const current: ChatOutboxMessage | undefined = getChatOutboxMessage(clientNonce);
  if (!current || current.status !== "pending") return;
  const stepped: number = Math.floor(share / CHAT_PROGRESS_STEP) * CHAT_PROGRESS_STEP;
  if (current.progress !== null && stepped <= current.progress) return;
  putChatOutboxMessage({ ...current, progress: stepped });
}

/** No response at all: a network failure or a timeout, which a retry may fix. */
function isNoResponseError(error: Error): boolean {
  return isApiError(error) && error.status === undefined;
}

export function isChatSendRetryable(failureCount: number, error: Error): boolean {
  return failureCount < CHAT_SEND_MAX_RETRIES && isNoResponseError(error);
}

export function chatSendRetryDelay(failureCount: number): number {
  return Math.min(CHAT_SEND_RETRY_BASE_MS * 2 ** failureCount, CHAT_SEND_RETRY_MAX_MS);
}

/**
 * The text of a failed send: the app's own text for a closed thread and for a
 * mention of a member without Kaizen, else the server's own message when it
 * sent a code. The send request is silent, so this toast is the only one.
 */
export function chatSendErrorMessage(error: Error): string {
  if (isApiError(error, CHAT_ERROR_CODES.threadClosed)) return translate("chatThreadClosedError");
  if (isApiError(error, MEMBER_WITHOUT_KAIZEN_ACCESS_CODE)) {
    return translate("chatMentionWithoutKaizenAccess");
  }
  if (isApiError(error) && error.code !== undefined && error.message.trim() !== "") {
    return error.message;
  }
  return translate("chatSendFailed");
}

/**
 * The send of one outbox row. Every retry carries the row's nonce, so the
 * server stores one message however often the request goes out; the echo
 * on the channel topic and this response settle the row, whichever is first.
 */
function useChatSendMutation() {
  const queryClient: QueryClient = useQueryClient();
  return useMutation({
    networkMode: "online",
    scope: { id: CHAT_SEND_SCOPE },
    retry: isChatSendRetryable,
    retryDelay: chatSendRetryDelay,
    mutationFn: async (row: ChatOutboxMessage): Promise<ChatMessageResponse | null> => {
      // Discarded or already confirmed by the echo while it waited its turn.
      if (!getChatOutboxMessage(row.clientNonce)) return null;
      return createChatMessage(
        {
          channelId: row.channelId,
          // A message of files alone has no text.
          content: row.content.trim().length > 0 ? row.content : null,
          threadId: row.threadId,
          replyToMessageId: row.replyToMessageId,
          clientNonce: row.clientNonce,
        },
        row.organizationId,
        row.files,
        (share: number) => reportChatSendProgress(row.clientNonce, share)
      );
    },
    onMutate: (row: ChatOutboxMessage) => {
      putChatOutboxMessage({
        ...row,
        status: "pending",
        progress: row.files.length > 0 ? 0 : null,
        errorCode: null,
        failedByNetwork: false,
      });
    },
    onSuccess: async (message: ChatMessageResponse | null, row: ChatOutboxMessage) => {
      if (!message) return;
      if (!message.deleted) insertChatMessage(queryClient, row.organizationId, message);
      removeChatOutboxMessage(row.clientNonce);
      // A reply in a thread is not the channel's newest message; it moves the thread's count.
      if (row.threadId) {
        await refreshChatThread(queryClient, row.organizationId, row.threadId);
      } else {
        queryClient.setQueryData(chatKeys.preview(row.organizationId, row.channelId), message);
      }
    },
    onError: async (error: Error, row: ChatOutboxMessage) => {
      // Closed meanwhile: reading the thread again shows it closed, which replaces its composer.
      if (row.threadId && isApiError(error, CHAT_ERROR_CODES.threadClosed)) {
        await refreshChatThread(queryClient, row.organizationId, row.threadId);
      }
      if (!getChatOutboxMessage(row.clientNonce)) return;
      putChatOutboxMessage({
        ...row,
        status: "failed",
        progress: null,
        errorCode: isApiError(error) ? (error.code ?? null) : null,
        failedByNetwork: isNoResponseError(error),
      });
      console.warn("[chat] send failed", error.message);
      // A rate limit is explained on the row itself; a toast per row would pile up.
      if (!isApiError(error, CHAT_ERROR_CODES.rateLimited)) {
        toast.error(chatSendErrorMessage(error));
      }
    },
  });
}

export interface ChatSendInput {
  content: string;
  channelId: string;
  threadId: string | null;
  replyToMessageId: string | null;
  /** Files picked in the composer; empty for text alone. */
  files: ChatLocalFile[];
}

/** Send, Retry and Delete of the composer and the "Not sent" rows. */
export function useSendChatMessage() {
  const orgId: string | null = useActiveOrganizationId();
  const { mutate, mutateAsync } = useChatSendMutation();

  const send = useCallback(
    (input: ChatSendInput): string => {
      const clientNonce: string = randomUUID();
      mutate({
        clientNonce,
        organizationId: requireOrgId(orgId),
        channelId: input.channelId,
        threadId: input.threadId,
        content: input.content,
        replyToMessageId: input.replyToMessageId,
        files: input.files,
        progress: null,
        createdAt: new Date().toISOString(),
        status: "pending",
        errorCode: null,
        failedByNetwork: false,
      });
      return clientNonce;
    },
    [mutate, orgId]
  );

  const retry = useCallback(
    (clientNonce: string): void => {
      const row: ChatOutboxMessage | undefined = getChatOutboxMessage(clientNonce);
      if (row && row.status === "failed") mutate(row);
    },
    [mutate]
  );

  const discard = useCallback((clientNonce: string): void => {
    const row: ChatOutboxMessage | undefined = getChatOutboxMessage(clientNonce);
    if (row && row.status === "failed") removeChatOutboxMessage(clientNonce);
  }, []);

  return { send, retry, discard, resend: mutateAsync };
}

// ── Edit, delete, pin ───────────────────────────────────────────────

/**
 * Saves an edited text. A failure is toasted by the app's API error layer;
 * a refusal because the thread was closed meanwhile reads the thread again.
 */
export function useEditChatMessage() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: ({ messageId, content }: { messageId: string; content: string }) =>
      updateChatMessage(messageId, content),
    onSuccess: (message: ChatMessageResponse) => {
      replaceChatMessage(queryClient, requireOrgId(orgId), message);
    },
    onError: async (error: Error, { messageId }: { messageId: string; content: string }) => {
      console.warn("[chat] edit failed", error.message);
      if (!orgId || !isApiError(error, CHAT_ERROR_CODES.threadClosed)) return;
      const threadId: string | null | undefined = findChatMessage(
        queryClient,
        orgId,
        messageId
      )?.threadId;
      if (threadId) await refreshChatThread(queryClient, orgId, threadId);
    },
  });
}

export interface ChatDeleteInput {
  messageId: string;
  channelId: string;
  /** The message's thread, or null for a message of the channel itself. */
  threadId: string | null;
}

/**
 * Deletes a message. A refusal means the reader lost the right to delete it
 * (a role change), so the profile is read again to hide the action.
 */
export function useDeleteChatMessage() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: ({ messageId }: ChatDeleteInput) => deleteChatMessage(messageId),
    onSuccess: async (_: void, { messageId, channelId, threadId }: ChatDeleteInput) => {
      const organizationId: string = requireOrgId(orgId);
      removeChatMessage(queryClient, organizationId, messageId);
      await (threadId
        ? refreshChatThread(queryClient, organizationId, threadId)
        : queryClient.invalidateQueries({ queryKey: chatKeys.preview(organizationId, channelId) }));
    },
    onError: async (error: Error) => {
      console.warn("[chat] delete failed", error.message);
      if (!isApiError(error, CHAT_ERROR_CODES.deleteRefused)) {
        toast.error(error.message.trim() !== "" ? error.message : translate("apiErrorGeneric"));
        return;
      }
      toast.error(translate("chatDeleteRefused"));
      await queryClient.invalidateQueries({ queryKey: userKeys.me });
    },
  });
}

export function usePinChatMessage() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: ({ messageId, pinned }: { messageId: string; pinned: boolean }) =>
      setChatMessagePinned(messageId, pinned),
    onSuccess: async (message: ChatMessageResponse) => {
      const organizationId: string = requireOrgId(orgId);
      replaceChatMessage(queryClient, organizationId, message);
      await queryClient.invalidateQueries({
        queryKey: chatKeys.pins(organizationId, message.channelId),
      });
    },
    onError: (error: Error) => {
      console.warn("[chat] pin failed", error.message);
    },
  });
}

// ── Threads ─────────────────────────────────────────────────────────

/**
 * The outcome of a create: the new thread, or, when the message got one
 * meanwhile (409), only that thread's ID. Either way the caller opens it.
 */
export type ChatThreadCreateResult =
  | { created: true; threadId: string; thread: ChatThreadResponse }
  | { created: false; threadId: string; thread: null };

export interface ChatThreadCreateInput {
  rootMessageId: string;
  /** Null lets the server title the thread from its root. */
  title: string | null;
}

/** Starts a thread on a channel message; a refusal is toasted here, the call is silent. */
export function useCreateChatThread() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: async ({
      rootMessageId,
      title,
    }: ChatThreadCreateInput): Promise<ChatThreadCreateResult> => {
      try {
        const thread: ChatThreadResponse = await createChatThread({ rootMessageId, title });
        return { created: true, threadId: thread.id, thread };
      } catch (error) {
        const existing: unknown = isApiError(error, CHAT_ERROR_CODES.threadExists)
          ? error.errorValues.threadId
          : undefined;
        if (typeof existing !== "string") throw error;
        return { created: false, threadId: existing, thread: null };
      }
    },
    onSuccess: async (result: ChatThreadCreateResult, { rootMessageId }: ChatThreadCreateInput) => {
      const organizationId: string = requireOrgId(orgId);
      if (result.thread) {
        await syncChatThread(queryClient, organizationId, result.thread);
        return;
      }
      // Someone else started it first; the channel row has not shown it yet.
      replaceChatMessage(queryClient, organizationId, await getChatMessage(rootMessageId));
    },
    onError: (error: Error) => {
      console.warn("[chat] creating a thread failed", error.message);
      toast.error(error.message.trim() !== "" ? error.message : translate("apiErrorGeneric"));
    },
  });
}

/**
 * Closes, reopens, archives or unarchives a thread. A failure is toasted by
 * the app's API error layer; a refusal because it was closed meanwhile
 * reads the thread again, so the reader sees why.
 */
export function useChangeChatThreadStatus() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: ({ threadId, status }: { threadId: string; status: ChatThreadStatus }) =>
      changeChatThreadStatus(threadId, status),
    onSuccess: (thread: ChatThreadResponse) =>
      syncChatThread(queryClient, requireOrgId(orgId), thread),
    onError: async (error: Error, { threadId }: { threadId: string; status: ChatThreadStatus }) => {
      console.warn("[chat] changing the thread status failed", error.message);
      if (orgId && isApiError(error, CHAT_ERROR_CODES.threadClosed)) {
        await refreshChatThread(queryClient, orgId, threadId);
      }
    },
  });
}

/**
 * Renames a thread and sets its archive window (its creator, an OWNER or an
 * ADMIN). A refusal is toasted with the server's message by the app's API
 * error layer; the form keeps its input.
 */
export function useUpdateChatThread() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: ({ threadId, data }: { threadId: string; data: ChatThreadUpdateRequest }) =>
      updateChatThread(threadId, data),
    onSuccess: (thread: ChatThreadResponse) =>
      syncChatThread(queryClient, requireOrgId(orgId), thread),
    onError: (error: Error) => {
      console.warn("[chat] updating the thread failed", error.message);
    },
  });
}

/**
 * Joins or leaves a thread; the unread counts follow through the reader's
 * own signal. A failure is toasted by the app's API error layer.
 */
export function useChatThreadMembership() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: ({ threadId, member }: { threadId: string; member: boolean }) =>
      setChatThreadMembership(threadId, member),
    onSuccess: (thread: ChatThreadResponse) =>
      syncChatThread(queryClient, requireOrgId(orgId), thread),
    onError: (error: Error) => {
      console.warn("[chat] changing the thread membership failed", error.message);
    },
  });
}

// ── Channels ────────────────────────────────────────────────────────

/**
 * Creates a channel (OWNER or ADMIN). The request is silent: the sheet
 * shows the refusals it can explain and toasts the rest.
 */
export function useCreateChatChannel() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: (data: ChatChannelCreateRequest) => createChatChannel(data),
    onSuccess: async (channel: ChatChannelResponse) => {
      const organizationId: string = requireOrgId(orgId);
      queryClient.setQueryData<ChatChannelResponse[]>(
        chatKeys.channels(organizationId),
        (channels) =>
          channels && !channels.some((known) => known.id === channel.id)
            ? [...channels, channel]
            : channels
      );
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: chatKeys.channels(organizationId) }),
        queryClient.invalidateQueries({ queryKey: chatKeys.unread(organizationId) }),
      ]);
    },
    onError: async (error: Error) => {
      console.warn("[chat] creating a channel failed", error.message);
      // Another channel took the purpose meanwhile: the list shows which one.
      if (orgId && isApiError(error, CHAT_ERROR_CODES.channelPurposeTaken)) {
        await queryClient.invalidateQueries({ queryKey: chatKeys.channels(orgId) });
      }
    },
  });
}

/** Puts the server's copy of a channel into the cached list, in place. */
function replaceChatChannel(
  queryClient: QueryClient,
  orgId: string,
  channel: ChatChannelResponse
): void {
  queryClient.setQueryData<ChatChannelResponse[]>(chatKeys.channels(orgId), (channels) =>
    channels?.map((known) => (known.id === channel.id ? channel : known))
  );
}

/**
 * Drops the cached content of a channel the reader can no longer read
 * (deleted, or left): its messages, pins, thread lists, member lists and its
 * preview. A later read of it is 404 `KAI_NOT_015`, so nothing is refetched.
 */
function removeChatChannelContent(
  queryClient: QueryClient,
  orgId: string,
  channelId: string
): void {
  queryClient.removeQueries({ queryKey: chatKeys.channel(orgId, channelId) });
  queryClient.removeQueries({ queryKey: chatKeys.pins(orgId, channelId) });
  queryClient.removeQueries({ queryKey: chatKeys.channelThreads(orgId, channelId) });
  queryClient.removeQueries({ queryKey: chatKeys.channelMemberLists(orgId, channelId) });
  queryClient.removeQueries({ queryKey: chatKeys.preview(orgId, channelId) });
}

/** The name of a cached channel, read before its removal refetches the list. */
function cachedChatChannelName(
  queryClient: QueryClient,
  orgId: string,
  channelId: string
): string | undefined {
  return queryClient
    .getQueryData<ChatChannelResponse[]>(chatKeys.channels(orgId))
    ?.find((channel) => channel.id === channelId)?.name;
}

/** Options of a hook that removes a channel from the reader's view. */
export interface ChatChannelRemovalOptions {
  /**
   * Runs on success after the channel's cached content is dropped and before
   * the lists are read again, so the screen showing the channel can leave
   * before it reads the channel into a "not found".
   */
  onBeforeInvalidate?: () => void;
}

/**
 * The text of a refused channel write: the app's own text for the refusals
 * the web app explains, else the server's message.
 */
export function chatChannelErrorMessage(error: Error): string {
  const key: TranslationKey | null = chatChannelErrorKey(
    isApiError(error) ? error.code : undefined
  );
  if (key) return translate(key);
  return error.message.trim() !== "" ? error.message : translate("apiErrorGeneric");
}

/**
 * Renames a channel and sets its visibility and purpose (OWNER or ADMIN). A
 * visibility change changes who reads the channel, so the list, the counts
 * and the channel's member lists are read again. The request is silent: the
 * sheet shows the refusals it can explain and toasts the rest.
 */
export function useUpdateChatChannel() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: ({ channelId, data }: { channelId: string; data: ChatChannelUpdateRequest }) =>
      updateChatChannel(channelId, data),
    onSuccess: async (channel: ChatChannelResponse) => {
      const organizationId: string = requireOrgId(orgId);
      replaceChatChannel(queryClient, organizationId, channel);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: chatKeys.channels(organizationId) }),
        queryClient.invalidateQueries({ queryKey: chatKeys.unread(organizationId) }),
        queryClient.invalidateQueries({
          queryKey: chatKeys.channelMemberLists(organizationId, channel.id),
        }),
      ]);
    },
    onError: async (error: Error) => {
      console.warn("[chat] changing a channel failed", error.message);
      // Another channel took the purpose meanwhile: the list shows which one.
      if (orgId && isApiError(error, CHAT_ERROR_CODES.channelPurposeTaken)) {
        await queryClient.invalidateQueries({ queryKey: chatKeys.channels(orgId) });
      }
    },
  });
}

/**
 * Deletes a channel for everyone (OWNER or ADMIN) and drops its cached
 * content. The request is silent; the result and a refusal are toasted here,
 * since the confirmation that asked is already closed and the channel screen
 * leaves on success.
 */
export function useDeleteChatChannel(options?: ChatChannelRemovalOptions) {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: (channelId: string) => deleteChatChannel(channelId),
    onSuccess: async (_: void, channelId: string) => {
      const organizationId: string = requireOrgId(orgId);
      const name: string | undefined = cachedChatChannelName(
        queryClient,
        organizationId,
        channelId
      );
      if (name !== undefined) toast.success(translate("chatChannelDeleted", { name }));
      removeChatChannelContent(queryClient, organizationId, channelId);
      options?.onBeforeInvalidate?.();
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: chatKeys.channels(organizationId) }),
        queryClient.invalidateQueries({ queryKey: chatKeys.unread(organizationId) }),
        queryClient.invalidateQueries({ queryKey: chatKeys.searchAll(organizationId) }),
      ]);
    },
    onError: (error: Error) => {
      console.warn("[chat] deleting a channel failed", error.message);
      toast.error(chatChannelErrorMessage(error));
    },
  });
}

export interface ChatChannelMembersInput {
  channelId: string;
  userIds: string[];
}

/**
 * Adds users to a private channel's member list (OWNER or ADMIN). An admin
 * who adds themselves now reads the channel and gets counts for it. The
 * request is silent; the result and a refusal are toasted here, so the toast
 * still shows when the sheet that asked has closed.
 */
export function useAddChatChannelMembers() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: ({ channelId, userIds }: ChatChannelMembersInput) =>
      addChatChannelMembers(channelId, { userIds }),
    onSuccess: async (channel: ChatChannelResponse, { userIds }: ChatChannelMembersInput) => {
      toast.success(translate("chatChannelMembersAdded", { count: userIds.length }));
      const organizationId: string = requireOrgId(orgId);
      replaceChatChannel(queryClient, organizationId, channel);
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: chatKeys.channelMemberLists(organizationId, channel.id),
        }),
        queryClient.invalidateQueries({ queryKey: chatKeys.channels(organizationId) }),
        queryClient.invalidateQueries({ queryKey: chatKeys.unread(organizationId) }),
      ]);
    },
    onError: (error: Error) => {
      console.warn("[chat] adding channel members failed", error.message);
      toast.error(chatChannelErrorMessage(error));
    },
  });
}

export interface ChatChannelMemberInput {
  channelId: string;
  userId: string;
  /** The removed member's name for the success toast; the reader leaving needs none. */
  memberName?: string;
}

/**
 * Removes a user from a private channel (OWNER or ADMIN), or lets the reader
 * leave it when `userId` is the reader; after leaving, the reader's cached
 * content of the channel is dropped and `onBeforeInvalidate` runs. The
 * request is silent; the result and every refusal are toasted here, so the
 * toast still shows when the sheet or the channel that asked has closed.
 */
export function useRemoveChatChannelMember(options?: ChatChannelRemovalOptions) {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const meId: string | undefined = useChatMeId();
  return useMutation({
    mutationFn: ({ channelId, userId }: ChatChannelMemberInput) =>
      removeChatChannelMember(channelId, userId),
    onSuccess: async (_: void, { channelId, userId, memberName }: ChatChannelMemberInput) => {
      const organizationId: string = requireOrgId(orgId);
      if (userId === meId) {
        const name: string | undefined = cachedChatChannelName(
          queryClient,
          organizationId,
          channelId
        );
        if (name !== undefined) toast.success(translate("chatChannelLeft", { name }));
        removeChatChannelContent(queryClient, organizationId, channelId);
        options?.onBeforeInvalidate?.();
      } else {
        if (memberName) {
          toast.success(translate("chatChannelMemberRemoved", { name: memberName }));
        }
        await queryClient.invalidateQueries({
          queryKey: chatKeys.channelMemberLists(organizationId, channelId),
        });
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: chatKeys.channels(organizationId) }),
        queryClient.invalidateQueries({ queryKey: chatKeys.unread(organizationId) }),
      ]);
    },
    onError: (error: Error) => {
      console.warn("[chat] removing a channel member failed", error.message);
      toast.error(chatChannelErrorMessage(error));
    },
  });
}

// ── Reactions ───────────────────────────────────────────────────────

export interface ChatReactionInput {
  messageId: string;
  emoji: string;
  add: boolean;
}

/**
 * Adds or removes the reader's reaction at once and settles on the server's
 * list; a failure undoes it and is toasted by the app's API error layer.
 */
export function useToggleChatReaction() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const meId: string | undefined = useChatMeId();
  return useMutation({
    mutationFn: ({ messageId, emoji, add }: ChatReactionInput) =>
      add ? addChatReaction(messageId, emoji) : removeChatReaction(messageId, emoji),
    onMutate: ({ messageId, emoji, add }: ChatReactionInput) => {
      if (!orgId || !meId) return;
      patchChatMessage(queryClient, orgId, messageId, (message) =>
        applyChatReaction(message, { emoji, userId: meId, added: add, isMe: true })
      );
    },
    onSuccess: (reactions: ChatReactionSummaryResponse[], { messageId }: ChatReactionInput) => {
      patchChatMessage(queryClient, requireOrgId(orgId), messageId, (message) => ({
        ...message,
        reactions,
      }));
    },
    onError: (error: Error, { messageId, emoji, add }: ChatReactionInput) => {
      console.warn("[chat] reaction failed", error.message);
      if (orgId && meId) {
        patchChatMessage(queryClient, orgId, messageId, (message) =>
          applyChatReaction(message, { emoji, userId: meId, added: !add, isMe: true })
        );
      }
    },
  });
}

/** True when the reader's reaction with this emoji is on the message. */
export function hasMyChatReaction(
  queryClient: QueryClient,
  orgId: string,
  messageId: string,
  emoji: string
): boolean {
  const message: ChatMessageResponse | undefined = findChatMessage(queryClient, orgId, messageId);
  return !!message?.reactions.some((reaction) => reaction.emoji === emoji && reaction.reactedByMe);
}

// ── Read state ──────────────────────────────────────────────────────

/** Moves the read position; the answer replaces the unread cache. */
export function useMarkChatRead() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation({
    mutationFn: (variables: ChatReadStateRequest) => markChatRead(variables),
    onSuccess: (unread: ChatUnread) => {
      if (orgId) queryClient.setQueryData(chatKeys.unread(orgId), unread);
    },
    onError: (error: Error) => {
      console.warn("[chat] marking as read failed", error.message);
    },
  });
}

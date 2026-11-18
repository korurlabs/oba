"use client";

import { useCallback, useEffect, useMemo } from "react";
import { mutationOptions, useMutation, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { isApiError } from "@oba/shared/api/axios";
import { USERS_KEYS } from "@oba/shared/features/users/hooks/use-get-me";
import { useRealtimeStatus } from "@/features/realtime/hooks/use-realtime-status";
import {
  addChatReaction,
  changeChatThreadStatus,
  createChatMessage,
  createChatThread,
  deleteChatMessage,
  joinChatThread,
  leaveChatThread,
  pinChatMessage,
  removeChatReaction,
  requireChatData,
  unpinChatMessage,
  updateChatMessage,
  updateChatThread,
} from "../api";
import {
  CHAT_ERROR_CODES,
  type ChatAutoArchiveMinutes,
  type ChatMessageResponse,
  type ChatOutboxMessage,
  type ChatThreadResponse,
  type ChatThreadStatus,
} from "../schemas";
import { CHAT_KEYS } from "./query-keys";
import {
  applyChatReaction,
  findChatMessage,
  getChatOutboxMessage,
  getChatOutboxMessages,
  insertChatMessage,
  patchChatMessage,
  putChatOutboxMessage,
  refreshChatMessage,
  refreshChatThread,
  removeChatMessage,
  removeChatOutboxMessage,
  replaceChatMessage,
  useChatMeId,
  useChatOrganizationId,
} from "./use-chat-messages";

// Chat writes show their result in place (the message appears, the reaction
// toggles, the pin icon shows), so they confirm nothing with a success toast;
// every failure is toasted with the server's message.

function toastError(error: Error): void {
  toast.error(error.message);
}

/** True when the server refused a write because the thread is closed (409). */
function isThreadClosedError(error: unknown): boolean {
  return isApiError(error, CHAT_ERROR_CODES.threadClosed);
}

/**
 * The message a refused write shows: the server's, except for a closed
 * thread, which says in the reader's language that it must be reopened, and
 * for a mention of a member without Kazan access, which says to remove it.
 */
function useChatErrorMessage(): (error: Error) => string {
  const t = useTranslations("chat.thread.status");
  const tComposer = useTranslations("chat.composer");
  return (error) => {
    if (isThreadClosedError(error)) return t("closedError");
    if (isApiError(error, CHAT_ERROR_CODES.memberWithoutKazanAccess)) {
      return tComposer("mentionWithoutKazanAccess");
    }
    return error.message;
  };
}

/** Organization of a mutation; a write without one would go to no organization. */
function requireOrgId(orgId: string | undefined): string {
  if (!orgId) throw new Error("No active organization for the chat");
  return orgId;
}

/** After a thread write: the thread's detail and every thread list, and the root row's summary in the channel. */
async function syncThread(queryClient: QueryClient, orgId: string, thread: ChatThreadResponse): Promise<void> {
  queryClient.setQueryData(CHAT_KEYS.threadDetail(orgId, thread.id), thread);
  replaceChatMessage(queryClient, orgId, thread.rootMessage);
  await queryClient.invalidateQueries({ queryKey: CHAT_KEYS.threadsAll(orgId) });
}

// ── Send ────────────────────────────────────────────────────────────

/** Mutation scope of every message send of the tab; sends of one scope run one at a time. */
const CHAT_SEND_SCOPE = "chat-send";

/** Further tries of a send that got no response, after the first one. */
const CHAT_SEND_MAX_RETRIES = 5;

/** The first wait before a retry; each later wait doubles, up to the cap. */
const CHAT_SEND_RETRY_BASE_MS = 1000;
const CHAT_SEND_RETRY_MAX_MS = 15000;

/** True when a failure got no response at all, so the same send may simply go out again. */
function isNoResponseError(error: Error): boolean {
  return isApiError(error) && error.status === undefined;
}

/**
 * True when a send got no response at all (a network failure or a timeout).
 * Right after the browser reports it is online again, the first requests
 * still fail this way for a few seconds. Sending again is safe because every
 * try carries the same client nonce, and the server answers a repeated nonce
 * with the message it already stored. A response with an error status is the
 * server's answer and is never sent again by itself.
 */
function isChatSendRetryable(failureCount: number, error: Error): boolean {
  return failureCount < CHAT_SEND_MAX_RETRIES && isNoResponseError(error);
}

/** 1 s, 2 s, 4 s, 8 s, then 15 s; the wait pauses while the browser is offline. */
function chatSendRetryDelay(failureCount: number): number {
  return Math.min(CHAT_SEND_RETRY_BASE_MS * 2 ** failureCount, CHAT_SEND_RETRY_MAX_MS);
}

export interface ChatSendInput {
  /** Markdown with tokens; may be empty when files are attached. */
  content: string;
  /** The channel, also for a message in one of its threads. */
  channelId: string;
  /** Null sends to the channel itself. */
  threadId: string | null;
  replyToMessageId: string | null;
  files: File[];
}

/**
 * The send mutation shared by every sender of the tab. Its variables are the
 * outbox row, so every try, retry and resend of a row carries the same
 * client nonce. `errorMessage` turns a failure into the row's text.
 */
export function chatSendMutationOptions(queryClient: QueryClient, errorMessage: (error: Error) => string) {
  return mutationOptions({
    // A message sent while the browser is offline waits in the outbox as
    // pending and goes out when the browser is back online, instead of
    // failing at once. One scope sends the tab's messages one after another,
    // so messages written offline arrive in the order they were written.
    networkMode: "online",
    scope: { id: CHAT_SEND_SCOPE },
    // The row stays pending through every try; only the last failure marks it "Not sent".
    retry: isChatSendRetryable,
    retryDelay: chatSendRetryDelay,
    mutationFn: async (row: ChatOutboxMessage): Promise<ChatMessageResponse | null> => {
      // The MESSAGE_CREATED signal confirmed the row while it waited behind
      // earlier sends or between tries: it is delivered, so nothing is sent.
      if (!getChatOutboxMessage(row.clientNonce)) return null;
      return requireChatData(
        await createChatMessage(
          {
            channelId: row.channelId,
            content: row.content.trim().length > 0 ? row.content : null,
            threadId: row.threadId,
            replyToMessageId: row.replyToMessageId,
            clientNonce: row.clientNonce,
          },
          row.files,
        ),
      );
    },
    onMutate: (row: ChatOutboxMessage) => {
      putChatOutboxMessage({ ...row, status: "pending", errorMessage: null, errorCode: null, failedByNetwork: false });
    },
    onSuccess: async (message: ChatMessageResponse | null, row: ChatOutboxMessage) => {
      if (!message) return;
      // A repeated nonce of a message the author has deleted since returns its
      // deleted placeholder: the send had succeeded, so the row only goes away.
      if (message.deleted) {
        removeChatOutboxMessage(row.clientNonce);
        return;
      }
      insertChatMessage(queryClient, row.organizationId, message);
      removeChatOutboxMessage(row.clientNonce);
      if (message.threadId) await refreshChatThread(queryClient, row.organizationId, message.threadId);
    },
    onError: async (error: Error, row: ChatOutboxMessage) => {
      // The MESSAGE_CREATED signal confirmed the message while a later try
      // failed: it was delivered, so it must not come back as "Not sent".
      if (!getChatOutboxMessage(row.clientNonce)) return;
      const message = errorMessage(error);
      putChatOutboxMessage({
        ...row,
        status: "failed",
        errorMessage: message,
        errorCode: isApiError(error) ? (error.code ?? null) : null,
        failedByNetwork: isNoResponseError(error),
      });
      // Sending too fast is shown by the composer, next to the kept text.
      if (!isApiError(error, CHAT_ERROR_CODES.rateLimited)) toast.error(message);
      // The thread was closed meanwhile: show it closed, which replaces its composer.
      if (row.threadId && isThreadClosedError(error)) {
        await refreshChatThread(queryClient, row.organizationId, row.threadId);
      }
    },
  });
}

function useChatSendMutation() {
  const queryClient = useQueryClient();
  const errorMessage = useChatErrorMessage();
  return useMutation(chatSendMutationOptions(queryClient, errorMessage));
}

/**
 * Sends messages optimistically. `send` puts the message in the outbox at
 * once (pending, shown dimmed with "Sending…" below the newest page), keyed
 * by a client nonce that the server echoes in its response and in the
 * MESSAGE_CREATED signal; the confirmed message then replaces it in the
 * list. A send that gets no response is tried again with the same nonce
 * and stays pending meanwhile. A failure keeps the row as "Not sent" with the text, files and the server's error
 * (`errorCode` `GEN_API_004` when sending too fast), and toasts the
 * message, except sending too fast, which the composer shows; `retry`
 * sends it again and `discard` drops it. A row that got no response goes out
 * again by itself once the network is back (`useChatOutboxResend`).
 */
export function useSendChatMessage() {
  const orgId = useChatOrganizationId();
  const mutation = useChatSendMutation();
  const { mutate } = mutation;

  const send = useCallback(
    (input: ChatSendInput): string => {
      const clientNonce = crypto.randomUUID();
      mutate({
        clientNonce,
        organizationId: requireOrgId(orgId),
        channelId: input.channelId,
        threadId: input.threadId,
        content: input.content,
        replyToMessageId: input.replyToMessageId,
        files: input.files,
        createdAt: new Date().toISOString(),
        status: "pending",
        errorMessage: null,
        errorCode: null,
        failedByNetwork: false,
      });
      return clientNonce;
    },
    [mutate, orgId],
  );

  /** Sends a "Not sent" row again with its original text, files and nonce. */
  const retry = useCallback(
    (clientNonce: string): void => {
      const row = getChatOutboxMessage(clientNonce);
      if (row && row.status === "failed") mutate(row);
    },
    [mutate],
  );

  /** Drops a "Not sent" row. */
  const discard = useCallback((clientNonce: string): void => {
    const row = getChatOutboxMessage(clientNonce);
    if (row && row.status === "failed") removeChatOutboxMessage(clientNonce);
  }, []);

  return { send, retry, discard, isPending: mutation.isPending };
}

/**
 * A resend run: every "Not sent" row that got no response goes to `send` in
 * the order it was first sent; a call while a run is unsettled does nothing.
 */
export function createChatOutboxResender(send: (row: ChatOutboxMessage) => Promise<unknown>): () => void {
  let running = false;
  return (): void => {
    if (running) return;
    const rows = getChatOutboxMessages().filter((row) => row.status === "failed" && row.failedByNetwork);
    if (rows.length === 0) return;
    running = true;
    // A failed resend already marks its row "Not sent" and toasts in the
    // mutation's onError; the settled results only end the run.
    void Promise.allSettled(rows.map((row) => send(row))).then(() => {
      running = false;
    });
  };
}

/**
 * Sends the "Not sent" rows that got no response again, by themselves, as
 * soon as there is a sign that the network is back: the realtime connection
 * is (re)connected, the browser reports it is online, or any query of the app
 * fetched successfully. The retry budget of a send ends after about 30 s,
 * which a longer outage outlasts, and the browser does not always report
 * that it was offline.
 *
 * Each row goes out with its own nonce, in the order it was first sent,
 * through the same send scope, and shows "Sending…" again meanwhile; the
 * server answers a repeated nonce with the message it already stored. One
 * resend runs at a time; a row that fails again waits for the next sign. A
 * row the reader discarded or the signal confirmed has left the outbox and is
 * not sent. A row the server refused waits for a manual Retry.
 *
 * Mounted once for the whole app, by `useChatChannelListRealtime`.
 */
export function useChatOutboxResend(): void {
  const queryClient = useQueryClient();
  const { mutateAsync } = useChatSendMutation();
  const { status } = useRealtimeStatus();
  const resend = useMemo(() => createChatOutboxResender(mutateAsync), [mutateAsync]);

  useEffect(() => {
    if (status === "connected") resend();
  }, [status, resend]);

  useEffect(() => {
    window.addEventListener("online", resend);
    return () => window.removeEventListener("online", resend);
  }, [resend]);

  useEffect(
    () =>
      queryClient.getQueryCache().subscribe((event) => {
        // A fetch that got data proves the API is reachable; data written by
        // the app itself (setQueryData) proves nothing.
        if (event.type === "updated" && event.action.type === "success" && !event.action.manual) resend();
      }),
    [queryClient, resend],
  );
}

// ── Edit and delete ─────────────────────────────────────────────────

/** Edits the reader's own message (403 `KAZ_SEC_004` for another's, 422 `KAZ_VAL_047` for a daily update). */
export function useEditChatMessage() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();

  const errorMessage = useChatErrorMessage();

  return useMutation({
    mutationFn: async ({ messageId, content }: { messageId: string; content: string }) =>
      requireChatData(await updateChatMessage(messageId, { content })),
    onSuccess: (message: ChatMessageResponse) => {
      replaceChatMessage(queryClient, requireOrgId(orgId), message);
    },
    onError: async (error: Error, { messageId }: { messageId: string; content: string }) => {
      toast.error(errorMessage(error));
      if (!orgId || !isThreadClosedError(error)) return;
      const threadId = findChatMessage(queryClient, orgId, messageId)?.threadId;
      if (threadId) await refreshChatThread(queryClient, orgId, threadId);
    },
  });
}

/**
 * Deletes a message for everyone (the author, an OWNER or an ADMIN); a thread
 * under it survives. A refusal (403 `KAZ_SEC_007`) means the reader is not the
 * author and no longer OWNER or ADMIN: the toast says so in the reader's
 * language, and the reader's roles are refetched, so the Delete action
 * disappears.
 */
export function useDeleteChatMessage() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  const t = useTranslations("chat.deleteMessage");

  return useMutation({
    mutationFn: async ({ messageId }: { messageId: string; threadId: string | null }) => {
      await deleteChatMessage(messageId);
    },
    onSuccess: async (_: void, { messageId, threadId }: { messageId: string; threadId: string | null }) => {
      const organizationId = requireOrgId(orgId);
      const wasPinned = !!findChatMessage(queryClient, organizationId, messageId)?.pinnedAt;
      removeChatMessage(queryClient, organizationId, messageId);
      await Promise.all([
        wasPinned ? queryClient.invalidateQueries({ queryKey: CHAT_KEYS.pinsAll(organizationId) }) : undefined,
        threadId ? refreshChatThread(queryClient, organizationId, threadId) : undefined,
      ]);
    },
    onError: async (error: Error) => {
      if (!isApiError(error, CHAT_ERROR_CODES.deleteRefused)) {
        toast.error(error.message);
        return;
      }
      toast.error(t("refused"));
      await queryClient.invalidateQueries({ queryKey: USERS_KEYS.me });
    },
  });
}

// ── Reactions and pins ──────────────────────────────────────────────

export interface ChatReactionInput {
  messageId: string;
  emoji: string;
  /** True adds the reader's reaction, false removes it. */
  add: boolean;
}

/**
 * Toggles the reader's reaction optimistically; the response's summaries
 * then replace the guess, and a failure reverts it.
 */
export function useToggleChatReaction() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  const meId = useChatMeId();

  return useMutation({
    mutationFn: async ({ messageId, emoji, add }: ChatReactionInput) =>
      requireChatData(add ? await addChatReaction(messageId, emoji) : await removeChatReaction(messageId, emoji)),
    onMutate: ({ messageId, emoji, add }: ChatReactionInput) => {
      if (!orgId || !meId) return;
      patchChatMessage(queryClient, orgId, messageId, (message) =>
        applyChatReaction(message, { emoji, userId: meId, added: add, isMe: true }),
      );
    },
    onSuccess: (reactions, { messageId }: ChatReactionInput) => {
      patchChatMessage(queryClient, requireOrgId(orgId), messageId, (message) => ({ ...message, reactions }));
    },
    onError: (error: Error, { messageId, emoji, add }: ChatReactionInput) => {
      if (orgId && meId) {
        patchChatMessage(queryClient, orgId, messageId, (message) =>
          applyChatReaction(message, { emoji, userId: meId, added: !add, isMe: true }),
        );
      }
      toastError(error);
    },
  });
}

/** Pins or unpins a message (author, OWNER or ADMIN; 50 pins at most). */
export function usePinChatMessage() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();

  return useMutation({
    mutationFn: async ({ messageId, pinned }: { messageId: string; pinned: boolean }) =>
      requireChatData(pinned ? await pinChatMessage(messageId) : await unpinChatMessage(messageId)),
    onSuccess: async (message: ChatMessageResponse) => {
      const organizationId = requireOrgId(orgId);
      replaceChatMessage(queryClient, organizationId, message);
      await queryClient.invalidateQueries({ queryKey: CHAT_KEYS.pins(organizationId, message.channelId) });
    },
    onError: toastError,
  });
}

// ── Threads ─────────────────────────────────────────────────────────

/**
 * The outcome of a create: the new thread, or, when the message already had
 * one (409 `KAZ_STA_013`), only the existing thread's ID. Either way the
 * caller opens `threadId`.
 */
export type ChatThreadCreateResult =
  | { created: true; threadId: string; thread: ChatThreadResponse }
  | { created: false; threadId: string; thread: null };

/** A new thread on `rootMessageId`; a null or missing `title` lets the server name it. */
export interface ChatThreadCreateVariables {
  rootMessageId: string;
  title?: string | null;
}

export function useCreateChatThread() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();

  return useMutation({
    mutationFn: async ({ rootMessageId, title }: ChatThreadCreateVariables): Promise<ChatThreadCreateResult> => {
      try {
        const thread = requireChatData(await createChatThread({ rootMessageId, title: title ?? null }));
        return { created: true, threadId: thread.id, thread };
      } catch (error) {
        const existingThreadId = isApiError(error, CHAT_ERROR_CODES.threadExists)
          ? error.errorValues.threadId
          : undefined;
        if (typeof existingThreadId !== "string") throw error;
        return { created: false, threadId: existingThreadId, thread: null };
      }
    },
    onSuccess: async (result: ChatThreadCreateResult, { rootMessageId }: ChatThreadCreateVariables) => {
      const organizationId = requireOrgId(orgId);
      if (result.thread) {
        await syncThread(queryClient, organizationId, result.thread);
      } else {
        // Someone else started it first; the channel row has not shown it yet.
        await refreshChatMessage(queryClient, organizationId, rootMessageId);
      }
    },
    onError: toastError,
  });
}

/** Renames a thread and sets its auto-archive window (creator, OWNER or ADMIN). */
export function useUpdateChatThread() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();

  return useMutation({
    mutationFn: async ({
      threadId,
      title,
      autoArchiveMinutes,
    }: {
      threadId: string;
      title: string;
      autoArchiveMinutes: ChatAutoArchiveMinutes;
    }) => requireChatData(await updateChatThread(threadId, { title, autoArchiveMinutes })),
    onSuccess: (thread: ChatThreadResponse) => syncThread(queryClient, requireOrgId(orgId), thread),
    onError: toastError,
  });
}

/**
 * Closes, reopens, archives or unarchives a thread (creator, OWNER or
 * ADMIN). A refusal because the thread was closed meanwhile (409) refetches
 * it, so the reader sees why.
 */
export function useChangeChatThreadStatus() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  const errorMessage = useChatErrorMessage();

  return useMutation({
    mutationFn: async ({ threadId, status }: { threadId: string; status: ChatThreadStatus }) =>
      requireChatData(await changeChatThreadStatus(threadId, { status })),
    onSuccess: (thread: ChatThreadResponse) => syncThread(queryClient, requireOrgId(orgId), thread),
    onError: async (error: Error, { threadId }: { threadId: string; status: ChatThreadStatus }) => {
      toast.error(errorMessage(error));
      if (orgId && isThreadClosedError(error)) await refreshChatThread(queryClient, orgId, threadId);
    },
  });
}

/** Joins a thread; the unread count follows through the reader's UNREAD_CHANGED signal. */
export function useJoinChatThread() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();

  return useMutation({
    mutationFn: async (threadId: string) => requireChatData(await joinChatThread(threadId)),
    onSuccess: (thread: ChatThreadResponse) => syncThread(queryClient, requireOrgId(orgId), thread),
    onError: toastError,
  });
}

/** Leaves a thread; a later direct mention adds the reader again. */
export function useLeaveChatThread() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();

  return useMutation({
    mutationFn: async (threadId: string) => requireChatData(await leaveChatThread(threadId)),
    onSuccess: (thread: ChatThreadResponse) => syncThread(queryClient, requireOrgId(orgId), thread),
    onError: toastError,
  });
}

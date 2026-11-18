import { z } from "zod";

import { api, ORGANIZATION_HEADER, readData } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import {
  CHAT_PAGE_SIZE,
  CHAT_SEARCH_PAGE_SIZE,
  chatChannelResponseSchema,
  chatMemberResponseSchema,
  chatMessagePageSchema,
  chatMessageResponseSchema,
  chatReactionSummaryResponseSchema,
  chatThreadPageSchema,
  chatThreadResponseSchema,
  chatTicketSuggestionSchema,
  chatUnreadSchema,
  type ChatChannelCreateRequest,
  type ChatChannelMembersRequest,
  type ChatChannelResponse,
  type ChatChannelUpdateRequest,
  type ChatLocalFile,
  type ChatMemberResponse,
  type ChatMessageCreateRequest,
  type ChatMessageCursor,
  type ChatMessagePage,
  type ChatMessageResponse,
  type ChatReactionSummaryResponse,
  type ChatReadStateRequest,
  type ChatThreadCreateRequest,
  type ChatThreadPage,
  type ChatThreadResponse,
  type ChatThreadStatus,
  type ChatThreadUpdateRequest,
  type ChatTicketSuggestion,
  type ChatUnread,
} from "@/lib/schemas/chat";

/*
 * The chat endpoints. Every body is checked against its schema; a body that
 * does not match throws, which a query shows as its error state.
 */

/** Writes the user does not start by hand stay out of the global toast. */
const SILENT = { _silentErrors: true } as const;

export async function listChatChannels(): Promise<ChatChannelResponse[]> {
  const response = await api.get(API_ROUTES.chat.channels);
  return readData(z.array(chatChannelResponseSchema), response.data);
}

/** A page of a channel's own messages, oldest first. */
export async function listChannelMessages(
  channelId: string,
  cursor: ChatMessageCursor,
  limit: number = CHAT_PAGE_SIZE
): Promise<ChatMessagePage> {
  const response = await api.get(API_ROUTES.chat.messages, {
    params: { ...cursor, channelId, limit },
  });
  return readData(chatMessagePageSchema, response.data);
}

/**
 * A page of a channel's messages posted on one business day (`YYYY-MM-DD`),
 * oldest first. The server applies the day to the cursors as well.
 */
export async function listChannelDayMessages(
  channelId: string,
  day: string,
  cursor: ChatMessageCursor,
  signal?: AbortSignal,
  limit: number = CHAT_PAGE_SIZE
): Promise<ChatMessagePage> {
  const response = await api.get(API_ROUTES.chat.messages, {
    params: { ...cursor, channelId, day, limit },
    signal,
  });
  return readData(chatMessagePageSchema, response.data);
}

/** A page of a thread's messages, oldest first; the same cursors as a channel. */
export async function listThreadMessages(
  threadId: string,
  cursor: ChatMessageCursor,
  limit: number = CHAT_PAGE_SIZE
): Promise<ChatMessagePage> {
  const response = await api.get(API_ROUTES.chat.threadMessages(threadId), {
    params: { ...cursor, limit },
  });
  return readData(chatMessagePageSchema, response.data);
}

/**
 * One message. `silent` keeps a refusal out of the global toast, for a
 * caller that shows it itself, such as the preview of a linked message the
 * reader may not read.
 */
export async function getChatMessage(
  messageId: string,
  silent: boolean = false
): Promise<ChatMessageResponse> {
  const response = await api.get(API_ROUTES.chat.message(messageId), silent ? SILENT : undefined);
  return readData(chatMessageResponseSchema, response.data);
}

/** A file without a known type goes up as bytes; the server stores what it sniffs. */
const UNKNOWN_FILE_TYPE = "application/octet-stream";

/**
 * Sends a message as the multipart request the server takes: a JSON part
 * `message` and one `files` part per file. React Native's FormData sends a
 * `{ string, type }` part with that content type, which a plain string part
 * would not carry, and streams a `{ uri, name, type }` part from the file
 * on the device. A failed upload fails the whole send. `onProgress` gets the
 * share uploaded, 0 to 1, while files go up. The message goes to the
 * organization it was written in, named explicitly, because a send that
 * waits for its turn or is resent can run after the active organization
 * changed. Silent: the outbox row shows the failure and the send hook
 * toasts it once.
 */
export async function createChatMessage(
  data: ChatMessageCreateRequest,
  organizationId: string,
  files: readonly ChatLocalFile[] = [],
  onProgress?: (share: number) => void
): Promise<ChatMessageResponse> {
  const formData = new FormData();
  formData.append("message", {
    string: JSON.stringify(data),
    type: "application/json",
  } as unknown as Blob);
  for (const file of files) {
    formData.append("files", {
      uri: file.uri,
      name: file.name,
      type: file.mimeType ?? UNKNOWN_FILE_TYPE,
    } as unknown as Blob);
  }
  const response = await api.post(API_ROUTES.chat.messages, formData, {
    headers: { "Content-Type": "multipart/form-data", [ORGANIZATION_HEADER]: organizationId },
    ...SILENT,
    onUploadProgress:
      files.length > 0 && onProgress
        ? (event) => {
            if (event.total) onProgress(Math.min(1, event.loaded / event.total));
          }
        : undefined,
  });
  return readData(chatMessageResponseSchema, response.data);
}

export async function updateChatMessage(
  messageId: string,
  content: string
): Promise<ChatMessageResponse> {
  const response = await api.put(API_ROUTES.chat.message(messageId), { content });
  return readData(chatMessageResponseSchema, response.data);
}

/** Silent: a refusal gets its own text from the delete hook. */
export async function deleteChatMessage(messageId: string): Promise<void> {
  await api.delete(API_ROUTES.chat.message(messageId), SILENT);
}

export async function addChatReaction(
  messageId: string,
  emoji: string
): Promise<ChatReactionSummaryResponse[]> {
  const response = await api.post(API_ROUTES.chat.reactions(messageId), { emoji });
  return readData(z.array(chatReactionSummaryResponseSchema), response.data);
}

export async function removeChatReaction(
  messageId: string,
  emoji: string
): Promise<ChatReactionSummaryResponse[]> {
  const response = await api.delete(API_ROUTES.chat.reactions(messageId), { params: { emoji } });
  return readData(z.array(chatReactionSummaryResponseSchema), response.data);
}

export async function setChatMessagePinned(
  messageId: string,
  pinned: boolean
): Promise<ChatMessageResponse> {
  const response = pinned
    ? await api.put(API_ROUTES.chat.pin(messageId))
    : await api.delete(API_ROUTES.chat.pin(messageId));
  return readData(chatMessageResponseSchema, response.data);
}

/**
 * A page of the pinned messages of a channel and its threads, most recently
 * pinned first; `hasOlder` means more pins before the oldest one listed.
 */
export async function listChatPins(
  channelId: string,
  before: string | undefined
): Promise<ChatMessagePage> {
  const response = await api.get(API_ROUTES.chat.pins, {
    params: { channelId, before, limit: CHAT_PAGE_SIZE },
  });
  return readData(chatMessagePageSchema, response.data);
}

export interface ChatSearchInput {
  /** At least `CHAT_SEARCH_MIN` characters, trimmed. */
  q: string;
  /** One channel and its threads; every readable channel without it. */
  channelId: string | null;
  authorId: string | null;
}

/** A page of the messages matching `q`, newest first; `hasOlder` means more matches. */
export async function searchChatMessages(
  input: ChatSearchInput,
  before: string | undefined
): Promise<ChatMessagePage> {
  const response = await api.get(API_ROUTES.chat.search, {
    params: {
      q: input.q,
      channelId: input.channelId ?? undefined,
      authorId: input.authorId ?? undefined,
      before,
      limit: CHAT_SEARCH_PAGE_SIZE,
    },
  });
  return readData(chatMessagePageSchema, response.data);
}

/**
 * The organization's active members, online first; with `channelId` the
 * members of that private channel, or with `authorsOnly` the distinct
 * authors of the channel's live messages, public or private.
 */
export async function listChatMembers(
  channelId?: string,
  authorsOnly: boolean = false
): Promise<ChatMemberResponse[]> {
  const response = await api.get(
    API_ROUTES.chat.members,
    channelId ? { params: { channelId, authorsOnly: authorsOnly || undefined } } : undefined
  );
  return readData(z.array(chatMemberResponseSchema), response.data);
}

/**
 * A page of a channel's threads whose effective status is `status`, most
 * recent activity first; `before` is the last thread of the previous page.
 */
export async function listChatThreads(
  channelId: string,
  status: ChatThreadStatus,
  before: string | undefined
): Promise<ChatThreadPage> {
  const response = await api.get(API_ROUTES.chat.threads, {
    params: { channelId, status, before, limit: CHAT_PAGE_SIZE },
  });
  return readData(chatThreadPageSchema, response.data);
}

/** One thread with its root message, members and the reader's rights. */
export async function getChatThread(threadId: string): Promise<ChatThreadResponse> {
  const response = await api.get(API_ROUTES.chat.thread(threadId));
  return readData(chatThreadResponseSchema, response.data);
}

/**
 * Starts a thread. Silent: a 409 because the message already has one is
 * answered by opening that thread, and any other refusal is toasted by the
 * create hook.
 */
export async function createChatThread(data: ChatThreadCreateRequest): Promise<ChatThreadResponse> {
  const response = await api.post(API_ROUTES.chat.threads, data, SILENT);
  return readData(chatThreadResponseSchema, response.data);
}

/** Renames a thread and sets its archive window (its creator, an OWNER or an ADMIN). */
export async function updateChatThread(
  threadId: string,
  data: ChatThreadUpdateRequest
): Promise<ChatThreadResponse> {
  const response = await api.put(API_ROUTES.chat.thread(threadId), data);
  return readData(chatThreadResponseSchema, response.data);
}

/** Closes, reopens, archives or unarchives a thread (its creator, an OWNER or an ADMIN). */
export async function changeChatThreadStatus(
  threadId: string,
  status: ChatThreadStatus
): Promise<ChatThreadResponse> {
  const response = await api.put(API_ROUTES.chat.threadStatus(threadId), { status });
  return readData(chatThreadResponseSchema, response.data);
}

/** Joins (`member` true) or leaves a thread; repeating either changes nothing. */
export async function setChatThreadMembership(
  threadId: string,
  member: boolean
): Promise<ChatThreadResponse> {
  const response = member
    ? await api.put(API_ROUTES.chat.threadMembership(threadId))
    : await api.delete(API_ROUTES.chat.threadMembership(threadId));
  return readData(chatThreadResponseSchema, response.data);
}

/**
 * Creates a channel (OWNER or ADMIN). Silent: the sheet explains the
 * refusals it can name and toasts the others itself.
 */
export async function createChatChannel(
  data: ChatChannelCreateRequest
): Promise<ChatChannelResponse> {
  const response = await api.post(API_ROUTES.chat.channels, data, SILENT);
  return readData(chatChannelResponseSchema, response.data);
}

/**
 * Renames a channel and sets its visibility and purpose (OWNER or ADMIN).
 * Silent, like a create: the sheet explains the refusals it can name and
 * toasts the others itself.
 */
export async function updateChatChannel(
  channelId: string,
  data: ChatChannelUpdateRequest
): Promise<ChatChannelResponse> {
  const response = await api.put(API_ROUTES.chat.channel(channelId), data, SILENT);
  return readData(chatChannelResponseSchema, response.data);
}

/**
 * Deletes a channel for everyone (OWNER or ADMIN); its content is hidden on
 * the server, not erased. Silent: the caller shows the refusal.
 */
export async function deleteChatChannel(channelId: string): Promise<void> {
  await api.delete(API_ROUTES.chat.channel(channelId), SILENT);
}

/**
 * Adds users to a private channel's member list (OWNER or ADMIN). Silent:
 * the members sheet shows the refusal.
 */
export async function addChatChannelMembers(
  channelId: string,
  data: ChatChannelMembersRequest
): Promise<ChatChannelResponse> {
  const response = await api.put(API_ROUTES.chat.channelMembers(channelId), data, SILENT);
  return readData(chatChannelResponseSchema, response.data);
}

/**
 * Removes a user from a private channel (OWNER or ADMIN), or lets the reader
 * leave it when `userId` is the reader. Silent: the caller shows the refusal.
 */
export async function removeChatChannelMember(channelId: string, userId: string): Promise<void> {
  await api.delete(API_ROUTES.chat.channelMember(channelId, userId), SILENT);
}

/** Tickets offered by `#`: at most this many, matching key or title on the reader's boards. */
export const CHAT_TICKET_SUGGESTIONS_MAX = 10;

/** `GET /kazan/tickets` searched by key or title, for the composer's `#` suggestion. */
/** Silent: the suggestion list shows a failed search in place. */
export async function searchChatTickets(search: string): Promise<ChatTicketSuggestion[]> {
  const response = await api.get(API_ROUTES.tickets.all, {
    ...SILENT,
    params: { search: search || undefined, amount: CHAT_TICKET_SUGGESTIONS_MAX },
  });
  const tickets: ChatTicketSuggestion[] = readData(
    z.array(chatTicketSuggestionSchema),
    response.data
  );
  return tickets.slice(0, CHAT_TICKET_SUGGESTIONS_MAX);
}

export async function getChatUnread(): Promise<ChatUnread> {
  const response = await api.get(API_ROUTES.chat.unread, SILENT);
  return readData(chatUnreadSchema, response.data);
}

/** Moves the read position; the answer is the reader's whole unread state. */
export async function markChatRead(data: ChatReadStateRequest): Promise<ChatUnread> {
  const response = await api.put(API_ROUTES.chat.readState, data);
  return readData(chatUnreadSchema, response.data);
}

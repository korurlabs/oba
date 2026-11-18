import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import { filterEmptyValues } from "@/lib/filter-utils";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  ChatChannelCreateRequest,
  ChatChannelMembersRequest,
  ChatChannelMessageQueryParams,
  ChatChannelResponse,
  ChatChannelUpdateRequest,
  ChatMemberResponse,
  ChatMembersParams,
  ChatMessageCreateRequest,
  ChatMessagePage,
  ChatMessageQueryParams,
  ChatMessageResponse,
  ChatMessageUpdateRequest,
  ChatPinsParams,
  ChatReactionSummaryResponse,
  ChatReadStateRequest,
  ChatSearchParams,
  ChatThreadCreateRequest,
  ChatThreadListParams,
  ChatThreadPage,
  ChatThreadResponse,
  ChatThreadStatusUpdateRequest,
  ChatThreadUpdateRequest,
  ChatUnreadResponse,
} from "../schemas";

// ── Envelope ────────────────────────────────────────────────────────

/** The `data` of a successful envelope; a success without data is a broken contract. */
export function requireChatData<T>(response: ApiResponse<T>): T {
  if (response.data === undefined || response.data === null) {
    throw new Error(`Chat response without data: ${response.message}`);
  }
  return response.data;
}

// ── Channels ────────────────────────────────────────────────────────

/** The channels the reader sees, in creation order. */
export async function listChatChannels() {
  const res = await api.get<ApiResponse<ChatChannelResponse[]>>(API_ROUTES.chat.channels);
  return res.data;
}

/** OWNER and ADMIN; 201 with the channel. */
export async function createChatChannel(data: ChatChannelCreateRequest) {
  const res = await api.post<ApiResponse<ChatChannelResponse>>(API_ROUTES.chat.channels, data);
  return res.data;
}

/** OWNER and ADMIN; 422 `KAI_VAL_051` or `KAI_VAL_053` for a refused visibility or purpose change. */
export async function updateChatChannel(channelId: string, data: ChatChannelUpdateRequest) {
  const res = await api.put<ApiResponse<ChatChannelResponse>>(API_ROUTES.chat.channel(channelId), data);
  return res.data;
}

/** OWNER and ADMIN; the channel and its content are hidden, not erased. */
export async function deleteChatChannel(channelId: string): Promise<void> {
  await api.delete(API_ROUTES.chat.channel(channelId));
}

/** OWNER and ADMIN; adds users to a private channel (422 `KAI_VAL_052` for a public one). */
export async function addChatChannelMembers(channelId: string, data: ChatChannelMembersRequest) {
  const res = await api.put<ApiResponse<ChatChannelResponse>>(API_ROUTES.chat.channelMembers(channelId), data);
  return res.data;
}

/** OWNER and ADMIN, or the user leaving; 422 `KAI_VAL_052` for a public channel. */
export async function removeChatChannelMember(channelId: string, userId: string): Promise<void> {
  await api.delete(API_ROUTES.chat.channelMember(channelId, userId));
}

// ── Messages ────────────────────────────────────────────────────────

/**
 * A channel page, oldest first; no cursor asks for the newest page.
 * `pullRequestStatuses` goes out comma-separated, which is how the server
 * binds its list; an empty list is left out.
 */
export async function listChannelMessages(params: ChatChannelMessageQueryParams) {
  const { pullRequestStatuses, ...rest } = params;
  const res = await api.get<ApiResponse<ChatMessagePage>>(API_ROUTES.chat.messages, {
    params: filterEmptyValues({ ...rest, pullRequestStatuses: pullRequestStatuses?.join(",") }),
  });
  return res.data;
}

/** A thread page, oldest first; no cursor asks for the newest page. */
export async function listThreadMessages(threadId: string, params: ChatMessageQueryParams) {
  const res = await api.get<ApiResponse<ChatMessagePage>>(API_ROUTES.chat.threadMessages(threadId), {
    params: filterEmptyValues(params),
  });
  return res.data;
}

/** One message as the reader sees it (mentions, `canOpen`, `reactedByMe`); 404 once deleted. */
export async function getChatMessage(messageId: string) {
  const res = await api.get<ApiResponse<ChatMessageResponse>>(API_ROUTES.chat.message(messageId));
  return res.data;
}

/**
 * Sends a message: a multipart body with the JSON part `message` and one
 * `files` part per file. A failed upload fails the whole send.
 */
export async function createChatMessage(data: ChatMessageCreateRequest, files: File[]) {
  const formData = new FormData();
  formData.append("message", new Blob([JSON.stringify(data)], { type: "application/json" }));
  files.forEach((file) => {
    formData.append("files", file);
  });
  const res = await api.post<ApiResponse<ChatMessageResponse>>(API_ROUTES.chat.messages, formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return res.data;
}

export async function updateChatMessage(messageId: string, data: ChatMessageUpdateRequest) {
  const res = await api.put<ApiResponse<ChatMessageResponse>>(API_ROUTES.chat.message(messageId), data);
  return res.data;
}

export async function deleteChatMessage(messageId: string): Promise<void> {
  await api.delete(API_ROUTES.chat.message(messageId));
}

/** Adds the reader's reaction (idempotent); returns the message's reactions. */
export async function addChatReaction(messageId: string, emoji: string) {
  const res = await api.post<ApiResponse<ChatReactionSummaryResponse[]>>(API_ROUTES.chat.reactions(messageId), {
    emoji,
  });
  return res.data;
}

/** Removes the reader's reaction (idempotent); returns the message's reactions. */
export async function removeChatReaction(messageId: string, emoji: string) {
  const res = await api.delete<ApiResponse<ChatReactionSummaryResponse[]>>(API_ROUTES.chat.reactions(messageId), {
    params: { emoji },
  });
  return res.data;
}

export async function pinChatMessage(messageId: string) {
  const res = await api.put<ApiResponse<ChatMessageResponse>>(API_ROUTES.chat.pin(messageId));
  return res.data;
}

export async function unpinChatMessage(messageId: string) {
  const res = await api.delete<ApiResponse<ChatMessageResponse>>(API_ROUTES.chat.pin(messageId));
  return res.data;
}

/** Pinned messages, newest pin first; `hasOlder` means more pins. */
export async function listChatPins(params: ChatPinsParams) {
  const res = await api.get<ApiResponse<ChatMessagePage>>(API_ROUTES.chat.pins, {
    params: filterEmptyValues(params),
  });
  return res.data;
}

/** Matches in one channel and its threads, or in every readable channel without `channelId`; newest first. */
export async function searchChatMessages(params: ChatSearchParams) {
  const res = await api.get<ApiResponse<ChatMessagePage>>(API_ROUTES.chat.search, {
    params: filterEmptyValues(params),
  });
  return res.data;
}

// ── Members and read state ──────────────────────────────────────────

/**
 * Active members, online first; `search` matches name or email. With
 * `channelId` a private channel's members, or with `authorsOnly` the
 * channel's message authors. Not paged.
 */
export async function listChatMembers(params: ChatMembersParams = {}) {
  const res = await api.get<ApiResponse<ChatMemberResponse[]>>(API_ROUTES.chat.members, {
    params: filterEmptyValues(params),
  });
  return res.data;
}

export async function getChatUnread() {
  const res = await api.get<ApiResponse<ChatUnreadResponse>>(API_ROUTES.chat.unread);
  return res.data;
}

/** Moves the reader's position forward (an older message is ignored); returns the new counts. */
export async function markChatRead(data: ChatReadStateRequest) {
  const res = await api.put<ApiResponse<ChatUnreadResponse>>(API_ROUTES.chat.readState, data);
  return res.data;
}

// ── Threads ─────────────────────────────────────────────────────────

/** 201 with the thread; 409 `KAI_STA_013` with `errorValues.threadId` when the message has one. */
export async function createChatThread(data: ChatThreadCreateRequest) {
  const res = await api.post<ApiResponse<ChatThreadResponse>>(API_ROUTES.chat.threads, data);
  return res.data;
}

/** Threads by most recent activity first. */
export async function listChatThreads(params: ChatThreadListParams) {
  const res = await api.get<ApiResponse<ChatThreadPage>>(API_ROUTES.chat.threads, {
    params: filterEmptyValues(params),
  });
  return res.data;
}

export async function getChatThread(threadId: string) {
  const res = await api.get<ApiResponse<ChatThreadResponse>>(API_ROUTES.chat.thread(threadId));
  return res.data;
}

export async function updateChatThread(threadId: string, data: ChatThreadUpdateRequest) {
  const res = await api.put<ApiResponse<ChatThreadResponse>>(API_ROUTES.chat.thread(threadId), data);
  return res.data;
}

/**
 * Close, Reopen, Archive or Unarchive (creator, OWNER or ADMIN). The current
 * status is a no-op that returns the thread; 409 `KAI_STA_014` when a closed
 * thread is archived.
 */
export async function changeChatThreadStatus(threadId: string, data: ChatThreadStatusUpdateRequest) {
  const res = await api.put<ApiResponse<ChatThreadResponse>>(API_ROUTES.chat.threadStatus(threadId), data);
  return res.data;
}

export async function joinChatThread(threadId: string) {
  const res = await api.put<ApiResponse<ChatThreadResponse>>(API_ROUTES.chat.threadMembership(threadId));
  return res.data;
}

export async function leaveChatThread(threadId: string) {
  const res = await api.delete<ApiResponse<ChatThreadResponse>>(API_ROUTES.chat.threadMembership(threadId));
  return res.data;
}

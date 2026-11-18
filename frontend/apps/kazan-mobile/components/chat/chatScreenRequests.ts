import { useSyncExternalStore } from "react";

/*
 * What a chat sheet asks of the screen under it. A sheet is its own route,
 * so it cannot reach that screen's state: it leaves one request here before
 * it closes, and the channel or thread screen it names takes it.
 */

export type ChatScreenRequest =
  /**
   * Start a reply to, or an edit of, a message in the composer of its
   * conversation: the channel itself when `threadId` is null, else the thread.
   */
  | { kind: "reply" | "edit"; channelId: string; threadId: string | null; messageId: string }
  /** Show another channel in place of this one. */
  | { kind: "switch"; channelId: string; toChannelId: string }
  /** Open a thread of this channel. */
  | { kind: "openThread"; channelId: string; threadId: string }
  /** Ask for a title and start a thread on a message of this channel. */
  | { kind: "newThread"; channelId: string; messageId: string }
  /** Open the sheet that creates a channel. */
  | { kind: "newChannel"; channelId: string }
  /** Open the members sheet of `membersChannelId`, a private channel the reader is not on. */
  | { kind: "manageMembers"; channelId: string; membersChannelId: string }
  /**
   * Show a found or pinned message of this channel: in the channel itself
   * when `threadId` is null, else in that thread, opened over the channel.
   */
  | { kind: "jump"; channelId: string; threadId: string | null; messageId: string };

/** The thread whose screen takes the request, or null for the channel screen. */
export function chatScreenRequestThreadId(request: ChatScreenRequest): string | null {
  return request.kind === "reply" || request.kind === "edit" ? request.threadId : null;
}

let pending: ChatScreenRequest | null = null;
const listeners = new Set<() => void>();

function notify(): void {
  for (const listener of listeners) listener();
}

/** Leaves a request for the channel screen; a newer one replaces an untaken one. */
export function requestChatScreen(request: ChatScreenRequest): void {
  pending = request;
  notify();
}

/** Removes the request once its screen has acted on it. */
export function clearChatScreenRequest(request: ChatScreenRequest): void {
  if (pending !== request) return;
  pending = null;
  notify();
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The untaken request, whichever channel it names. */
export function pendingChatScreenRequest(): ChatScreenRequest | null {
  return pending;
}

/** The request addressed to this channel, or with `threadId` to that thread, or null. */
export function useChatScreenRequest(
  channelId: string,
  threadId: string | null = null
): ChatScreenRequest | null {
  const request: ChatScreenRequest | null = useSyncExternalStore(
    subscribe,
    pendingChatScreenRequest,
    pendingChatScreenRequest
  );
  if (!request || request.channelId !== channelId) return null;
  return chatScreenRequestThreadId(request) === threadId ? request : null;
}

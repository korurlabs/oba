"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useKaizenOpen } from "@/components/layout/use-kaizen-access";
import { getChatUnread, markChatRead, requireChatData } from "../api";
import { type ChatConversationUnreadResponse, type ChatUnreadResponse } from "../schemas";
import { CHAT_KEYS } from "./query-keys";
import { useChatOrganizationId } from "./use-chat-messages";

/** Delay between the reader reaching the bottom and the read request. */
const MARK_READ_DEBOUNCE_MS = 500;

/**
 * The reader's unread counts in the active organization (each readable
 * channel, each active thread the reader is a member of, and their total,
 * each capped at 100), with the notifications among them per conversation
 * and in total. Used by the sidebar badge on every dashboard page and by
 * the chat view, so it is mounted many times at once; it only reads the
 * cache. Keeping the counts live is `useChatChannelListRealtime`, mounted
 * once: each component refetching on its own would send one request per
 * mounted copy after every reconnect. It waits until Kaizen is open in the
 * organization, as the sidebar badge would otherwise be refused on every page.
 */
export function useChatUnread() {
  const orgId = useChatOrganizationId();
  const kaizenOpen = useKaizenOpen();
  return useQuery({
    queryKey: CHAT_KEYS.unread(orgId ?? ""),
    queryFn: async () => requireChatData(await getChatUnread()),
    enabled: !!orgId && kaizenOpen,
  });
}

/**
 * The unread state of one conversation: the channel `channelId` when
 * `threadId` is null, otherwise the thread. Undefined when the counts hold
 * none for it (nothing read yet in an unknown channel, a thread the reader is
 * no member of).
 */
export function findChatConversationUnread(
  unread: ChatUnreadResponse | undefined,
  channelId: string | null | undefined,
  threadId: string | null,
): ChatConversationUnreadResponse | undefined {
  if (!unread) return undefined;
  if (threadId) return unread.threads.find((thread) => thread.id === threadId);
  if (!channelId) return undefined;
  return unread.channels.find((channel) => channel.id === channelId);
}

/** The unread count of one conversation (`threadId` null: the channel itself), as `findChatConversationUnread`. */
export function chatConversationUnread(
  unread: ChatUnreadResponse | undefined,
  channelId: string | null | undefined,
  threadId: string | null,
): number {
  return findChatConversationUnread(unread, channelId, threadId)?.unread ?? 0;
}

/**
 * The notification count of one conversation (`threadId` null: the channel
 * itself): its unread messages that mention the reader or reply to the reader.
 */
export function chatConversationMentions(
  unread: ChatUnreadResponse | undefined,
  channelId: string | null | undefined,
  threadId: string | null,
): number {
  return findChatConversationUnread(unread, channelId, threadId)?.mentionCount ?? 0;
}

/** Each readable channel's unread state by channel ID, for the badges of the channel list. */
export function chatChannelUnreadById(
  unread: ChatUnreadResponse | undefined,
): ReadonlyMap<string, ChatConversationUnreadResponse> {
  return new Map((unread?.channels ?? []).map((channel) => [channel.id, channel]));
}

/** What the threads hold for the reader, for the channel header's "Threads" button. */
export interface ChatThreadNotifications {
  /** The notification counts of every unread thread added up; each is capped, the sum is not. */
  mentionCount: number;
  /** At least one thread has unread messages. */
  hasUnread: boolean;
}

/** The notifications and unread state of the reader's active member threads in one channel, from the counts. */
export function chatThreadNotifications(
  unread: ChatUnreadResponse | undefined,
  channelId: string | null | undefined,
): ChatThreadNotifications {
  const threads = unread && channelId ? unread.threads.filter((thread) => thread.channelId === channelId) : [];
  return {
    mentionCount: threads.reduce((sum, thread) => sum + thread.mentionCount, 0),
    hasUnread: threads.some((thread) => thread.unread > 0),
  };
}

/**
 * The reader's read position in one conversation when it was opened. It
 * places the "New messages" divider and where the list opens, and stays put
 * while the reader reads, so the divider does not move as the server's
 * position moves forward; it is dropped when the reader leaves or sends.
 */
export interface ChatUnreadMarker {
  /** The conversation's last read message; null before the first read. */
  lastReadMessageId: string | null;
  lastReadAt: string | null;
  /** Unread messages at that moment, capped at `CHAT_UNREAD_CAP`. */
  count: number;
}

/**
 * The read position of a channel (`threadId` null) or one of its threads,
 * taken once from the unread counts when the conversation opens. `marker` is
 * undefined until the counts are in, null when nothing was unread, when the
 * counts could not be loaded, or after `clearMarker` (the reader sent a
 * message). The caller remounts the hook (a `key`) when the channel changes.
 */
export function useChatUnreadMarker(
  channelId: string | null | undefined,
  threadId: string | null,
): {
  marker: ChatUnreadMarker | null | undefined;
  clearMarker: () => void;
} {
  const { data: unread, isError } = useChatUnread();
  const [marker, setMarker] = useState<ChatUnreadMarker | null | undefined>(undefined);
  // Taken once the conversation is known: a channel view waits for the open channel.
  if (marker === undefined && (threadId || channelId) && (unread || isError)) {
    const conversation = findChatConversationUnread(unread, channelId, threadId);
    setMarker(
      conversation && conversation.unread > 0
        ? {
            lastReadMessageId: conversation.lastReadMessageId,
            lastReadAt: conversation.lastReadAt,
            count: conversation.unread,
          }
        : null,
    );
  }
  const clearMarker = useCallback(() => setMarker(null), []);
  return { marker, clearMarker };
}

export interface ChatMarkReadVariables {
  channelId: string;
  /** Null for the channel itself. */
  threadId: string | null;
  lastReadMessageId: string;
}

/**
 * Marks a channel (`threadId` null) or one of its threads read up to a
 * message. The server only moves the position forward and answers with the
 * new counts, which replace the cached ones. A send does not move the
 * reader's position: the list marks read when the reader reaches the bottom.
 */
export function useMarkChatRead() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();

  return useMutation({
    mutationFn: async (variables: ChatMarkReadVariables) => requireChatData(await markChatRead(variables)),
    onSuccess: (unread: ChatUnreadResponse) => {
      if (orgId) queryClient.setQueryData(CHAT_KEYS.unread(orgId), unread);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export interface UseMarkReadOnVisibleOptions {
  /** The channel, also for one of its threads; nothing is sent while it is unknown. */
  channelId: string | null | undefined;
  /** Null for the channel itself. */
  threadId: string | null;
  /** The newest loaded message of the conversation; the position moves up to it. */
  newestMessageId: string | null | undefined;
  /** The sentinel below the newest message; pass the element from a callback ref. */
  sentinel: Element | null;
  /** False while the list does not hold the newest page, or while it loads. */
  enabled?: boolean;
}

/**
 * Marks a conversation read up to its newest loaded message while the reader
 * sees its bottom (the sentinel intersects the viewport) and the browser tab
 * is visible, debounced by 500 ms. It sends only while the conversation has
 * unread messages or notifications, and only once per newest message while
 * they stay, so a message that arrives while the reader is at the bottom of
 * a visible tab is read at once.
 */
export function useMarkReadOnVisible({
  channelId,
  threadId,
  newestMessageId,
  sentinel,
  enabled = true,
}: UseMarkReadOnVisibleOptions): void {
  const { data: unread } = useChatUnread();
  const { mutate } = useMarkChatRead();
  const [bottomVisible, setBottomVisible] = useState<boolean>(false);
  const [tabVisible, setTabVisible] = useState<boolean>(
    () => typeof document === "undefined" || document.visibilityState === "visible",
  );
  const lastSentRef = useRef<string | null>(null);

  useEffect(() => {
    if (!sentinel) {
      setBottomVisible(false);
      return;
    }
    const observer = new IntersectionObserver((entries) => {
      setBottomVisible(entries.some((entry) => entry.isIntersecting));
    });
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [sentinel]);

  useEffect(() => {
    const onVisibilityChange = () => setTabVisible(document.visibilityState === "visible");
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => document.removeEventListener("visibilitychange", onVisibilityChange);
  }, []);

  const unreadCount = chatConversationUnread(unread, channelId, threadId);
  // A reviewer added to a pull request share by an edit has a notification
  // on a card they had already read, so the count of unread messages stays 0;
  // a read at the same position is what clears it on the server.
  const mentionCount = chatConversationMentions(unread, channelId, threadId);
  const pending = unreadCount > 0 || mentionCount > 0;

  useEffect(() => {
    // Once nothing is pending the next notification may be read at the same
    // newest message; while one stays pending, the message is sent only once.
    if (!pending) {
      lastSentRef.current = null;
      return;
    }
    if (!enabled || !bottomVisible || !tabVisible || !newestMessageId || !channelId) return;
    if (lastSentRef.current === newestMessageId) return;
    const timer = setTimeout(() => {
      lastSentRef.current = newestMessageId;
      mutate(
        { channelId, threadId, lastReadMessageId: newestMessageId },
        {
          onError: () => {
            // Allow the next visit of the bottom to try again; the hook's
            // own onError has toasted the failure.
            lastSentRef.current = null;
          },
        },
      );
    }, MARK_READ_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [enabled, bottomVisible, tabVisible, newestMessageId, pending, channelId, threadId, mutate]);
}

import { useIsFocused } from "@react-navigation/native";
import { useEffect, useRef, useState } from "react";
import { AppState, type AppStateStatus } from "react-native";

import {
  chatChannelUnread,
  chatThreadUnread,
  shouldMarkChatRead,
} from "@/components/chat/chatScreenModel";
import { useChatUnread } from "@/hooks/queries/chat";
import { useMarkChatRead } from "@/hooks/queries/chatMutations";
import type { ChatConversationUnreadResponse } from "@/lib/schemas/chat";

/** A burst of arriving messages moves the position once. */
const MARK_READ_DEBOUNCE_MS = 500;

/** True while the app is in the foreground. */
function useAppActive(): boolean {
  const [active, setActive] = useState<boolean>(AppState.currentState === "active");
  useEffect(() => {
    const subscription = AppState.addEventListener("change", (state: AppStateStatus) => {
      setActive(state === "active");
    });
    return () => subscription.remove();
  }, []);
  return active;
}

export interface UseMarkChatReadOnVisibleOptions {
  channelId: string | null;
  /** The thread read in the channel, or null for the channel itself. */
  threadId?: string | null;
  /** The newest loaded message of the conversation. */
  newestMessageId: string | null | undefined;
  /**
   * The reader sees the newest message: the list holds the newest page, it is
   * on screen, and the list is not still reaching the unread divider.
   */
  newestSeen: boolean;
}

/**
 * Moves the reader's position in a channel or thread to its newest message while the
 * reader sees it, the screen is focused and the app is in the foreground. It
 * sends only while something is unread, and once per newest message, so a
 * message that arrives while the reader watches the bottom is read at once.
 * A failed request is logged by the mutation and tried again on the next
 * visit of the bottom; the unread badge stays, which shows it.
 */
export function useMarkChatReadOnVisible({
  channelId,
  threadId = null,
  newestMessageId,
  newestSeen,
}: UseMarkChatReadOnVisibleOptions): void {
  const { data: unread } = useChatUnread();
  const { mutate } = useMarkChatRead();
  const focused: boolean = useIsFocused();
  const appActive: boolean = useAppActive();
  const lastSentRef = useRef<string | null>(null);

  const entry: ChatConversationUnreadResponse | undefined = threadId
    ? chatThreadUnread(unread, threadId)
    : chatChannelUnread(unread, channelId);
  const pending: boolean = !!entry && (entry.unread > 0 || entry.mentionCount > 0);

  useEffect(() => {
    // Once nothing is pending, the next notification may be read at the same message.
    if (!pending) {
      lastSentRef.current = null;
      return;
    }
    const send: boolean = shouldMarkChatRead({
      newestSeen,
      visible: focused && appActive,
      pending,
      newestMessageId,
      lastSentMessageId: lastSentRef.current,
    });
    if (!send || !channelId || !newestMessageId) return;
    const timer = setTimeout(() => {
      lastSentRef.current = newestMessageId;
      mutate(
        { channelId, threadId, lastReadMessageId: newestMessageId },
        {
          onError: () => {
            lastSentRef.current = null;
          },
        }
      );
    }, MARK_READ_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [newestSeen, focused, appActive, pending, newestMessageId, channelId, threadId, mutate]);
}

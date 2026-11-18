"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { CHAT_TYPING_TIMEOUT_MS, type ChatSignal } from "../schemas";
import { useChatOpenChannelId } from "./use-chat-channels";

/** One typing member of one conversation; the key is unique per channel, conversation and user. */
interface TypingEntry {
  userId: string;
  /** The channel whose topic carried the signal. */
  channelId: string;
  /** Null for the channel itself. */
  threadId: string | null;
}

function entryKey(channelId: string, threadId: string | null, userId: string): string {
  return `${channelId}:${threadId ?? "channel"}:${userId}`;
}

const NO_TYPING: readonly string[] = [];

/**
 * Who is typing, per conversation, from the TYPING signals of the channel
 * topics. A member stops typing `CHAT_TYPING_TIMEOUT_MS` after their last
 * signal, or at once when their message arrives in that conversation. The
 * reader's own signals never reach `onSignal`; the realtime hook filters them.
 *
 * `onSignal` goes to `useChatRealtime({ onSignal })`, which names the channel
 * whose topic carried the signal; `typingUserIds(threadId)` lists the typing
 * members of the open channel (`null`) or of one of its threads, in the order
 * they started typing. Members still typing in a channel the reader left are
 * not listed, so a switch never shows them in the new channel.
 */
export function useTypingUsers() {
  const openChannelId = useChatOpenChannelId();
  const [entries, setEntries] = useState<Record<string, TypingEntry>>({});
  const timersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

  const stopTyping = useCallback((key: string) => {
    const timer = timersRef.current.get(key);
    if (timer !== undefined) clearTimeout(timer);
    timersRef.current.delete(key);
    setEntries((current) => {
      if (!(key in current)) return current;
      const next = { ...current };
      delete next[key];
      return next;
    });
  }, []);

  const onSignal = useCallback(
    (signal: ChatSignal, channelId: string) => {
      if (signal.type === "TYPING") {
        const key = entryKey(channelId, signal.threadId, signal.userId);
        const previous = timersRef.current.get(key);
        if (previous !== undefined) clearTimeout(previous);
        timersRef.current.set(
          key,
          setTimeout(() => stopTyping(key), CHAT_TYPING_TIMEOUT_MS),
        );
        setEntries((current) =>
          key in current
            ? current
            : { ...current, [key]: { userId: signal.userId, channelId, threadId: signal.threadId } },
        );
        return;
      }
      if (signal.type === "MESSAGE_CREATED" && signal.authorId) {
        stopTyping(entryKey(channelId, signal.threadId, signal.authorId));
      }
    },
    [stopTyping],
  );

  useEffect(() => {
    const timers = timersRef.current;
    return () => {
      timers.forEach((timer) => clearTimeout(timer));
      timers.clear();
    };
  }, []);

  const byConversation = useMemo(() => {
    const map = new Map<string, string[]>();
    for (const entry of Object.values(entries)) {
      if (entry.channelId !== openChannelId) continue;
      const conversation = entry.threadId ?? "channel";
      const list = map.get(conversation) ?? [];
      list.push(entry.userId);
      map.set(conversation, list);
    }
    return map;
  }, [entries, openChannelId]);

  const typingUserIds = useCallback(
    (threadId: string | null): readonly string[] => byConversation.get(threadId ?? "channel") ?? NO_TYPING,
    [byConversation],
  );

  return { onSignal, typingUserIds };
}

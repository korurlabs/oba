"use client";

import { useMemo } from "react";
import { useChatChannels } from "@/features/chat/hooks/use-chat-channels";
import { useChatUnread } from "@/features/chat/hooks/use-mark-read";
import { ChatChannelPurpose } from "@/features/chat/schemas";
import { BADGE_MAX } from "./sidebar-item";

export interface ChatUnreadCount {
  /** Unread messages; the notifications are among them, never added on top. */
  count: number;
  /** The notifications among `count`. */
  mentions: number;
  /** The count as a badge shows it: "7", or "99+" above 99. */
  shownCount: string;
}

/**
 * The unread messages of some channels and of the reader's member threads in
 * them, with the notifications among them. Each server count is capped; the
 * sum is not, and the shown count is "99+" above 99. Every chat badge and the
 * tab title count with this, so they never disagree.
 */
export function useChatUnreadCount(channelIds: ReadonlySet<string>): ChatUnreadCount {
  // Kept live by the reader's own realtime queue and refetched on every reconnect.
  const { data: unread } = useChatUnread();

  let count = 0;
  let mentions = 0;
  for (const channel of unread?.channels ?? []) {
    if (!channelIds.has(channel.id)) continue;
    count += channel.unread;
    mentions += channel.mentionCount;
  }
  for (const thread of unread?.threads ?? []) {
    if (!channelIds.has(thread.channelId)) continue;
    count += thread.unread;
    mentions += thread.mentionCount;
  }

  const shownCount = count > BADGE_MAX ? `${BADGE_MAX}+` : String(count);
  return { count, mentions, shownCount };
}

/**
 * The unread messages of every channel that has a row in the sidebar: the
 * GENERAL channels under Chats, and the one channel of each other purpose,
 * which has a root row of its own. The sum of all chat badges, capped for
 * display the same way.
 */
export function useSidebarChatUnreadCount(): ChatUnreadCount {
  const { data: channels } = useChatChannels();
  const channelIds = useMemo<ReadonlySet<string>>(() => {
    const ids = new Set<string>();
    const heldPurposes = new Set<ChatChannelPurpose>();
    for (const channel of channels ?? []) {
      if (channel.purpose !== ChatChannelPurpose.GENERAL) {
        // A root row opens the first channel holding its purpose.
        if (heldPurposes.has(channel.purpose)) continue;
        heldPurposes.add(channel.purpose);
      }
      ids.add(channel.id);
    }
    return ids;
  }, [channels]);
  return useChatUnreadCount(channelIds);
}

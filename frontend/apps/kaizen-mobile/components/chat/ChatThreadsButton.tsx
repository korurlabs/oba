import { useRouter } from "expo-router";
import { MessagesSquare } from "lucide-react-native";
import * as React from "react";

import { chatChannelUnreadThreads } from "@/components/chat/chatThreadModel";
import { IconButton } from "@/components/ui/icon-button";
import { useChatUnread } from "@/hooks/queries/chat";
import { useTranslation } from "@/i18n";

/**
 * The Threads action of a channel's navigation bar: opens the channel's
 * threads list, with the number of its threads holding unread replies as its
 * badge. It reads the unread counts itself, so the badge follows them (and
 * clears once those threads are read) while the header stays as it is.
 */
export function ChatThreadsButton({ channelId }: { channelId: string }) {
  const { t } = useTranslation();
  const router = useRouter();
  const unreadThreads: number = chatChannelUnreadThreads(useChatUnread().data, channelId);
  return (
    <IconButton
      icon={MessagesSquare}
      label={t("chatThreadsOpen")}
      badge={unreadThreads}
      accessibilityValue={
        unreadThreads > 0
          ? { text: t("chatThreadsUnreadThreads", { count: unreadThreads }) }
          : undefined
      }
      onPress={() => router.push({ pathname: "/chat/threads", params: { channelId } })}
    />
  );
}

import { useLocalSearchParams } from "expo-router";
import { Pin } from "lucide-react-native";
import * as React from "react";
import { View } from "react-native";

import { ChatFoundMessage, ChatLoadMore } from "@/components/chat/ChatFoundMessage";
import { requestChatScreen } from "@/components/chat/chatScreenRequests";
import { BottomSheet, useSheet } from "@/components/kazan/BottomSheet";
import { EmptyState, InlineError } from "@/components/kazan/EmptyState";
import { ListSection } from "@/components/kazan/List";
import { SkeletonRow } from "@/components/ui/skeleton";
import { useChatPins } from "@/hooks/queries/chat";
import { useTranslation } from "@/i18n";
import type { ChatMessageResponse } from "@/lib/schemas/chat";

/**
 * The pinned messages of a channel (`channelId`) and its threads, most
 * recently pinned first, opened from the channel menu. Jump closes the sheet
 * and shows the message in the channel, or in its thread over the channel.
 * A pin or unpin anywhere refreshes the list through its signal.
 */
export default function ChatPinsSheet() {
  const { t } = useTranslation();
  return (
    <BottomSheet title={t("chatPinsTitle")} cancelLabel={t("chatClose")} detent="medium" scroll>
      <PinsContent />
    </BottomSheet>
  );
}

function PinsContent() {
  const { t } = useTranslation();
  const { dismiss } = useSheet();
  const params = useLocalSearchParams<{ channelId: string }>();
  const channelId: string | null = params.channelId || null;
  const query = useChatPins(channelId);
  const messages: ChatMessageResponse[] = React.useMemo(
    () => query.data?.pages.flatMap((page) => page.items) ?? [],
    [query.data]
  );

  const onJump = React.useCallback(
    (message: ChatMessageResponse) => {
      requestChatScreen({
        kind: "jump",
        channelId: message.channelId,
        threadId: message.threadId,
        messageId: message.id,
      });
      dismiss();
    },
    [dismiss]
  );

  if (query.isPending) {
    return (
      <ListSection>
        {[0, 1, 2].map((index) => (
          <SkeletonRow key={index} />
        ))}
      </ListSection>
    );
  }
  if (query.isError && messages.length === 0) {
    return (
      <InlineError
        message={t("chatPinsError")}
        onRetry={() => void query.refetch()}
        className="mx-4 mt-4"
      />
    );
  }
  if (messages.length === 0) {
    return (
      <EmptyState
        icon={Pin}
        title={t("chatPinsEmpty")}
        message={t("chatPinsEmptyBody")}
        className="mt-8"
      />
    );
  }
  return (
    <View className="pb-6">
      {messages.map((message) => (
        <ChatFoundMessage key={message.id} message={message} onJump={onJump} />
      ))}
      <ChatLoadMore
        hasNextPage={query.hasNextPage}
        isFetchingNextPage={query.isFetchingNextPage}
        isFetchNextPageError={query.isFetchNextPageError}
        fetchNextPage={query.fetchNextPage}
      />
    </View>
  );
}

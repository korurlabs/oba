import { CornerDownRight, MessagesSquare } from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable, View } from "react-native";

import { chatAuthorName, chatMessageSnippet } from "@/components/chat/chatDisplay";
import { chatSnippetParts, type ChatSnippetParts } from "@/components/chat/chatFoundModel";
import { InlineError } from "@/components/kazan/EmptyState";
import { Avatar } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useChatThread } from "@/hooks/queries/chat";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";
import type { ChatMessageResponse } from "@/lib/schemas/chat";

/** Characters of a found message kept for its three-line snippet. */
const SNIPPET_MAX = 280;

/** "In Release 2.4" for a reply in a thread; the title comes from the thread, cached once read. */
function ThreadName({ threadId }: { threadId: string }) {
  const { t } = useTranslation();
  // Until the thread is in, or when it cannot be read, the row still says it is in a thread.
  const { data: thread } = useChatThread(threadId);
  return (
    <View className="flex-row items-center gap-1">
      <Icon as={MessagesSquare} size={12} className="text-muted-foreground" />
      <Text variant="caption" numberOfLines={1} className="shrink text-muted-foreground">
        {thread ? t("chatFoundInThread", { title: thread.title }) : t("chatFoundInAThread")}
      </Text>
    </View>
  );
}

type ChatFoundMessageProps = {
  message: ChatMessageResponse;
  /** The searched text, marked in the snippet. */
  query?: string | null;
  /** The channel's name, for a search across every channel. */
  channelName?: string | null;
  onJump: (message: ChatMessageResponse) => void;
};

/**
 * A message found by the search or listed among the pins: author, time
 * ("09:31" today, "Oct 24" before), the thread it is in, a snippet with the
 * match marked, and Jump. The whole row jumps too.
 */
function ChatFoundMessageImpl({
  message,
  query = null,
  channelName = null,
  onJump,
}: ChatFoundMessageProps) {
  const { t } = useTranslation();
  const dates = useDates();
  const colors = useThemeColors();
  const author: string = chatAuthorName(message, t("chatUnknownMember"));
  const day: string | null = dates.instantShortDate(message.createdAt);
  const time: string | null =
    day !== null && day === dates.instantShortDate(new Date())
      ? dates.time(message.createdAt)
      : day;
  const snippet: string = chatMessageSnippet(message, t("chatUnknownMember"), SNIPPET_MAX);
  const parts: ChatSnippetParts = chatSnippetParts(snippet, query);
  const jump = (): void => onJump(message);

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={t("chatFoundJumpTo", { name: author })}
      onPress={jump}
      android_ripple={{ color: colors.accent }}
      className={cn("flex-row gap-3 px-4 py-3", Platform.select({ ios: "active:opacity-60" }))}
    >
      <Avatar name={author} size="sm" />
      <View className="flex-1 gap-0.5">
        <View className="flex-row items-baseline gap-2">
          <Text variant="subhead" numberOfLines={1} className="shrink font-sans-semibold">
            {author}
          </Text>
          {time ? (
            <Text variant="caption" className="text-muted-foreground">
              {time}
            </Text>
          ) : null}
          {channelName ? (
            <Text variant="caption" numberOfLines={1} className="shrink text-muted-foreground">
              {channelName}
            </Text>
          ) : null}
        </View>
        {message.threadId ? <ThreadName threadId={message.threadId} /> : null}
        {snippet ? (
          <Text variant="footnote" numberOfLines={3}>
            {parts.before}
            {parts.match ? <Text className="font-sans-bold">{parts.match}</Text> : null}
            {parts.after}
          </Text>
        ) : (
          <Text variant="footnote" className="italic text-muted-foreground">
            {t("chatFoundNoText")}
          </Text>
        )}
      </View>
      <Button
        variant="outline"
        size="sm"
        icon={CornerDownRight}
        onPress={jump}
        accessibilityLabel={t("chatFoundJumpTo", { name: author })}
        className="self-start"
      >
        {t("chatFoundJump")}
      </Button>
    </Pressable>
  );
}

export const ChatFoundMessage = React.memo(ChatFoundMessageImpl);

type ChatLoadMoreProps = {
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  isFetchNextPageError: boolean;
  fetchNextPage: () => Promise<unknown>;
};

/** "Load more" under a newest-first list, or the failure of the last attempt with a retry. */
export function ChatLoadMore({
  hasNextPage,
  isFetchingNextPage,
  isFetchNextPageError,
  fetchNextPage,
}: ChatLoadMoreProps) {
  const { t } = useTranslation();
  const load = (): void => {
    fetchNextPage().catch((error: unknown) => {
      // The row below the list shows the failure with a retry.
      console.warn(
        "[chat] loading more matches failed",
        error instanceof Error ? error.message : error
      );
    });
  };
  if (isFetchNextPageError) {
    return (
      <InlineError message={t("chatFoundLoadMoreError")} onRetry={load} className="mx-4 my-3" />
    );
  }
  if (!hasNextPage) return null;
  return (
    <View className="items-center py-3">
      <Button variant="ghost" size="sm" loading={isFetchingNextPage} onPress={load}>
        {t("chatFoundLoadMore")}
      </Button>
    </View>
  );
}

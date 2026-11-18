import { useIsFocused } from "@react-navigation/native";
import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import { Archive, Lock, MessagesSquare, type LucideIcon } from "lucide-react-native";
import * as React from "react";
import { FlatList, RefreshControl, StyleSheet, View, type ListRenderItemInfo } from "react-native";

import { ChatLoadMore } from "@/components/chat/ChatFoundMessage";
import { ChatThreadRow } from "@/components/chat/ChatThreadRow";
import {
  CHAT_THREAD_TABS,
  chatThreadListMarks,
  type ChatThreadListMarks,
} from "@/components/chat/chatThreadModel";
import { ConnectionBanner } from "@/components/chat/ConnectionBanner";
import { EmptyState } from "@/components/kaizen/EmptyState";
import { BAR_TITLE_MAX_FONT_SCALE } from "@/components/kaizen/stackHeader";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { SkeletonRow } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useChatChannels, useChatThreads, useChatUnread } from "@/hooks/queries/chat";
import { useTranslation, type TranslationKey } from "@/i18n";
import {
  ChatChannelVisibility,
  ChatThreadStatus,
  type ChatChannelResponse,
  type ChatThreadResponse,
  type ChatThreadStatus as ChatThreadStatusValue,
} from "@/lib/schemas/chat";

const TAB_LABEL: Record<ChatThreadStatusValue, TranslationKey> = {
  ACTIVE: "chatThreadsActive",
  CLOSED: "chatThreadsClosed",
  ARCHIVED: "chatThreadsArchived",
};

const EMPTY: Record<ChatThreadStatusValue, { title: TranslationKey; icon: LucideIcon }> = {
  ACTIVE: { title: "chatThreadsEmptyActive", icon: MessagesSquare },
  CLOSED: { title: "chatThreadsEmptyClosed", icon: Lock },
  ARCHIVED: { title: "chatThreadsEmptyArchived", icon: Archive },
};

const SKELETON_ROWS: readonly number[] = [0, 1, 2, 3, 4];

const keyExtractor = (thread: ChatThreadResponse): string => thread.id;

/** A hairline under a row, inset to the title as in the platform lists. */
function Separator() {
  return (
    <View className="bg-card">
      <View className="ml-[52px] bg-border" style={styles.hairline} />
    </View>
  );
}

const styles = StyleSheet.create({ hairline: { height: StyleSheet.hairlineWidth } });

/** The navigation bar title: "Threads" over the channel's name. */
function ThreadsTitle({ channel }: { channel: ChatChannelResponse | undefined }) {
  const { t } = useTranslation();
  const prefix: string = channel?.visibility === ChatChannelVisibility.PRIVATE ? "" : "# ";
  return (
    <View accessibilityRole="header" className="max-w-64 items-center">
      <Text maxFontSizeMultiplier={BAR_TITLE_MAX_FONT_SCALE} variant="headline" numberOfLines={1}>
        {t("chatThreadsTitle")}
      </Text>
      {channel ? (
        <Text
          maxFontSizeMultiplier={BAR_TITLE_MAX_FONT_SCALE}
          variant="caption"
          numberOfLines={1}
          className="text-muted-foreground"
        >
          {`${prefix}${channel.name}`}
        </Text>
      ) : null}
    </View>
  );
}

/**
 * The threads of one channel (`channelId`), pushed from the channel's
 * header: Active, Closed and Archived, most recent activity first, a page
 * at a time. A row opens the thread over the channel. Realtime refreshes the
 * shown list on thread changes and new replies; a list left behind the
 * thread screen refreshes once it shows again.
 */
export default function ChatThreadsScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const colors = useThemeColors();
  const focused: boolean = useIsFocused();
  const params = useLocalSearchParams<{ channelId: string }>();
  const channelId: string | null = params.channelId || null;
  const channel: ChatChannelResponse | undefined = useChatChannels().data?.find(
    (item) => item.id === channelId
  );
  const [status, setStatus] = React.useState<ChatThreadStatusValue>(ChatThreadStatus.ACTIVE);
  const query = useChatThreads(channelId, status, focused);
  const unreadQuery = useChatUnread();
  const unread = unreadQuery.data;
  const [refreshing, setRefreshing] = React.useState<boolean>(false);

  const threads: ChatThreadResponse[] = React.useMemo(
    () => query.data?.pages.flatMap((page) => page.items) ?? [],
    [query.data]
  );

  const { hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage, refetch } = query;

  const onOpen = React.useCallback(
    (thread: ChatThreadResponse) => {
      router.push({
        pathname: "/chat/thread/[threadId]",
        params: { threadId: thread.id, channelId: thread.channelId },
      });
    },
    [router]
  );

  const refresh = React.useCallback(() => {
    setRefreshing(true);
    Promise.all([refetch(), unreadQuery.refetch()])
      .catch((error: unknown) => {
        // The list shows its own error state; this only ends the pull.
        console.warn(
          "[chat] threads refresh failed",
          error instanceof Error ? error.message : error
        );
      })
      .finally(() => setRefreshing(false));
  }, [refetch, unreadQuery]);

  const onEndReached = React.useCallback(() => {
    if (!hasNextPage || isFetchingNextPage || isFetchNextPageError) return;
    fetchNextPage().catch((error: unknown) => {
      // The footer shows the failure with a retry.
      console.warn(
        "[chat] loading more threads failed",
        error instanceof Error ? error.message : error
      );
    });
  }, [hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage]);

  const renderItem = React.useCallback(
    ({ item }: ListRenderItemInfo<ChatThreadResponse>) => {
      const marks: ChatThreadListMarks = chatThreadListMarks(item, unread);
      return (
        <ChatThreadRow
          thread={item}
          unreadCount={marks.unread}
          mentionCount={marks.mentions}
          onOpen={onOpen}
        />
      );
    },
    [unread, onOpen]
  );

  const empty: React.ReactElement = (() => {
    // A link without a channel names nothing to load; the query never starts.
    if (!channelId) return <EmptyState title={t("chatChannelNotFound")} className="mt-12" />;
    if (query.isPending) {
      return (
        <View className="bg-card">
          {SKELETON_ROWS.map((index) => (
            <SkeletonRow key={index} />
          ))}
        </View>
      );
    }
    if (query.isError) {
      return (
        <EmptyState
          variant="error"
          title={t("chatThreadsError")}
          onAction={() => void refetch()}
          className="mt-12"
        />
      );
    }
    return (
      <EmptyState icon={EMPTY[status].icon} title={t(EMPTY[status].title)} className="mt-12" />
    );
  })();

  return (
    <View className="flex-1 bg-screen">
      <Stack.Screen options={{ headerTitle: () => <ThreadsTitle channel={channel} /> }} />
      <ConnectionBanner />
      <View className="px-4 pb-3 pt-2">
        <SegmentedControl
          values={CHAT_THREAD_TABS.map((value) => t(TAB_LABEL[value]))}
          selectedIndex={CHAT_THREAD_TABS.indexOf(status)}
          onChange={(index: number) => {
            const next: ChatThreadStatusValue | undefined = CHAT_THREAD_TABS[index];
            if (next) setStatus(next);
          }}
        />
      </View>
      <FlatList
        key={status}
        data={query.isError && threads.length === 0 ? [] : threads}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        extraData={unread}
        ItemSeparatorComponent={Separator}
        ListEmptyComponent={empty}
        ListFooterComponent={
          <ChatLoadMore
            hasNextPage={hasNextPage}
            isFetchingNextPage={isFetchingNextPage}
            isFetchNextPageError={isFetchNextPageError}
            fetchNextPage={fetchNextPage}
          />
        }
        onEndReached={onEndReached}
        onEndReachedThreshold={0.5}
        contentInsetAdjustmentBehavior="automatic"
        contentContainerClassName="pb-10"
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={refresh}
            tintColor={colors.mutedForeground}
          />
        }
      />
    </View>
  );
}

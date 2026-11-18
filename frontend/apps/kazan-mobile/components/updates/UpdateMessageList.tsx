import { useQueryClient } from "@tanstack/react-query";
import { CalendarDays, Newspaper } from "lucide-react-native";
import { useRouter } from "expo-router";
import * as React from "react";
import { ActivityIndicator, FlatList, View, type ListRenderItemInfo } from "react-native";

import {
  buildChatListItems,
  NO_CHAT_UNREAD_MARKER,
  type ChatListItem,
} from "@/components/chat/chatListModel";
import { DaySeparator, JumpPill, UnreadBar, UnreadDivider } from "@/components/chat/ChatListParts";
import {
  useChatUnreadMarkerAtOpen,
  type ChatUnreadMarker,
} from "@/components/chat/ChatMessageList";
import {
  ChatMessageRow,
  ChatOutboxRow,
  type ChatMessageRowProps,
} from "@/components/chat/ChatMessageRow";
import { ChatSwipeToReply } from "@/components/chat/ChatSwipeToReply";
import { chatMessageThreadMentions } from "@/components/chat/chatThreadModel";
import { isChatNewestSeen } from "@/components/chat/chatUnreadModel";
import {
  useChatAtBottom,
  useChatFirstUnreadId,
  useChatLocateByReadTime,
  useChatNewestEnd,
  useChatUnreadBar,
  useChatUnreadOpen,
  useChatUnreadStateError,
  useScrollToChatRow,
} from "@/components/chat/useChatUnreadEntry";
import { useHomeLinks } from "@/components/home/homeNavigation";
import { EmptyState, InlineError } from "@/components/kazan/EmptyState";
import { UpdateCard } from "@/components/updates/UpdateCard";
import { readChatDailyUpdate } from "@/components/updates/updateModel";
import { Skeleton, SkeletonGroup } from "@/components/ui/skeleton";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useChatUnread } from "@/hooks/queries/chat";
import { useSendChatMessage } from "@/hooks/queries/chatMutations";
import { useChatOutbox } from "@/hooks/queries/chatOutbox";
import { useMarkChatReadOnVisible } from "@/hooks/queries/chatReadState";
import {
  listUpdateChannelDay,
  updateKeys,
  useUpdateChannelMessages,
} from "@/hooks/queries/updates";
import { useMe } from "@/hooks/queries/users";
import { useOpenKazanLink } from "@/hooks/useOpenKazanLink";
import { useTranslation } from "@/i18n";
import { BUSINESS_TIME_ZONE, dayInZone } from "@/lib/dates";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import type {
  ChatMessageCursor,
  ChatMessagePage,
  ChatMessageResponse,
  ChatOutboxMessage,
} from "@/lib/schemas/chat";
import type { UpdateChannelFilter, UpdateResponse } from "@/lib/schemas/updates";

const NO_OUTBOX: ChatOutboxMessage[] = [];

export type UpdateMessageListProps = Pick<
  ChatMessageRowProps,
  "onReactionPress" | "onOpenThread" | "onOpenAttachment" | "onSwipeReply"
> & {
  channelId: string;
  filter: UpdateChannelFilter;
  /** Today in the business zone; only today's view shows unsent messages and marks read. */
  today: string;
  /** Messages of others that arrived while the newest page was not loaded. */
  newMessageCount: number;
  clearNewMessageCount: () => void;
  /** The readable text of an unconfirmed message; the raw text without it. */
  outboxText?: (content: string) => string;
  /** Opens the actions of a message; only today's full view offers them. */
  onLongPress?: (message: ChatMessageResponse) => void;
  /** Opens the author's edit form of an update. */
  onEdit: (update: UpdateResponse, messageId: string) => void;
};

const keyExtractor = (item: ChatListItem): string => `${item.kind}:${item.key}`;

/** The business day of an instant, so the list groups as the server's day filter does. */
const businessDayOf = (iso: string): string => dayInZone(iso, BUSINESS_TIME_ZONE) ?? iso;

function MessagesSkeleton() {
  return (
    <SkeletonGroup className="flex-1 justify-end gap-5 px-4 pb-4">
      {[0, 1, 2].map((index) => (
        <View key={index} className="flex-row gap-3">
          <Skeleton width={32} height={32} round />
          <View className="flex-1 gap-2">
            <Skeleton width={120} height={12} />
            <Skeleton width="100%" height={180} />
          </View>
        </View>
      ))}
    </SkeletonGroup>
  );
}

/**
 * One business day of the daily updates channel, newest at the bottom:
 * updates as full cards and the team's messages between them, narrowed to
 * one author when the filter says so. The screen refreshes it on every
 * channel signal. Today's full view also shows the device's unsent messages
 * and marks the channel read; a past day or one person's view does neither,
 * since it is not the channel's newest state.
 */
export function UpdateMessageList({
  channelId,
  filter,
  today,
  newMessageCount,
  clearNewMessageCount,
  outboxText,
  onLongPress,
  onReactionPress,
  onOpenThread,
  onOpenAttachment,
  onSwipeReply,
  onEdit,
}: UpdateMessageListProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const meQuery = useMe(true);
  const me = meQuery.data;
  const meId: string | undefined = me?.id;
  const { openTicket } = useHomeLinks();
  const { openLink } = useOpenKazanLink();
  const router = useRouter();
  // These lists show a day or a filter, not the whole channel: the quoted
  // message opens in the channel screen, which pages to any message.
  const openQuote = React.useCallback(
    (messageId: string) =>
      router.push({
        pathname: "/chat/channel/[channelId]",
        params: { channelId, message: messageId },
      }),
    [router, channelId]
  );
  const { retry, discard } = useSendChatMessage();
  const live: boolean = filter.day === today && filter.personId === null;
  const query = useUpdateChannelMessages(channelId, filter);
  const {
    messages,
    atNewest,
    fetchPreviousPage,
    hasPreviousPage,
    isFetchingPreviousPage,
    isFetchPreviousPageError,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = query;
  const channelOutbox: ChatOutboxMessage[] = useChatOutbox(orgId, channelId, null);
  const outbox: ChatOutboxMessage[] = live ? channelOutbox : NO_OUTBOX;
  const listRef = React.useRef<FlatList<ChatListItem>>(null);
  const { scrollToRow, onScrollToIndexFailed } = useScrollToChatRow(listRef);

  const loadNewer = React.useCallback(() => {
    if (!hasNextPage || isFetchingNextPage) return;
    fetchNextPage().catch((error: unknown) => {
      // The query keeps its pages; the next scroll to the bottom tries again.
      console.warn(
        "[updates] loading newer messages failed",
        error instanceof Error ? error.message : error
      );
    });
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);
  const { atBottom, onScroll, onScrollEnd, onContentSizeChange, onNewerAddedInPlace } =
    useChatAtBottom(loadNewer);
  const newestEnd = useChatNewestEnd({ atNewest, messages, onNewerAddedInPlace });
  const unread = useChatUnread().data;
  const unreadMarker: ChatUnreadMarker | undefined = useChatUnreadMarkerAtOpen(channelId);
  // Only today's full view is the channel's sequence, so only it marks where unread updates begin.
  const marker: ChatUnreadMarker = unreadMarker && live ? unreadMarker : NO_CHAT_UNREAD_MARKER;
  // Only the list that reads needs the read position, so only it says that it failed to load.
  const unreadState = useChatUnreadStateError(unreadMarker !== undefined || !live);

  // A confirmed send leaves the outbox and lands in the chat's own list,
  // which this day's list does not share: read the day again so it appears.
  const outboxSize: number = channelOutbox.length;
  const previousOutboxSize = React.useRef<number>(outboxSize);
  React.useEffect(() => {
    const shrank: boolean = outboxSize < previousOutboxSize.current;
    previousOutboxSize.current = outboxSize;
    if (!shrank || !orgId || !live) return;
    queryClient
      .invalidateQueries({ queryKey: updateKeys.channel(orgId, channelId, filter) })
      .catch((error: unknown) => {
        // The list keeps what it shows; the next signal reads it again.
        console.warn(
          "[updates] refresh after a send failed",
          error instanceof Error ? error.message : error
        );
      });
  }, [outboxSize, orgId, live, queryClient, channelId, filter]);

  const firstUnreadId: string | null = useChatFirstUnreadId({
    messages,
    marker,
    meId,
    atNewest,
    startLoaded: query.isSuccess && !hasPreviousPage,
  });
  // The live view names its day with the "Today" separator; a past day or a
  // person's updates name it in the screen's header, so they draw none.
  const items: ChatListItem[] = React.useMemo(() => {
    const all: ChatListItem[] = buildChatListItems(messages, outbox, {
      firstUnreadId,
      dayOf: businessDayOf,
    });
    return live ? all : all.filter((item) => item.kind !== "day");
  }, [messages, outbox, live, firstUnreadId]);
  const newestMessageId: string | undefined = messages[messages.length - 1]?.id;

  // Read on an earlier day, the divider is above today's first unread message:
  // older pages of today lead to it, a load around the read message cannot.
  const readBeforeList: boolean =
    !!marker.lastReadAt && businessDayOf(marker.lastReadAt) < filter.day;
  const loadAround = React.useCallback(
    async (messageId: string): Promise<void> => {
      if (!orgId) throw new Error("No active organization");
      const cursor: ChatMessageCursor = { around: messageId };
      const page: ChatMessagePage = await listUpdateChannelDay(channelId, filter, cursor);
      queryClient.setQueryData(updateKeys.channel(orgId, channelId, filter), {
        pages: [page],
        pageParams: [cursor],
      });
    },
    [orgId, channelId, filter, queryClient]
  );

  const onUnreadLoadFailed = React.useCallback(() => toast.error(t("chatMessagesError")), [t]);
  const locateByTime = useChatLocateByReadTime(channelId, null);
  const unreadOpen = useChatUnreadOpen({
    ready: query.isSuccess && unreadMarker !== undefined && !meQuery.isPending,
    marker,
    items,
    messages,
    atNewest,
    atBottom,
    jumping: false,
    readBeforeList,
    paging: {
      pageCount: query.data?.pages.length ?? 0,
      hasPreviousPage,
      isFetchingPreviousPage,
      isFetchPreviousPageError,
      fetchPreviousPage,
    },
    loadAround,
    locateByTime,
    scrollToRow,
    onLoadFailed: onUnreadLoadFailed,
  });
  const newestSeen: boolean = isChatNewestSeen({
    phase: unreadOpen.phase,
    live,
    loaded: query.isSuccess,
    atNewest,
    atBottom,
  });

  useMarkChatReadOnVisible({
    channelId,
    threadId: null,
    newestMessageId,
    newestSeen,
  });

  const renderItem = React.useCallback(
    ({ item }: ListRenderItemInfo<ChatListItem>) => {
      switch (item.kind) {
        case "message": {
          const update: UpdateResponse | null = readChatDailyUpdate(item.message);
          if (update) {
            const card: React.ReactElement = (
              <UpdateCard
                message={item.message}
                update={update}
                meId={meId}
                onOpenTicket={openTicket}
                onEdit={onEdit}
                onLongPress={onLongPress}
                onOpenThread={onOpenThread}
                threadMentionCount={chatMessageThreadMentions(item.message, unread)}
                onReactionPress={onReactionPress}
              />
            );
            return onSwipeReply && !item.message.deleted ? (
              <ChatSwipeToReply onReply={() => onSwipeReply(item.message)}>{card}</ChatSwipeToReply>
            ) : (
              card
            );
          }
          return (
            <ChatMessageRow
              message={item.message}
              grouped={item.grouped}
              meId={meId}
              onOpenTicket={openTicket}
              onOpenLink={openLink}
              onLongPress={onLongPress}
              onReactionPress={onReactionPress}
              onOpenThread={onOpenThread}
              threadMentionCount={chatMessageThreadMentions(item.message, unread)}
              onOpenAttachment={onOpenAttachment}
              onOpenQuote={openQuote}
              onSwipeReply={onSwipeReply}
            />
          );
        }
        case "outbox":
          return (
            <ChatOutboxRow
              row={item.row}
              text={outboxText ? outboxText(item.row.content) : item.row.content}
              onRetry={retry}
              onDiscard={discard}
            />
          );
        case "day":
          return <DaySeparator at={item.at} />;
        case "unread":
          return <UnreadDivider />;
        default:
          return null;
      }
    },
    [
      meId,
      openTicket,
      openLink,
      openQuote,
      onEdit,
      onLongPress,
      onReactionPress,
      onOpenThread,
      onOpenAttachment,
      onSwipeReply,
      outboxText,
      retry,
      discard,
      unread,
    ]
  );

  const loadOlder = React.useCallback(() => {
    if (!hasPreviousPage || isFetchingPreviousPage || isFetchPreviousPageError) return;
    fetchPreviousPage().catch((error: unknown) => {
      // The retry row at the top of the list shows the failure.
      console.warn(
        "[updates] loading older messages failed",
        error instanceof Error ? error.message : error
      );
    });
  }, [hasPreviousPage, isFetchingPreviousPage, isFetchPreviousPageError, fetchPreviousPage]);

  React.useEffect(() => {
    if (atBottom && atNewest && newMessageCount > 0) clearNewMessageCount();
  }, [atBottom, atNewest, newMessageCount, clearNewMessageCount]);

  const jumpToLatest = React.useCallback(() => {
    clearNewMessageCount();
    if (!atNewest && orgId) {
      // The newest page was dropped while scrolling back: load it again.
      queryClient
        .resetQueries({ queryKey: updateKeys.channel(orgId, channelId, filter) })
        .catch((error: unknown) => {
          console.warn(
            "[updates] reloading the newest messages failed",
            error instanceof Error ? error.message : error
          );
          toast.error(t("chatMessagesError"));
        });
    }
    listRef.current?.scrollToOffset({ offset: 0, animated: true });
  }, [atNewest, orgId, filter, queryClient, channelId, clearNewMessageCount, t]);

  const unreadBar = useChatUnreadBar({
    channelId,
    threadId: null,
    marker,
    newestInView: newestSeen,
    atNewest,
    newestMessageId,
    jumpToLatest,
    releaseReadHold: unreadOpen.release,
  });

  if (query.isPending) return <MessagesSkeleton />;

  if (query.isError && messages.length === 0) {
    return (
      <View className="flex-1 justify-center">
        <EmptyState
          variant="error"
          title={t("chatMessagesError")}
          onAction={() => void query.refetch()}
          actionLabel={t("chatRetry")}
        />
      </View>
    );
  }

  if (items.length === 0) {
    return (
      <View className="flex-1 justify-center">
        {live ? (
          <EmptyState
            icon={Newspaper}
            title={t("updEmptyTodayTitle")}
            message={t("updEmptyTodayBody")}
          />
        ) : (
          <EmptyState
            icon={CalendarDays}
            title={t("updFilteredEmptyTitle")}
            message={t("updFilteredEmptyBody")}
          />
        )}
      </View>
    );
  }

  const showJump: boolean = !atBottom || !atNewest || newMessageCount > 0;

  return (
    <View className="flex-1">
      {unreadState.failed ? (
        <InlineError
          message={t("chatUnreadStateError")}
          onRetry={unreadState.retry}
          className="mx-4 my-2"
        />
      ) : unreadBar.visible ? (
        <UnreadBar
          marker={marker}
          onMarkRead={unreadBar.markAllRead}
          onDismiss={unreadBar.dismiss}
        />
      ) : null}
      <FlatList
        ref={listRef}
        inverted
        data={items}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        onScroll={onScroll}
        onScrollEndDrag={onScrollEnd}
        onMomentumScrollEnd={onScrollEnd}
        onContentSizeChange={onContentSizeChange}
        scrollEventThrottle={64}
        onEndReached={loadOlder}
        onEndReachedThreshold={0.5}
        onScrollToIndexFailed={onScrollToIndexFailed}
        maintainVisibleContentPosition={newestEnd}
        keyboardDismissMode="interactive"
        keyboardShouldPersistTaps="handled"
        contentContainerClassName="pb-2"
        initialNumToRender={6}
        windowSize={9}
        ListHeaderComponent={
          isFetchingNextPage ? (
            <ActivityIndicator className="py-3" color={colors.mutedForeground} />
          ) : null
        }
        ListFooterComponent={
          isFetchPreviousPageError ? (
            <InlineError
              message={t("chatMessagesError")}
              onRetry={() => {
                fetchPreviousPage().catch((error: unknown) => {
                  console.warn(
                    "[updates] retrying older messages failed",
                    error instanceof Error ? error.message : error
                  );
                });
              }}
              className="mx-4 my-3"
            />
          ) : isFetchingPreviousPage ? (
            <ActivityIndicator className="py-3" color={colors.mutedForeground} />
          ) : null
        }
      />
      {showJump ? <JumpPill count={newMessageCount} onPress={jumpToLatest} /> : null}
    </View>
  );
}

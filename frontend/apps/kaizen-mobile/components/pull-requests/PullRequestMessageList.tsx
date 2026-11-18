import { useQueryClient } from "@tanstack/react-query";
import { GitPullRequest, MessageSquare } from "lucide-react-native";
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
import { EmptyState, InlineError } from "@/components/kaizen/EmptyState";
import { PullRequestCard } from "@/components/pull-requests/PullRequestCard";
import { readChatPullRequestShare } from "@/components/pull-requests/pullRequestModel";
import { Skeleton, SkeletonGroup } from "@/components/ui/skeleton";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { chatConversationKey, useChatMessages, useChatUnread } from "@/hooks/queries/chat";
import { listChannelMessages } from "@/hooks/queries/chatApi";
import { useSendChatMessage } from "@/hooks/queries/chatMutations";
import { useMarkChatReadOnVisible } from "@/hooks/queries/chatReadState";
import {
  isPullRequestFilterActive,
  pullRequestKeys,
  usePullRequestChannelMessages,
} from "@/hooks/queries/pullRequests";
import { useMe } from "@/hooks/queries/users";
import { useOpenKaizenLink } from "@/hooks/useOpenKaizenLink";
import { useTranslation } from "@/i18n";
import { isApiError } from "@/lib/api";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import {
  CHAT_ERROR_CODES,
  type ChatMessageCursor,
  type ChatMessagePage,
  type ChatMessageResponse,
} from "@/lib/schemas/chat";
import type {
  PullRequestChannelFilter,
  PullRequestShareResponse,
} from "@/lib/schemas/pullRequests";

const NO_OUTBOX: never[] = [];

export type PullRequestMessageListProps = Pick<
  ChatMessageRowProps,
  "onReactionPress" | "onOpenThread" | "onOpenAttachment" | "onSwipeReply"
> & {
  channelId: string;
  filter: PullRequestChannelFilter;
  /** Messages of others that arrived while the newest page was not loaded. */
  newMessageCount: number;
  clearNewMessageCount: () => void;
  /** The readable text of an unconfirmed message; the raw text without it. */
  outboxText?: (content: string) => string;
  /**
   * Opens the actions of a message. Only the full channel offers them: the
   * actions find their message among the chat's own loaded pages.
   */
  onLongPress?: (message: ChatMessageResponse) => void;
  /** Opens the author's edit form of a share. */
  onEdit: (share: PullRequestShareResponse, messageId: string) => void;
  /**
   * The reader is deleting the channel: its "not found" is their own doing
   * and the screen is about to close, so it shows nothing.
   */
  goneExpected?: boolean;
};

const keyExtractor = (item: ChatListItem): string => `${item.kind}:${item.key}`;

function MessagesSkeleton() {
  return (
    <SkeletonGroup className="flex-1 justify-end gap-5 px-4 pb-4">
      {[0, 1, 2].map((index) => (
        <View key={index} className="flex-row gap-3">
          <Skeleton width={32} height={32} round />
          <View className="flex-1 gap-2">
            <Skeleton width={120} height={12} />
            <Skeleton width="100%" height={140} />
          </View>
        </View>
      ))}
    </SkeletonGroup>
  );
}

/**
 * The pull requests channel, newest at the bottom: shares as full cards and
 * the team's messages between them. Without a filter it is the chat's own
 * channel list, with the outbox, the unread divider (loaded first when it
 * lies before the newest page) and read marking; with
 * one it is the server's narrowed list, which the screen refreshes on every
 * channel signal. Both page older and newer as the chat does.
 */
export function PullRequestMessageList({
  channelId,
  filter,
  newMessageCount,
  clearNewMessageCount,
  outboxText,
  onLongPress,
  onReactionPress,
  onOpenThread,
  onOpenAttachment,
  onSwipeReply,
  onEdit,
  goneExpected = false,
}: PullRequestMessageListProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const meQuery = useMe(true);
  const me = meQuery.data;
  const meId: string | undefined = me?.id;
  const { openTicket } = useHomeLinks();
  const { openLink } = useOpenKaizenLink();
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
  const filtered: boolean = isPullRequestFilterActive(filter);
  const channelQuery = useChatMessages(channelId, null);
  const filteredQuery = usePullRequestChannelMessages(channelId, filter);
  const query = filtered ? filteredQuery : channelQuery;
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
  const outbox = filtered ? NO_OUTBOX : channelQuery.outbox;
  const unread = useChatUnread().data;
  const unreadMarker: ChatUnreadMarker | undefined = useChatUnreadMarkerAtOpen(channelId);
  // A narrowed list is not the channel's sequence, so only the full one marks where unread shares begin.
  const marker: ChatUnreadMarker = unreadMarker && !filtered ? unreadMarker : NO_CHAT_UNREAD_MARKER;
  // Only the list that reads needs the read position, so only it says that it failed to load.
  const unreadState = useChatUnreadStateError(unreadMarker !== undefined || filtered);
  const listRef = React.useRef<FlatList<ChatListItem>>(null);
  const { scrollToRow, onScrollToIndexFailed } = useScrollToChatRow(listRef);

  const loadNewer = React.useCallback(() => {
    if (!hasNextPage || isFetchingNextPage) return;
    fetchNextPage().catch((error: unknown) => {
      // The query keeps its pages; the next scroll to the bottom tries again.
      console.warn(
        "[pull-requests] loading newer messages failed",
        error instanceof Error ? error.message : error
      );
    });
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);
  const { atBottom, onScroll, onScrollEnd, onContentSizeChange, onNewerAddedInPlace } =
    useChatAtBottom(loadNewer);
  const newestEnd = useChatNewestEnd({ atNewest, messages, onNewerAddedInPlace });

  const firstUnreadId: string | null = useChatFirstUnreadId({
    messages,
    marker,
    meId,
    atNewest,
    startLoaded: query.isSuccess && !hasPreviousPage,
  });
  const items: ChatListItem[] = React.useMemo(
    () => buildChatListItems(messages, outbox, { firstUnreadId }),
    [messages, outbox, firstUnreadId]
  );
  const newestMessageId: string | undefined = messages[messages.length - 1]?.id;

  // Only the unfiltered list opens at the divider, and it is the chat's own channel list.
  const loadAround = React.useCallback(
    async (messageId: string): Promise<void> => {
      if (!orgId) throw new Error("No active organization");
      const cursor: ChatMessageCursor = { around: messageId };
      const page: ChatMessagePage = await listChannelMessages(channelId, cursor);
      queryClient.setQueryData(chatConversationKey(orgId, channelId, null), {
        pages: [page],
        pageParams: [cursor],
      });
    },
    [orgId, channelId, queryClient]
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
    live: !filtered,
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
          const share: PullRequestShareResponse | null = readChatPullRequestShare(item.message);
          if (share) {
            const card: React.ReactElement = (
              <PullRequestCard
                message={item.message}
                share={share}
                meId={meId}
                onOpenTicket={openTicket}
                onOpenLink={openLink}
                onEdit={onEdit}
                onLongPress={onLongPress}
                onReactionPress={onReactionPress}
                onOpenThread={onOpenThread}
                threadMentionCount={chatMessageThreadMentions(item.message, unread)}
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
        "[pull-requests] loading older messages failed",
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
      const queryKey = filtered
        ? pullRequestKeys.channel(orgId, channelId, filter)
        : chatConversationKey(orgId, channelId, null);
      queryClient.resetQueries({ queryKey }).catch((error: unknown) => {
        console.warn(
          "[pull-requests] reloading the newest messages failed",
          error instanceof Error ? error.message : error
        );
        toast.error(t("chatMessagesError"));
      });
    }
    listRef.current?.scrollToOffset({ offset: 0, animated: true });
  }, [atNewest, orgId, filtered, filter, queryClient, channelId, clearNewMessageCount, t]);

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
    if (goneExpected && isApiError(query.error, CHAT_ERROR_CODES.channelNotFound)) {
      return <View className="flex-1" />;
    }
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
        {filtered ? (
          <EmptyState
            icon={GitPullRequest}
            title={t("prFilteredEmptyTitle")}
            message={t("prFilteredEmptyBody")}
          />
        ) : (
          <EmptyState
            icon={MessageSquare}
            title={t("chatNoMessagesTitle")}
            message={t("chatNoMessagesBody")}
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
        initialNumToRender={10}
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
                    "[pull-requests] retrying older messages failed",
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

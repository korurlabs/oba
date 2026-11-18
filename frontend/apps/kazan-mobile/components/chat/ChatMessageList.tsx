import { useQueryClient } from "@tanstack/react-query";
import { MessageSquare } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, FlatList, View, type ListRenderItemInfo } from "react-native";

import { useMe } from "@/hooks/queries/users";
import {
  chatJumpStep,
  type ChatJumpStep,
  type ChatJumpTarget,
} from "@/components/chat/chatFoundModel";
import {
  buildChatListItems,
  NO_CHAT_UNREAD_MARKER,
  type ChatListItem,
  type ChatUnreadMarker,
} from "@/components/chat/chatListModel";
import { DaySeparator, JumpPill, UnreadBar, UnreadDivider } from "@/components/chat/ChatListParts";
import { chatChannelUnread, chatThreadUnread } from "@/components/chat/chatScreenModel";
import { chatMessageThreadMentions } from "@/components/chat/chatThreadModel";
import {
  ChatMessageRow,
  ChatOutboxRow,
  type ChatMessageRowProps,
} from "@/components/chat/ChatMessageRow";
import {
  heldChatUnreadMarker,
  isChatNewestSeen,
  type ChatHeldUnreadMarker,
} from "@/components/chat/chatUnreadModel";
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
import { SkeletonGroup, Skeleton } from "@/components/ui/skeleton";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import {
  chatConversationKey,
  useChatChannels,
  useChatMessages,
  useChatUnread,
} from "@/hooks/queries/chat";
import { listChannelMessages, listThreadMessages } from "@/hooks/queries/chatApi";
import { useSendChatMessage } from "@/hooks/queries/chatMutations";
import { useMarkChatReadOnVisible } from "@/hooks/queries/chatReadState";
import { useOpenKazanLink } from "@/hooks/useOpenKazanLink";
import { useTranslation } from "@/i18n";
import { isApiError } from "@/lib/api";
import { linkedMessageInConversation } from "@/lib/kazanLinks";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import {
  CHAT_ERROR_CODES,
  type ChatMessageCursor,
  type ChatMessagePage,
  type ChatMessageResponse,
} from "@/lib/schemas/chat";

/** How long the message a jump brought into view stays marked. */
const JUMP_MARK_MS = 2500;

export type { ChatUnreadMarker };

/**
 * Remembers where the unread messages of a channel, or of one of its threads,
 * began when it opened, so the divider stays put while the reader reads them
 * and the position moves. Undefined until the unread state is loaded, also
 * while loading it fails (`heldChatUnreadMarker`): the list then waits to
 * open at the divider and to read, and `useChatUnreadStateError` tells the
 * reader.
 */
export function useChatUnreadMarkerAtOpen(
  channelId: string,
  threadId: string | null = null
): ChatUnreadMarker | undefined {
  const { data: unread } = useChatUnread();
  const conversationId: string = threadId ?? channelId;
  const [held, setHeld] = React.useState<ChatHeldUnreadMarker | null>(null);
  const next: ChatHeldUnreadMarker | null = heldChatUnreadMarker(held, conversationId, {
    loaded: !!unread,
    entry: threadId ? chatThreadUnread(unread, threadId) : chatChannelUnread(unread, channelId),
  });
  if (next !== held) setHeld(next);
  return next?.conversationId === conversationId ? next.marker : undefined;
}

export type ChatMessageListProps = Pick<
  ChatMessageRowProps,
  "onLongPress" | "onSwipeReply" | "onReactionPress" | "onOpenThread" | "onOpenAttachment"
> & {
  channelId: string;
  /** The thread whose replies are listed, or null for the channel itself. */
  threadId?: string | null;
  /**
   * A thread's root, shown above its oldest reply once that is loaded. It
   * takes reactions but no long press: it is acted on in its channel.
   */
  root?: ChatMessageResponse | null;
  /** The read position when the conversation opened; undefined while it loads. */
  unreadMarker: ChatUnreadMarker | undefined;
  /** Messages of others that arrived while the newest page was not loaded. */
  newMessageCount: number;
  clearNewMessageCount: () => void;
  /** The readable text of an unconfirmed message; the raw text without it. */
  outboxText?: (content: string) => string;
  /** A message to bring into view and mark; `onJumpHandled` is called once it is handled. */
  jumpTo?: ChatJumpTarget | null;
  onJumpHandled?: () => void;
  /**
   * The reader is deleting or leaving the channel: its "not found" is their
   * own doing and the screen is about to close, so it shows nothing.
   */
  goneExpected?: boolean;
};

const keyExtractor = (item: ChatListItem): string => `${item.kind}:${item.key}`;

function MessagesSkeleton() {
  return (
    <SkeletonGroup className="flex-1 justify-end gap-5 px-4 pb-4">
      {[0, 1, 2, 3, 4].map((index) => (
        <View key={index} className="flex-row gap-3">
          <Skeleton width={32} height={32} round />
          <View className="flex-1 gap-2">
            <Skeleton width={120} height={12} />
            <Skeleton width={index % 2 === 0 ? "90%" : "60%"} />
          </View>
        </View>
      ))}
    </SkeletonGroup>
  );
}

/**
 * A channel's or a thread's messages, newest at the bottom: an inverted list
 * that loads older pages when scrolled up and newer ones when the far end
 * was dropped, keeps the reader's place when messages arrive above the fold,
 * opens at the "New messages" divider with the unread bar above, marks the
 * conversation read once its newest message is in view (`isChatNewestSeen`),
 * and shows the jump pill while the reader is away from it. A jump target is
 * scrolled to when loaded; otherwise the pages are replaced by the page
 * around it, and the pill leads back to the newest messages.
 */
export function ChatMessageList({
  channelId,
  threadId = null,
  root = null,
  unreadMarker,
  newMessageCount,
  clearNewMessageCount,
  onLongPress,
  onSwipeReply,
  onReactionPress,
  onOpenThread,
  onOpenAttachment,
  outboxText,
  jumpTo = null,
  onJumpHandled,
  goneExpected = false,
}: ChatMessageListProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const meQuery = useMe(true);
  const me = meQuery.data;
  const meKnown: boolean = !meQuery.isPending;
  const marker: ChatUnreadMarker = unreadMarker ?? NO_CHAT_UNREAD_MARKER;
  const meId: string | undefined = me?.id;
  const { openTicket } = useHomeLinks();
  const { openLink: openKazanLink } = useOpenKazanLink();
  const channel = useChatChannels().data?.find((item) => item.id === channelId);
  const { retry, discard } = useSendChatMessage();
  const query = useChatMessages(channelId, threadId);
  const unread = useChatUnread().data;
  const {
    messages,
    outbox,
    atNewest,
    fetchPreviousPage,
    hasPreviousPage,
    isFetchingPreviousPage,
    isFetchPreviousPageError,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = query;
  const listRef = React.useRef<FlatList<ChatListItem>>(null);
  const { scrollToRow, onScrollToIndexFailed } = useScrollToChatRow(listRef);

  const loadNewer = React.useCallback(() => {
    if (!hasNextPage || isFetchingNextPage) return;
    fetchNextPage().catch((error: unknown) => {
      // The query keeps its pages; the next scroll to the bottom tries again.
      console.warn(
        "[chat] loading newer messages failed",
        error instanceof Error ? error.message : error
      );
    });
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);
  const { atBottom, onScroll, onScrollEnd, onContentSizeChange, onNewerAddedInPlace } =
    useChatAtBottom(loadNewer);
  const newestEnd = useChatNewestEnd({ atNewest, messages, onNewerAddedInPlace });
  const unreadState = useChatUnreadStateError(unreadMarker !== undefined);
  // Re-runs the jump once a load around its target has settled.
  const [aroundTick, setAroundTick] = React.useState<number>(0);

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
  const [jumpedId, setJumpedId] = React.useState<string | null>(null);
  // The jump whose surrounding page was loaded, so a message still missing then is not loaded for again.
  const loadedAroundSeq = React.useRef<number | null>(null);
  const loadingAround = React.useRef<boolean>(false);

  // A jump the list asks for itself: a link to, or a quote of, a message of this conversation.
  const [ownJump, setOwnJump] = React.useState<ChatJumpTarget | null>(null);
  const ownJumpSeq = React.useRef<number>(0);
  const jumpToOwn = React.useCallback((messageId: string) => {
    // Negative, so it never equals the sequence of a jump the screen asked for.
    ownJumpSeq.current -= 1;
    setOwnJump({ messageId, seq: ownJumpSeq.current });
  }, []);
  const activeJump: ChatJumpTarget | null = jumpTo ?? ownJump;

  // Replaces the pages by the page around a message: it may lie far from the loaded ones.
  const loadAround = React.useCallback(
    async (messageId: string): Promise<void> => {
      if (!orgId) throw new Error("No active organization");
      const cursor: ChatMessageCursor = { around: messageId };
      const page: ChatMessagePage = threadId
        ? await listThreadMessages(threadId, cursor)
        : await listChannelMessages(channelId, cursor);
      queryClient.setQueryData(chatConversationKey(orgId, channelId, threadId), {
        pages: [page],
        pageParams: [cursor],
      });
    },
    [orgId, threadId, channelId, queryClient]
  );

  const onUnreadLoadFailed = React.useCallback(() => toast.error(t("chatMessagesError")), [t]);
  const locateByTime = useChatLocateByReadTime(channelId, threadId);
  const unreadOpen = useChatUnreadOpen({
    ready: query.isSuccess && unreadMarker !== undefined && meKnown,
    marker,
    items,
    messages,
    atNewest,
    atBottom,
    jumping: !!jumpTo,
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
    live: true,
    loaded: query.isSuccess,
    atNewest,
    atBottom,
  });

  useMarkChatReadOnVisible({
    channelId,
    threadId,
    newestMessageId,
    newestSeen,
  });

  const openLink = React.useCallback(
    (url: string): void => {
      openKazanLink(url, (target) => {
        const messageId: string | null = linkedMessageInConversation(target, channel, threadId);
        if (!messageId) return false;
        jumpToOwn(messageId);
        return true;
      });
    },
    [openKazanLink, channel, threadId, jumpToOwn]
  );

  const renderItem = React.useCallback(
    ({ item }: ListRenderItemInfo<ChatListItem>) => {
      switch (item.kind) {
        case "message":
          return (
            <ChatMessageRow
              message={item.message}
              grouped={item.grouped}
              meId={meId}
              onOpenTicket={openTicket}
              onOpenLink={openLink}
              onLongPress={onLongPress}
              onSwipeReply={onSwipeReply}
              onReactionPress={onReactionPress}
              onOpenThread={onOpenThread}
              threadMentionCount={chatMessageThreadMentions(item.message, unread)}
              onOpenAttachment={onOpenAttachment}
              jumped={item.message.id === jumpedId}
              onOpenQuote={jumpToOwn}
            />
          );
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
      onLongPress,
      onSwipeReply,
      onReactionPress,
      onOpenThread,
      onOpenAttachment,
      outboxText,
      retry,
      discard,
      jumpedId,
      jumpToOwn,
      unread,
    ]
  );

  React.useEffect(() => {
    if (!activeJump || !query.isSuccess || loadingAround.current || !orgId) return;
    const target: ChatJumpTarget = activeJump;
    const finish = (): void => {
      loadedAroundSeq.current = null;
      setOwnJump(null);
      if (jumpTo) onJumpHandled?.();
    };
    const step: ChatJumpStep = chatJumpStep(
      items,
      target.messageId,
      root?.id ?? null,
      loadedAroundSeq.current === target.seq
    );
    switch (step.kind) {
      case "scroll":
        scrollToRow(step.index, 0.5, true);
        setJumpedId(target.messageId);
        finish();
        return;
      case "root":
        listRef.current?.scrollToEnd({ animated: true });
        setJumpedId(target.messageId);
        finish();
        return;
      case "missing":
        toast.error(t("chatMessageGone"));
        finish();
        return;
      case "loadAround":
        break;
    }
    loadingAround.current = true;
    loadAround(target.messageId)
      .then(() => {
        loadedAroundSeq.current = target.seq;
      })
      .catch((error: unknown) => {
        console.warn(
          "[chat] loading around a message failed",
          error instanceof Error ? error.message : error
        );
        toast.error(t("chatJumpFailed"));
        finish();
      })
      .finally(() => {
        loadingAround.current = false;
        setAroundTick((tick: number) => tick + 1);
      });
  }, [
    activeJump,
    jumpTo,
    query.isSuccess,
    items,
    root,
    orgId,
    loadAround,
    onJumpHandled,
    scrollToRow,
    t,
    aroundTick,
  ]);

  // The mark fades after a moment, like a highlight the reader has seen.
  React.useEffect(() => {
    if (!jumpedId) return;
    const timer: ReturnType<typeof setTimeout> = setTimeout(() => setJumpedId(null), JUMP_MARK_MS);
    return () => clearTimeout(timer);
  }, [jumpedId]);

  const header: React.ReactElement | null = root ? (
    <View className="border-b border-border pb-3">
      <ChatMessageRow
        message={root}
        grouped={false}
        meId={meId}
        onOpenTicket={openTicket}
        onOpenLink={openLink}
        onReactionPress={onReactionPress}
        onOpenAttachment={onOpenAttachment}
        hideThreadSummary
        jumped={root.id === jumpedId}
      />
    </View>
  ) : null;

  const loadOlder = React.useCallback(() => {
    if (!hasPreviousPage || isFetchingPreviousPage || isFetchPreviousPageError) return;
    fetchPreviousPage().catch((error: unknown) => {
      // The retry row at the top of the list shows the failure.
      console.warn(
        "[chat] loading older messages failed",
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
        .resetQueries({ queryKey: chatConversationKey(orgId, channelId, threadId) })
        .catch((error: unknown) => {
          console.warn(
            "[chat] reloading the newest messages failed",
            error instanceof Error ? error.message : error
          );
          toast.error(t("chatMessagesError"));
        });
    }
    listRef.current?.scrollToOffset({ offset: 0, animated: true });
  }, [atNewest, orgId, queryClient, channelId, threadId, clearNewMessageCount, t]);

  const unreadBar = useChatUnreadBar({
    channelId,
    threadId,
    marker,
    newestInView: newestSeen,
    atNewest,
    newestMessageId,
    jumpToLatest,
    releaseReadHold: unreadOpen.release,
  });

  if (query.isPending) return <MessagesSkeleton />;

  if (query.isError && messages.length === 0) {
    const gone: boolean = threadId
      ? isApiError(query.error, CHAT_ERROR_CODES.threadNotFound)
      : isApiError(query.error, CHAT_ERROR_CODES.channelNotFound);
    const goneTitle: string = threadId ? t("chatThreadNotFound") : t("chatChannelNotFound");
    if (gone && goneExpected) return <View className="flex-1" />;
    return (
      <View className="flex-1 justify-center">
        <EmptyState
          variant="error"
          title={gone ? goneTitle : t("chatMessagesError")}
          onAction={gone ? undefined : () => void query.refetch()}
          actionLabel={gone ? undefined : t("chatRetry")}
        />
      </View>
    );
  }

  if (items.length === 0) {
    const empty: React.ReactElement = threadId ? (
      <EmptyState
        icon={MessageSquare}
        title={t("chatThreadNoRepliesTitle")}
        message={t("chatThreadNoRepliesBody")}
      />
    ) : (
      <EmptyState
        icon={MessageSquare}
        title={t("chatNoMessagesTitle")}
        message={t("chatNoMessagesBody")}
      />
    );
    return (
      <View className="flex-1">
        {header}
        <View className="flex-1 justify-center">{empty}</View>
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
        initialNumToRender={20}
        windowSize={11}
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
                    "[chat] retrying older messages failed",
                    error instanceof Error ? error.message : error
                  );
                });
              }}
              className="mx-4 my-3"
            />
          ) : isFetchingPreviousPage ? (
            <ActivityIndicator className="py-3" color={colors.mutedForeground} />
          ) : hasPreviousPage ? null : (
            header
          )
        }
      />
      {showJump ? <JumpPill count={newMessageCount} onPress={jumpToLatest} /> : null}
    </View>
  );
}

import { useIsFocused } from "@react-navigation/native";
import { useQueryClient } from "@tanstack/react-query";
import * as Haptics from "expo-haptics";
import { Stack, useRouter } from "expo-router";
import {
  Ellipsis,
  EllipsisVertical,
  Filter,
  GitPullRequest,
  Pin,
  Search,
  Users,
} from "lucide-react-native";
import * as React from "react";
import { Platform, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import {
  chatChannelContentHidden,
  chatChannelManageActions,
} from "@/components/chat/chatChannelMenuModel";
import { chatAuthorName, chatMemberName } from "@/components/chat/chatDisplay";
import { ChatComposer, type ChatComposerMode } from "@/components/chat/ChatComposer";
import { TypingLine } from "@/components/chat/ChatListParts";
import { chatOutboxText } from "@/components/chat/chatMentions";
import { chatCanWrite, chatMessageActions } from "@/components/chat/chatMessageActions";
import { ChatThreadsButton } from "@/components/chat/ChatThreadsButton";
import { chatPurposeScreenFollow } from "@/components/chat/chatScreenModel";
import {
  clearChatScreenRequest,
  useChatScreenRequest,
  type ChatScreenRequest,
} from "@/components/chat/chatScreenRequests";
import { ConnectionBanner } from "@/components/chat/ConnectionBanner";
import { useChatChannelMenu } from "@/components/chat/useChatChannelMenu";
import { useKeyboardOverlap } from "@/components/chat/useKeyboardOverlap";
import { ActionSheet, type ActionSheetAction } from "@/components/kazan/ActionSheet";
import { EmptyState } from "@/components/kazan/EmptyState";
import { BAR_TITLE_MAX_FONT_SCALE } from "@/components/kazan/stackHeader";
import { usePullRequestFilter } from "@/components/pull-requests/pullRequestFilterStore";
import { PullRequestMessageList } from "@/components/pull-requests/PullRequestMessageList";
import { pullRequestFilterCount } from "@/components/pull-requests/pullRequestModel";
import { IconButton } from "@/components/ui/icon-button";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useChatChannelMembers, useChatChannels, useChatMeId } from "@/hooks/queries/chat";
import { findChatMessage } from "@/hooks/queries/chatCache";
import {
  useEditChatMessage,
  useSendChatMessage,
  useToggleChatReaction,
} from "@/hooks/queries/chatMutations";
import {
  useChatChannelRealtime,
  useChatTypingPublisher,
  useChatTypingUsers,
} from "@/hooks/queries/chatRealtime";
import {
  isPullRequestFilterActive,
  pullRequestKeys,
  useOpenPullRequestShareCount,
} from "@/hooks/queries/pullRequests";
import { useOrganizationRole } from "@/hooks/useOrganizationRole";
import { useTranslation } from "@/i18n";
import { chatLinkHref, type ChatLinkHref } from "@/lib/kazanLinks";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import { useRealtimeResync } from "@/lib/realtime";
import {
  ChatChannelPurpose,
  type ChatAttachmentResponse,
  type ChatChannelResponse,
  type ChatLocalFile,
  type ChatMemberResponse,
  type ChatMessageResponse,
  type ChatReactionSummaryResponse,
  type ChatSignal,
} from "@/lib/schemas/chat";
import type { PullRequestShareResponse } from "@/lib/schemas/pullRequests";

/** "Pull requests" over "# pr · 4 open shares"; the count is left out while unknown. */
function PullRequestsTitle({ channel }: { channel: ChatChannelResponse | undefined }) {
  const { t } = useTranslation();
  const openCount: number | undefined = useOpenPullRequestShareCount().data;
  const parts: string[] = [];
  if (channel) parts.push(`# ${channel.name}`);
  if (openCount !== undefined) parts.push(t("prOpenShares", { count: openCount }));
  return (
    <View accessibilityRole="header" className="items-center">
      <Text maxFontSizeMultiplier={BAR_TITLE_MAX_FONT_SCALE} variant="headline" numberOfLines={1}>
        {t("chatPullRequests")}
      </Text>
      {parts.length > 0 ? (
        <Text
          maxFontSizeMultiplier={BAR_TITLE_MAX_FONT_SCALE}
          variant="caption"
          numberOfLines={1}
          className="text-muted-foreground"
        >
          {parts.join(" · ")}
        </Text>
      ) : null}
    </View>
  );
}

/**
 * The organization's pull requests channel: shares as full cards between the
 * team's messages, narrowed by status and person in the filter sheet, with
 * the composer and Share PR below. Signals of the channel topic keep the
 * chat's list current and refresh the narrowed list, the open count and the
 * Person options.
 */
export default function PullRequestsScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const channels = useChatChannels();
  const channel: ChatChannelResponse | undefined = channels.data?.find(
    (item) => item.purpose === ChatChannelPurpose.PULL_REQUESTS
  );
  const channelId: string = channel?.id ?? "";
  // The channel last shown here, which a delete from its menu removes from
  // the list before the screen closes; while that request is on the way, a
  // list that already shows it gone shows only the header.
  const lastChannelId = React.useRef<string>(channelId);
  if (channel) lastChannelId.current = channel.id;
  const channelMenu = useChatChannelMenu(lastChannelId.current);
  const hidden: boolean = chatChannelContentHidden(
    channelMenu.removal,
    lastChannelId.current,
    channel
  );
  // An owner or admin gave the channel shown here another purpose: it now
  // opens where that purpose is shown, and this screen never shows its
  // "no channel" state in between.
  const follow: ChatLinkHref | null = hidden
    ? null
    : chatPurposeScreenFollow(
        ChatChannelPurpose.PULL_REQUESTS,
        lastChannelId.current,
        channels.data
      );
  const filter = usePullRequestFilter(orgId);
  const filterCount: number = pullRequestFilterCount(filter);
  const membersQuery = useChatChannelMembers(channel);
  const members: ChatMemberResponse[] | undefined = membersQuery.data;
  const role: string | null = useOrganizationRole();
  const canWrite: boolean = chatCanWrite(role);
  const meId: string | undefined = useChatMeId();
  const { send } = useSendChatMessage();
  const { mutateAsync: editMessage } = useEditChatMessage();
  const { mutate: toggleReaction } = useToggleChatReaction();
  const publishTyping = useChatTypingPublisher(channelId);
  const typing = useChatTypingUsers(channelId);
  const typingOnSignal = typing.onSignal;

  // The chat applies each signal to its own list; the narrowed list, the
  // open count and the Person options are this screen's and are re-read.
  const onSignal = React.useCallback(
    (signal: ChatSignal) => {
      typingOnSignal(signal);
      if (!orgId) return;
      const changesMessages: boolean =
        signal.type === "MESSAGE_CREATED" ||
        signal.type === "MESSAGE_UPDATED" ||
        signal.type === "MESSAGE_DELETED";
      if (!changesMessages) return;
      Promise.all([
        queryClient.invalidateQueries({ queryKey: pullRequestKeys.channelsAll(orgId) }),
        queryClient.invalidateQueries({ queryKey: pullRequestKeys.openCount(orgId) }),
        signal.type === "MESSAGE_UPDATED"
          ? undefined
          : queryClient.invalidateQueries({ queryKey: pullRequestKeys.authorsAll(orgId) }),
      ]).catch((error: unknown) => {
        // The lists keep what they show; the next signal or reopening reads them again.
        console.warn(
          "[pull-requests] refresh after a signal failed",
          error instanceof Error ? error.message : error
        );
      });
    },
    [typingOnSignal, orgId, queryClient]
  );
  // Signals missed while the socket was down are made up by reading again.
  useRealtimeResync(() => {
    if (!orgId) return;
    queryClient
      .invalidateQueries({ queryKey: pullRequestKeys.all(orgId) })
      .catch((error: unknown) => {
        console.warn(
          "[pull-requests] refresh after reconnecting failed",
          error instanceof Error ? error.message : error
        );
      });
  });
  const { newMessageCount, clearNewMessageCount } = useChatChannelRealtime(
    channel ? channelId : null,
    onSignal
  );
  const [mode, setMode] = React.useState<ChatComposerMode | null>(null);
  const { overlap, viewRef, onLayout } = useKeyboardOverlap();
  const request: ChatScreenRequest | null = useChatScreenRequest(channelId);
  const focused: boolean = useIsFocused();

  // Replaced once this screen is on top again, so the sheet that changed the
  // channel closes first and Back still returns to Chat home; only once, as
  // a refetched list asks again before this screen is gone.
  const followed = React.useRef<boolean>(false);
  React.useEffect(() => {
    if (!follow || !focused || followed.current) return;
    followed.current = true;
    router.replace(follow);
  }, [follow, focused, router]);
  const [menuOpen, setMenuOpen] = React.useState<boolean>(false);

  // A sheet over this screen asked for a reply, an edit, a thread or a
  // message. A message is shown in the plain channel screen, which can page
  // to any message; a new screen is pushed once the sheet has closed.
  React.useEffect(() => {
    if (!request) return;
    const navigates: boolean =
      request.kind === "openThread" ||
      request.kind === "newThread" ||
      request.kind === "switch" ||
      request.kind === "jump";
    if (navigates && !focused) return;
    clearChatScreenRequest(request);
    switch (request.kind) {
      case "jump":
        router.push({
          pathname: "/chat/channel/[channelId]",
          params: request.threadId
            ? { channelId, message: request.messageId, thread: request.threadId }
            : { channelId, message: request.messageId },
        });
        return;
      case "openThread":
        router.push({
          pathname: "/chat/thread/[threadId]",
          params: { threadId: request.threadId, channelId },
        });
        return;
      case "newThread":
        router.push({
          pathname: "/chat/new-thread",
          params: { channelId, messageId: request.messageId },
        });
        return;
      case "switch": {
        // A channel created from this screen's menu opens over it, on the
        // screen of its purpose.
        const created: ChatChannelResponse | undefined = channels.data?.find(
          (item) => item.id === request.toChannelId
        );
        router.push(
          created
            ? chatLinkHref(created, null, null)
            : { pathname: "/chat/channel/[channelId]", params: { channelId: request.toChannelId } }
        );
        return;
      }
      case "newChannel":
      case "manageMembers":
        // The pull requests channel has no switcher, so these are never asked here.
        console.warn(`[pull-requests] ignored a ${request.kind} request`);
        return;
      case "reply":
      case "edit":
        break;
    }
    const message: ChatMessageResponse | undefined = orgId
      ? findChatMessage(queryClient, orgId, request.messageId)
      : undefined;
    if (!message || message.deleted) {
      toast.error(t("chatMessageGone"));
      return;
    }
    setMode(
      request.kind === "reply"
        ? { type: "reply", message, authorName: chatAuthorName(message, t("chatUnknownMember")) }
        : { type: "edit", message }
    );
  }, [request, focused, router, channelId, channels.data, orgId, queryClient, t]);

  // A swipe to the right answers a message, as the actions sheet's Reply does.
  const onSwipeReply = React.useCallback(
    (message: ChatMessageResponse) => {
      setMode({
        type: "reply",
        message,
        authorName: chatAuthorName(message, t("chatUnknownMember")),
      });
    },
    [t]
  );

  const onOpenThread = React.useCallback(
    (threadId: string) => {
      router.push({ pathname: "/chat/thread/[threadId]", params: { threadId, channelId } });
    },
    [router, channelId]
  );

  const onLongPress = React.useCallback(
    (message: ChatMessageResponse) => {
      if (chatMessageActions(message, { meId, role }).length === 0) return;
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch((error: unknown) => {
        // Haptics are a courtesy: the sheet opens without them, so the failure is only logged.
        console.warn(
          "[pull-requests] haptics failed",
          error instanceof Error ? error.message : error
        );
      });
      router.push({ pathname: "/chat/message-actions", params: { messageId: message.id } });
    },
    [meId, role, router]
  );

  const onOpenAttachment = React.useCallback(
    (message: ChatMessageResponse, attachment: ChatAttachmentResponse) => {
      router.push({
        pathname: "/chat/image/[attachmentId]",
        params: { attachmentId: attachment.id, messageId: message.id },
      });
    },
    [router]
  );

  const onReactionPress = React.useCallback(
    (message: ChatMessageResponse, reaction: ChatReactionSummaryResponse) => {
      toggleReaction({ messageId: message.id, emoji: reaction.emoji, add: !reaction.reactedByMe });
    },
    [toggleReaction]
  );

  const onEditShare = React.useCallback(
    (share: PullRequestShareResponse, messageId: string) => {
      router.push({ pathname: "/chat/share-pr", params: { shareId: share.id, messageId } });
    },
    [router]
  );

  const outboxText = React.useCallback(
    (content: string): string => chatOutboxText(content, members ?? [], t("chatUnknownMember")),
    [members, t]
  );

  const typingNames: string[] = React.useMemo(() => {
    const byId = new Map<string, ChatMemberResponse>(
      (members ?? []).map((member) => [member.userId, member])
    );
    return typing.typingUserIds.flatMap((userId) => {
      const member: ChatMemberResponse | undefined = byId.get(userId);
      const name: string = member ? chatMemberName(member) : "";
      return name ? [name] : [];
    });
  }, [members, typing.typingUserIds]);

  const onSend = React.useCallback(
    (content: string, replyToMessageId: string | null, files: ChatLocalFile[]) => {
      send({ content, channelId, threadId: null, replyToMessageId, files });
    },
    [send, channelId]
  );

  const onSaveEdit = React.useCallback(
    async (messageId: string, content: string): Promise<void> => {
      await editMessage({ messageId, content });
    },
    [editMessage]
  );

  const menuActions: ActionSheetAction[] = [
    {
      label: t("chatSearchOpen"),
      icon: Search,
      onPress: () => router.push({ pathname: "/chat/search", params: { channelId } }),
    },
    {
      label: t("chatMembersOpen"),
      icon: Users,
      onPress: () => router.push({ pathname: "/chat/members", params: { channelId } }),
    },
    {
      label: t("chatPinsOpen"),
      icon: Pin,
      onPress: () => router.push({ pathname: "/chat/pins", params: { channelId } }),
    },
    // New channel, Change channel and Delete channel, as on every other channel.
    ...(channel ? channelMenu.rows(channel, chatChannelManageActions(channel, role)) : []),
  ];

  const header = (
    <Stack.Screen
      options={{
        headerTitle: () => <PullRequestsTitle channel={channel} />,
        headerRight: () =>
          channel && !hidden ? (
            <View className="flex-row items-center">
              <ChatThreadsButton channelId={channelId} />
              <IconButton
                icon={Filter}
                label={t("prFilterOpen")}
                badge={filterCount > 0 ? filterCount : null}
                badgeTone="primary"
                onPress={() => router.push({ pathname: "/chat/pr-filter", params: { channelId } })}
              />
              <IconButton
                icon={Platform.OS === "android" ? EllipsisVertical : Ellipsis}
                label={t("chatChannelMenu")}
                onPress={() => setMenuOpen(true)}
              />
            </View>
          ) : null,
      }}
    />
  );

  if (hidden || follow) {
    return <View className="flex-1 bg-screen">{header}</View>;
  }

  if (channels.isPending) {
    return (
      <View className="flex-1 gap-4 bg-screen p-4">
        {header}
        <Skeleton width="100%" height={160} />
        <Skeleton width="100%" height={160} />
      </View>
    );
  }

  if (channels.isError && !channels.data) {
    return (
      <View className="flex-1 justify-center bg-screen">
        {header}
        <EmptyState
          variant="error"
          title={t("chatChannelsError")}
          onAction={() => void channels.refetch()}
          actionLabel={t("chatRetry")}
        />
      </View>
    );
  }

  if (!channel || !channel.member) {
    return (
      <View className="flex-1 justify-center bg-screen">
        {header}
        <EmptyState
          icon={GitPullRequest}
          title={t("prNoChannelTitle")}
          message={t("prNoChannelBody")}
        />
      </View>
    );
  }

  return (
    <View
      ref={viewRef}
      onLayout={onLayout}
      className="flex-1 bg-screen"
      style={{ paddingBottom: overlap }}
    >
      {header}
      <ConnectionBanner />
      {/* Clearing the filter opens the channel's own list afresh, at its unread divider. */}
      <PullRequestMessageList
        key={`${channelId}:${isPullRequestFilterActive(filter)}`}
        channelId={channelId}
        filter={filter}
        newMessageCount={newMessageCount}
        clearNewMessageCount={clearNewMessageCount}
        outboxText={outboxText}
        onLongPress={filterCount > 0 ? undefined : onLongPress}
        onReactionPress={canWrite ? onReactionPress : undefined}
        onOpenThread={onOpenThread}
        onOpenAttachment={onOpenAttachment}
        onSwipeReply={canWrite ? onSwipeReply : undefined}
        onEdit={onEditShare}
        goneExpected={channelMenu.goneExpected}
      />
      <TypingLine names={typingNames} />
      {/* Under the tab bar on iOS the composer needs the safe area; above the keyboard it does not. */}
      <SafeAreaView edges={overlap > 0 ? [] : ["bottom"]}>
        <ChatComposer
          key={channelId}
          channelName={channel.name}
          canWrite={canWrite}
          mode={mode}
          onCancelMode={() => setMode(null)}
          onSend={onSend}
          onSaveEdit={onSaveEdit}
          onTyping={publishTyping}
          members={members}
          membersError={membersQuery.isError}
          onRetryMembers={() => void membersQuery.refetch()}
          action={{
            label: t("prSharePr"),
            icon: GitPullRequest,
            onPress: () => router.push("/chat/share-pr"),
          }}
        />
      </SafeAreaView>
      <ActionSheet
        visible={menuOpen}
        onClose={() => setMenuOpen(false)}
        title={channel.name}
        actions={menuActions}
        cancelLabel={t("chatCancel")}
      />
    </View>
  );
}

import { useIsFocused } from "@react-navigation/native";
import { useQueryClient } from "@tanstack/react-query";
import * as Haptics from "expo-haptics";
import { Stack, useRouter } from "expo-router";
import {
  CalendarDays,
  Ellipsis,
  EllipsisVertical,
  Filter,
  Newspaper,
  Pin,
  Search,
  SquarePen,
  UserX,
  Users,
} from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable, View } from "react-native";
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
import { setUpdateFilter, useStoredUpdateFilter } from "@/components/updates/updateFilterStore";
import { UpdateMessageList } from "@/components/updates/UpdateMessageList";
import {
  NO_UPDATE_FILTER,
  canSeeNotPosted,
  resolveUpdateFilter,
  splitMissingMembers,
  updateFilterCount,
} from "@/components/updates/updateModel";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { IconButton } from "@/components/ui/icon-button";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import {
  useChatChannelMembers,
  useChatChannels,
  useChatMeId,
  useChatMembers,
} from "@/hooks/queries/chat";
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
import { updateKeys, useMissingUpdates } from "@/hooks/queries/updates";
import { useDates } from "@/hooks/useDates";
import { useOrganizationRole } from "@/hooks/useOrganizationRole";
import { useTranslation } from "@/i18n";
import { businessToday } from "@/lib/dates";
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
import type { UpdateChannelFilter, UpdateResponse } from "@/lib/schemas/updates";

/**
 * "Daily updates" over "3 of 8 posted · 5 missing" for an owner or admin,
 * which opens Not posted, or over "# daily" for anyone else and while the
 * count is unknown.
 */
function UpdatesTitle({
  channel,
  today,
  showNotPosted,
  onOpenNotPosted,
}: {
  channel: ChatChannelResponse | undefined;
  today: string;
  showNotPosted: boolean;
  onOpenNotPosted: () => void;
}) {
  const { t } = useTranslation();
  const report = useMissingUpdates(today, showNotPosted).data;
  const summary: string | null =
    showNotPosted && report && report.workday
      ? (() => {
          const missing: number = splitMissingMembers(report).notPosted.length;
          return t("updPostedSummary", {
            posted: Math.max(0, report.expectedCount - missing),
            expected: report.expectedCount,
            missing,
          });
        })()
      : null;
  const subtitle: string | null = summary ?? (channel ? `# ${channel.name}` : null);
  const content = (
    <View className="items-center">
      <Text maxFontSizeMultiplier={BAR_TITLE_MAX_FONT_SCALE} variant="headline" numberOfLines={1}>
        {t("chatDailyUpdates")}
      </Text>
      {subtitle ? (
        <Text
          maxFontSizeMultiplier={BAR_TITLE_MAX_FONT_SCALE}
          variant="caption"
          numberOfLines={1}
          className={summary ? "text-link" : "text-muted-foreground"}
        >
          {subtitle}
        </Text>
      ) : null}
    </View>
  );
  if (!summary) return <View accessibilityRole="header">{content}</View>;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityHint={t("updNotPostedOpen")}
      onPress={onOpenNotPosted}
      className="active:opacity-60"
    >
      {content}
    </Pressable>
  );
}

/**
 * The day and person a past view shows, opening the filter sheet, with
 * "Back to today" beside it to return to the live channel.
 */
function FilterPill({
  filter,
  today,
  onPress,
  onReset,
}: {
  filter: UpdateChannelFilter;
  today: string;
  onPress: () => void;
  onReset: () => void;
}) {
  const { t } = useTranslation();
  const dates = useDates();
  const members: ChatMemberResponse[] | undefined = useChatMembers().data;
  const parts: string[] = [];
  if (filter.day !== today) {
    parts.push(t("updStartOfDay", { day: dates.longDate(filter.day) ?? filter.day }));
  } else {
    parts.push(t("dateToday"));
  }
  if (filter.personId) {
    const member: ChatMemberResponse | undefined = members?.find(
      (item) => item.userId === filter.personId
    );
    parts.push(member ? chatMemberName(member) : t("chatUnknownMember"));
  }
  return (
    <View className="flex-row items-center justify-center gap-1 px-4 pb-1 pt-2">
      <Pressable
        accessibilityRole="button"
        accessibilityHint={t("updFilterOpen")}
        onPress={onPress}
        className="shrink flex-row items-center gap-1.5 rounded-full border border-border bg-card px-3 py-1.5 active:opacity-60"
      >
        <Icon as={CalendarDays} size={14} className="text-muted-foreground" />
        <Text variant="caption" numberOfLines={1} className="shrink font-sans-medium">
          {parts.join(" · ")}
        </Text>
      </Pressable>
      <Button variant="ghost" size="sm" onPress={onReset}>
        {t("updBackToToday")}
      </Button>
    </View>
  );
}

/**
 * The organization's daily updates channel, one business day at a time:
 * updates as full cards between the team's messages, narrowed by day and
 * person in the filter sheet. The composer stays under a past day or a
 * person too, and a message sent there brings the reader back to today,
 * where it shows; Post update opens the update form. An owner or admin sees who has not posted
 * and can nudge them. Signals of the channel topic refresh the day's list,
 * Not posted and the Person options.
 */
export default function UpdatesScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const today: string = businessToday();
  const channels = useChatChannels();
  const channel: ChatChannelResponse | undefined = channels.data?.find(
    (item) => item.purpose === ChatChannelPurpose.DAILY_UPDATES
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
        ChatChannelPurpose.DAILY_UPDATES,
        lastChannelId.current,
        channels.data
      );
  const stored = useStoredUpdateFilter(orgId);
  const filter: UpdateChannelFilter = React.useMemo(
    () => resolveUpdateFilter(stored, today),
    [stored, today]
  );
  const filterCount: number = updateFilterCount(filter, today);
  const live: boolean = filterCount === 0;
  const membersQuery = useChatChannelMembers(channel);
  const members: ChatMemberResponse[] | undefined = membersQuery.data;
  const role: string | null = useOrganizationRole();
  const canWrite: boolean = chatCanWrite(role);
  const showNotPosted: boolean = canSeeNotPosted(role);
  const meId: string | undefined = useChatMeId();
  const { send } = useSendChatMessage();
  const { mutateAsync: editMessage } = useEditChatMessage();
  const { mutate: toggleReaction } = useToggleChatReaction();
  const publishTyping = useChatTypingPublisher(channelId);
  const typing = useChatTypingUsers(channelId);
  const typingOnSignal = typing.onSignal;

  // The chat applies each signal to its own list; the day's list, Not
  // posted and the Person options are this screen's and are re-read.
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
        queryClient.invalidateQueries({ queryKey: updateKeys.channelsAll(orgId) }),
        queryClient.invalidateQueries({ queryKey: updateKeys.missingAll(orgId) }),
        signal.type === "MESSAGE_UPDATED"
          ? undefined
          : queryClient.invalidateQueries({ queryKey: updateKeys.authorsAll(orgId) }),
      ]).catch((error: unknown) => {
        // The lists keep what they show; the next signal or reopening reads them again.
        console.warn(
          "[updates] refresh after a signal failed",
          error instanceof Error ? error.message : error
        );
      });
    },
    [typingOnSignal, orgId, queryClient]
  );
  // Signals missed while the socket was down are made up by reading again.
  useRealtimeResync(() => {
    if (!orgId) return;
    queryClient.invalidateQueries({ queryKey: updateKeys.all(orgId) }).catch((error: unknown) => {
      console.warn(
        "[updates] refresh after reconnecting failed",
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
        // The daily updates channel has no switcher, so these are never asked here.
        console.warn(`[updates] ignored a ${request.kind} request`);
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
        console.warn("[updates] haptics failed", error instanceof Error ? error.message : error);
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

  const onEditUpdate = React.useCallback(
    (update: UpdateResponse, messageId: string) => {
      router.push({ pathname: "/chat/new-update", params: { updateId: update.id, messageId } });
    },
    [router]
  );

  const openFilter = React.useCallback(() => {
    router.push({ pathname: "/chat/updates-filter", params: { channelId } });
  }, [router, channelId]);

  const openNotPosted = React.useCallback(() => {
    router.push("/chat/not-posted");
  }, [router]);

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
      // A new message belongs to today, which a past day or another person's view does not show.
      if (!live && orgId) setUpdateFilter(orgId, NO_UPDATE_FILTER);
    },
    [send, channelId, live, orgId]
  );

  const onSaveEdit = React.useCallback(
    async (messageId: string, content: string): Promise<void> => {
      await editMessage({ messageId, content });
    },
    [editMessage]
  );

  const menuActions: ActionSheetAction[] = [
    ...(showNotPosted
      ? [{ label: t("updNotPostedTitle"), icon: UserX, onPress: openNotPosted }]
      : []),
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
        headerTitle: () => (
          <UpdatesTitle
            channel={channel}
            today={today}
            showNotPosted={showNotPosted && !!channel}
            onOpenNotPosted={openNotPosted}
          />
        ),
        headerRight: () =>
          channel && !hidden ? (
            <View className="flex-row items-center">
              <ChatThreadsButton channelId={channelId} />
              <IconButton
                icon={Filter}
                label={t("updFilterOpen")}
                badge={filterCount > 0 ? filterCount : null}
                badgeTone="primary"
                onPress={openFilter}
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
        <Skeleton width="100%" height={200} />
        <Skeleton width="100%" height={200} />
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
          icon={Newspaper}
          title={t("updNoChannelTitle")}
          message={t("updNoChannelBody")}
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
      {live ? null : (
        <FilterPill
          filter={filter}
          today={today}
          onPress={openFilter}
          onReset={() => {
            if (orgId) setUpdateFilter(orgId, NO_UPDATE_FILTER);
          }}
        />
      )}
      <UpdateMessageList
        key={`${channelId}:${filter.day}:${filter.personId ?? ""}`}
        channelId={channelId}
        filter={filter}
        today={today}
        newMessageCount={newMessageCount}
        clearNewMessageCount={clearNewMessageCount}
        outboxText={outboxText}
        onLongPress={onLongPress}
        onReactionPress={canWrite ? onReactionPress : undefined}
        onOpenThread={onOpenThread}
        onOpenAttachment={onOpenAttachment}
        onSwipeReply={canWrite ? onSwipeReply : undefined}
        onEdit={onEditUpdate}
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
            label: t("updPostUpdate"),
            icon: SquarePen,
            onPress: () => router.push("/chat/new-update"),
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

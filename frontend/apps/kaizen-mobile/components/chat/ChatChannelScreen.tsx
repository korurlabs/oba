import { useIsFocused } from "@react-navigation/native";
import { useQueryClient } from "@tanstack/react-query";
import * as Haptics from "expo-haptics";
import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import { ChevronDown, Ellipsis, EllipsisVertical, Lock, Search } from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import {
  chatChannelContentHidden,
  chatChannelMenuActions,
} from "@/components/chat/chatChannelMenuModel";
import { chatAuthorName, chatMemberName } from "@/components/chat/chatDisplay";
import { chatOnlineCount } from "@/components/chat/chatMembersModel";
import { ChatComposer, type ChatComposerMode } from "@/components/chat/ChatComposer";
import type { ChatJumpTarget } from "@/components/chat/chatFoundModel";
import { TypingLine } from "@/components/chat/ChatListParts";
import { chatOutboxText } from "@/components/chat/chatMentions";
import { chatCanWrite, chatMessageActions } from "@/components/chat/chatMessageActions";
import {
  ChatMessageList,
  useChatUnreadMarkerAtOpen,
  type ChatUnreadMarker,
} from "@/components/chat/ChatMessageList";
import { chatShareTicketDraft, type ChatSharedTicket } from "@/components/chat/chatShare";
import { ChatThreadsButton } from "@/components/chat/ChatThreadsButton";
import { chatChannelScreenFollow, type ChatShownChannel } from "@/components/chat/chatScreenModel";
import {
  clearChatScreenRequest,
  useChatScreenRequest,
  type ChatScreenRequest,
} from "@/components/chat/chatScreenRequests";
import { ConnectionBanner } from "@/components/chat/ConnectionBanner";
import { useChatChannelMenu } from "@/components/chat/useChatChannelMenu";
import { useKeyboardOverlap } from "@/components/chat/useKeyboardOverlap";
import { ActionSheet, type ActionSheetAction } from "@/components/kaizen/ActionSheet";
import { EmptyState } from "@/components/kaizen/EmptyState";
import { BAR_TITLE_MAX_FONT_SCALE } from "@/components/kaizen/stackHeader";
import { Icon } from "@/components/ui/icon";
import { IconButton } from "@/components/ui/icon-button";
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
import { useOrganizationRole } from "@/hooks/useOrganizationRole";
import { useTranslation } from "@/i18n";
import type { ChatLinkHref } from "@/lib/kaizenLinks";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import {
  ChatChannelPurpose,
  ChatChannelVisibility,
  type ChatAttachmentResponse,
  type ChatChannelResponse,
  type ChatLocalFile,
  type ChatMemberResponse,
  type ChatMessageResponse,
  type ChatReactionSummaryResponse,
} from "@/lib/schemas/chat";

/**
 * The title of the navigation bar: "# name" over how many of its members are
 * online, which presence keeps current; the member count until the members
 * load. On a general channel of the Chat tab it opens the channel switcher;
 * daily updates and pull requests are reached only from ChatHome.
 */
function ChannelTitle({
  channel,
  members,
  canSwitch,
}: {
  channel: ChatChannelResponse | undefined;
  members: ChatMemberResponse[] | undefined;
  canSwitch: boolean;
}) {
  const { t } = useTranslation();
  const router = useRouter();
  if (!channel) return null;
  const prefix: string = channel.visibility === ChatChannelVisibility.PRIVATE ? "" : "# ";
  const switchable: boolean = canSwitch && channel.purpose === ChatChannelPurpose.GENERAL;
  const online: number | null = chatOnlineCount(members);
  return (
    <Pressable
      disabled={!switchable}
      accessibilityRole={switchable ? "button" : "header"}
      accessibilityHint={switchable ? t("chatSwitcherHint") : undefined}
      hitSlop={8}
      onPress={() => router.push({ pathname: "/chat/channels", params: { channelId: channel.id } })}
      className="items-center active:opacity-60"
    >
      <View className="flex-row items-center gap-1">
        <Text maxFontSizeMultiplier={BAR_TITLE_MAX_FONT_SCALE} variant="headline" numberOfLines={1}>
          {`${prefix}${channel.name}`}
        </Text>
        {switchable ? <Icon as={ChevronDown} size={14} className="text-muted-foreground" /> : null}
      </View>
      <Text
        maxFontSizeMultiplier={BAR_TITLE_MAX_FONT_SCALE}
        variant="caption"
        className="text-muted-foreground"
      >
        {online === null
          ? t("chatMembers", { count: channel.memberCount })
          : t("chatOnlineCount", { count: online })}
      </Text>
    </Pressable>
  );
}

type ChatChannelScreenProps = {
  /**
   * The ticket being shared: the composer starts with its chip. The screen
   * then sits in the app stack above the tabs, so everything that would
   * open a screen of the Chat tab (switcher, threads, search, members,
   * pins, message actions, the file viewer) is left out, and Back returns
   * to where the share started. Swipe to reply, reactions and ticket and
   * web links still work.
   */
  share?: ChatSharedTicket | null;
};

/**
 * A channel: its live message list, who is typing and the composer. Signals
 * of the channel topic keep the list current; a message of this device shows
 * at once and is confirmed by the server's answer or its echo. A `message`
 * link param shows that message, and with `thread` it opens in that thread
 * over this channel; search results and pins arrive as jump requests. The
 * channel is the `channelId` param of the route that renders it: the Chat
 * tab's channel route, or the Share to chat route.
 */
export function ChatChannelScreen({ share = null }: ChatChannelScreenProps) {
  const { t } = useTranslation();
  const router = useRouter();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const params = useLocalSearchParams<{ channelId: string; message?: string; thread?: string }>();
  const channelId: string = params.channelId ?? "";
  const linkedMessageId: string | null = params.message || null;
  const linkedThreadId: string | null = params.thread || null;
  const channels = useChatChannels();
  const channel: ChatChannelResponse | undefined = channels.data?.find(
    (item) => item.id === channelId
  );
  // Once the reader's delete or leave succeeded the screen is on its way back
  // to Chat home and reads nothing of the channel any more, whose reads would
  // now answer "not found"; while the request is on the way, a list that
  // already shows the channel gone hides the content too. The title keeps
  // the last known channel.
  const channelMenu = useChatChannelMenu(channelId);
  const hidden: boolean = chatChannelContentHidden(channelMenu.removal, channelId, channel);
  const lastChannel = React.useRef<ChatChannelResponse | undefined>(channel);
  if (channel) lastChannel.current = channel;
  const shownChannel: ChatChannelResponse | undefined = hidden ? lastChannel.current : channel;
  // The daily updates and pull requests channels have screens of their own:
  // a channel given one of those purposes while shown here, or one just
  // created with it, opens there. A channel opened at a linked message stays
  // here, so the reader keeps that message in view.
  const shownBefore = React.useRef<ChatShownChannel | null>(null);
  const follow: ChatLinkHref | null =
    share || hidden ? null : chatChannelScreenFollow(shownBefore.current, channel, linkedMessageId);
  if (channel && !follow) {
    shownBefore.current = { channelId: channel.id, purpose: channel.purpose };
  }
  const membersQuery = useChatChannelMembers(hidden ? undefined : channel);
  const members: ChatMemberResponse[] | undefined = membersQuery.data;
  const role: string | null = useOrganizationRole();
  const meId: string | undefined = useChatMeId();
  const { send } = useSendChatMessage();
  const { mutateAsync: editMessage } = useEditChatMessage();
  const { mutate: toggleReaction } = useToggleChatReaction();
  const publishTyping = useChatTypingPublisher(channelId);
  const typing = useChatTypingUsers(channelId);
  const { newMessageCount, clearNewMessageCount } = useChatChannelRealtime(
    channelId,
    typing.onSignal
  );
  const unreadMarker: ChatUnreadMarker | undefined = useChatUnreadMarkerAtOpen(channelId);
  const [mode, setMode] = React.useState<ChatComposerMode | null>(null);
  const shareDraft = React.useMemo(
    () => (share ? chatShareTicketDraft(share) : undefined),
    [share]
  );
  const { overlap, viewRef, onLayout } = useKeyboardOverlap();
  const request: ChatScreenRequest | null = useChatScreenRequest(channelId);
  const focused: boolean = useIsFocused();

  // Replaced once this screen is on top again, so the sheet that changed the
  // channel closes first; only once, as a refetched list asks again before
  // this screen is gone.
  const followed = React.useRef<boolean>(false);
  React.useEffect(() => {
    if (!follow || !focused || followed.current) return;
    followed.current = true;
    router.replace(follow);
  }, [follow, focused, router]);
  const [menuOpen, setMenuOpen] = React.useState<boolean>(false);
  const [jumpTarget, setJumpTarget] = React.useState<ChatJumpTarget | null>(null);
  const jumpSeq = React.useRef<number>(0);
  const handledLink = React.useRef<string | null>(null);

  const jumpTo = React.useCallback((messageId: string) => {
    jumpSeq.current += 1;
    setJumpTarget({ messageId, seq: jumpSeq.current });
  }, []);
  const onJumpHandled = React.useCallback(() => setJumpTarget(null), []);

  const openThreadAt = React.useCallback(
    (threadId: string, messageId: string | null) => {
      router.push({
        pathname: "/chat/thread/[threadId]",
        params: messageId ? { threadId, channelId, message: messageId } : { threadId, channelId },
      });
    },
    [router, channelId]
  );

  // A link to a message: shown here, or in its thread once this screen is on top.
  React.useEffect(() => {
    if (!linkedMessageId && !linkedThreadId) return;
    const link: string = `${channelId}:${linkedThreadId ?? ""}:${linkedMessageId ?? ""}`;
    if (handledLink.current === link) return;
    if (linkedThreadId) {
      if (!focused) return;
      handledLink.current = link;
      openThreadAt(linkedThreadId, linkedMessageId);
      return;
    }
    handledLink.current = link;
    if (linkedMessageId) jumpTo(linkedMessageId);
  }, [channelId, linkedMessageId, linkedThreadId, focused, openThreadAt, jumpTo]);

  // A sheet over this screen asked for a reply, an edit, another channel or
  // a new screen. A new screen is pushed once the sheet has closed and this
  // screen is on top again, so it does not open under the closing sheet.
  React.useEffect(() => {
    // Requests come from sheets of the Chat tab, which a shared channel does not open.
    if (!request || share) return;
    const navigates: boolean =
      request.kind === "openThread" ||
      request.kind === "newThread" ||
      request.kind === "newChannel" ||
      request.kind === "manageMembers" ||
      request.kind === "jump";
    if (navigates && !focused) return;
    clearChatScreenRequest(request);
    switch (request.kind) {
      case "switch":
        setMode(null);
        setJumpTarget(null);
        router.setParams({ channelId: request.toChannelId, message: undefined, thread: undefined });
        return;
      case "jump":
        if (request.threadId) openThreadAt(request.threadId, request.messageId);
        else jumpTo(request.messageId);
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
      case "newChannel":
        router.push({ pathname: "/chat/new-channel", params: { channelId } });
        return;
      case "manageMembers":
        router.push({
          pathname: "/chat/channel-members",
          params: { channelId: request.membersChannelId },
        });
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
  }, [request, share, focused, router, channelId, orgId, queryClient, t, openThreadAt, jumpTo]);

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
        console.warn("[chat] haptics failed", error instanceof Error ? error.message : error);
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

  // A message of this device shows its mentions by name until the server's copy arrives.
  const outboxText = React.useCallback(
    (content: string): string => chatOutboxText(content, members ?? [], t("chatUnknownMember")),
    [members, t]
  );

  const canReact: boolean = chatCanWrite(role);

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
  const onReactionPress = React.useCallback(
    (message: ChatMessageResponse, reaction: ChatReactionSummaryResponse) => {
      toggleReaction({ messageId: message.id, emoji: reaction.emoji, add: !reaction.reactedByMe });
    },
    [toggleReaction]
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

  const menuActions: ActionSheetAction[] = channel
    ? channelMenu.rows(channel, chatChannelMenuActions(channel, role))
    : [];

  const header = (
    <Stack.Screen
      options={{
        headerTitle: () => (
          <ChannelTitle channel={shownChannel} members={members} canSwitch={!share && !hidden} />
        ),
        headerRight: () =>
          channel?.member && !share && !hidden ? (
            <View className="flex-row items-center">
              <ChatThreadsButton channelId={channelId} />
              <IconButton
                icon={Search}
                label={t("chatSearchOpen")}
                onPress={() => router.push({ pathname: "/chat/search", params: { channelId } })}
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

  if (hidden) {
    return <View className="flex-1 bg-screen">{header}</View>;
  }

  if (channel && !channel.member) {
    return (
      <View className="flex-1 justify-center bg-screen">
        {header}
        <EmptyState icon={Lock} title={t("chatNotMember")} message={t("chatNotMemberBody")} />
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
      <ChatMessageList
        key={channelId}
        channelId={channelId}
        unreadMarker={unreadMarker}
        newMessageCount={newMessageCount}
        clearNewMessageCount={clearNewMessageCount}
        onLongPress={share ? undefined : onLongPress}
        onSwipeReply={canReact ? onSwipeReply : undefined}
        onReactionPress={canReact ? onReactionPress : undefined}
        onOpenThread={share ? undefined : onOpenThread}
        onOpenAttachment={share ? undefined : onOpenAttachment}
        outboxText={outboxText}
        jumpTo={jumpTarget}
        onJumpHandled={onJumpHandled}
        goneExpected={channelMenu.goneExpected}
      />
      <TypingLine names={typingNames} />
      {/* Under the tab bar on iOS the composer needs the safe area; above the keyboard it does not. */}
      <SafeAreaView edges={overlap > 0 ? [] : ["bottom"]}>
        <ChatComposer
          key={channelId}
          channelName={channel?.name ?? ""}
          canWrite={role !== "GUEST"}
          mode={mode}
          onCancelMode={() => setMode(null)}
          onSend={onSend}
          onSaveEdit={onSaveEdit}
          onTyping={publishTyping}
          members={members}
          membersError={membersQuery.isError}
          onRetryMembers={() => void membersQuery.refetch()}
          initialDraft={shareDraft}
        />
      </SafeAreaView>
      <ActionSheet
        visible={menuOpen}
        onClose={() => setMenuOpen(false)}
        title={channel ? channel.name : undefined}
        actions={menuActions}
        cancelLabel={t("chatCancel")}
      />
    </View>
  );
}

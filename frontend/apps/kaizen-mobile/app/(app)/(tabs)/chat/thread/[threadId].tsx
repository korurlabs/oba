import { useQueryClient } from "@tanstack/react-query";
import * as Haptics from "expo-haptics";
import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import {
  Archive,
  ArchiveRestore,
  Ellipsis,
  EllipsisVertical,
  Lock,
  LockOpen,
  LogIn,
  LogOut,
  MessageSquareOff,
  Pencil,
  Users,
  type LucideIcon,
} from "lucide-react-native";
import * as React from "react";
import { Platform, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { chatAuthorName, chatMemberName, chatUserDisplayName } from "@/components/chat/chatDisplay";
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
import {
  clearChatScreenRequest,
  useChatScreenRequest,
  type ChatScreenRequest,
} from "@/components/chat/chatScreenRequests";
import {
  CHAT_THREAD_ACTION_STATUS,
  chatThreadMenuActions,
  type ChatThreadMenuActionId,
} from "@/components/chat/chatThreadModel";
import { ConnectionBanner } from "@/components/chat/ConnectionBanner";
import { useKeyboardOverlap } from "@/components/chat/useKeyboardOverlap";
import { ActionSheet, type ActionSheetAction } from "@/components/kaizen/ActionSheet";
import { EmptyState } from "@/components/kaizen/EmptyState";
import { BAR_TITLE_MAX_FONT_SCALE } from "@/components/kaizen/stackHeader";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { IconButton } from "@/components/ui/icon-button";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import {
  useChatChannelMembers,
  useChatChannels,
  useChatMeId,
  useChatThread,
} from "@/hooks/queries/chat";
import { findChatMessage } from "@/hooks/queries/chatCache";
import {
  useChangeChatThreadStatus,
  useChatThreadMembership,
  useEditChatMessage,
  useSendChatMessage,
  useToggleChatReaction,
} from "@/hooks/queries/chatMutations";
import {
  useChatChannelRealtime,
  useChatTypingPublisher,
  useChatTypingUsers,
} from "@/hooks/queries/chatRealtime";
import { useDates } from "@/hooks/useDates";
import { useOrganizationRole } from "@/hooks/useOrganizationRole";
import { useTranslation, type TranslationKey } from "@/i18n";
import { isApiError } from "@/lib/api";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import {
  CHAT_ERROR_CODES,
  ChatThreadStatus,
  threadRootAsMessage,
  type ChatAttachmentResponse,
  type ChatChannelResponse,
  type ChatLocalFile,
  type ChatMemberResponse,
  type ChatMessageResponse,
  type ChatReactionSummaryResponse,
  type ChatThreadResponse,
} from "@/lib/schemas/chat";

const MENU_ROWS: Record<ChatThreadMenuActionId, { label: TranslationKey; icon: LucideIcon }> = {
  join: { label: "chatThreadJoin", icon: LogIn },
  leave: { label: "chatThreadLeave", icon: LogOut },
  edit: { label: "chatThreadEdit", icon: Pencil },
  close: { label: "chatThreadClose", icon: Lock },
  reopen: { label: "chatThreadReopen", icon: LockOpen },
  archive: { label: "chatThreadArchive", icon: Archive },
  unarchive: { label: "chatThreadUnarchive", icon: ArchiveRestore },
};

/**
 * The navigation bar title: the thread's title over "6 replies · 4 members",
 * or, once it is closed, over who closed it and when.
 */
function ThreadTitle({ thread }: { thread: ChatThreadResponse | undefined }) {
  const { t } = useTranslation();
  const dates = useDates();
  if (!thread) return null;
  const closed: boolean = thread.status === ChatThreadStatus.CLOSED;
  const subtitle: string = closed
    ? [
        thread.statusChangedBy
          ? t("chatThreadClosedBy", {
              name: chatUserDisplayName(thread.statusChangedBy, t("chatUnknownMember")),
            })
          : t("chatThreadsClosed"),
        thread.statusChangedAt ? dates.timestamp(thread.statusChangedAt) : null,
      ]
        .filter(Boolean)
        .join(" · ")
    : [
        t("chatThreadReplies", { count: thread.messageCount }),
        t("chatMembers", { count: thread.members.length }),
      ].join(" · ");
  return (
    // The bar centres the title between Back and the two buttons on the right,
    // so it stays narrower than the space left of those buttons.
    <View accessibilityRole="header" className="max-w-40 items-center">
      <Text maxFontSizeMultiplier={BAR_TITLE_MAX_FONT_SCALE} variant="headline" numberOfLines={1}>
        {thread.title}
      </Text>
      <Text
        maxFontSizeMultiplier={BAR_TITLE_MAX_FONT_SCALE}
        variant="caption"
        numberOfLines={1}
        className="text-muted-foreground"
      >
        {subtitle}
      </Text>
    </View>
  );
}

/**
 * Takes the place of the composer in a closed thread. It always says who may
 * reopen it; its creator and the organization's owners and admins also find
 * Reopen here.
 */
function ClosedNotice({
  canReopen,
  reopening,
  onReopen,
}: {
  canReopen: boolean;
  reopening: boolean;
  onReopen: () => void;
}) {
  const { t } = useTranslation();
  return (
    <View className="items-center gap-2 border-t border-border bg-card px-4 py-3">
      <View className="flex-row items-center gap-2">
        <Icon as={Lock} size={14} className="text-muted-foreground" />
        <Text variant="subhead" className="font-sans-semibold">
          {t("chatThreadClosed")}
        </Text>
      </View>
      <Text variant="footnote" className="text-center text-muted-foreground">
        {t("chatThreadClosedHint")}
      </Text>
      {canReopen ? (
        <Button
          size="sm"
          variant="secondary"
          icon={LockOpen}
          loading={reopening}
          onPress={onReopen}
        >
          {t("chatThreadReopen")}
        </Button>
      ) : null}
    </View>
  );
}

/**
 * A thread: its root above the live replies, who is typing and its own
 * composer. Signals of the channel topic for this thread keep it current; a
 * closed thread shows why it takes no reply instead of the composer. The
 * thread menu joins or leaves it and, for its creator and the
 * organization's owners and admins, opens its settings and changes its
 * status. A `message` param
 * (a search result, a pin or a link) brings that reply into view.
 */
export default function ThreadScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const params = useLocalSearchParams<{ threadId: string; channelId: string; message?: string }>();
  const threadId: string = params.threadId ?? "";
  const threadQuery = useChatThread(threadId || null);
  const thread: ChatThreadResponse | undefined = threadQuery.data;
  const root: ChatMessageResponse | null = React.useMemo(
    () => (thread ? threadRootAsMessage(thread) : null),
    [thread]
  );
  // The channel is known from the link before the thread loads, so the list starts at once.
  const channelId: string = thread?.channelId ?? params.channelId ?? "";
  const channels = useChatChannels();
  const channel: ChatChannelResponse | undefined = channels.data?.find(
    (item) => item.id === channelId
  );
  const membersQuery = useChatChannelMembers(channel);
  const members: ChatMemberResponse[] | undefined = membersQuery.data;
  const role: string | null = useOrganizationRole();
  const meId: string | undefined = useChatMeId();
  const { send } = useSendChatMessage();
  const { mutateAsync: editMessage } = useEditChatMessage();
  const { mutate: toggleReaction } = useToggleChatReaction();
  const { mutate: changeStatus, isPending: changingStatus } = useChangeChatThreadStatus();
  const { mutate: setMembership } = useChatThreadMembership();
  const publishTyping = useChatTypingPublisher(channelId || null, threadId);
  const typing = useChatTypingUsers(channelId || null, threadId);
  const { newMessageCount, clearNewMessageCount } = useChatChannelRealtime(
    channelId || null,
    typing.onSignal,
    threadId
  );
  // A reader who is no thread member has no position in it and sees no divider.
  const unreadMarker: ChatUnreadMarker | undefined = useChatUnreadMarkerAtOpen(channelId, threadId);
  const [mode, setMode] = React.useState<ChatComposerMode | null>(null);
  const [menuOpen, setMenuOpen] = React.useState<boolean>(false);
  const { overlap, viewRef, onLayout } = useKeyboardOverlap();
  const request: ChatScreenRequest | null = useChatScreenRequest(channelId, threadId);
  const linkedMessageId: string | null = params.message || null;
  const [jumpTarget, setJumpTarget] = React.useState<ChatJumpTarget | null>(null);
  const handledLink = React.useRef<string | null>(null);
  const onJumpHandled = React.useCallback(() => setJumpTarget(null), []);

  React.useEffect(() => {
    if (!linkedMessageId || handledLink.current === linkedMessageId) return;
    handledLink.current = linkedMessageId;
    setJumpTarget({ messageId: linkedMessageId, seq: 1 });
  }, [linkedMessageId]);

  const closed: boolean = thread?.status === ChatThreadStatus.CLOSED;
  const canWrite: boolean = chatCanWrite(role);

  // The message actions over this screen asked for a reply or an edit in the thread.
  React.useEffect(() => {
    if (!request || (request.kind !== "reply" && request.kind !== "edit")) return;
    clearChatScreenRequest(request);
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
  }, [request, orgId, queryClient, t]);

  // A thread closed meanwhile takes no reply or edit; the open one is dropped.
  React.useEffect(() => {
    if (closed) setMode(null);
  }, [closed]);

  const onLongPress = React.useCallback(
    (message: ChatMessageResponse) => {
      if (chatMessageActions(message, { meId, role, closed }).length === 0) return;
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch((error: unknown) => {
        // Haptics are a courtesy: the sheet opens without them, so the failure is only logged.
        console.warn("[chat] haptics failed", error instanceof Error ? error.message : error);
      });
      router.push({ pathname: "/chat/message-actions", params: { messageId: message.id } });
    },
    [meId, role, closed, router]
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
      send({ content, channelId, threadId, replyToMessageId, files });
    },
    [send, channelId, threadId]
  );

  const onSaveEdit = React.useCallback(
    async (messageId: string, content: string): Promise<void> => {
      await editMessage({ messageId, content });
    },
    [editMessage]
  );

  const menuIds: ChatThreadMenuActionId[] = thread ? chatThreadMenuActions(thread, canWrite) : [];
  const menuActions: ActionSheetAction[] = menuIds.map((id) => ({
    label: t(MENU_ROWS[id].label),
    icon: MENU_ROWS[id].icon,
    onPress: () => {
      if (id === "join" || id === "leave") {
        setMembership({ threadId, member: id === "join" });
        return;
      }
      if (id === "edit") {
        router.push({ pathname: "/chat/edit-thread", params: { threadId } });
        return;
      }
      changeStatus({ threadId, status: CHAT_THREAD_ACTION_STATUS[id] });
    },
  }));

  const header = (
    <Stack.Screen
      options={{
        headerTitle: () => <ThreadTitle thread={thread} />,
        headerRight: () =>
          thread ? (
            <View className="flex-row items-center">
              <IconButton
                icon={Users}
                label={t("chatMembersOpen")}
                onPress={() =>
                  router.push({ pathname: "/chat/members", params: { threadId, channelId } })
                }
              />
              {menuActions.length > 0 ? (
                <IconButton
                  icon={Platform.OS === "android" ? EllipsisVertical : Ellipsis}
                  label={t("chatThreadMenu")}
                  onPress={() => setMenuOpen(true)}
                />
              ) : null}
            </View>
          ) : null,
      }}
    />
  );

  if (!threadId || (threadQuery.isError && !thread)) {
    const gone: boolean =
      !threadId || isApiError(threadQuery.error, CHAT_ERROR_CODES.threadNotFound);
    return (
      <View className="flex-1 justify-center bg-screen">
        {header}
        <EmptyState
          variant="error"
          icon={gone ? MessageSquareOff : undefined}
          title={gone ? t("chatThreadNotFound") : t("chatThreadError")}
          onAction={gone ? undefined : () => void threadQuery.refetch()}
          actionLabel={gone ? undefined : t("chatRetry")}
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
      {channelId ? (
        <ChatMessageList
          key={threadId}
          channelId={channelId}
          threadId={threadId}
          root={root}
          unreadMarker={unreadMarker}
          newMessageCount={newMessageCount}
          clearNewMessageCount={clearNewMessageCount}
          onLongPress={onLongPress}
          onSwipeReply={canWrite && !closed ? onSwipeReply : undefined}
          onOpenAttachment={onOpenAttachment}
          onReactionPress={canWrite ? onReactionPress : undefined}
          outboxText={outboxText}
          jumpTo={jumpTarget}
          onJumpHandled={onJumpHandled}
        />
      ) : (
        <View className="flex-1" />
      )}
      <TypingLine names={typingNames} />
      {thread?.status === ChatThreadStatus.ARCHIVED ? (
        <Text variant="footnote" className="px-4 pb-1 text-center text-muted-foreground">
          {t("chatThreadArchivedHint")}
        </Text>
      ) : null}
      {/* Without the keyboard the composer needs the bottom safe area; above the keyboard it does not. */}
      <SafeAreaView edges={overlap > 0 ? [] : ["bottom"]}>
        {closed && thread ? (
          <ClosedNotice
            canReopen={thread.canManage}
            reopening={changingStatus}
            onReopen={() => changeStatus({ threadId, status: ChatThreadStatus.ACTIVE })}
          />
        ) : (
          <ChatComposer
            key={threadId}
            channelName={channel?.name ?? ""}
            placeholder={t("chatThreadComposerPlaceholder")}
            canWrite={role !== "GUEST"}
            mode={mode}
            onCancelMode={() => setMode(null)}
            onSend={onSend}
            onSaveEdit={onSaveEdit}
            onTyping={publishTyping}
            members={members}
            membersError={membersQuery.isError}
            onRetryMembers={() => void membersQuery.refetch()}
          />
        )}
      </SafeAreaView>
      <ActionSheet
        visible={menuOpen}
        onClose={() => setMenuOpen(false)}
        title={thread?.title}
        actions={menuActions}
        cancelLabel={t("chatCancel")}
      />
    </View>
  );
}

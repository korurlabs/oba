import { useQueryClient } from "@tanstack/react-query";
import * as Clipboard from "expo-clipboard";
import { useLocalSearchParams } from "expo-router";
import {
  Copy,
  Link2,
  MessageSquarePlus,
  MessagesSquare,
  Pencil,
  Pin,
  PinOff,
  Reply,
  SmilePlus,
  type LucideIcon,
} from "lucide-react-native";
import * as React from "react";
import { Alert, View } from "react-native";

import { chatAuthorName, chatMessageSnippet, chatMessageText } from "@/components/chat/chatDisplay";
import {
  chatMessageActions,
  chatMessageWebLink,
  type ChatMessageActionId,
} from "@/components/chat/chatMessageActions";
import { ChatEmojiPicker, ChatReactionPicker } from "@/components/chat/ChatReactionPicker";
import { requestChatScreen } from "@/components/chat/chatScreenRequests";
import { BottomSheet, useSheet } from "@/components/kaizen/BottomSheet";
import { EmptyState } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { Avatar } from "@/components/ui/avatar";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useChatChannels, useChatMeId } from "@/hooks/queries/chat";
import { chatKeys, findChatMessage } from "@/hooks/queries/chatCache";
import {
  useDeleteChatMessage,
  usePinChatMessage,
  useToggleChatReaction,
} from "@/hooks/queries/chatMutations";
import { useDates } from "@/hooks/useDates";
import { useOrganizationRole } from "@/hooks/useOrganizationRole";
import { useTranslation, type TranslationKey } from "@/i18n";
import { ENV } from "@/lib/env";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import {
  ChatThreadStatus,
  type ChatChannelResponse,
  type ChatMessageResponse,
  type ChatThreadResponse,
} from "@/lib/schemas/chat";

/**
 * The long-press sheet of a message: a preview, the quick reactions and the
 * actions. The preview names the sheet, so it has no title.
 */
export default function MessageActionsSheet() {
  const { t } = useTranslation();
  return (
    <BottomSheet title="" cancelLabel={t("chatClose")} scroll>
      <MessageActionsContent />
    </BottomSheet>
  );
}

/** Row label and icon of each action; thread and pin depend on the message. */
function actionRow(
  id: Exclude<ChatMessageActionId, "delete">,
  message: ChatMessageResponse
): { label: TranslationKey; icon: LucideIcon } {
  switch (id) {
    case "reply":
      return { label: "chatActionReply", icon: Reply };
    case "react":
      return { label: "chatActionReact", icon: SmilePlus };
    case "thread":
      return message.thread
        ? { label: "chatActionOpenThread", icon: MessagesSquare }
        : { label: "chatActionCreateThread", icon: MessageSquarePlus };
    case "pin":
      return message.pinnedAt
        ? { label: "chatActionUnpin", icon: PinOff }
        : { label: "chatActionPin", icon: Pin };
    case "copyText":
      return { label: "chatActionCopy", icon: Copy };
    case "copyLink":
      return { label: "chatActionCopyLink", icon: Link2 };
    case "edit":
      return { label: "chatActionEdit", icon: Pencil };
  }
}

function MessagePreview({ message }: { message: ChatMessageResponse }) {
  const { t } = useTranslation();
  const dates = useDates();
  const author: string = chatAuthorName(message, t("chatUnknownMember"));
  const time: string | null = dates.time(message.createdAt);
  return (
    <View className="mx-4 mb-4 flex-row gap-3 rounded-lg bg-card p-3">
      <Avatar name={author} size="md" />
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
        </View>
        <Text variant="footnote" numberOfLines={3}>
          {chatMessageSnippet(message, t("chatUnknownMember"), 240)}
        </Text>
      </View>
    </View>
  );
}

function MessageActionsContent() {
  const { t } = useTranslation();
  const { dismiss } = useSheet();
  const params = useLocalSearchParams<{ messageId: string }>();
  const messageId: string = params.messageId ?? "";
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const meId: string | undefined = useChatMeId();
  const role: string | null = useOrganizationRole();
  const channels: ChatChannelResponse[] | undefined = useChatChannels().data;
  const { mutate: toggleReaction } = useToggleChatReaction();
  const { mutate: pin } = usePinChatMessage();
  const { mutate: deleteMessage } = useDeleteChatMessage();
  const [pickerOpen, setPickerOpen] = React.useState<boolean>(false);

  // Read once: the sheet closes after every action, so a later change of
  // the message never needs to show here.
  const [message] = React.useState<ChatMessageResponse | undefined>(() =>
    orgId ? findChatMessage(queryClient, orgId, messageId) : undefined
  );

  if (!message) {
    return <EmptyState variant="error" title={t("chatMessageGone")} className="mt-8" />;
  }

  // A reply in a closed thread is read-only; the open thread screen holds its status.
  const thread: ChatThreadResponse | undefined =
    orgId && message.threadId
      ? queryClient.getQueryData<ChatThreadResponse>(chatKeys.threadDetail(orgId, message.threadId))
      : undefined;
  const closed: boolean = thread?.status === ChatThreadStatus.CLOSED;
  const actions: ChatMessageActionId[] = chatMessageActions(message, { meId, role, closed });
  const mine = new Set<string>(
    message.reactions.filter((reaction) => reaction.reactedByMe).map((reaction) => reaction.emoji)
  );

  const react = (emoji: string): void => {
    toggleReaction({ messageId: message.id, emoji, add: !mine.has(emoji) });
    dismiss();
  };

  const copy = (text: string, done: TranslationKey): void => {
    Clipboard.setStringAsync(text)
      .then(() => toast.success(t(done)))
      .catch((error: unknown) => {
        console.warn("[chat] copying failed", error instanceof Error ? error.message : error);
        toast.error(t("chatCopyFailed"));
      });
  };

  const run = (id: Exclude<ChatMessageActionId, "delete">): void => {
    switch (id) {
      case "reply":
      case "edit":
        requestChatScreen({
          kind: id,
          channelId: message.channelId,
          threadId: message.threadId ?? null,
          messageId: message.id,
        });
        dismiss();
        return;
      case "thread":
        requestChatScreen(
          message.thread
            ? {
                kind: "openThread",
                channelId: message.channelId,
                threadId: message.thread.threadId,
              }
            : { kind: "newThread", channelId: message.channelId, messageId: message.id }
        );
        dismiss();
        return;
      case "react":
        setPickerOpen(true);
        return;
      case "pin":
        pin({ messageId: message.id, pinned: !message.pinnedAt });
        dismiss();
        return;
      case "copyText":
        copy(chatMessageText(message, t("chatUnknownMember")), "chatCopied");
        dismiss();
        return;
      case "copyLink": {
        const channel: ChatChannelResponse | undefined = channels?.find(
          (candidate) => candidate.id === message.channelId
        );
        if (!channel) {
          console.error(
            "[chat] copy link: the message's channel is not in the channel list",
            message.channelId
          );
          toast.error(t("chatCopyFailed"));
          return;
        }
        copy(chatMessageWebLink(ENV.kaizenWebUrl, message, channel), "chatLinkCopied");
        dismiss();
        return;
      }
    }
  };

  const confirmDelete = (): void => {
    Alert.alert(t("chatDeleteConfirmTitle"), t("chatDeleteConfirmBody"), [
      { text: t("chatCancel"), style: "cancel" },
      {
        text: t("chatDelete"),
        style: "destructive",
        onPress: () => {
          // A refusal is toasted by the delete mutation.
          deleteMessage({
            messageId: message.id,
            channelId: message.channelId,
            threadId: message.threadId ?? null,
          });
          dismiss();
        },
      },
    ]);
  };

  const rows = actions.filter(
    (id): id is Exclude<ChatMessageActionId, "delete"> => id !== "delete"
  );

  return (
    <View className="pb-6 pt-2">
      <MessagePreview message={message} />
      {actions.includes("react") ? (
        <ChatReactionPicker mine={mine} onPick={react} onMore={() => setPickerOpen(true)} />
      ) : null}
      {rows.length > 0 ? (
        <ListSection>
          {rows.map((id) => {
            const row = actionRow(id, message);
            return (
              <ListRow
                key={id}
                title={t(row.label)}
                icon={row.icon}
                accessory="none"
                onPress={() => run(id)}
              />
            );
          })}
        </ListSection>
      ) : null}
      {actions.includes("delete") ? (
        <ListSection>
          <ListRow
            title={t("chatActionDelete")}
            destructive
            center
            accessory="none"
            onPress={confirmDelete}
          />
        </ListSection>
      ) : null}
      <ChatEmojiPicker open={pickerOpen} onClose={() => setPickerOpen(false)} onPick={react} />
    </View>
  );
}

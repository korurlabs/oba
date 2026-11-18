import { useLocalSearchParams } from "expo-router";
import { Hash, Lock, Plus } from "lucide-react-native";
import * as React from "react";
import { View } from "react-native";

import { ChatChannelReorderList } from "@/components/chat/ChatChannelReorderList";
import { chatCanModerate } from "@/components/chat/chatMessageActions";
import { chatChannelUnread, chatSwitcherChannels } from "@/components/chat/chatScreenModel";
import { requestChatScreen } from "@/components/chat/chatScreenRequests";
import { BottomSheet, useSheet } from "@/components/kazan/BottomSheet";
import { EmptyState, InlineError } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import { Input } from "@/components/ui/input";
import { SkeletonRow } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useChatUnread } from "@/hooks/queries/chat";
import { useChatChannelOrder } from "@/hooks/queries/chatChannelOrder";
import { useOrganizationRole } from "@/hooks/useOrganizationRole";
import { useTranslation } from "@/i18n";
import { ChatChannelVisibility, type ChatChannelResponse } from "@/lib/schemas/chat";

/**
 * The channel switcher, opened from a channel's title: the general channels
 * in the reader's order with their unread counts, a search, and Edit order,
 * where a channel is dragged by its grip; the order is saved for every
 * device.
 * Owners and admins also find New channel here, and a private channel they
 * are not on opens its members, where they can add themselves; the channel
 * itself stays closed until they do.
 */
export default function ChannelSwitcherSheet() {
  const { t } = useTranslation();
  const [editing, setEditing] = React.useState<boolean>(false);
  return (
    <BottomSheet
      title={t("chatSectionChats")}
      cancelLabel={t("chatClose")}
      confirmLabel={editing ? t("chatDone") : t("chatEditOrder")}
      onConfirm={() => setEditing((value) => !value)}
      scroll
    >
      <ChannelSwitcherContent editing={editing} />
    </BottomSheet>
  );
}

function ChannelSwitcherContent({ editing }: { editing: boolean }) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const { dismiss } = useSheet();
  const params = useLocalSearchParams<{ channelId: string }>();
  const currentId: string = params.channelId ?? "";
  const { query, channels, move } = useChatChannelOrder();
  const unread = useChatUnread().data;
  const canManage: boolean = chatCanModerate(useOrganizationRole());
  const [search, setSearch] = React.useState<string>("");

  // Moving works on the whole list, so the search is set aside while editing.
  const listed: ChatChannelResponse[] = React.useMemo(
    () => chatSwitcherChannels(channels, editing ? "" : search),
    [channels, editing, search]
  );

  const open = (channel: ChatChannelResponse): void => {
    if (channel.id !== currentId) {
      requestChatScreen({ kind: "switch", channelId: currentId, toChannelId: channel.id });
    }
    dismiss();
  };

  // The members sheet opens over the channel screen once this sheet has closed.
  const openMembers = (channel: ChatChannelResponse): void => {
    requestChatScreen({
      kind: "manageMembers",
      channelId: currentId,
      membersChannelId: channel.id,
    });
    dismiss();
  };

  const body = (() => {
    if (query.isPending) {
      return (
        <ListSection>
          {[0, 1, 2].map((index) => (
            <SkeletonRow key={index} />
          ))}
        </ListSection>
      );
    }
    if (query.isError && channels.length === 0) {
      return (
        <InlineError
          message={t("chatChannelsError")}
          onRetry={() => void query.refetch()}
          className="mx-4 mt-4"
        />
      );
    }
    if (listed.length === 0) {
      return <EmptyState title={t("chatSwitcherEmpty")} className="mt-8" />;
    }
    if (editing) {
      return (
        <ChatChannelReorderList header={t("chatSwitcherTitle")} channels={listed} onMove={move} />
      );
    }
    return (
      <ListSection header={t("chatSwitcherTitle")}>
        {listed.map((channel) => {
          const isPrivate: boolean = channel.visibility === ChatChannelVisibility.PRIVATE;
          const channelUnread = chatChannelUnread(unread, channel.id);
          const opensMembers: boolean = !channel.member && canManage && currentId !== "";
          let onPress: (() => void) | undefined;
          if (channel.member) onPress = () => open(channel);
          else if (opensMembers) onPress = () => openMembers(channel);
          return (
            <ListRow
              key={channel.id}
              icon={isPrivate ? Lock : Hash}
              title={channel.name}
              subtitle={
                channel.member
                  ? undefined
                  : t(opensMembers ? "chatChannelNotMemberManage" : "chatPrivateNotMember")
              }
              badge={channelUnread?.unread}
              mentions={channelUnread?.mentionCount}
              unread={(channelUnread?.unread ?? 0) > 0}
              accessory="none"
              active={channel.id === currentId}
              disabled={!onPress}
              onPress={onPress}
            />
          );
        })}
      </ListSection>
    );
  })();

  return (
    <View className="pb-6 pt-2">
      {editing ? (
        <Text variant="footnote" className="px-4 text-muted-foreground">
          {t("chatEditOrderHint")}
        </Text>
      ) : (
        <View className="px-4">
          <Input
            value={search}
            onChangeText={setSearch}
            placeholder={t("chatSwitcherSearch")}
            placeholderTextColor={colors.mutedForeground}
            accessibilityLabel={t("chatSwitcherSearch")}
            autoCorrect={false}
            autoCapitalize="none"
            returnKeyType="search"
            clearButtonMode="while-editing"
          />
        </View>
      )}
      {body}
      {canManage && !editing && currentId ? (
        <ListSection>
          <ListRow
            title={t("chatNewChannel")}
            icon={Plus}
            accessory="none"
            onPress={() => {
              requestChatScreen({ kind: "newChannel", channelId: currentId });
              dismiss();
            }}
          />
        </ListSection>
      ) : null}
    </View>
  );
}

import { useLocalSearchParams } from "expo-router";
import { UserMinus } from "lucide-react-native";
import * as React from "react";
import { View } from "react-native";

import { chatMemberName } from "@/components/chat/chatDisplay";
import {
  chatChannelAddCandidates,
  chatMemberPickBlocked,
  chatMemberPickSubtitle,
} from "@/components/chat/chatMembersModel";
import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { EmptyState, InlineError } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { IconButton } from "@/components/ui/icon-button";
import { SkeletonRow } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import {
  useChatChannelMembers,
  useChatChannels,
  useChatMeId,
  useChatMembers,
} from "@/hooks/queries/chat";
import {
  useAddChatChannelMembers,
  useRemoveChatChannelMember,
} from "@/hooks/queries/chatMutations";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation } from "@/i18n";
import { isApiError } from "@/lib/api";
import {
  CHAT_CHANNEL_MAX_MEMBER_IDS,
  CHAT_ERROR_CODES,
  type ChatChannelResponse,
  type ChatMemberResponse,
} from "@/lib/schemas/chat";

/**
 * The member list of a private channel (`channelId`), for owners and admins:
 * add members of the organization, remove members at once without a
 * confirmation, as the web does. An admin who is not on the list reads
 * neither the channel nor its members until they add themselves. The hooks
 * toast every result and refusal, so a toast survives the sheet closing.
 */
export default function ChannelMembersSheet() {
  const { t } = useTranslation();
  const params = useLocalSearchParams<{ channelId: string }>();
  const channelId: string = params.channelId ?? "";
  const meId: string | undefined = useChatMeId();
  const channelsQuery = useChatChannels();
  const channel: ChatChannelResponse | undefined = channelsQuery.data?.find(
    (known) => known.id === channelId
  );
  const membersQuery = useChatChannelMembers(channel);
  const organizationQuery = useChatMembers();
  const addMembers = useAddChatChannelMembers();
  const removeMember = useRemoveChatChannelMember();
  const online = useOnlineWriteGuard();
  const [selected, setSelected] = React.useState<string[]>([]);

  const members: ChatMemberResponse[] | undefined = membersQuery.data;
  const candidates: ChatMemberResponse[] = React.useMemo(
    () => chatChannelAddCandidates(organizationQuery.data, members),
    [organizationQuery.data, members]
  );
  // The server hides the list from an admin who is not on it, as it hides the channel's content.
  const hiddenFromMe: boolean =
    !!channel &&
    !channel.member &&
    membersQuery.isError &&
    isApiError(membersQuery.error, CHAT_ERROR_CODES.channelNotFound);

  const add = (userIds: string[]): void => {
    if (!channel || userIds.length === 0 || !online()) return;
    addMembers.mutate(
      { channelId: channel.id, userIds },
      // Only clears the picks; the hook toasts the result and any refusal.
      { onSuccess: () => setSelected([]) }
    );
  };

  const remove = (member: ChatMemberResponse): void => {
    if (!channel || !online()) return;
    removeMember.mutate({
      channelId: channel.id,
      userId: member.userId,
      memberName: chatMemberName(member) || t("chatUnknownMember"),
    });
  };

  const toggle = (userId: string, checked: boolean): void => {
    setSelected((current: string[]) => {
      if (!checked) return current.filter((known) => known !== userId);
      // The server takes at most this many people in one request.
      if (current.length >= CHAT_CHANNEL_MAX_MEMBER_IDS) return current;
      return [...current, userId];
    });
  };

  const memberList = (() => {
    if (hiddenFromMe) {
      return (
        <View className="items-center gap-3 px-6 py-6">
          <Text variant="footnote" className="text-center text-muted-foreground">
            {t("chatChannelMembersNotOnList")}
          </Text>
          <Button
            size="sm"
            disabled={!meId}
            loading={addMembers.isPending}
            onPress={() => meId && add([meId])}
          >
            {t("chatChannelMembersAddMe")}
          </Button>
        </View>
      );
    }
    if (membersQuery.isError && !members) {
      return (
        <InlineError
          message={t("chatChannelMembersLoadError")}
          onRetry={() => void membersQuery.refetch()}
          className="mx-4 mt-4"
        />
      );
    }
    if (!members) {
      return (
        <ListSection>
          {[0, 1, 2].map((index) => (
            <SkeletonRow key={index} />
          ))}
        </ListSection>
      );
    }
    if (members.length === 0) {
      return <EmptyState title={t("chatChannelMembersEmpty")} className="mt-6" />;
    }
    return (
      <ListSection header={t("chatChannelMembersList")}>
        {members.map((member) => {
          const name: string = chatMemberName(member) || t("chatUnknownMember");
          const isMe: boolean = member.userId === meId;
          const removeLabel: string = t("chatChannelMembersRemove", { name });
          return (
            <ListRow
              key={member.userId}
              title={name}
              avatar={{ name }}
              accessory="none"
              trailing={
                isMe ? (
                  <Badge>{t("chatChannelMembersYou")}</Badge>
                ) : (
                  <IconButton
                    icon={UserMinus}
                    label={removeLabel}
                    iconClassName="text-destructive"
                    disabled={removeMember.isPending}
                    onPress={() => remove(member)}
                  />
                )
              }
              // The row is one element for VoiceOver and TalkBack, so the button is offered as its action.
              actions={
                isMe || removeMember.isPending
                  ? undefined
                  : [{ name: "remove", label: removeLabel, onAction: () => remove(member) }]
              }
            />
          );
        })}
      </ListSection>
    );
  })();

  const addSection = (() => {
    if (organizationQuery.isError && !organizationQuery.data) {
      return (
        <InlineError
          message={t("chatNewChannelMembersError")}
          onRetry={() => void organizationQuery.refetch()}
          className="mx-4 mt-4"
        />
      );
    }
    // Nobody is left to add, or the list they would join is still loading.
    if (candidates.length === 0 || (!members && !membersQuery.isError)) {
      return null;
    }
    return (
      <ListSection header={t("chatChannelMembersAdd")}>
        {candidates.map((member) => {
          const name: string = chatMemberName(member) || t("chatUnknownMember");
          const checked: boolean = selected.includes(member.userId);
          const blocked: boolean = chatMemberPickBlocked(member, checked);
          return (
            <ListRow
              key={member.userId}
              title={member.userId === meId ? t("chatChannelMembersMe", { name }) : name}
              subtitle={chatMemberPickSubtitle(null, blocked, t("memberNoKaizenAccess"))}
              avatar={{ name }}
              accessory="checkbox"
              checked={checked}
              disabled={addMembers.isPending || blocked}
              onCheckedChange={(next: boolean) => toggle(member.userId, next)}
              onPress={() => toggle(member.userId, !checked)}
            />
          );
        })}
      </ListSection>
    );
  })();

  const body = (() => {
    if (channel) {
      return (
        <View className="pb-6 pt-2">
          <Text variant="footnote" className="px-4 text-muted-foreground">
            {t("chatChannelMembersDescription")}
          </Text>
          {memberList}
          {addSection}
        </View>
      );
    }
    if (channelsQuery.data) {
      return <EmptyState title={t("chatChannelNotFound")} className="mt-8" />;
    }
    if (channelsQuery.isError) {
      return (
        <InlineError
          message={t("chatChannelsError")}
          onRetry={() => void channelsQuery.refetch()}
          className="mx-4 mt-4"
        />
      );
    }
    return (
      <ListSection>
        {[0, 1, 2].map((index) => (
          <SkeletonRow key={index} />
        ))}
      </ListSection>
    );
  })();

  return (
    <BottomSheet
      title={t("chatChannelMembersTitle", { name: channel?.name ?? "" })}
      cancelLabel={t("chatClose")}
      confirmLabel={t("chatChannelMembersAddButton")}
      onConfirm={() => add(selected)}
      confirmDisabled={!channel || selected.length === 0}
      confirmLoading={addMembers.isPending}
      scroll
    >
      {body}
    </BottomSheet>
  );
}

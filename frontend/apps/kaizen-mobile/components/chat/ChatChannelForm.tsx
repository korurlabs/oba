import { TriangleAlert } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import { chatMemberName } from "@/components/chat/chatDisplay";
import {
  chatChannelAuthorIds,
  chatChannelMakesPrivate,
  chatChannelMakesPublic,
  chatChannelMembersOverLimit,
  chatChannelPrivateWithPurpose,
  type ChatChannelDraft,
} from "@/components/chat/chatChannelMenuModel";
import { chatMemberPickBlocked, chatMemberPickSubtitle } from "@/components/chat/chatMembersModel";
import { useSheet } from "@/components/kaizen/BottomSheet";
import { InlineError } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { Icon } from "@/components/ui/icon";
import { Label } from "@/components/ui/label";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Text } from "@/components/ui/text";
import { TextField } from "@/components/ui/text-field";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import {
  useChatChannelAuthors,
  useChatChannels,
  useChatMeId,
  useChatMembers,
} from "@/hooks/queries/chat";
import { useTranslation, type TranslationKey } from "@/i18n";
import {
  CHAT_CHANNEL_MAX_MEMBER_IDS,
  CHAT_CHANNEL_NAME_MAX,
  ChatChannelPurpose,
  ChatChannelVisibility,
  type ChatChannelResponse,
  type ChatMemberResponse,
} from "@/lib/schemas/chat";

const VISIBILITIES: readonly ChatChannelVisibility[] = [
  ChatChannelVisibility.PUBLIC,
  ChatChannelVisibility.PRIVATE,
];

const PURPOSES: readonly ChatChannelPurpose[] = [
  ChatChannelPurpose.GENERAL,
  ChatChannelPurpose.DAILY_UPDATES,
  ChatChannelPurpose.PULL_REQUESTS,
];

const PURPOSE_LABELS: Record<ChatChannelPurpose, TranslationKey> = {
  GENERAL: "chatNewChannelPurposeGeneral",
  DAILY_UPDATES: "chatNewChannelPurposeDailyUpdates",
  PULL_REQUESTS: "chatNewChannelPurposePullRequests",
};

export interface ChatChannelFormProps {
  draft: ChatChannelDraft;
  onChange: (draft: ChatChannelDraft) => void;
  /**
   * The channel being changed; undefined while a channel is created. A
   * change keeps the purpose when the channel is made private and says why
   * it cannot be saved, preselects the channel's authors when it becomes
   * private, and warns before a private channel's past becomes public.
   */
  channel?: ChatChannelResponse;
  /** A refusal of the last save, shown under the purpose list. */
  formError: string | null;
  /** The server's validation messages, shown under the field each one names. */
  fieldErrors: Record<string, string>;
  nameTooLong: boolean;
  /** The save went through: the sheet closes. */
  done: boolean;
}

/**
 * The fields of a channel (owners and admins): a name, public or private,
 * the members of a channel that becomes private, and its purpose. Daily
 * updates and pull requests each go to one public channel, so a purpose
 * another channel holds is offered disabled, and a private channel offers
 * only General.
 */
export function ChatChannelForm({
  draft,
  onChange,
  channel,
  formError,
  fieldErrors,
  nameTooLong,
  done,
}: ChatChannelFormProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const { dismiss } = useSheet();
  const meId: string | undefined = useChatMeId();
  const channels: ChatChannelResponse[] | undefined = useChatChannels().data;
  const isPrivate: boolean = draft.visibility === ChatChannelVisibility.PRIVATE;
  // Members are picked only for a channel that becomes private; any other change keeps its list.
  const picksMembers: boolean = channel ? chatChannelMakesPrivate(channel, draft) : isPrivate;
  const makesPublic: boolean = !!channel && chatChannelMakesPublic(channel, draft);
  const membersQuery = useChatMembers(!channel || picksMembers);
  const authorsQuery = useChatChannelAuthors(channel?.id ?? null, !!channel && picksMembers);
  const membersTouched = React.useRef<boolean>(false);
  const authorsPrefilled = React.useRef<boolean>(false);

  const purposeError: string | null = formError ?? fieldErrors.purpose ?? null;
  // One request takes at most 200 members; Save waits until fewer are picked.
  const membersError: string | undefined =
    picksMembers && chatChannelMembersOverLimit(draft.memberIds)
      ? t("chatChannelMembersTooMany", { max: CHAT_CHANNEL_MAX_MEMBER_IDS })
      : fieldErrors.memberIds;
  // Only a change keeps a purpose on a private channel; Save waits until it is General.
  const purposeWarning: boolean = !!channel && chatChannelPrivateWithPurpose(draft);

  React.useEffect(() => {
    if (done) dismiss();
  }, [done, dismiss]);

  // Everyone who wrote in the channel is preselected once, unless members were already picked.
  React.useEffect(() => {
    if (!picksMembers || !authorsQuery.data || authorsPrefilled.current) return;
    authorsPrefilled.current = true;
    if (membersTouched.current) return;
    onChange({ ...draft, memberIds: chatChannelAuthorIds(authorsQuery.data, meId) });
  }, [picksMembers, authorsQuery.data, meId, draft, onChange]);

  // The reader joins on their own, so they are not offered.
  const others: ChatMemberResponse[] = React.useMemo(
    () => (membersQuery.data ?? []).filter((member) => member.userId !== meId),
    [membersQuery.data, meId]
  );

  // The channel that holds a purpose keeps it selectable.
  const holderOf = (purpose: ChatChannelPurpose): ChatChannelResponse | undefined =>
    purpose === ChatChannelPurpose.GENERAL
      ? undefined
      : channels?.find((other) => other.purpose === purpose && other.id !== channel?.id);

  const setVisibility = (visibility: ChatChannelVisibility): void => {
    // A new private channel is always general. A change keeps the purpose
    // and says why it cannot be saved, so a channel's purpose is never
    // dropped by toggling its visibility.
    onChange({
      ...draft,
      visibility,
      purpose:
        !channel && visibility === ChatChannelVisibility.PRIVATE
          ? ChatChannelPurpose.GENERAL
          : draft.purpose,
    });
  };

  const toggleMember = (userId: string, checked: boolean): void => {
    membersTouched.current = true;
    onChange({
      ...draft,
      memberIds: checked
        ? [...draft.memberIds, userId]
        : draft.memberIds.filter((known) => known !== userId),
    });
  };

  const membersBody = (() => {
    if (membersQuery.isError && !membersQuery.data) {
      return (
        <InlineError
          message={t("chatNewChannelMembersError")}
          onRetry={() => void membersQuery.refetch()}
          className="mx-4"
        />
      );
    }
    // A change shows the list once the authors are preselected, so no box flips under a finger.
    if (!membersQuery.data || (!!channel && authorsQuery.isPending && !authorsQuery.isError)) {
      return <ActivityIndicator className="py-3" color={colors.mutedForeground} />;
    }
    return (
      <ListSection
        header={t("chatNewChannelMembers")}
        footer={t(channel ? "chatChannelMembersAuthorsHint" : "chatNewChannelMembersFooter")}
        error={membersError}
      >
        {others.map((member) => {
          const checked: boolean = draft.memberIds.includes(member.userId);
          const blocked: boolean = chatMemberPickBlocked(member, checked);
          return (
            <ListRow
              key={member.userId}
              title={chatMemberName(member)}
              subtitle={chatMemberPickSubtitle(member.email, blocked, t("memberNoKaizenAccess"))}
              wrap={blocked}
              avatar={{ name: chatMemberName(member) }}
              accessory="checkbox"
              checked={checked}
              disabled={blocked}
              onCheckedChange={(next: boolean) => toggleMember(member.userId, next)}
              onPress={() => toggleMember(member.userId, !checked)}
            />
          );
        })}
      </ListSection>
    );
  })();

  const visibilityNote: React.ReactNode = fieldErrors.visibility ? (
    <Text variant="footnote" className="text-destructive">
      {fieldErrors.visibility}
    </Text>
  ) : makesPublic ? (
    <FormWarning message={t("chatChannelMakesPublicWarning")} />
  ) : isPrivate ? (
    <Text variant="footnote" className="text-muted-foreground">
      {t("chatNewChannelPrivateHint")}
    </Text>
  ) : null;

  return (
    <View className="gap-2 pb-6 pt-2">
      {channel ? (
        <Text variant="footnote" className="px-4 text-muted-foreground">
          {t("chatChannelChangeDescription")}
        </Text>
      ) : null}
      <View className="px-4">
        <TextField
          label={t("chatNewChannelName")}
          value={draft.name}
          onChangeText={(name: string) => onChange({ ...draft, name })}
          placeholder={t("chatNewChannelNamePlaceholder")}
          error={
            nameTooLong
              ? t("chatNewChannelNameTooLong", { max: CHAT_CHANNEL_NAME_MAX })
              : (fieldErrors.name ?? null)
          }
          autoCapitalize="none"
          autoCorrect={false}
          autoFocus={!channel}
          returnKeyType="done"
        />
      </View>
      <View className="gap-1.5 px-4 pt-2">
        <Label>{t("chatNewChannelVisibility")}</Label>
        <SegmentedControl
          values={VISIBILITIES.map((visibility) =>
            t(
              visibility === ChatChannelVisibility.PUBLIC
                ? "chatNewChannelPublic"
                : "chatNewChannelPrivate"
            )
          )}
          selectedIndex={VISIBILITIES.indexOf(draft.visibility)}
          onChange={(index: number) => {
            const next: ChatChannelVisibility | undefined = VISIBILITIES[index];
            if (next) setVisibility(next);
          }}
        />
        {visibilityNote}
      </View>
      {picksMembers ? membersBody : null}
      <ListSection header={t("chatNewChannelPurpose")} footer={t("chatNewChannelPurposeInfo")}>
        {PURPOSES.map((purpose) => {
          const holder: ChatChannelResponse | undefined = holderOf(purpose);
          const disabled: boolean =
            purpose !== ChatChannelPurpose.GENERAL && (isPrivate || !!holder);
          return (
            <ListRow
              key={purpose}
              title={t(PURPOSE_LABELS[purpose])}
              subtitle={
                holder ? t("chatNewChannelPurposeHeldBy", { channel: holder.name }) : undefined
              }
              accessory="radio"
              checked={draft.purpose === purpose}
              disabled={disabled}
              onPress={disabled ? undefined : () => onChange({ ...draft, purpose })}
            />
          );
        })}
      </ListSection>
      {purposeError ? (
        <Text variant="footnote" accessibilityRole="alert" className="px-4 text-destructive">
          {purposeError}
        </Text>
      ) : purposeWarning ? (
        <View className="px-4">
          <FormWarning message={t("chatChannelPurposePrivateWarning")} />
        </View>
      ) : null}
    </View>
  );
}

/** A consequence of the current choice that the reader should see before saving. */
function FormWarning({ message }: { message: string }) {
  return (
    <View accessibilityRole="alert" className="flex-row items-start gap-2">
      <Icon as={TriangleAlert} size={14} className="mt-0.5 text-warning" />
      <Text variant="footnote" className="flex-1">
        {message}
      </Text>
    </View>
  );
}

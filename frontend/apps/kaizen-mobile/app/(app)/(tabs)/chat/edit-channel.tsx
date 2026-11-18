import { useLocalSearchParams } from "expo-router";
import * as React from "react";
import { ActivityIndicator } from "react-native";

import { ChatChannelForm } from "@/components/chat/ChatChannelForm";
import {
  chatChannelErrorKey,
  chatChannelUpdateChanged,
  chatChannelUpdateDraft,
  chatChannelUpdateRequest,
  chatChannelUpdateValid,
  type ChatChannelDraft,
} from "@/components/chat/chatChannelMenuModel";
import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { EmptyState, InlineError } from "@/components/kaizen/EmptyState";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useChatChannels } from "@/hooks/queries/chat";
import { chatChannelErrorMessage, useUpdateChatChannel } from "@/hooks/queries/chatMutations";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation, type TranslationKey } from "@/i18n";
import { isApiError, isValidationError } from "@/lib/api";
import {
  CHAT_CHANNEL_NAME_MAX,
  CHAT_ERROR_CODES,
  type ChatChannelResponse,
} from "@/lib/schemas/chat";

/**
 * Changes a channel (owners and admins): its name, public or private, and
 * its purpose. A public channel made private takes the members picked here,
 * everyone who wrote in it preselected; a private channel made public loses
 * its member list and shows its past to everyone. The values are taken when
 * the sheet opens, so a refetch of the channel does not wipe the edits.
 */
export default function EditChannelSheet() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const params = useLocalSearchParams<{ channelId: string }>();
  const channelId: string = params.channelId ?? "";
  const channelsQuery = useChatChannels();
  const channel: ChatChannelResponse | undefined = channelsQuery.data?.find(
    (known) => known.id === channelId
  );
  const [initial, setInitial] = React.useState<ChatChannelDraft | null>(() =>
    channel ? chatChannelUpdateDraft(channel) : null
  );
  const [draft, setDraft] = React.useState<ChatChannelDraft | null>(initial);
  // A refusal the sheet explains, shown under the purpose list.
  const [formError, setFormError] = React.useState<string | null>(null);
  // The server's validation message of each request field, keyed by its name.
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const [saved, setSaved] = React.useState<boolean>(false);
  const { mutate: updateChannel, isPending } = useUpdateChatChannel();
  const online = useOnlineWriteGuard();

  // The channel list may still be loading when the sheet opens.
  React.useEffect(() => {
    if (initial || !channel) return;
    const start: ChatChannelDraft = chatChannelUpdateDraft(channel);
    setInitial(start);
    setDraft(start);
  }, [channel, initial]);

  const onChange = React.useCallback((next: ChatChannelDraft): void => {
    setDraft(next);
    setFormError(null);
    setFieldErrors({});
  }, []);

  const valid: boolean = !!channel && !!draft && chatChannelUpdateValid(channel, draft);

  const submit = (): void => {
    if (!channel || !draft || !valid || !online()) return;
    setFormError(null);
    setFieldErrors({});
    updateChannel(
      { channelId: channel.id, data: chatChannelUpdateRequest(channel, draft) },
      {
        onSuccess: () => {
          toast.success(t("chatChannelSaved"));
          setSaved(true);
        },
        onError: (error: Error) => {
          // Deleted meanwhile, or the reader lost access: nothing is left to change.
          if (isApiError(error, CHAT_ERROR_CODES.channelNotFound)) {
            toast.error(chatChannelErrorMessage(error));
            setSaved(true);
            return;
          }
          const key: TranslationKey | null = chatChannelErrorKey(
            isApiError(error) ? error.code : undefined
          );
          if (key) {
            setFormError(t(key));
          } else if (isValidationError(error)) {
            setFieldErrors(error.fieldErrors);
          } else {
            toast.error(chatChannelErrorMessage(error));
          }
        },
      }
    );
  };

  const body = (() => {
    if (draft && channel) {
      return (
        <ChatChannelForm
          draft={draft}
          onChange={onChange}
          channel={channel}
          formError={formError}
          fieldErrors={fieldErrors}
          nameTooLong={draft.name.trim().length > CHAT_CHANNEL_NAME_MAX}
          done={saved}
        />
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
    return <ActivityIndicator className="py-6" color={colors.mutedForeground} />;
  })();

  return (
    <BottomSheet
      title={t("chatChannelChange")}
      cancelLabel={t("chatCancel")}
      confirmLabel={t("chatSave")}
      onConfirm={submit}
      confirmDisabled={!channel || !valid}
      confirmLoading={isPending}
      dirty={
        !saved &&
        !!channel &&
        !!draft &&
        !!initial &&
        chatChannelUpdateChanged(channel, initial, draft)
      }
      scroll
    >
      {body}
    </BottomSheet>
  );
}

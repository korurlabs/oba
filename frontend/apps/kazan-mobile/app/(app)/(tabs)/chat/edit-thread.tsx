import { useLocalSearchParams } from "expo-router";
import { MessageSquareOff } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import {
  chatThreadSettingsChanged,
  chatThreadSettingsDraft,
  chatThreadTitleProblem,
  chatThreadUpdateRequest,
  type ChatThreadSettingsDraft,
} from "@/components/chat/chatThreadModel";
import { BottomSheet, useSheet } from "@/components/kazan/BottomSheet";
import { EmptyState } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import { TextField } from "@/components/ui/text-field";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useChatThread } from "@/hooks/queries/chat";
import { useUpdateChatThread } from "@/hooks/queries/chatMutations";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation, type TranslationKey } from "@/i18n";
import { isApiError } from "@/lib/api";
import {
  CHAT_ERROR_CODES,
  CHAT_THREAD_ARCHIVE_OPTIONS,
  CHAT_THREAD_TITLE_MAX,
  type ChatThreadArchiveMinutes,
  type ChatThreadResponse,
} from "@/lib/schemas/chat";

const ARCHIVE_LABELS: Record<ChatThreadArchiveMinutes, TranslationKey> = {
  60: "chatThreadEditArchive60",
  1440: "chatThreadEditArchive1440",
  4320: "chatThreadEditArchive4320",
  10080: "chatThreadEditArchive10080",
};

/**
 * A thread's settings (its creator and the organization's owners and
 * admins): its name and how long it waits without a reply before it is
 * archived. Save sends both. The values are taken when the sheet opens, so a
 * refetch of the thread does not wipe the edits; a refused save keeps them
 * and is toasted with the server's message by the app's API error layer.
 */
export default function EditThreadSheet() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const params = useLocalSearchParams<{ threadId: string }>();
  const threadId: string = params.threadId ?? "";
  const threadQuery = useChatThread(threadId || null);
  const thread: ChatThreadResponse | undefined = threadQuery.data;
  const [initial, setInitial] = React.useState<ChatThreadSettingsDraft | null>(() =>
    thread ? chatThreadSettingsDraft(thread) : null
  );
  const [draft, setDraft] = React.useState<ChatThreadSettingsDraft | null>(initial);
  const [saved, setSaved] = React.useState<boolean>(false);
  const { mutate: updateThread, isPending } = useUpdateChatThread();
  const online = useOnlineWriteGuard();

  // The thread may still be loading when the sheet opens.
  React.useEffect(() => {
    if (initial || !thread) return;
    const start: ChatThreadSettingsDraft = chatThreadSettingsDraft(thread);
    setInitial(start);
    setDraft(start);
  }, [thread, initial]);

  const valid: boolean = !!draft && chatThreadTitleProblem(draft.title) === null;

  const submit = (): void => {
    if (!draft || !valid || isPending || !online()) return;
    updateThread(
      { threadId, data: chatThreadUpdateRequest(draft) },
      { onSuccess: () => setSaved(true) }
    );
  };

  const body = (() => {
    if (draft) {
      return (
        <ThreadSettingsForm draft={draft} onChange={setDraft} disabled={isPending} done={saved} />
      );
    }
    if (!threadId || (threadQuery.isError && !thread)) {
      const gone: boolean =
        !threadId || isApiError(threadQuery.error, CHAT_ERROR_CODES.threadNotFound);
      return (
        <EmptyState
          variant="error"
          icon={gone ? MessageSquareOff : undefined}
          title={gone ? t("chatThreadNotFound") : t("chatThreadError")}
          onAction={gone ? undefined : () => void threadQuery.refetch()}
          actionLabel={gone ? undefined : t("chatRetry")}
          className="mt-8"
        />
      );
    }
    return <ActivityIndicator className="py-6" color={colors.mutedForeground} />;
  })();

  return (
    <BottomSheet
      title={t("chatThreadEdit")}
      cancelLabel={t("chatCancel")}
      confirmLabel={t("chatSave")}
      onConfirm={submit}
      confirmDisabled={!valid}
      confirmLoading={isPending}
      detent="medium"
      dirty={!saved && !!initial && !!draft && chatThreadSettingsChanged(initial, draft)}
      scroll
    >
      {body}
    </BottomSheet>
  );
}

function ThreadSettingsForm({
  draft,
  onChange,
  disabled,
  done,
}: {
  draft: ChatThreadSettingsDraft;
  onChange: (draft: ChatThreadSettingsDraft) => void;
  /** A save is under way: the values stay as sent. */
  disabled: boolean;
  /** The save went through: the sheet closes. */
  done: boolean;
}) {
  const { t } = useTranslation();
  const { dismiss } = useSheet();

  React.useEffect(() => {
    if (done) dismiss();
  }, [done, dismiss]);

  const problem: "required" | "tooLong" | null = chatThreadTitleProblem(draft.title);

  return (
    <View className="gap-2 pb-6 pt-2">
      <View className="px-4">
        <TextField
          label={t("chatThreadEditName")}
          required
          value={draft.title}
          onChangeText={(title: string) => onChange({ ...draft, title })}
          error={
            problem === "required"
              ? t("chatThreadEditNameRequired")
              : problem === "tooLong"
                ? t("chatThreadEditNameTooLong", { max: CHAT_THREAD_TITLE_MAX })
                : null
          }
          // Room past the limit, so a pasted name is not cut silently but shows why Save is off.
          maxLength={CHAT_THREAD_TITLE_MAX + 20}
          editable={!disabled}
          returnKeyType="done"
        />
      </View>
      <ListSection header={t("chatThreadEditArchive")} footer={t("chatThreadEditArchiveHint")}>
        {CHAT_THREAD_ARCHIVE_OPTIONS.map((minutes: ChatThreadArchiveMinutes) => (
          <ListRow
            key={minutes}
            title={t(ARCHIVE_LABELS[minutes])}
            accessory="radio"
            checked={draft.autoArchiveMinutes === minutes}
            disabled={disabled}
            onPress={
              disabled ? undefined : () => onChange({ ...draft, autoArchiveMinutes: minutes })
            }
          />
        ))}
      </ListSection>
    </View>
  );
}

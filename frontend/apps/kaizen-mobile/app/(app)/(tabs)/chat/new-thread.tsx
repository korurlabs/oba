import { useQueryClient } from "@tanstack/react-query";
import { useLocalSearchParams } from "expo-router";
import * as React from "react";
import { View } from "react-native";

import { chatThreadDefaultTitle } from "@/components/chat/chatThreadModel";
import { requestChatScreen } from "@/components/chat/chatScreenRequests";
import { BottomSheet, useSheet } from "@/components/kaizen/BottomSheet";
import { EmptyState } from "@/components/kaizen/EmptyState";
import { TextField } from "@/components/ui/text-field";
import { findChatMessage } from "@/hooks/queries/chatCache";
import { useCreateChatThread, type ChatThreadCreateResult } from "@/hooks/queries/chatMutations";
import { useTranslation } from "@/i18n";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import { CHAT_THREAD_TITLE_MAX, type ChatMessageResponse } from "@/lib/schemas/chat";

/**
 * Starts a thread on a channel message: a title field prefilled the way the
 * server would title it, then Create, which opens the new thread. When the
 * message got a thread meanwhile, that thread opens instead.
 */
export default function NewThreadSheet() {
  const { t } = useTranslation();
  const params = useLocalSearchParams<{ channelId: string; messageId: string }>();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  // Read once: the sheet only needs the message to prefill its title.
  const [message] = React.useState<ChatMessageResponse | undefined>(() =>
    orgId && params.messageId ? findChatMessage(queryClient, orgId, params.messageId) : undefined
  );
  const [title, setTitle] = React.useState<string>(() =>
    message ? chatThreadDefaultTitle(message, t("chatUnknownMember")) : ""
  );
  const { mutate: createThread, isPending } = useCreateChatThread();
  const [created, setCreated] = React.useState<boolean>(false);

  return (
    <BottomSheet
      title={t("chatNewThreadTitle")}
      cancelLabel={t("chatCancel")}
      confirmLabel={t("chatCreate")}
      confirmDisabled={!message || title.trim().length > CHAT_THREAD_TITLE_MAX}
      confirmLoading={isPending}
      detent="medium"
      onConfirm={() => {
        if (!message) return;
        const trimmed: string = title.trim();
        createThread(
          { rootMessageId: message.id, title: trimmed === "" ? null : trimmed },
          {
            onSuccess: (result: ChatThreadCreateResult) => {
              requestChatScreen({
                kind: "openThread",
                channelId: message.channelId,
                threadId: result.threadId,
              });
              setCreated(true);
            },
          }
        );
      }}
    >
      <NewThreadContent
        message={message}
        title={title}
        onTitleChange={setTitle}
        created={created}
      />
    </BottomSheet>
  );
}

function NewThreadContent({
  message,
  title,
  onTitleChange,
  created,
}: {
  message: ChatMessageResponse | undefined;
  title: string;
  onTitleChange: (title: string) => void;
  /** The thread exists: the sheet closes and the channel screen opens it. */
  created: boolean;
}) {
  const { t } = useTranslation();
  const { dismiss } = useSheet();

  React.useEffect(() => {
    if (created) dismiss();
  }, [created, dismiss]);

  if (!message) {
    return <EmptyState variant="error" title={t("chatMessageGone")} className="mt-8" />;
  }
  return (
    <View className="px-4 pb-6 pt-2">
      <TextField
        label={t("chatNewThreadName")}
        value={title}
        onChangeText={onTitleChange}
        placeholder={t("chatNewThreadNamePlaceholder")}
        helper={t("chatNewThreadNameHelper")}
        error={
          title.trim().length > CHAT_THREAD_TITLE_MAX
            ? t("chatNewThreadNameTooLong", { max: CHAT_THREAD_TITLE_MAX })
            : null
        }
        maxLength={CHAT_THREAD_TITLE_MAX + 20}
        autoFocus
        returnKeyType="done"
      />
    </View>
  );
}

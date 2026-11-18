import { useLocalSearchParams } from "expo-router";
import * as React from "react";

import { ChatChannelForm } from "@/components/chat/ChatChannelForm";
import {
  chatChannelMembersOverLimit,
  type ChatChannelDraft,
} from "@/components/chat/chatChannelMenuModel";
import { requestChatScreen } from "@/components/chat/chatScreenRequests";
import { BottomSheet } from "@/components/kazan/BottomSheet";
import { toast } from "@/components/ui/toast";
import { chatChannelErrorMessage, useCreateChatChannel } from "@/hooks/queries/chatMutations";
import { useTranslation } from "@/i18n";
import { isApiError, isValidationError } from "@/lib/api";
import {
  CHAT_CHANNEL_NAME_MAX,
  CHAT_ERROR_CODES,
  ChatChannelPurpose,
  ChatChannelVisibility,
  type ChatChannelResponse,
} from "@/lib/schemas/chat";

/**
 * A purpose refusal the form explains under the purpose list. A validation
 * error goes under the field it names; anything else is toasted.
 */
type PurposeProblem = "taken" | "private" | null;

const EMPTY_DRAFT: ChatChannelDraft = {
  name: "",
  visibility: ChatChannelVisibility.PUBLIC,
  purpose: ChatChannelPurpose.GENERAL,
  memberIds: [],
};

/**
 * Creates a channel (owners and admins): a name, public or private, the
 * members of a private channel, and its purpose. Daily updates and pull
 * requests each go to one public channel, so a purpose another channel
 * holds is offered disabled, and a private channel is always general. The
 * new channel opens in place of the one the sheet was opened from.
 */
export default function NewChannelSheet() {
  const { t } = useTranslation();
  const params = useLocalSearchParams<{ channelId: string }>();
  const fromChannelId: string = params.channelId ?? "";
  const [draft, setDraft] = React.useState<ChatChannelDraft>(EMPTY_DRAFT);
  const [problem, setProblem] = React.useState<PurposeProblem>(null);
  // The server's validation message of each request field, keyed by its name.
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const [created, setCreated] = React.useState<boolean>(false);
  const { mutate: createChannel, isPending } = useCreateChatChannel();

  const name: string = draft.name.trim();
  const nameTooLong: boolean = name.length > CHAT_CHANNEL_NAME_MAX;
  const isPrivate: boolean = draft.visibility === ChatChannelVisibility.PRIVATE;
  // The form says why; a public channel sends no members, so its picks do not count.
  const tooManyMembers: boolean = isPrivate && chatChannelMembersOverLimit(draft.memberIds);

  const submit = (): void => {
    if (!name || nameTooLong || tooManyMembers) return;
    setProblem(null);
    setFieldErrors({});
    createChannel(
      {
        name,
        visibility: draft.visibility,
        purpose: draft.purpose,
        // A public channel is open to everyone; the server refuses a member list for it.
        memberIds: isPrivate ? [...draft.memberIds] : undefined,
      },
      {
        onSuccess: (channel: ChatChannelResponse) => {
          if (fromChannelId) {
            requestChatScreen({
              kind: "switch",
              channelId: fromChannelId,
              toChannelId: channel.id,
            });
          }
          setCreated(true);
        },
        onError: (error: Error) => {
          if (isApiError(error, CHAT_ERROR_CODES.channelPurposeTaken)) {
            setProblem("taken");
          } else if (isApiError(error, CHAT_ERROR_CODES.channelPurposePrivate)) {
            setProblem("private");
          } else if (isValidationError(error)) {
            setFieldErrors(error.fieldErrors);
          } else {
            toast.error(chatChannelErrorMessage(error));
          }
        },
      }
    );
  };

  return (
    <BottomSheet
      title={t("chatNewChannel")}
      cancelLabel={t("chatCancel")}
      confirmLabel={t("chatCreate")}
      onConfirm={submit}
      confirmDisabled={!name || nameTooLong || tooManyMembers}
      confirmLoading={isPending}
      dirty={!created && (draft.name.trim() !== "" || draft.memberIds.length > 0)}
      scroll
    >
      <ChatChannelForm
        draft={draft}
        onChange={(next: ChatChannelDraft) => {
          setDraft(next);
          setProblem(null);
          setFieldErrors({});
        }}
        formError={
          problem
            ? t(
                problem === "taken"
                  ? "chatNewChannelPurposeTaken"
                  : "chatNewChannelPurposePrivateError"
              )
            : null
        }
        fieldErrors={fieldErrors}
        nameTooLong={nameTooLong}
        done={created}
      />
    </BottomSheet>
  );
}

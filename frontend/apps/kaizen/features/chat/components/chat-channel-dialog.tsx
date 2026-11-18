"use client";

import { useEffect, useMemo, useRef, type MouseEvent } from "react";
import { useTranslations } from "next-intl";
import { useFormContext } from "react-hook-form";
import { toast } from "sonner";
import { AlertTriangle } from "lucide-react";
import { isApiError } from "@oba/shared/api/axios";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { FormGrid, ObaForm } from "@oba/shared/features/form";
import { SelectField as MultiSelectField } from "@oba/shared/features/form/components";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { FormField } from "@/components/ui/form";
import { SelectField, TextField } from "@/components/ui/form-fields";
import { FormFieldWrapper } from "@/components/ui/form-fields/form-field-wrapper";
import { InlineAlert } from "@/components/ui/inline-alert";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { getDisplayName } from "@/components/ui/user-avatar";
import { hasKaizenAccess } from "@/lib/kaizen-member-access";
import {
  useChatChannels,
  useCreateChatChannel,
  useDeleteChatChannel,
  useRemoveChatChannelMember,
  useUpdateChatChannel,
} from "../hooks/use-chat-channels";
import { useChatMeId, useChatMembers, useChatOrganizationId } from "../hooks/use-chat-messages";
import {
  CHAT_CHANNEL_NAME_MAX,
  CHAT_ERROR_CODES,
  ChatChannelPurpose,
  ChatChannelVisibility,
  chatChannelCreateRequestSchema,
  isChatChannelDialogErrorCode,
  type ChatChannelCreateRequest,
  type ChatChannelDialogErrorCode,
  type ChatChannelResponse,
  type ChatMemberResponse,
} from "../schemas";

/**
 * The text of each refusal of `CHAT_CHANNEL_DIALOG_ERROR_CODES`, by key under
 * `chat.channels.errors`. Keyed by the code type, so the map holds exactly
 * the codes the channel mutations leave to the dialogs.
 */
const CHANNEL_ERROR_KEYS: Record<ChatChannelDialogErrorCode, string> = {
  [CHAT_ERROR_CODES.channelMembersNotExpected]: "membersNotExpected",
  [CHAT_ERROR_CODES.channelNotPrivate]: "notPrivate",
  [CHAT_ERROR_CODES.channelPurposePrivate]: "purposePrivate",
  [CHAT_ERROR_CODES.channelPurposeTaken]: "purposeTaken",
  [CHAT_ERROR_CODES.mentionNotMember]: "memberNotActive",
  [CHAT_ERROR_CODES.channelNotFound]: "notFound",
  [CHAT_ERROR_CODES.channelMemberRemoveRefused]: "notAllowed",
  [CHAT_ERROR_CODES.memberWithoutKaizenAccess]: "noKaizenAccess",
  // The organization-level refusal of a reader who is no longer OWNER or ADMIN.
  ORG_SEC_003: "notAllowed",
};

/** True for an OWNER or ADMIN of the chat's organization, who create, change and delete channels and manage private member lists. */
export function useCanManageChatChannels(): boolean {
  const orgId = useChatOrganizationId();
  const { data: meResponse } = useGetMe();
  const role = meResponse?.data?.organizations?.find(
    (organization) => organization.id === orgId
  )?.role;
  return role === "OWNER" || role === "ADMIN";
}

/** The `chat.channels.errors` key of a refused channel write; null for any other failure, which the mutation toasts with the server's text. */
export function chatChannelErrorKey(error: Error | null): string | null {
  if (!isApiError(error) || !error.code || !isChatChannelDialogErrorCode(error.code)) return null;
  return CHANNEL_ERROR_KEYS[error.code];
}

/** The translated refusal of a failed channel write, shown next to the form that sent it. */
export function ChatChannelWriteError({ error }: { error: Error | null }) {
  const t = useTranslations("chat.channels.errors");
  const key = chatChannelErrorKey(error);
  if (!key) return null;
  return <InlineAlert variant="danger" icon={<AlertTriangle />} description={t(key)} />;
}

export function memberDisplayName(member: ChatMemberResponse): string {
  return getDisplayName({
    firstName: member.firstName ?? undefined,
    lastName: member.lastName ?? undefined,
    email: member.email ?? undefined,
  });
}

// ── Create and change ───────────────────────────────────────────────

interface ChatChannelDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** The channel to change; undefined creates a channel. */
  channel?: ChatChannelResponse;
  /** Called with the new channel after a create, to open it. */
  onCreated?: (channel: ChatChannelResponse) => void;
}

/**
 * Creates a channel or changes one (OWNER and ADMIN): its name, visibility
 * and purpose. A private channel's members are picked here when it is
 * created or made private; the reader is added by the server. Making a public
 * channel private pre-selects everyone who wrote in it, so nobody loses a
 * conversation they took part in without the admin seeing it.
 */
export function ChatChannelDialog({
  open,
  onOpenChange,
  channel,
  onCreated,
}: ChatChannelDialogProps) {
  const t = useTranslations("chat.channels");
  const tCommon = useTranslations("common");
  const createChannel = useCreateChatChannel();
  const updateChannel = useUpdateChatChannel();
  const mutation = channel ? updateChannel : createChannel;
  const isPending = mutation.isPending;
  const { reset: resetCreate } = createChannel;
  const { reset: resetUpdate } = updateChannel;

  // A dialog opened again starts without the refusal of its last attempt.
  useEffect(() => {
    if (!open) return;
    resetCreate();
    resetUpdate();
  }, [open, resetCreate, resetUpdate]);

  const defaultValues = useMemo<ChatChannelCreateRequest>(
    () => ({
      name: channel?.name ?? "",
      visibility: channel?.visibility ?? ChatChannelVisibility.PUBLIC,
      purpose: channel?.purpose ?? ChatChannelPurpose.GENERAL,
      memberIds: [],
    }),
    // Taken when the dialog opens: a refetch of the channel while it is open must not wipe the admin's edits.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reason: the snapshot is keyed by the channel and the opening, not by its fields
    [channel?.id, open]
  );

  const close = () => {
    if (!isPending) onOpenChange(false);
  };

  const submit = (values: ChatChannelCreateRequest) => {
    const makesPrivate =
      values.visibility === ChatChannelVisibility.PRIVATE &&
      (!channel || channel.visibility === ChatChannelVisibility.PUBLIC);
    // Members go only with a channel that becomes private; any other update refuses them.
    const request: ChatChannelCreateRequest = {
      name: values.name.trim(),
      visibility: values.visibility,
      purpose: values.purpose,
      memberIds: makesPrivate ? (values.memberIds ?? []) : undefined,
    };
    if (channel) {
      updateChannel.mutate(
        { channelId: channel.id, data: request },
        {
          onSuccess: () => {
            toast.success(t("updated"));
            onOpenChange(false);
          },
        }
      );
      return;
    }
    createChannel.mutate(request, {
      onSuccess: (created) => {
        toast.success(t("created"));
        onOpenChange(false);
        onCreated?.(created);
      },
    });
  };

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? onOpenChange(true) : close())}>
      <DialogContent className="sm:max-w-[500px]">
        <ObaForm
          schema={chatChannelCreateRequestSchema}
          onSubmit={submit}
          defaultValues={defaultValues}
          className="space-y-0"
        >
          <DialogHeader>
            <DialogTitle>{channel ? t("dialog.changeTitle") : t("dialog.createTitle")}</DialogTitle>
            <DialogDescription>
              {channel ? t("dialog.changeDescription") : t("dialog.createDescription")}
            </DialogDescription>
          </DialogHeader>

          <ChannelFormFields
            channel={channel}
            open={open}
            isPending={isPending}
            error={mutation.error}
          />

          <DialogFooter>
            <Button type="button" variant="ghost" onClick={close} disabled={isPending}>
              {tCommon("cancel")}
            </Button>
            <ChannelSubmitButton isPending={isPending} isChange={!!channel} />
          </DialogFooter>
        </ObaForm>
      </DialogContent>
    </Dialog>
  );
}

/**
 * True when the form asks for a purpose other than GENERAL on a private
 * channel. Such a channel serves the whole organization, so the server
 * refuses it as private.
 */
function isPrivateWithPurpose(values: Pick<ChatChannelCreateRequest, "visibility" | "purpose">): boolean {
  return values.visibility === ChatChannelVisibility.PRIVATE && values.purpose !== ChatChannelPurpose.GENERAL;
}

/** Submit, disabled while the form asks for a purpose other than GENERAL on a private channel, which the server refuses. */
function ChannelSubmitButton({ isPending, isChange }: { isPending: boolean; isChange: boolean }) {
  const t = useTranslations("chat.channels.dialog");
  const { watch } = useFormContext<ChatChannelCreateRequest>();
  const invalid = isPrivateWithPurpose({ visibility: watch("visibility"), purpose: watch("purpose") });
  return (
    <Button type="submit" disabled={isPending || invalid}>
      {isPending ? t("saving") : isChange ? t("save") : t("create")}
    </Button>
  );
}

function ChannelFormFields({
  channel,
  open,
  isPending,
  error,
}: {
  channel: ChatChannelResponse | undefined;
  open: boolean;
  isPending: boolean;
  error: Error | null;
}) {
  const t = useTranslations("chat.channels.dialog");
  const tAccess = useTranslations("kaizen.memberAccess");
  const { control, watch, setValue, getFieldState } = useFormContext<ChatChannelCreateRequest>();
  const meId = useChatMeId();
  const visibility = watch("visibility");
  const purpose = watch("purpose");

  const makesPrivate =
    visibility === ChatChannelVisibility.PRIVATE &&
    (!channel || channel.visibility === ChatChannelVisibility.PUBLIC);
  const makesPublic =
    !!channel &&
    channel.visibility === ChatChannelVisibility.PRIVATE &&
    visibility === ChatChannelVisibility.PUBLIC;

  const { data: members, isLoading: membersLoading } = useChatMembers({
    enabled: open && makesPrivate,
  });
  // The authors of a public channel being made private; they are pre-selected.
  const { data: authors } = useChatMembers({
    channelId: channel?.id,
    authorsOnly: true,
    enabled: open && makesPrivate && !!channel,
  });

  // Pre-selects the authors once per opening, unless the admin already picked
  // members. An author without Kaizen access is left out: the server refuses
  // them as a member, and the picker shows why.
  const prefilledRef = useRef(false);
  useEffect(() => {
    if (!open) prefilledRef.current = false;
  }, [open]);
  useEffect(() => {
    if (!makesPrivate || !authors || prefilledRef.current) return;
    prefilledRef.current = true;
    if (getFieldState("memberIds").isDirty) return;
    const authorIds = authors
      .filter((author) => author.userId !== meId && hasKaizenAccess(author))
      .map((author) => author.userId);
    setValue("memberIds", authorIds, { shouldDirty: true });
  }, [makesPrivate, authors, meId, setValue, getFieldState]);

  // Each purpose other than GENERAL has one channel per organization; the
  // server refuses a second one, so its option stays closed while another
  // channel holds it. The holder itself keeps it selectable.
  const { data: channels } = useChatChannels();
  const holderOf = (held: ChatChannelPurpose) =>
    channels?.find((other) => other.purpose === held && other.id !== channel?.id);
  const dailyUpdatesHolder = holderOf(ChatChannelPurpose.DAILY_UPDATES);
  const pullRequestsHolder = holderOf(ChatChannelPurpose.PULL_REQUESTS);
  const purposeDescription = [
    dailyUpdatesHolder && t("dailyUpdatesTaken", { name: dailyUpdatesHolder.name }),
    pullRequestsHolder && t("pullRequestsTaken", { name: pullRequestsHolder.name }),
  ]
    .filter(Boolean)
    .join(" ");

  const memberOptions = (members ?? [])
    .filter((member) => member.userId !== meId)
    .map((member) => ({
      value: member.userId,
      label: memberDisplayName(member),
      disabled: !hasKaizenAccess(member),
      disabledLabel: tAccess("noAccess"),
    }));

  return (
    <FormGrid className="py-6 md:grid-cols-1">
      <TextField
        name="name"
        label={t("name")}
        placeholder={t("namePlaceholder")}
        control={control}
        maxLength={CHAT_CHANNEL_NAME_MAX}
        required
        disabled={isPending}
      />
      <SelectField
        name="visibility"
        label={t("visibility")}
        control={control}
        required
        disabled={isPending}
        options={[
          { value: ChatChannelVisibility.PUBLIC, label: t("visibilityPublic") },
          { value: ChatChannelVisibility.PRIVATE, label: t("visibilityPrivate") },
        ]}
      />
      {makesPublic && (
        <InlineAlert
          variant="warning"
          icon={<AlertTriangle />}
          description={t("makesPublicWarning")}
        />
      )}
      {makesPrivate && (
        <MultiSelectField
          name="memberIds"
          label={t("members")}
          description={channel ? t("membersAuthorsHint") : t("membersHint")}
          control={control}
          isMulti
          options={memberOptions}
          isLoading={membersLoading}
          placeholder={t("membersPlaceholder")}
          searchPlaceholder={t("membersSearch")}
          disabled={isPending}
        />
      )}
      <FormField
        control={control}
        name="purpose"
        render={({ field: { value, onChange, ...field } }) => (
          <FormFieldWrapper
            label={t("purpose")}
            required
            description={purposeDescription || undefined}
          >
            <Select value={value} onValueChange={onChange} disabled={isPending}>
              <SelectTrigger {...field}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem value={ChatChannelPurpose.GENERAL}>{t("purposeGeneral")}</SelectItem>
                  <SelectItem
                    value={ChatChannelPurpose.DAILY_UPDATES}
                    disabled={!!dailyUpdatesHolder}
                  >
                    {t("purposeDailyUpdates")}
                  </SelectItem>
                  <SelectItem
                    value={ChatChannelPurpose.PULL_REQUESTS}
                    disabled={!!pullRequestsHolder}
                  >
                    {t("purposePullRequests")}
                  </SelectItem>
                </SelectGroup>
              </SelectContent>
            </Select>
          </FormFieldWrapper>
        )}
      />
      {isPrivateWithPurpose({ visibility, purpose }) && (
        <InlineAlert variant="warning" icon={<AlertTriangle />} description={t("autoPostPrivate")} />
      )}
      <ChatChannelWriteError error={error} />
    </FormGrid>
  );
}

// ── Delete and leave ────────────────────────────────────────────────

interface ChatChannelConfirmProps {
  /** The channel; null keeps the dialog closed. */
  channel: ChatChannelResponse | null;
  onClose: () => void;
  /** Called right before the request, so an open view of the channel expects its 404. */
  onRemoving?: (channelId: string) => void;
  /** Called when that request fails: the channel stays, and a later loss of it is unexpected again. */
  onRemoveFailed?: (channelId: string) => void;
}

/** Confirms deleting a channel for everyone (OWNER and ADMIN); its content is hidden, not erased. */
export function ChatChannelDeleteDialog({ channel, onClose, onRemoving, onRemoveFailed }: ChatChannelConfirmProps) {
  const t = useTranslations("chat.channels");
  const deleteChannel = useDeleteChatChannel();
  const { reset } = deleteChannel;
  useEffect(() => reset(), [channel, reset]);

  const confirm = (event: MouseEvent) => {
    // Kept open until the server answers, so a failure stays in view.
    event.preventDefault();
    if (!channel) return;
    onRemoving?.(channel.id);
    deleteChannel.mutate(channel.id, {
      onSuccess: () => {
        toast.success(t("deleted", { name: channel.name }));
        onClose();
      },
      // The hook shows the failure; the dialog stays open with it.
      onError: () => onRemoveFailed?.(channel.id),
    });
  };

  return (
    <AlertDialog
      open={!!channel}
      onOpenChange={(open) => !open && !deleteChannel.isPending && onClose()}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t("delete.title", { name: channel?.name ?? "" })}</AlertDialogTitle>
          <AlertDialogDescription>
            {channel?.purpose === ChatChannelPurpose.DAILY_UPDATES
              ? t("delete.descriptionDailyUpdates")
              : channel?.purpose === ChatChannelPurpose.PULL_REQUESTS
                ? t("delete.descriptionPullRequests")
                : t("delete.description")}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <ChatChannelWriteError error={deleteChannel.error} />
        <AlertDialogFooter>
          <AlertDialogCancel disabled={deleteChannel.isPending}>
            {t("delete.cancel")}
          </AlertDialogCancel>
          <AlertDialogAction
            className={buttonVariants({ variant: "destructive" })}
            disabled={deleteChannel.isPending}
            onClick={confirm}
          >
            {t("delete.confirm")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

/** Confirms that the reader leaves a private channel; only an OWNER or ADMIN can add them again. */
export function ChatChannelLeaveDialog({ channel, onClose, onRemoving, onRemoveFailed }: ChatChannelConfirmProps) {
  const t = useTranslations("chat.channels");
  const meId = useChatMeId();
  const removeMember = useRemoveChatChannelMember();
  const { reset } = removeMember;
  useEffect(() => reset(), [channel, reset]);

  const confirm = (event: MouseEvent) => {
    event.preventDefault();
    if (!channel || !meId) return;
    onRemoving?.(channel.id);
    removeMember.mutate(
      { channelId: channel.id, userId: meId },
      {
        onSuccess: () => {
          toast.success(t("left", { name: channel.name }));
          onClose();
        },
        // The hook shows the failure; the dialog stays open with it.
        onError: () => onRemoveFailed?.(channel.id),
      }
    );
  };

  return (
    <AlertDialog
      open={!!channel}
      onOpenChange={(open) => !open && !removeMember.isPending && onClose()}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t("leave.title", { name: channel?.name ?? "" })}</AlertDialogTitle>
          <AlertDialogDescription>{t("leave.description")}</AlertDialogDescription>
        </AlertDialogHeader>
        <ChatChannelWriteError error={removeMember.error} />
        <AlertDialogFooter>
          <AlertDialogCancel disabled={removeMember.isPending}>
            {t("leave.cancel")}
          </AlertDialogCancel>
          <AlertDialogAction
            className={buttonVariants({ variant: "destructive" })}
            disabled={removeMember.isPending || !meId}
            onClick={confirm}
          >
            {t("leave.confirm")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

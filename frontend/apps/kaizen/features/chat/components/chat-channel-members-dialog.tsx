"use client";

import { useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { AlertCircle, Loader2, UserMinus } from "lucide-react";
import { isApiError } from "@oba/shared/api/axios";
import { SelectFieldStandalone } from "@oba/shared/features/form/components";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { UserAvatar } from "@/components/ui/user-avatar";
import { hasKaizenAccess } from "@/lib/kaizen-member-access";
import { useAddChatChannelMembers, useRemoveChatChannelMember } from "../hooks/use-chat-channels";
import { useChatMeId, useChatMembers } from "../hooks/use-chat-messages";
import {
  CHAT_CHANNEL_MAX_MEMBER_IDS,
  CHAT_ERROR_CODES,
  type ChatChannelResponse,
  type ChatMemberResponse,
} from "../schemas";
import { ChatChannelWriteError, memberDisplayName } from "./chat-channel-dialog";

interface ChatChannelMembersDialogProps {
  /** A private channel; null keeps the dialog closed. */
  channel: ChatChannelResponse | null;
  onClose: () => void;
}

/**
 * The member list of a private channel, for OWNER and ADMIN: add members of
 * the organization, remove members. An admin who is not on the list reads
 * neither the channel nor its members until they add themselves.
 */
export function ChatChannelMembersDialog({ channel, onClose }: ChatChannelMembersDialogProps) {
  const t = useTranslations("chat.channels.members");

  return (
    <Dialog open={!!channel} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>{t("title", { name: channel?.name ?? "" })}</DialogTitle>
          <DialogDescription>{t("description")}</DialogDescription>
        </DialogHeader>
        {channel && <MembersBody channel={channel} />}
      </DialogContent>
    </Dialog>
  );
}

function MembersBody({ channel }: { channel: ChatChannelResponse }) {
  const t = useTranslations("chat.channels.members");
  const tAccess = useTranslations("kaizen.memberAccess");
  const meId = useChatMeId();
  const {
    data: members,
    error,
    isPending,
    isError,
    isFetching,
    refetch,
  } = useChatMembers({ channelId: channel.id });
  const { data: organizationMembers, isLoading: organizationLoading } = useChatMembers();
  const addMembers = useAddChatChannelMembers();
  const removeMember = useRemoveChatChannelMember();
  const [selected, setSelected] = useState<string[]>([]);

  // The selection belongs to one channel.
  useEffect(() => setSelected([]), [channel.id]);

  const memberIds = useMemo(
    () => new Set((members ?? []).map((member) => member.userId)),
    [members]
  );
  // A member without Kaizen access is listed but cannot be picked: the server refuses to add them.
  const candidates = (organizationMembers ?? [])
    .filter((member) => !memberIds.has(member.userId))
    .map((member) => ({
      value: member.userId,
      label:
        member.userId === meId
          ? t("me", { name: memberDisplayName(member) })
          : memberDisplayName(member),
      disabled: !hasKaizenAccess(member),
      disabledLabel: tAccess("noAccess"),
    }));

  const add = (userIds: string[]) => {
    addMembers.mutate(
      { channelId: channel.id, userIds },
      {
        onSuccess: () => {
          toast.success(t("added", { count: userIds.length }));
          setSelected([]);
        },
      }
    );
  };

  const remove = (member: ChatMemberResponse) => {
    removeMember.mutate(
      { channelId: channel.id, userId: member.userId },
      { onSuccess: () => toast.success(t("removed", { name: memberDisplayName(member) })) }
    );
  };

  // The server hides the list from an admin who is not on it, as it hides the channel's content.
  const hiddenFromMe =
    !channel.member && isError && isApiError(error, CHAT_ERROR_CODES.channelNotFound);

  let list;
  if (hiddenFromMe) {
    list = (
      <div className="flex flex-col items-center gap-2 py-6 text-center">
        <p className="text-muted-foreground text-sm">{t("notOnList")}</p>
        <Button
          size="sm"
          disabled={!meId || addMembers.isPending}
          onClick={() => meId && add([meId])}
        >
          {t("addMe")}
        </Button>
      </div>
    );
  } else if (isPending) {
    list = (
      <div className="text-muted-foreground flex items-center justify-center gap-2 py-8 text-xs">
        <Loader2 className="h-4 w-4 animate-spin" />
        {t("loading")}
      </div>
    );
  } else if (isError && !members) {
    list = (
      <div role="alert" className="flex flex-col items-center gap-2 py-6 text-center">
        <AlertCircle className="text-destructive h-5 w-5" />
        <p className="text-foreground text-xs font-medium">{t("loadError")}</p>
        {error?.message && <p className="text-muted-foreground text-xs">{error.message}</p>}
        <Button
          variant="outline"
          size="sm"
          className="h-7"
          disabled={isFetching}
          onClick={() => void refetch()}
        >
          {t("retry")}
        </Button>
      </div>
    );
  } else if (members.length === 0) {
    list = <p className="text-muted-foreground py-6 text-center text-xs">{t("empty")}</p>;
  } else {
    list = (
      <ul className="flex max-h-[40vh] flex-col overflow-y-auto" aria-label={t("listLabel")}>
        {members.map((member) => {
          const name = memberDisplayName(member);
          const isMe = member.userId === meId;
          return (
            <li key={member.userId} className="flex items-center gap-2.5 rounded-md px-2 py-1.5">
              {/* `w-0 flex-1`: the name takes the width the row has left beside
                  the badge or button, but adds nothing to the row's minimum
                  width. Otherwise a long name widens the auto-sized dialog
                  instead of ending in an ellipsis. */}
              <UserAvatar
                user={{
                  firstName: member.firstName ?? undefined,
                  lastName: member.lastName ?? undefined,
                  email: member.email ?? undefined,
                }}
                size="sm"
                name={name}
                className="w-0 flex-1 gap-2.5"
                nameClassName="font-normal"
              />
              {isMe && (
                <Badge variant="outline" className="shrink-0 px-1.5 py-0 text-[10px] font-medium">
                  {t("you")}
                </Badge>
              )}
              {!isMe && (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={t("remove", { name })}
                  title={t("remove", { name })}
                  disabled={removeMember.isPending}
                  onClick={() => remove(member)}
                >
                  <UserMinus className="h-4 w-4" />
                </Button>
              )}
            </li>
          );
        })}
      </ul>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
        <SelectFieldStandalone
          isMulti
          label={t("add")}
          value={selected}
          onValueChange={(next) => setSelected(next.slice(0, CHAT_CHANNEL_MAX_MEMBER_IDS))}
          options={candidates}
          isLoading={organizationLoading}
          placeholder={t("addPlaceholder")}
          searchPlaceholder={t("addSearch")}
          disabled={addMembers.isPending}
          className="min-w-0 flex-1"
        />
        <Button
          disabled={selected.length === 0 || addMembers.isPending}
          onClick={() => add(selected)}
        >
          {t("addButton")}
        </Button>
      </div>
      <ChatChannelWriteError error={addMembers.error ?? removeMember.error} />
      {list}
    </div>
  );
}

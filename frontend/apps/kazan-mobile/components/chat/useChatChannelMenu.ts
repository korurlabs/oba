import { useRouter } from "expo-router";
import {
  LogOut,
  Pencil,
  Pin,
  Plus,
  Trash2,
  UserCog,
  Users,
  type LucideIcon,
} from "lucide-react-native";
import * as React from "react";
import { Alert } from "react-native";

import {
  chatChannelDeleteMessageKey,
  chatChannelGoneExpected,
  type ChatChannelMenuActionId,
  type ChatChannelRemovalState,
} from "@/components/chat/chatChannelMenuModel";
import type { ActionSheetAction } from "@/components/kazan/actionSheetModel";
import { useChatMeId } from "@/hooks/queries/chat";
import { useDeleteChatChannel, useRemoveChatChannelMember } from "@/hooks/queries/chatMutations";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation, type TranslationKey } from "@/i18n";
import type { ChatChannelResponse } from "@/lib/schemas/chat";

/** The label and Android icon of each channel menu row. */
const MENU_ROWS: Record<ChatChannelMenuActionId, { label: TranslationKey; icon: LucideIcon }> = {
  members: { label: "chatMembersOpen", icon: Users },
  pins: { label: "chatPinsOpen", icon: Pin },
  create: { label: "chatNewChannel", icon: Plus },
  change: { label: "chatChannelChange", icon: Pencil },
  manageMembers: { label: "chatChannelManageMembers", icon: UserCog },
  leave: { label: "chatChannelLeave", icon: LogOut },
  delete: { label: "chatChannelDelete", icon: Trash2 },
};

export interface ChatChannelMenu {
  /** The reader's delete or leave of the shown channel succeeded; the screen is closing. */
  leaving: boolean;
  /** The reader's delete or leave of the shown channel is on the way or done. */
  removal: ChatChannelRemovalState;
  /** True when a "not found" for the shown channel is the reader's own doing. */
  goneExpected: boolean;
  /** The menu rows of `ids` for `channel`, in the order given. */
  rows: (
    channel: ChatChannelResponse,
    ids: readonly ChatChannelMenuActionId[]
  ) => ActionSheetAction[];
}

/**
 * The channel menu of a screen that shows one channel (`shownChannelId`):
 * its rows, the Leave and Delete confirmations, and the way back to Chat home
 * once either succeeds. A delete or leave marks the channel's "not found" as
 * expected before the request is sent, since a realtime refresh can read the
 * channel before the request answers; a refusal clears the mark again, so a
 * later real loss still shows. On success the screen leaves before the lists
 * are read again, unless it shows another channel or is gone by then. The
 * hooks toast the result and every refusal, since the alert is closed by then.
 */
export function useChatChannelMenu(shownChannelId: string): ChatChannelMenu {
  const { t } = useTranslation();
  const router = useRouter();
  const meId: string | undefined = useChatMeId();
  const online = useOnlineWriteGuard();
  const [removingId, setRemovingId] = React.useState<string | null>(null);
  const [leaving, setLeaving] = React.useState<boolean>(false);
  // The request's callbacks read these; state would be a render behind.
  const removingRef = React.useRef<string | null>(null);
  const shownRef = React.useRef<string>(shownChannelId);
  shownRef.current = shownChannelId;
  const mounted = React.useRef<boolean>(true);
  React.useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const markRemoving = (channelId: string | null): void => {
    removingRef.current = channelId;
    setRemovingId(channelId);
  };

  const leaveRemovedChannel = React.useCallback((): void => {
    const removed: string | null = removingRef.current;
    removingRef.current = null;
    if (!mounted.current) return;
    if (removed !== shownRef.current) {
      setRemovingId(null);
      return;
    }
    setLeaving(true);
    router.dismissTo("/chat");
  }, [router]);
  const { mutate: deleteChannel, isPending: deleting } = useDeleteChatChannel({
    onBeforeInvalidate: leaveRemovedChannel,
  });
  const { mutate: removeMember, isPending: removing } = useRemoveChatChannelMember({
    onBeforeInvalidate: leaveRemovedChannel,
  });
  // The hooks log and toast the refusal; here only the expected "not found" is taken back.
  const onRemoveFailed = (): void => {
    if (mounted.current) markRemoving(null);
  };

  const confirmLeave = (target: ChatChannelResponse): void => {
    Alert.alert(t("chatChannelLeaveTitle", { name: target.name }), t("chatChannelLeaveBody"), [
      { text: t("chatCancel"), style: "cancel" },
      {
        text: t("chatChannelLeaveConfirm"),
        style: "destructive",
        onPress: () => {
          if (!meId || !online()) return;
          markRemoving(target.id);
          removeMember({ channelId: target.id, userId: meId }, { onError: onRemoveFailed });
        },
      },
    ]);
  };

  const confirmDelete = (target: ChatChannelResponse): void => {
    Alert.alert(
      t("chatChannelDeleteTitle", { name: target.name }),
      t(chatChannelDeleteMessageKey(target.purpose)),
      [
        { text: t("chatCancel"), style: "cancel" },
        {
          text: t("chatChannelDeleteConfirm"),
          style: "destructive",
          onPress: () => {
            if (!online()) return;
            markRemoving(target.id);
            deleteChannel(target.id, { onError: onRemoveFailed });
          },
        },
      ]
    );
  };

  const rowAction = (id: ChatChannelMenuActionId, target: ChatChannelResponse): (() => void) => {
    const channelId: string = target.id;
    switch (id) {
      case "members":
        return () => router.push({ pathname: "/chat/members", params: { channelId } });
      case "pins":
        return () => router.push({ pathname: "/chat/pins", params: { channelId } });
      case "create":
        return () => router.push({ pathname: "/chat/new-channel", params: { channelId } });
      case "change":
        return () => router.push({ pathname: "/chat/edit-channel", params: { channelId } });
      case "manageMembers":
        return () => router.push({ pathname: "/chat/channel-members", params: { channelId } });
      case "leave":
        return () => confirmLeave(target);
      case "delete":
        return () => confirmDelete(target);
    }
  };

  const removalPending: boolean = deleting || removing;
  const rows = (
    channel: ChatChannelResponse,
    ids: readonly ChatChannelMenuActionId[]
  ): ActionSheetAction[] =>
    ids.map((id) => ({
      label: t(MENU_ROWS[id].label),
      icon: MENU_ROWS[id].icon,
      destructive: id === "delete",
      // Leaving needs the reader's id; a delete or leave already sent is not sent twice.
      disabled:
        (id === "leave" && !meId) || ((id === "leave" || id === "delete") && removalPending),
      onPress: rowAction(id, channel),
    }));

  const removal: ChatChannelRemovalState = { removingId, leaving };
  return {
    leaving,
    removal,
    goneExpected: chatChannelGoneExpected(removal, shownChannelId),
    rows,
  };
}

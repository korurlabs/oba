"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { arrayMove } from "@dnd-kit/sortable";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { useUpdateMe } from "@oba/shared/features/users/hooks/use-update-me";
import type { UserPreferences } from "@oba/shared/features/users/schema";
import { ChatChannelPurpose, type ChatChannelResponse } from "../schemas";
import { useChatChannels } from "./use-chat-channels";
import { useChatOrganizationId } from "./use-chat-messages";

/** Key in `userPreferences`: the reader's channel order per organization, `{ [orgId]: channelId[] }`. */
const CHAT_CHANNEL_ORDER_KEY = "chatChannelOrder";

/** A burst of moves (Move down, Move down) is saved once, after the last. */
const ORDER_SAVE_DELAY_MS = 1000;

/** The saved channel order of one organization; empty when none is saved or the value is not a list of ids. */
export function savedChatChannelOrder(
  preferences: UserPreferences | null | undefined,
  orgId: string
): string[] {
  const byOrganization = preferences?.[CHAT_CHANNEL_ORDER_KEY];
  if (!byOrganization || typeof byOrganization !== "object") return [];
  const order = (byOrganization as Record<string, unknown>)[orgId];
  return Array.isArray(order) ? order.filter((id): id is string => typeof id === "string") : [];
}

/**
 * The channels in the reader's order: the saved ids first, then every other
 * channel in server (creation) order. A saved id the reader no longer sees
 * (deleted, left, made private) is skipped, and a repeated id counts once.
 */
export function orderChatChannels(
  channels: ChatChannelResponse[],
  savedOrder: string[]
): ChatChannelResponse[] {
  const byId = new Map(channels.map((channel) => [channel.id, channel]));
  const placed = new Set<string>();
  const ordered: ChatChannelResponse[] = [];
  for (const id of savedOrder) {
    const channel = byId.get(id);
    if (!channel || placed.has(id)) continue;
    placed.add(id);
    ordered.push(channel);
  }
  for (const channel of channels) {
    if (!placed.has(channel.id)) ordered.push(channel);
  }
  return ordered;
}

/**
 * The first channel the reader can read in their order, of the given purpose
 * when one is given. OWNER and ADMIN see private channels they are not on
 * (`member` false) and read none of their content, so such a channel is never
 * opened by default.
 */
export function firstReadableChatChannel(
  ordered: ChatChannelResponse[],
  excludeIds: ReadonlySet<string> = new Set(),
  purpose?: ChatChannelPurpose
): ChatChannelResponse | undefined {
  return ordered.find(
    (channel) =>
      channel.member && !excludeIds.has(channel.id) && (!purpose || channel.purpose === purpose)
  );
}

/**
 * The reader's channel list in their own order, and the moves that change it.
 * `channels` holds every channel; `listedChannels` only the GENERAL ones,
 * which the channel list shows and moves among. The channels of the other
 * purposes keep their place in the saved order, so a move never needs them.
 *
 * The order is a preference of the user, stored in `userPreferences` through
 * `PUT /users/me` next to the other preferences, which are kept as they are.
 * A move shows at once; the save follows shortly after, and a failed save is
 * toasted, because the new order stays on screen and would otherwise be taken
 * for saved and lost on the next page load.
 */
export function useChatChannelOrder() {
  const t = useTranslations("chat.channels");
  const orgId = useChatOrganizationId();
  const channelsQuery = useChatChannels();
  const meQuery = useGetMe();
  const meResponse = meQuery.data;
  const updateMe = useUpdateMe();

  const preferences = meResponse?.data?.userPreferences;
  const savedOrder = useMemo(
    () => (orgId ? savedChatChannelOrder(preferences, orgId) : []),
    [preferences, orgId]
  );
  // The order of the last move until the saved preferences come back with it.
  const [localOrder, setLocalOrder] = useState<string[] | null>(null);

  const channels = useMemo(
    () => orderChatChannels(channelsQuery.data ?? [], localOrder ?? savedOrder),
    [channelsQuery.data, localOrder, savedOrder]
  );
  const listedChannels = useMemo(
    () => channels.filter((channel) => channel.purpose === ChatChannelPurpose.GENERAL),
    [channels]
  );

  // The latest preferences, so a delayed save merges into what the server holds now.
  const preferencesRef = useRef(preferences);
  useEffect(() => {
    preferencesRef.current = preferences;
  }, [preferences]);

  const saveTimerRef = useRef<ReturnType<typeof setTimeout>>(undefined);
  useEffect(() => () => clearTimeout(saveTimerRef.current), []);

  const { mutateAsync: saveMe } = updateMe;
  const scheduleSave = useCallback(
    (order: string[]) => {
      if (!orgId) return;
      clearTimeout(saveTimerRef.current);
      saveTimerRef.current = setTimeout(async () => {
        const current = (preferencesRef.current ?? {}) as Record<string, unknown>;
        const byOrganization = current[CHAT_CHANNEL_ORDER_KEY];
        const orders = byOrganization && typeof byOrganization === "object" ? byOrganization : {};
        try {
          await saveMe({
            userPreferences: {
              ...current,
              [CHAT_CHANNEL_ORDER_KEY]: { ...orders, [orgId]: order },
            },
          });
        } catch (error) {
          console.error("[useChatChannelOrder] channel order save failed:", error);
          toast.error(t("orderSaveError"));
        }
      }, ORDER_SAVE_DELAY_MS);
    },
    [orgId, saveMe, t]
  );

  /**
   * Moves a channel to the place of another (a drop). The ids are those of the
   * whole order, so a channel of another purpose between the two keeps its
   * place relative to the channels around it.
   */
  const moveChannel = useCallback(
    (activeId: string, overId: string) => {
      const ids = channels.map((channel) => channel.id);
      const from = ids.indexOf(activeId);
      const to = ids.indexOf(overId);
      if (from < 0 || to < 0 || from === to) return;
      const order = arrayMove(ids, from, to);
      setLocalOrder(order);
      scheduleSave(order);
    },
    [channels, scheduleSave]
  );

  /**
   * Moves a listed channel one place up (-1) or down (+1) among the listed
   * channels, for the row menu and the keyboard.
   */
  const moveChannelBy = useCallback(
    (channelId: string, offset: -1 | 1) => {
      const index = listedChannels.findIndex((channel) => channel.id === channelId);
      const target = listedChannels[index + offset];
      if (index < 0 || !target) return;
      moveChannel(channelId, target.id);
    },
    [listedChannels, moveChannel]
  );

  return {
    ...channelsQuery,
    channels,
    listedChannels,
    /** The saved order is loaded, or could not be: then the channels keep server order. */
    orderKnown: meQuery.isSuccess || meQuery.isError,
    moveChannel,
    moveChannelBy,
  };
}

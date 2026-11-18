import { useQueryClient, type QueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { UserMe, UserPreferences } from "@oba/shared/features/users/schema";

import { saveUserPreferences, userKeys, useMe } from "@/hooks/queries/users";
import { moveChatChannel } from "@/components/chat/chatChannelReorder";
import { toast } from "@/components/ui/toast";
import { useChatChannels } from "@/hooks/queries/chat";
import {
  createChatOrderSaver,
  type ChatOrderSaver,
  type PendingChatOrder,
} from "@/hooks/queries/chatOrderSaver";
import { translate } from "@/i18n";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import type { ChatChannelResponse } from "@/lib/schemas/chat";

/** Key in `userPreferences`: the channel order per organization, `{ [orgId]: channelId[] }`. */
const CHAT_CHANNEL_ORDER_KEY = "chatChannelOrder";
/** A burst of moves is saved once, after the last. */
const ORDER_SAVE_DELAY_MS = 1000;

/** The saved order of one organization; empty when none is saved or it is not a list of IDs. */
export function savedChatChannelOrder(
  preferences: UserPreferences | null | undefined,
  orgId: string
): string[] {
  const byOrganization: unknown = preferences?.[CHAT_CHANNEL_ORDER_KEY];
  if (!byOrganization || typeof byOrganization !== "object") return [];
  const order: unknown = (byOrganization as Record<string, unknown>)[orgId];
  return Array.isArray(order) ? order.filter((id): id is string => typeof id === "string") : [];
}

/**
 * The channels in the reader's order: the saved IDs first, then every other
 * channel in server order. A saved ID the reader no longer sees is skipped,
 * and a repeated ID counts once. The web app applies the same order.
 */
export function orderChatChannels(
  channels: readonly ChatChannelResponse[],
  savedOrder: readonly string[]
): ChatChannelResponse[] {
  const byId = new Map<string, ChatChannelResponse>(
    channels.map((channel) => [channel.id, channel])
  );
  const placed = new Set<string>();
  const ordered: ChatChannelResponse[] = [];
  for (const id of savedOrder) {
    const channel: ChatChannelResponse | undefined = byId.get(id);
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
 * The reader's channel order and the move that changes it. A move shows at
 * once; the save through `PUT /users/me` follows after a second, or at once
 * when the switcher closes first, and keeps the other preferences. A failed
 * save is toasted, because the new order stays on screen and would otherwise
 * be taken for saved.
 */
export function useChatChannelOrder() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const channelsQuery = useChatChannels();
  const preferences: UserPreferences | null | undefined = useMe(true).data?.userPreferences;
  const savedOrder: string[] = useMemo(
    () => (orgId ? savedChatChannelOrder(preferences, orgId) : []),
    [preferences, orgId]
  );
  // The order of the last move until the saved preferences come back with it.
  const [localOrder, setLocalOrder] = useState<{ orgId: string | null; ids: string[] } | null>(
    null
  );
  const activeLocalOrder: string[] | null = localOrder?.orgId === orgId ? localOrder.ids : null;
  const channels: ChatChannelResponse[] = useMemo(
    () => orderChatChannels(channelsQuery.data ?? [], activeLocalOrder ?? savedOrder),
    [channelsQuery.data, activeLocalOrder, savedOrder]
  );

  const preferencesRef = useRef<UserPreferences | null | undefined>(preferences);
  useEffect(() => {
    preferencesRef.current = preferences;
  }, [preferences]);

  // Reads only refs and the query client, so it may still run once the switcher is gone.
  const saveOrder = useCallback(
    ({ organizationId, order }: PendingChatOrder) => {
      const current: Record<string, unknown> = preferencesRef.current ?? {};
      const byOrganization: unknown = current[CHAT_CHANNEL_ORDER_KEY];
      const orders: object =
        byOrganization && typeof byOrganization === "object" ? byOrganization : {};
      saveUserPreferences({
        ...current,
        [CHAT_CHANNEL_ORDER_KEY]: { ...orders, [organizationId]: order },
      })
        .then((me: UserMe) => {
          queryClient.setQueryData(userKeys.me, me);
        })
        .catch((error: unknown) => {
          console.warn(
            "[chat] channel order save failed",
            error instanceof Error ? error.message : error
          );
          toast.error(translate("chatOrderSaveError"));
        });
    },
    [queryClient]
  );
  const saver: ChatOrderSaver = useMemo(
    () => createChatOrderSaver(saveOrder, ORDER_SAVE_DELAY_MS),
    [saveOrder]
  );
  useEffect(() => () => saver.flush(), [saver]);

  const move = useCallback(
    (channelId: string, steps: number) => {
      if (!orgId) return;
      const order: string[] | null = moveChatChannel(channels, channelId, steps);
      if (!order) return;
      setLocalOrder({ orgId, ids: order });
      saver.schedule({ organizationId: orgId, order });
    },
    [channels, orgId, saver]
  );

  return { query: channelsQuery, channels, move };
}

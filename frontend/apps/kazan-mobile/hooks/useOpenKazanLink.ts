import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "expo-router";
import { useCallback } from "react";
import { Linking } from "react-native";

import { boardHref } from "@/components/boards/boardRoutes";
import { isSafeChatUrl } from "@/components/chat/chatMarkdown";
import { hostOf } from "@/components/ticket/ticketModel";
import { toast } from "@/components/ui/toast";
import { chatKeys } from "@/hooks/queries/chat";
import { getChatMessage, listChatChannels } from "@/hooks/queries/chatApi";
import { boardKeys, fetchSprints, useResolveBoardSlug } from "@/hooks/queries/boards";
import { useTranslation } from "@/i18n";
import { isApiError } from "@/lib/api";
import { openInAppBrowser } from "@/lib/browser";
import { ENV } from "@/lib/env";
import {
  chatLinkLookupOutcome,
  chatLinkOutcome,
  findLinkChannel,
  kazanLinkDirectPath,
  linkSprintId,
  parseKazanLink,
  type ChatLinkOutcome,
  type KazanLinkTarget,
} from "@/lib/kazanLinks";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import type { Sprint } from "@/lib/schemas/boards";
import type { ChatChannelResponse, ChatMessageResponse } from "@/lib/schemas/chat";

/**
 * A chat link that the screen it was tapped on can show itself, such as a
 * message of the channel already open: the handler returns true when it did.
 */
export type LocalChatLinkHandler = (
  target: Extract<KazanLinkTarget, { kind: "channel" }>
) => boolean;

const KAZAN_WEB_HOST: string | null = hostOf(ENV.kazanWebUrl);

function messageOf(error: unknown): unknown {
  return error instanceof Error ? error.message : error;
}

/**
 * Opens a link tapped inside the app. A Kazan web link to a screen the app
 * has (a ticket, a board, a channel, a message or a thread, the forms and
 * tabs) opens that screen, never the web page; anything else opens in the
 * in-app browser, and a mail link in the mail app. Boards, sprints and
 * channels are looked up first (`linkSprintId`, `chatLinkOutcome`): a sprint
 * the board no longer has opens the board on its default sprint; a private
 * channel the reader is not on opens its screen, which says so; a channel or
 * message that cannot be found is toasted as such, and any other failed
 * lookup is logged and toasted.
 */
export function useOpenKazanLink() {
  const { t } = useTranslation();
  const router = useRouter();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const resolveBoardSlug = useResolveBoardSlug();

  const showOutcome = useCallback(
    (outcome: ChatLinkOutcome): void => {
      switch (outcome.kind) {
        case "open":
          router.push(outcome.href);
          return;
        case "channelNotFound":
          toast.error(t("chatChannelNotFound"));
          return;
        case "messageGone":
          toast.error(t("chatMessageGone"));
          return;
      }
    },
    [router, t]
  );

  const openTarget = useCallback(
    async (target: KazanLinkTarget): Promise<void> => {
      const direct: string | null = kazanLinkDirectPath(target);
      if (direct) {
        router.push(direct);
        return;
      }
      if (target.kind === "board") {
        const boardId: string = await resolveBoardSlug(target.boardSlug);
        let sprintId: string | null = null;
        if (target.sprint) {
          if (!orgId) throw new Error("No active organization");
          // Cached under the board screen's own key, so the screen shows its sprints at once.
          const sprints: Sprint[] = await queryClient.fetchQuery({
            queryKey: boardKeys.sprints(orgId, boardId),
            queryFn: () => fetchSprints(boardId),
          });
          sprintId = linkSprintId(sprints, target.sprint);
        }
        router.push(boardHref(boardId, { view: target.view, sprintId }));
        return;
      }
      if (target.kind !== "channel") return;
      if (!orgId) throw new Error("No active organization");
      const channels: ChatChannelResponse[] = await queryClient.fetchQuery({
        queryKey: chatKeys.channels(orgId),
        queryFn: listChatChannels,
      });
      let channel: ChatChannelResponse | undefined = target.channel
        ? findLinkChannel(channels, target.channel)
        : undefined;
      if (!channel && target.messageId) {
        // An old link names no channel, and a renamed channel has a new slug; the message names it.
        let message: ChatMessageResponse;
        try {
          message = await getChatMessage(target.messageId, true);
        } catch (error: unknown) {
          const refused: ChatLinkOutcome | null = isApiError(error)
            ? chatLinkLookupOutcome({ code: error.code, errorValues: error.errorValues }, channels)
            : null;
          if (!refused) throw error;
          console.warn("[links] the message of a chat link was refused", messageOf(error));
          showOutcome(refused);
          return;
        }
        channel = channels.find((item) => item.id === message.channelId);
      }
      showOutcome(chatLinkOutcome(channel, target));
    },
    [orgId, queryClient, resolveBoardSlug, router, showOutcome]
  );

  const openLink = useCallback(
    (url: string, onLocalChatLink?: LocalChatLinkHandler): void => {
      if (!isSafeChatUrl(url)) return;
      if (/^mailto:/i.test(url)) {
        Linking.openURL(url).catch((error: unknown) => {
          console.warn("[links] opening a mail link failed", messageOf(error));
          toast.error(t("chatOpenLinkFailed"));
        });
        return;
      }
      const target: KazanLinkTarget | null = parseKazanLink(url, KAZAN_WEB_HOST);
      if (!target) {
        openInAppBrowser(url).catch((error: unknown) => {
          console.warn("[links] opening a web link failed", messageOf(error));
          toast.error(t("chatOpenLinkFailed"));
        });
        return;
      }
      if (target.kind === "channel" && onLocalChatLink?.(target)) return;
      openTarget(target).catch((error: unknown) => {
        console.warn("[links] opening a Kazan link failed", url, messageOf(error));
        toast.error(t("chatOpenLinkFailed"));
      });
    },
    [openTarget, t]
  );

  return { openLink, openTarget };
}

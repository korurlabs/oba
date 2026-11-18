import * as Clipboard from "expo-clipboard";
import { useRouter } from "expo-router";
import { useCallback } from "react";
import { Platform, Share } from "react-native";

import { ticketWebPath } from "@/components/ticket/ticketModel";
import { ticketHref } from "@/components/ticket/ticketRoutes";
import { ticketShareContent, type TicketShareSubject } from "@/components/ticket/ticketShareModel";
import { toast } from "@/components/ui/toast";
import { useTranslation, type TranslationKey } from "@/i18n";
import { kaizenWebUrl, openInAppBrowser } from "@/lib/browser";

/**
 * Opens and shares tickets: the native ticket screen, the ticket's Kaizen web
 * page in the in-app browser, Copy link, Copy key and the system share menu.
 * A link, copy or share that fails is logged and toasted here.
 */
export function useTicketLinks() {
  const router = useRouter();
  const { t, language } = useTranslation();

  const openTicket = useCallback(
    (ticketKey: string) => router.push(ticketHref(ticketKey)),
    [router]
  );

  /** The address Copy link shares: the ticket's Kaizen web page in the app language. */
  const ticketWebUrl = useCallback(
    (ticketKey: string): string => kaizenWebUrl(ticketWebPath(language, ticketKey)),
    [language]
  );

  const openTicketOnWeb = useCallback(
    (ticketKey: string) => {
      openInAppBrowser(ticketWebUrl(ticketKey)).catch((error: unknown) => {
        console.warn("[ticket] web page failed", error instanceof Error ? error.message : error);
        toast.error(t("homeOpenFailed"));
      });
    },
    [t, ticketWebUrl]
  );

  /** Puts `text` on the clipboard and confirms it, or says it failed. */
  const copyText = useCallback(
    (text: string, copiedKey: TranslationKey, failedKey: TranslationKey) => {
      Clipboard.setStringAsync(text)
        .then((copied: boolean) => {
          if (!copied) throw new Error("The clipboard refused the text");
          toast.success(t(copiedKey));
        })
        .catch((error: unknown) => {
          console.warn("[ticket] copy failed", error instanceof Error ? error.message : error);
          toast.error(t(failedKey));
        });
    },
    [t]
  );

  const copyTicketLink = useCallback(
    (ticketKey: string) =>
      copyText(ticketWebUrl(ticketKey), "ticketLinkCopied", "ticketCopyFailed"),
    [copyText, ticketWebUrl]
  );

  const copyTicketKey = useCallback(
    (ticketKey: string) => copyText(ticketKey, "ticketKeyCopied", "ticketKeyCopyFailed"),
    [copyText]
  );

  /**
   * Opens the system share menu with the ticket's key, title and web link.
   * Resolves true once the reader shared it, false when they closed the menu
   * or it failed to open; a failure is toasted here.
   */
  const shareTicket = useCallback(
    async (ticket: TicketShareSubject): Promise<boolean> => {
      try {
        const result = await Share.share(
          ticketShareContent(ticket, ticketWebUrl(ticket.key), Platform.OS)
        );
        return result.action === Share.sharedAction;
      } catch (error: unknown) {
        console.warn("[ticket] share failed", error instanceof Error ? error.message : error);
        toast.error(t("ticketShareFailed"));
        return false;
      }
    },
    [t, ticketWebUrl]
  );

  return {
    openTicket,
    openTicketOnWeb,
    copyTicketLink,
    copyTicketKey,
    shareTicket,
    ticketWebUrl,
  };
}

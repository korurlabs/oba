"use client";

/**
 * useCopyTicketLink
 *
 * Returns a stable handler that copies a ticket's shareable URL to the
 * clipboard and shows a toast confirmation. The link is the short ticket
 * route (`/ticket/<KEY>`), which opens from anywhere and redirects to the
 * ticket's page under its board, so it stays valid when the board's slug
 * changes. A ticket moved to another board gets a new key, and its old link
 * then shows the not-found screen.
 */

import { useCallback } from "react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import type { TicketListResponse } from "@/features/tickets/schemas";
import { buildTicketShareLink } from "./use-ticket-deep-link";

export function useCopyTicketLink() {
  const t = useTranslations();

  return useCallback(
    async (ticket: Pick<TicketListResponse, "id" | "key" | "board">) => {
      const url = buildTicketShareLink(ticket);
      if (!url) return;
      // `navigator.clipboard` is undefined in non-secure contexts (HTTP)
      // and on older browsers. Fail gracefully with the same toast we'd
      // surface for any other copy failure rather than throwing a
      // TypeError into the unhandled rejection log.
      if (!navigator.clipboard?.writeText) {
        toast.error(t("kazan.tickets.messages.linkCopyError"));
        return;
      }
      try {
        await navigator.clipboard.writeText(url);
        toast.success(t("kazan.tickets.messages.linkCopied"));
      } catch {
        toast.error(t("kazan.tickets.messages.linkCopyError"));
      }
    },
    [t],
  );
}

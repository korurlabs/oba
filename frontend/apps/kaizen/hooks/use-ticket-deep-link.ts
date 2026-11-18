"use client";

/**
 * Ticket share links and the legacy `?id=<TICKET-KEY>` deep link
 *
 * A ticket is shared as its short key link, `/ticket/<KEY>`, which the app
 * redirects to the ticket's page under its board. Before tickets were
 * addressed by key in the dialog, the open ticket lived in a `?id=SEN-123`
 * query param on the list page; such links must keep working, so
 * `useLegacyTicketDeepLink` rewrites `?id=KEY` into the dialog's own
 * `t_ticket=KEY`, and the dialog resolves the key (and reports a key that
 * does not resolve).
 */

import { useEffect } from "react";
import { useSearchParams } from "next/navigation";
import { useTicketNavigation } from "@/features/tickets/hooks/use-ticket-navigation";
import { ticketKeyPath } from "@/features/tickets/lib/ticket-route-params";

export const TICKET_DEEP_LINK_PARAM = "id";

/**
 * Builds the shareable link for a ticket: `<origin>/ticket/<KEY>`.
 *
 * No query params are carried, so a colleague opening the link gets a clean
 * ticket view rather than the sender's filter/sprint/backlog state, and the
 * full page's Close falls back to the ticket's board.
 *
 * Returns `null` when invoked on the server.
 */
export function buildTicketShareLink(ticket: { key: string }): string | null {
  if (typeof window === "undefined") return null;
  return `${window.location.origin}${ticketKeyPath(ticket.key)}`;
}

/**
 * Mount on every list page. Rewrites an inbound `?id=KEY` into
 * `t_ticket=KEY` in place, without a history entry, which opens the dialog
 * on the same list.
 */
export function useLegacyTicketDeepLink() {
  const searchParams = useSearchParams();
  const urlKey = searchParams.get(TICKET_DEEP_LINK_PARAM)?.trim() ?? "";
  const { replaceOpenTicket } = useTicketNavigation();

  useEffect(() => {
    if (urlKey) replaceOpenTicket(urlKey);
  }, [urlKey, replaceOpenTicket]);
}

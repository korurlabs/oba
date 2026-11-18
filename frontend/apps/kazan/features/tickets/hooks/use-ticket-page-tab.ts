"use client";

/**
 * useTicketPageTab
 *
 * Which section of the ticket FULL PAGE is open, as URL state.
 *
 * The page body used to be one long scroll — title, description,
 * attachments and sub-tickets stacked under a thick header — which is what
 * made it feel cramped. It is split into tabs instead, and the active tab is
 * a search param so a refresh (or a shared link) lands on the same section.
 *
 * `t_tab` follows the same `t_` prefix as the dialog's params, but it is NOT
 * one of them: `TICKET_ROUTE_PARAM` / `OWNED_PARAMS` describe what the DIALOG
 * owns and clears on close, and this param belongs to the page shell only.
 * Keeping it out of that set is what stops opening or closing the dialog from
 * touching it.
 *
 * nuqs clears the param when it is at its default, so the plain ticket url
 * stays `?`-free until the user actually switches tab.
 */

import { parseAsStringLiteral, useQueryState } from "nuqs";

/** Search-param key. Page shell only — see the note above. */
export const TICKET_PAGE_TAB_PARAM = "t_tab";

/**
 * The tabs the page ships. There is deliberately no "activity" tab: kazan
 * has no comments or activity feature (only an unused
 * `ticketCommentResponseSchema` type — no endpoint, no hook, no UI), so a
 * third tab would be a permanently empty placeholder.
 */
export const TICKET_PAGE_TABS = ["details", "attachments"] as const;

export type TicketPageTab = (typeof TICKET_PAGE_TABS)[number];

export const TICKET_PAGE_TAB_DEFAULT: TicketPageTab = "details";

export function useTicketPageTab() {
  return useQueryState(
    TICKET_PAGE_TAB_PARAM,
    parseAsStringLiteral(TICKET_PAGE_TABS).withDefault(TICKET_PAGE_TAB_DEFAULT),
  );
}

/**
 * Ticket route contract (boundary-safe).
 *
 * This module is intentionally NOT marked "use client": the server
 * components under `app/[locale]/(dashboard)/…/ticket/…` import it to parse
 * their `searchParams`. If it lived in the "use client" navigation hook,
 * every export would become a client-reference proxy and the functions
 * would stop being callable on the server (see server-prefetch-convention).
 *
 * The `t_` prefix keeps these from colliding with the list params of the
 * same meaning (the All tab's `sprint`, the `statuses` filter), which the
 * list pages keep in the very same query string.
 */

import { TicketStatus } from "@/types/enums";

export const TICKET_ROUTE_PARAM = {
  /**
   * The ticket the DIALOG is showing, layered on top of whatever list route
   * the user is already on. Either a ticket key (`ACME-12`), or the literal
   * `TICKET_MODAL_CREATE` ("new") for the create form. A UUID here comes
   * from a link made before tickets were addressed by key; it needs `board`
   * beside it and is rewritten to the key once the ticket has loaded.
   *
   * This is what makes a refresh keep the user where they were: the dialog is
   * a search param on the LIST url, not a route of its own, so a fresh
   * document request re-renders the list and the modal slot re-opens the
   * dialog from the param. The standalone `/board/<slug>/ticket/<KEY>` page is
   * still a real route; the dialog's expand button navigates to it.
   */
  ticket: "t_ticket",
  /** Create the new ticket as a sub-ticket of the ticket with this key. */
  parent: "t_parent",
  /** Initial status for create (the kanban column the user clicked "+" in). */
  status: "t_status",
  /** Sprint placement for create, as the sprint's slug on the board. Mutually exclusive with `backlog`. */
  sprint: "t_sprint",
  /** Backlog placement for create, `TICKET_BACKLOG_PLACEMENT`. Mutually exclusive with `sprint`. */
  backlog: "t_backlog",
  /**
   * Board pre-selected in the create form, as its slug. The user may change
   * it. Old links also carry it beside a ticket UUID in `ticket`.
   */
  board: "t_board",
} as const;

/**
 * Return marker of the full page from before the return path moved to
 * session storage. Still stripped from urls so an old link does not carry it
 * further, but never read.
 */
export const LEGACY_TICKET_FROM_PARAM = "t_from";

export const TICKET_CREATE_PATH = "/ticket/new";

/**
 * `t_ticket` value that means "show the CREATE form" rather than an existing
 * ticket. A ticket key is `PREFIX-N`, so the literal can never collide with one.
 */
export const TICKET_MODAL_CREATE = "new";

/**
 * `t_backlog` value that places a new ticket in the board's backlog. A board
 * has exactly one backlog, so the flag needs no id.
 */
export const TICKET_BACKLOG_PLACEMENT = "1";

/** The full ticket page, `/board/<board-slug>/ticket/<KEY>`. */
export function ticketDetailPath(boardSlug: string, ticketKey: string): string {
  return `/board/${boardSlug}/ticket/${ticketKey}`;
}

/**
 * The short ticket link, `/ticket/<KEY>`, for places that know only the key.
 * The route redirects to the ticket's page under its board.
 */
export function ticketKeyPath(ticketKey: string): string {
  return `/ticket/${ticketKey}`;
}

/** True for every ticket route: `/ticket/new`, `/ticket/<KEY>` and `/board/x/ticket/y`. */
export function isTicketRoutePath(pathname: string): boolean {
  return /(^|\/)ticket\/[^/]+$/.test(pathname);
}

/**
 * Narrows a stored return path to an app-relative path, so a value planted
 * in session storage cannot turn the full page's Close button into an open
 * redirect.
 *
 * Prefix checks alone are NOT enough. The WHATWG URL parser strips ASCII
 * tab, LF and CR from ANYWHERE in the input before it resolves, so a value
 * that is same-origin to `startsWith` becomes protocol-relative by the time
 * the router resolves it:
 *
 *   "/\t/evil.com"  ->  startsWith("//") === false   (looks safe)
 *                    ->  new URL(...).href === "https://evil.com/"
 *
 * Hence two layers: reject the stripped characters outright, then verify
 * positively that resolving the value cannot leave the origin. The second
 * check is the real guarantee — it holds for anything the prefix rules
 * have not anticipated.
 */
export function safeReturnPath(value: string | null | undefined): string | null {
  if (!value) return null;
  // Characters the URL parser strips before resolving. Note this must NOT
  // reject spaces: a carried filter value legitimately contains them.
  if (/[\t\n\r]/.test(value)) return null;
  if (!value.startsWith("/")) return null;
  if (value.startsWith("//") || value.startsWith("/\\")) return null;

  try {
    const probeOrigin = "https://ticket-return-path.invalid";
    if (new URL(value, probeOrigin).origin !== probeOrigin) return null;
  } catch {
    // Unparseable as a relative url -> not a path we are willing to push.
    // Returning null is the surfaced outcome: the caller falls back to the
    // ticket's board route.
    return null;
  }
  return value;
}

/** Next.js `searchParams` shape for an App Router page. */
export type RouteSearchParams = Record<string, string | string[] | undefined>;

function single(value: string | string[] | undefined): string | undefined {
  if (Array.isArray(value)) return value[0];
  return value ?? undefined;
}

/**
 * What a create url asks for, as it reads in the url: slugs and a key, not
 * ids. `useTicketCreateTarget` turns it into the ids the form submits. A
 * UUID in `boardSlug`, `sprintSlug` or `parentKey` comes from a link made
 * before slugs and keys: it is used as the id itself, and the url is rewritten
 * to the slug or key once that is known.
 */
export interface TicketCreateTarget {
  /** Board pre-selected in the form; `null` offers the full board tree. */
  boardSlug: string | null;
  /** Sprint placement on that board. Wins over `backlog`. */
  sprintSlug: string | null;
  /** Backlog placement on that board. */
  backlog: boolean;
  /** Create as a sub-ticket of this ticket. */
  parentKey: string | null;
  initialStatus: TicketStatus | null;
}

/**
 * The `TicketForm` props of a create url, so a route file can spread the
 * parse result straight onto the component.
 */
export interface TicketRouteFormProps {
  createTarget: TicketCreateTarget;
}

/**
 * Reads the create params out of a page's `searchParams` (or the dialog's
 * url state). Unknown status values are dropped rather than trusted: the
 * value reaches the form as a select default, and a bogus one would fail
 * validation later with a far less obvious message.
 */
export function parseTicketRouteParams(searchParams: RouteSearchParams): TicketRouteFormProps {
  const rawStatus = single(searchParams[TICKET_ROUTE_PARAM.status]);
  const sprintSlug = single(searchParams[TICKET_ROUTE_PARAM.sprint]) || null;

  return {
    createTarget: {
      boardSlug: single(searchParams[TICKET_ROUTE_PARAM.board]) || null,
      // Placement is mutually exclusive on the backend (VAL_TICKET_PLACEMENT).
      // Any value counts as the backlog flag: old links carried the backlog id.
      sprintSlug,
      backlog: !sprintSlug && !!single(searchParams[TICKET_ROUTE_PARAM.backlog]),
      parentKey: single(searchParams[TICKET_ROUTE_PARAM.parent]) || null,
      initialStatus: isTicketStatus(rawStatus) ? rawStatus : null,
    },
  };
}

function isTicketStatus(value: string | undefined): value is TicketStatus {
  return !!value && (Object.values(TicketStatus) as string[]).includes(value);
}

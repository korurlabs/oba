"use client";

/**
 * useTicketNavigation
 *
 * "Which ticket is open" is URL state, but it is a SEARCH PARAM on the list
 * route the user is already on, not a route of its own:
 *
 *   /board/acme/backlog?…filters…&t_ticket=ACME-12
 *   /board/acme/sprint-5?…filters…&t_ticket=new&t_board=acme&t_sprint=sprint-5
 *
 * That is what makes a refresh keep the user where they were. The previous
 * design pushed the ticket route and relied on an intercepting route to
 * render it as a modal; interception only applies to SOFT navigations, so a
 * hard refresh fell through to the standalone full page and the user lost the
 * list they were reading. A search param has no such asymmetry: the document
 * request renders the same list, and the `@modal` slot re-opens the dialog
 * from the param (see `TicketModalHost`).
 *
 * The dialog params are still shareable and deep-linkable, and because opening
 * pushes a history entry, browser Back closes the dialog.
 *
 * The standalone page routes are still reachable:
 *
 *   /board/<slug>/ticket/<KEY>   full-page edit
 *   /ticket/new                  full-page create
 *
 * `expandToFullPage` (the dialog's maximise button) navigates to them, and
 * `openTicketPage` keeps sub-ticket navigation on the page shell on the page.
 *
 * The app sets `localePrefix: "never"`, so app-relative paths and
 * `window.location.pathname` are the same strings; the hard navigation below
 * can build plain paths with no locale arithmetic.
 *
 * Filter params
 * -------------
 * Opening the dialog does not change the path, so the list's nuqs filter
 * state simply stays in the URL untouched. Only the transitions that LEAVE
 * the list for a full page remember it (see `ticket-return-path.ts`), so the
 * full page's Close button can return to it.
 */

import { useCallback } from "react";
import { parseAsString, useQueryStates } from "nuqs";
import { useSearchParams } from "next/navigation";
import { usePathname, useRouter } from "@oba/shared/libs/i18n/navigation";
import type { TicketStatus } from "@/types/enums";
import {
  LEGACY_TICKET_FROM_PARAM,
  TICKET_BACKLOG_PLACEMENT,
  TICKET_CREATE_PATH,
  TICKET_MODAL_CREATE,
  TICKET_ROUTE_PARAM,
  isTicketRoutePath,
  ticketDetailPath,
  ticketKeyPath,
} from "../lib/ticket-route-params";
import {
  clearTicketReturnPath,
  readTicketReturnPath,
  saveTicketReturnPath,
} from "../lib/ticket-return-path";

/**
 * The route contract lives in a boundary-safe module so server components
 * can parse the same params. Re-exported here for client consumers.
 */
export { TICKET_CREATE_PATH, TICKET_MODAL_CREATE, TICKET_ROUTE_PARAM, ticketDetailPath };

/** Params the ticket dialog owns; never left behind on a list url. */
const OWNED_PARAMS: readonly string[] = [
  ...Object.values(TICKET_ROUTE_PARAM),
  // Legacy `?id=<TICKET-KEY>` deep link. Rewritten into `t_ticket` by
  // `useLegacyTicketDeepLink`; carrying it forward would rewrite it again.
  "id",
  LEGACY_TICKET_FROM_PARAM,
];

/**
 * nuqs parsers for the dialog params. Every one is a plain optional string:
 * the values are keys and slugs, and the only special values
 * (`t_ticket === "new"`, `t_backlog === "1"`) are checked where they matter.
 */
export const ticketModalParams = {
  [TICKET_ROUTE_PARAM.ticket]: parseAsString,
  [TICKET_ROUTE_PARAM.board]: parseAsString,
  [TICKET_ROUTE_PARAM.parent]: parseAsString,
  [TICKET_ROUTE_PARAM.status]: parseAsString,
  [TICKET_ROUTE_PARAM.sprint]: parseAsString,
  [TICKET_ROUTE_PARAM.backlog]: parseAsString,
};

/**
 * What the OPEN/CLOSE writes may touch. Same as `ticketModalParams` plus the
 * legacy `?id=<TICKET-KEY>` deep link and `t_from` marker, so a write that
 * opens or closes the dialog also drops them from the url. nuqs only writes
 * keys it has a parser for, hence the extra entries here.
 */
const ticketModalWriteParams = {
  ...ticketModalParams,
  id: parseAsString,
  [LEGACY_TICKET_FROM_PARAM]: parseAsString,
};

/** Every dialog param cleared. Spread it so no stale create option survives. */
const CLEARED_MODAL_PARAMS = {
  [TICKET_ROUTE_PARAM.ticket]: null,
  [TICKET_ROUTE_PARAM.board]: null,
  [TICKET_ROUTE_PARAM.parent]: null,
  [TICKET_ROUTE_PARAM.status]: null,
  [TICKET_ROUTE_PARAM.sprint]: null,
  [TICKET_ROUTE_PARAM.backlog]: null,
  id: null,
  [LEGACY_TICKET_FROM_PARAM]: null,
};

/**
 * The ids behind a ticket key, as a list row already knows them. Passing them
 * to `openTicket` spares the dialog its by-key lookup, so a click on a row
 * opens the dialog without a round trip first.
 */
export interface KnownTicketRef {
  key: string;
  ticketId: string;
  boardId: string;
  boardSlug: string;
}

/**
 * The ticket last opened with known ids. Module scope for the same reason as
 * `openedDialogInThisDocument` below: the list row that opens the dialog and
 * the host that renders it are different React trees. One slot is enough,
 * since only the ticket being opened needs it; any other key resolves
 * through the by-key lookup.
 */
let lastKnownTicket: KnownTicketRef | null = null;

/** The known ids of `key`, when it is the ticket last opened with them. */
export function knownTicketRef(key: string): KnownTicketRef | null {
  return lastKnownTicket?.key === key ? lastKnownTicket : null;
}

/**
 * Records the ids of a ticket the dialog shows, so that rewriting its url
 * value (an old id link to its key) finds the ids without a lookup.
 */
export function rememberKnownTicket(ref: KnownTicketRef): void {
  lastKnownTicket = ref;
}

/**
 * Whether THIS document opened the dialog (as opposed to the dialog being
 * present on first paint because the url was pasted, shared, or refreshed).
 *
 * Module scope rather than a ref on purpose: the component that OPENS the
 * dialog (a list row) and the component that CLOSES it (the form) are
 * different React trees, so a ref cannot be shared between them. A module
 * variable is per-document, which is exactly the lifetime of the question —
 * a refresh resets it, and a refreshed dialog must not close with `back()`
 * because the previous history entry belongs to whatever the user was doing
 * before, up to and including another site.
 */
let openedDialogInThisDocument = false;

export interface OpenCreateTicketOptions {
  /** Slug of the board to pre-select in the create form. The user can still change it. */
  boardSlug?: string;
  /**
   * Create the ticket as a sub-ticket of this ticket: its key, or its id
   * when the caller has no key at hand (the create url accepts both).
   */
  parentKey?: string;
  /** Initial status (kanban column the user clicked "+" in). */
  status?: TicketStatus;
  /** Sprint placement, the sprint's slug on the board. Wins over `backlog`. */
  sprintSlug?: string;
  /** Backlog placement on the board. */
  backlog?: boolean;
}

/** A ticket as the list rows and the dialog know it. */
export interface OpenableTicket {
  key: string;
  id: string;
  board?: { id: string; slug: string } | null;
}

export function useTicketNavigation() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  // Identity-stable form for dependency arrays: `useSearchParams` returns a
  // fresh instance on every navigation.
  const search = searchParams.toString();
  const [, setModalParams] = useQueryStates(ticketModalWriteParams);

  /**
   * Remembers the url the dialog is layered on, minus every param the dialog
   * owns, as the list the full page's Close returns to. On a ticket ROUTE
   * (one full page to the next) the list remembered when the user first
   * left it stays, so Close still goes back to that list.
   */
  const rememberListReturnPath = useCallback(() => {
    if (isTicketRoutePath(pathname)) return;
    const params = new URLSearchParams(search);
    for (const owned of OWNED_PARAMS) params.delete(owned);
    const cleaned = params.toString();
    saveTicketReturnPath(cleaned ? `${pathname}?${cleaned}` : pathname);
  }, [search, pathname]);

  /**
   * Opens the edit dialog for the ticket with `key` over the CURRENT page.
   * Shallow by default (nuqs), so the list underneath is never re-fetched or
   * unmounted; `history: "push"` is what makes browser Back close the dialog.
   * A caller that holds the ticket's ids passes the ticket, so the dialog
   * need not look the key up first.
   */
  const openTicket = useCallback(
    (key: string, known?: OpenableTicket) => {
      if (!key) return;
      if (known?.board && known.key === key) {
        rememberKnownTicket({
          key,
          ticketId: known.id,
          boardId: known.board.id,
          boardSlug: known.board.slug,
        });
      }
      openedDialogInThisDocument = true;
      void setModalParams(
        { ...CLEARED_MODAL_PARAMS, [TICKET_ROUTE_PARAM.ticket]: key },
        { history: "push" },
      );
    },
    [setModalParams],
  );

  const openCreateTicket = useCallback(
    (options: OpenCreateTicketOptions = {}) => {
      openedDialogInThisDocument = true;
      void setModalParams(
        {
          ...CLEARED_MODAL_PARAMS,
          [TICKET_ROUTE_PARAM.ticket]: TICKET_MODAL_CREATE,
          [TICKET_ROUTE_PARAM.board]: options.boardSlug ?? null,
          [TICKET_ROUTE_PARAM.parent]: options.parentKey ?? null,
          [TICKET_ROUTE_PARAM.status]: options.status ?? null,
          // Placement is mutually exclusive on the backend (VAL_TICKET_PLACEMENT).
          [TICKET_ROUTE_PARAM.sprint]: options.sprintSlug ?? null,
          [TICKET_ROUTE_PARAM.backlog]:
            !options.sprintSlug && options.backlog ? TICKET_BACKLOG_PLACEMENT : null,
        },
        { history: "push" },
      );
    },
    [setModalParams],
  );

  /**
   * Opens a ticket as a full PAGE. Used by the page shell for sub-ticket
   * navigation: on the standalone page, opening a sub-ticket as a dialog over
   * the page you are already reading would stack two copies of the same form.
   * Without the board slug the short `/ticket/<KEY>` link is used, which
   * redirects to the ticket's board.
   */
  const openTicketPage = useCallback(
    (key: string, boardSlug?: string | null) => {
      if (!key) return;
      rememberListReturnPath();
      const path = boardSlug ? ticketDetailPath(boardSlug, key) : ticketKeyPath(key);
      // next-intl's typed router: hrefs are built at runtime here, so we
      // widen to a known route literal exactly as the rest of the app does.
      router.push(path as "/home");
    },
    [router, rememberListReturnPath],
  );

  /**
   * Closes the dialog.
   *
   * When this document opened it, the open pushed a history entry, so `back()`
   * both closes the dialog and leaves history clean. When the dialog was
   * present on first paint (refresh, shared link), there is no entry of ours
   * to go back to (`back()` would leave the app entirely), so the params are
   * stripped in place instead, which keeps the user on their list.
   */
  const closeModal = useCallback(() => {
    if (openedDialogInThisDocument) {
      openedDialogInThisDocument = false;
      router.back();
      return;
    }
    void setModalParams(CLEARED_MODAL_PARAMS, { history: "replace" });
  }, [router, setModalParams]);

  /**
   * Replaces the dialog's ticket with `key` in place, without a history
   * entry: an old id link becomes the key link once the ticket is known, and
   * a legacy `?id=<KEY>` link becomes `t_ticket=<KEY>`.
   */
  const replaceOpenTicket = useCallback(
    (key: string) => {
      void setModalParams(
        { ...CLEARED_MODAL_PARAMS, [TICKET_ROUTE_PARAM.ticket]: key },
        { history: "replace" },
      );
    },
    [setModalParams],
  );

  /**
   * Closes the full-page shell: back to the list the user left for it in
   * this tab, else to `fallbackHref` (the ticket's board).
   *
   * Not `window.history.length`: that counter covers the entire tab session,
   * so a shared link opened in an existing tab would "close" onto whatever
   * site the user was reading before.
   */
  const closePage = useCallback(
    (fallbackHref: string) => {
      const returnPath = readTicketReturnPath();
      clearTicketReturnPath();
      router.push((returnPath ?? fallbackHref) as "/home");
    },
    [router],
  );

  /**
   * Leaves the dialog for the standalone full-page shell (the maximise
   * button). Callers must persist in-progress form values first (see
   * `saveTicketDraft`): the page is a different component instance.
   *
   * Uses `window.location.replace`, not `assign`: the expand SUPERSEDES the
   * dialog's history entry, so Back from the full page lands on the list the
   * dialog was over rather than on a second, now-empty copy of the form. The
   * hard navigation is also what lets `saveTicketDraft` (sessionStorage) be
   * the single carry-over channel: nothing in the React tree survives, so
   * there is no second source of truth for the in-progress values.
   *
   * The list's own params stay behind: the target url carries only the
   * params the full page reads (`params`).
   */
  const expandToFullPage = useCallback(
    (options: { targetPath: string; params?: Record<string, string | null | undefined> }) => {
      if (typeof window === "undefined") return;
      rememberListReturnPath();
      const url = new URL(options.targetPath, window.location.origin);
      for (const [key, value] of Object.entries(options.params ?? {})) {
        if (value) url.searchParams.set(key, value);
      }
      window.location.replace(url.toString());
    },
    [rememberListReturnPath],
  );

  return {
    openTicket,
    openCreateTicket,
    openTicketPage,
    closeModal,
    replaceOpenTicket,
    closePage,
    expandToFullPage,
  };
}

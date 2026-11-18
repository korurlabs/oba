"use client";

/**
 * TicketModalHost
 *
 * Renders the ticket create/edit DIALOG from the `t_ticket` search param on
 * whatever list route the user is on. Mounted once, in the `@modal` parallel
 * slot of the dashboard layout, so it is available over every list page
 * (board backlog / sprint / all, and the board-less `/tickets`).
 *
 * Why a param and not an intercepting route: interception only applies to SOFT
 * navigations. A hard refresh of an intercepted url falls through to the
 * standalone page, which used to throw the user out of the list they were
 * reading. Reading the dialog from a search param has no such asymmetry: the
 * document request re-renders the list, and this component re-opens the dialog
 * from the param. The url stays shareable, and because opening pushes a
 * history entry (see `useTicketNavigation`), browser Back closes the dialog.
 *
 * `t_ticket` holds the ticket's key, so the host looks the key up to get the
 * ids the form loads by. A link made before keys holds the ticket's id with
 * `t_board` beside it; that still opens, and the url is rewritten to the key
 * once the ticket has loaded.
 *
 * The standalone full page is still a real route; the dialog's maximise button
 * navigates to it. This component stands down on those routes so the two
 * shells can never render at once.
 */

import { useEffect, useMemo, useRef } from "react";
import { useQueryStates } from "nuqs";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { devError } from "@oba/shared/helpers/dev-log";
import { usePathname } from "@oba/shared/libs/i18n/navigation";
import { isUuid } from "@/features/boards/lib/board-route";
import { useTicketByKey } from "@/features/all-tickets/hooks/use-ticket-by-key";
import { TicketForm } from "./ticket-form";
import {
  knownTicketRef,
  rememberKnownTicket,
  ticketModalParams,
  useTicketNavigation,
  type KnownTicketRef,
} from "../hooks/use-ticket-navigation";
import { useTicket } from "../hooks/use-tickets";
import {
  TICKET_MODAL_CREATE,
  TICKET_ROUTE_PARAM,
  isTicketRoutePath,
  parseTicketRouteParams,
  type RouteSearchParams,
} from "../lib/ticket-route-params";

export function TicketModalHost() {
  const pathname = usePathname();
  const [params] = useQueryStates(ticketModalParams);

  const activeTicket = params[TICKET_ROUTE_PARAM.ticket];
  const boardParam = params[TICKET_ROUTE_PARAM.board];
  const parentParam = params[TICKET_ROUTE_PARAM.parent];
  const statusParam = params[TICKET_ROUTE_PARAM.status];
  const sprintParam = params[TICKET_ROUTE_PARAM.sprint];
  const backlogParam = params[TICKET_ROUTE_PARAM.backlog];

  // Reuse the route parser so the dialog and the full create page validate
  // `t_status` and resolve the sprint/backlog exclusivity identically.
  // Depends on the individual values, not the params object, so a re-render
  // that does not change the url cannot hand `TicketForm` a new target.
  const createTarget = useMemo(() => {
    const raw: RouteSearchParams = {
      [TICKET_ROUTE_PARAM.board]: boardParam ?? undefined,
      [TICKET_ROUTE_PARAM.parent]: parentParam ?? undefined,
      [TICKET_ROUTE_PARAM.status]: statusParam ?? undefined,
      [TICKET_ROUTE_PARAM.sprint]: sprintParam ?? undefined,
      [TICKET_ROUTE_PARAM.backlog]: backlogParam ?? undefined,
    };
    return parseTicketRouteParams(raw).createTarget;
  }, [boardParam, parentParam, statusParam, sprintParam, backlogParam]);

  if (!activeTicket) return null;
  // A stray `t_ticket` on a ticket ROUTE would stack the dialog on top of the
  // full page showing the same form. The page owns those urls.
  if (isTicketRoutePath(pathname)) return null;

  if (activeTicket === TICKET_MODAL_CREATE) {
    // Keyed by placement as well as mode: opening "+ Add" in a sprint and then
    // in the backlog without closing in between must give a FRESH form, not
    // one still holding the previous placement's defaults.
    const createKey = [
      "create",
      createTarget.boardSlug ?? "",
      createTarget.sprintSlug ?? (createTarget.backlog ? "backlog" : ""),
      createTarget.parentKey ?? "",
    ].join(":");
    return <TicketForm key={createKey} variant="dialog" createTarget={createTarget} />;
  }

  return <TicketEditDialog ticketRef={activeTicket} legacyBoardId={boardParam} />;
}

interface TicketEditDialogProps {
  /** The `t_ticket` value: a ticket key, or the ticket id of an old link. */
  ticketRef: string;
  /** `t_board` of an old link, the board id the ticket id belongs to. */
  legacyBoardId: string | null;
}

/**
 * Resolves `t_ticket` to the ids the form loads by, then renders the edit
 * form. Not keyed by the url value: when an old id link is rewritten to the
 * key, the same form stays mounted (it is keyed by the ticket id).
 */
function TicketEditDialog({ ticketRef, legacyBoardId }: TicketEditDialogProps) {
  const t = useTranslations();
  const { closeModal, replaceOpenTicket } = useTicketNavigation();

  const isLegacyId = isUuid(ticketRef);
  const known = isLegacyId ? null : knownTicketRef(ticketRef);

  // Old id link: the form's own detail query (same key) supplies the key.
  const legacyDetail = useTicket(
    isLegacyId ? (legacyBoardId ?? "") : "",
    isLegacyId ? ticketRef : "",
  );
  const byKey = useTicketByKey(ticketRef, { enabled: !isLegacyId && !known });

  const resolved: KnownTicketRef | null = useMemo(() => {
    if (isLegacyId) {
      if (!legacyBoardId) return null;
      const detail = legacyDetail.data?.data;
      return {
        key: detail?.key ?? "",
        ticketId: ticketRef,
        boardId: legacyBoardId,
        boardSlug: detail?.board?.slug ?? "",
      };
    }
    if (known) return known;
    const ticket = byKey.data;
    if (!ticket?.board) return null;
    return {
      key: ticket.key,
      ticketId: ticket.id,
      boardId: ticket.board.id,
      boardSlug: ticket.board.slug,
    };
  }, [isLegacyId, legacyBoardId, legacyDetail.data?.data, ticketRef, known, byKey.data]);

  // An old id link whose ticket has loaded: show the key in the url instead.
  // The ids are remembered first, so the key value finds them without a
  // lookup and the form is not remounted.
  const legacyKey = isLegacyId ? (resolved?.key ?? "") : "";
  useEffect(() => {
    if (!legacyKey || !resolved) return;
    rememberKnownTicket(resolved);
    replaceOpenTicket(legacyKey);
  }, [legacyKey, resolved, replaceOpenTicket]);

  // Reported at most once per url value, so re-renders during the close
  // cannot toast twice.
  const reportedRef = useRef<string | null>(null);
  const failure: "boardMissing" | "notFound" | "error" | null = isLegacyId
    ? legacyBoardId
      ? null
      : "boardMissing"
    : !known && byKey.data === null
      ? "notFound"
      : !known && byKey.data === undefined && byKey.isError
        ? "error"
        : // A ticket without a board cannot be loaded by the form.
          byKey.data && !byKey.data.board
          ? "notFound"
          : null;

  useEffect(() => {
    if (!failure || reportedRef.current === ticketRef) return;
    reportedRef.current = ticketRef;
    if (failure === "boardMissing") {
      // A truncated or hand-edited old link: the id alone cannot be loaded.
      toast.error(t("kaizen.tickets.messages.ticketBoardMissing"));
    } else if (failure === "notFound") {
      toast.error(t("kaizen.tickets.messages.ticketNotFound", { key: ticketRef }));
    } else {
      devError("Ticket key lookup failed:", byKey.error);
      toast.error(t("kaizen.tickets.messages.ticketLoadFailed"));
    }
    // Leaves the user on the list they navigated to rather than under a dead
    // overlay.
    closeModal();
  }, [failure, ticketRef, byKey.error, closeModal, t]);

  if (failure || !resolved) return null;

  // Keyed by ticket id: navigating parent -> sub-ticket swaps the url value in
  // place, and the form must remount rather than reconcile two tickets.
  return (
    <TicketForm
      key={resolved.ticketId}
      variant="dialog"
      boardId={resolved.boardId}
      boardSlug={resolved.boardSlug || null}
      ticketId={resolved.ticketId}
      ticketKey={resolved.key || null}
    />
  );
}

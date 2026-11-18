"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { IncidentCloseDialogProps, IncidentCloseValues } from "../components/incident-close-dialog";
import { TicketStatus, TicketType } from "../schemas";
import { useUpdateTicket } from "./use-tickets";

/** The fields of a ticket that decide whether a status change needs the dialog. */
export interface IncidentCloseGuardTicket {
  id: string;
  type: TicketType;
  status: TicketStatus;
}

/**
 * True when a ticket moves into Done as an incident. The server then requires
 * an RCA and lessons learned, so the user is asked for them first. An incident
 * already Done is not asked again: older incidents closed before the rule stay
 * editable.
 */
export function requiresIncidentClose(
  type: TicketType | undefined,
  currentStatus: TicketStatus | undefined,
  newStatus: TicketStatus | undefined,
): boolean {
  return (
    type === TicketType.INCIDENT &&
    newStatus === TicketStatus.DONE &&
    currentStatus !== TicketStatus.DONE
  );
}

interface PendingIncidentClose {
  ticketId: string;
  boardId: string;
}

/**
 * Status changes that stop at the incident close dialog when an incident moves
 * into Done, and go straight to the server otherwise.
 *
 * `guardStatusChange` returns a promise that settles once the change is over:
 * after the update for an ordinary change (rejecting as the update does), or
 * when the dialog closes, by confirm or cancel. Kanbans pass it to
 * `handOffTo`, so the dropped card stays in Done while the dialog is open and
 * returns to its column on cancel. Render `IncidentCloseDialog` with
 * `dialogProps`.
 */
export function useIncidentCloseGuard() {
  const { mutateAsync: updateStatus } = useUpdateTicket();
  const closeMutation = useUpdateTicket();
  const { mutateAsync: closeIncident } = closeMutation;

  const [pending, setPending] = useState<PendingIncidentClose | null>(null);
  // Kept outside state: resolving is a side effect, and a state updater must
  // stay pure.
  const resolveRef = useRef<(() => void) | null>(null);

  const finish = useCallback(() => {
    const resolve = resolveRef.current;
    resolveRef.current = null;
    setPending(null);
    resolve?.();
  }, []);

  // A page that unmounts with the dialog open must not leave its caller (and
  // a kanban card's placement) waiting forever.
  useEffect(() => () => resolveRef.current?.(), []);

  const guardStatusChange = useCallback(
    async (
      ticket: IncidentCloseGuardTicket,
      newStatus: TicketStatus,
      boardId: string,
    ): Promise<void> => {
      if (!requiresIncidentClose(ticket.type, ticket.status, newStatus)) {
        await updateStatus({ boardId, ticketId: ticket.id, data: { status: newStatus } });
        return;
      }
      // A second guarded change ends the first one as a cancel: one dialog
      // shows at a time.
      resolveRef.current?.();
      await new Promise<void>((resolve) => {
        resolveRef.current = resolve;
        setPending({ ticketId: ticket.id, boardId });
      });
    },
    [updateStatus],
  );

  const handleConfirm = useCallback(
    (values: IncidentCloseValues) => {
      if (!pending) return;
      closeIncident({
        boardId: pending.boardId,
        ticketId: pending.ticketId,
        data: {
          status: TicketStatus.DONE,
          rca: values.rca,
          lessonsLearned: values.lessonsLearned,
        },
      }).then(finish, () => {
        // rule-10-no-op -- reason: useUpdateTicket's onError already rolls
        // the cache back and toasts the translated refusal; the dialog stays
        // open so the user can correct the texts or cancel.
      });
    },
    [pending, closeIncident, finish],
  );

  const handleOpenChange = useCallback(
    (open: boolean) => {
      if (!open) finish();
    },
    [finish],
  );

  const dialogProps: IncidentCloseDialogProps = {
    open: pending !== null,
    onOpenChange: handleOpenChange,
    boardId: pending?.boardId ?? "",
    ticketId: pending?.ticketId,
    onConfirm: handleConfirm,
    isPending: closeMutation.isPending,
  };

  return { guardStatusChange, dialogProps };
}

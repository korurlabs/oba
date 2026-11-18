"use client";

import { useCallback, useMemo, useState } from "react";
import { useWatch, type UseFormReturn } from "react-hook-form";
import type { TicketListResponse } from "@/features/tickets/schemas";
import type { PullRequestShareFormValues, PullRequestShareTicketResponse } from "../schemas";

const NO_STORED_TICKETS: PullRequestShareTicketResponse[] = [];
const NO_TICKET_IDS: string[] = [];

/**
 * A ticket ticked in the picker as a related ticket row. The picker lists
 * only tickets of the reader's member boards, so a picked ticket can be
 * opened; one without its board has no ticket page to link to.
 */
function toPickedTicket(ticket: TicketListResponse): PullRequestShareTicketResponse {
  return {
    ticketId: ticket.id,
    boardId: ticket.board?.id ?? "",
    boardName: ticket.board?.name ?? "",
    ticketKey: ticket.key,
    ticketTitle: ticket.title,
    canOpen: !!ticket.board,
  };
}

/** The related tickets of a share form, kept in step with the ticket picker. */
export interface RelatedTicketSelection {
  /** The chosen ticket IDs in pick order, as the form holds them. */
  selectedIds: string[];
  /** The chosen tickets as rows, in the same order. */
  chosen: PullRequestShareTicketResponse[];
  /** Tickets ticked in the picker, and IDs unticked there. */
  onSelectionChange: (added: TicketListResponse[], removedIds: string[]) => void;
  remove: (ticketId: string) => void;
}

/**
 * The share form's `relatedTicketIds` and a row for each. A ticket keeps the
 * row it had when it was first known, so a stored ticket keeps the board
 * name, key, title and `canOpen` the share holds, even when the reader can
 * no longer open it or it was deleted, as the server keeps that snapshot for
 * a kept ticket. Every change goes through the form, so the edit screen sees
 * it as dirty and the limit is validated.
 */
export function useRelatedTicketSelection(
  form: UseFormReturn<PullRequestShareFormValues>,
  storedTickets: PullRequestShareTicketResponse[] = NO_STORED_TICKETS,
): RelatedTicketSelection {
  const { control, getValues, setValue } = form;
  const selectedIds = useWatch({ control, name: "relatedTicketIds" }) ?? NO_TICKET_IDS;

  const [known, setKnown] = useState<ReadonlyMap<string, PullRequestShareTicketResponse>>(
    () => new Map(storedTickets.map((ticket) => [ticket.ticketId, ticket])),
  );

  const setSelectedIds = useCallback(
    (ticketIds: string[]) => {
      setValue("relatedTicketIds", ticketIds, { shouldDirty: true, shouldValidate: true });
    },
    [setValue],
  );

  const onSelectionChange = useCallback(
    (added: TicketListResponse[], removedIds: string[]) => {
      const removed = new Set(removedIds);
      const kept = getValues("relatedTicketIds").filter((ticketId) => !removed.has(ticketId));
      const present = new Set(kept);
      const newlyAdded = added.filter((ticket) => {
        if (present.has(ticket.id)) return false;
        present.add(ticket.id);
        return true;
      });
      if (newlyAdded.length > 0) {
        setKnown((previous) => {
          const next = new Map(previous);
          for (const ticket of newlyAdded) {
            if (!next.has(ticket.id)) next.set(ticket.id, toPickedTicket(ticket));
          }
          return next;
        });
      }
      setSelectedIds([...kept, ...newlyAdded.map((ticket) => ticket.id)]);
    },
    [getValues, setSelectedIds],
  );

  const remove = useCallback(
    (ticketId: string) => {
      setSelectedIds(getValues("relatedTicketIds").filter((id) => id !== ticketId));
    },
    [getValues, setSelectedIds],
  );

  const chosen = useMemo(
    () =>
      selectedIds.flatMap((ticketId) => {
        const ticket = known.get(ticketId);
        return ticket ? [ticket] : [];
      }),
    [selectedIds, known],
  );

  return { selectedIds, chosen, onSelectionChange, remove };
}

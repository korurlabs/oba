/**
 * Copies the lines of the author's previous update into today's form.
 *
 * A copied line starts at the ticket's current percentage, not the one of the
 * previous update: creating an update writes a differing percentage back to
 * its ticket, so the old value would silently undo progress made since.
 * Questions are never copied, and finished tickets are not, because the server
 * computes them for today.
 */
import { TicketStatus, type TicketListResponse } from "@/features/tickets/schemas";
import { lineFromTicket } from "../hooks/use-update-form";
import {
  UPDATE_TICKETS_MAX,
  type UpdateFormLine,
  type UpdateFormValues,
  type UpdateResponse,
} from "../schemas";

export interface CopyPreviousUpdateInput {
  previous: UpdateResponse;
  current: UpdateFormValues;
  /** The current tickets of the previous lines, by ticket key. */
  ticketsByKey: ReadonlyMap<string, TicketListResponse>;
}

/** The previous lines that were not copied, by reason, as ticket keys. */
export interface CopyPreviousUpdateLeftOut {
  /** Deleted, on a board the author cannot open, or no longer found by its key. */
  unavailable: string[];
  /** Done since; the server lists it under the finished tickets instead. */
  done: string[];
  /** Beyond the ticket limit of one update. */
  overLimit: string[];
}

export interface CopyPreviousUpdateResult {
  /** The lines to append, in the order of the previous update. */
  added: UpdateFormLine[];
  /** The previous focus when today's is still blank; otherwise null, and the focus stays. */
  focus: string | null;
  leftOut: CopyPreviousUpdateLeftOut;
}

/**
 * The lines of `previous` that today's form does not have yet. A ticket
 * already on the form is skipped and its line there is kept as typed, so
 * copying again adds only what is still missing.
 */
export function copyPreviousUpdate({
  previous,
  current,
  ticketsByKey,
}: CopyPreviousUpdateInput): CopyPreviousUpdateResult {
  const onForm = new Set(current.tickets.map((line) => line.ticketId));
  const added: UpdateFormLine[] = [];
  const leftOut: CopyPreviousUpdateLeftOut = { unavailable: [], done: [], overLimit: [] };

  previous.items.forEach((item) => {
    const ticket = ticketsByKey.get(item.ticketKey);
    // A key can move to another ticket when a board is re-keyed, so the
    // resolved ticket must be the one the line was written for.
    if (item.ticketId === null || !item.canOpen || !ticket || ticket.id !== item.ticketId) {
      leftOut.unavailable.push(item.ticketKey);
      return;
    }
    if (ticket.status === TicketStatus.DONE) {
      leftOut.done.push(item.ticketKey);
      return;
    }
    if (onForm.has(ticket.id)) return;
    if (current.tickets.length + added.length >= UPDATE_TICKETS_MAX) {
      leftOut.overLimit.push(item.ticketKey);
      return;
    }
    added.push({ ...lineFromTicket(ticket), note: item.note ?? "" });
    onForm.add(ticket.id);
  });

  const previousFocus = previous.focus ?? "";
  const focus = !current.focus.trim() && previousFocus.trim() ? previousFocus : null;

  return { added, focus, leftOut };
}

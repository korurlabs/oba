/**
 * Server lookups of the ticket pages, `/ticket/<KEY>` and
 * `/board/<slug>/ticket/<KEY>`. A colocated module, not a route.
 *
 * Both lookups read a 404 as "no such ticket", which the pages turn into the
 * not-found screen. The backend answers 404 alike for a ticket that does not
 * exist and for one on a board the user is not a member of, so the page never
 * reveals which it was. Every other failure is re-thrown, so the error
 * boundary shows it instead of a misleading not-found screen.
 */

import { isApiError } from "@oba/shared/api/axios";
import type { ApiResponse } from "@oba/shared/schema";
import { getTicketByKey } from "@/features/all-tickets/api";
import { getTicketById } from "@/features/tickets/api";
import type { TicketBoardResponse } from "@/features/tickets/schemas";

/** What a ticket page needs to place a ticket: its ids, its key and its board. */
export interface TicketPageTicket {
  id: string;
  key: string;
  board: TicketBoardResponse;
}

/** The ticket with this key, or null when the user has no such ticket. */
export function findTicketByKey(ticketKey: string): Promise<TicketPageTicket | null> {
  return findTicket(() => getTicketByKey(ticketKey));
}

/**
 * The ticket with this id on this board, or null when there is none. Only
 * links made before tickets were addressed by key carry an id.
 */
export function findTicketById(boardId: string, ticketId: string): Promise<TicketPageTicket | null> {
  return findTicket(() => getTicketById(boardId, ticketId));
}

async function findTicket(
  load: () => Promise<ApiResponse<{ id: string; key: string; board?: TicketBoardResponse | null }>>,
): Promise<TicketPageTicket | null> {
  try {
    const ticket = (await load()).data;
    // A ticket page lives under its board, so a ticket without one has no page.
    if (!ticket?.board) return null;
    return { id: ticket.id, key: ticket.key, board: ticket.board };
  } catch (error) {
    if (isApiError(error) && error.status === 404) return null;
    throw error;
  }
}

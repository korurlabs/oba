/**
 * Which sub-tickets a searched list shows.
 *
 * The backend lists a ticket when its own text or a sub-ticket nested under it
 * matches the search, and marks every ticket of a searched list with
 * `searchMatch`: whether its own title, description or key holds the search
 * text. A searched list shows only the sub-tickets on the way to a match, so
 * searching for one sub-ticket does not also list its siblings.
 */

type SearchedTicket = {
  searchMatch?: boolean | null;
  subTickets?: readonly SearchedTicket[] | null;
};

/**
 * Whether the ticket matches the search or has a sub-ticket, at any depth, that
 * does. A ticket the backend did not mark (a list it did not search, e.g. a
 * blank search) is always shown.
 */
export function isOnSearchPath(ticket: SearchedTicket): boolean {
  if (ticket.searchMatch == null) return true;
  return ticket.searchMatch || (ticket.subTickets ?? []).some(isOnSearchPath);
}

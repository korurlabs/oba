import { useMemo } from "react";

/**
 * Which sub-tickets a searched list shows.
 *
 * The backend lists a ticket when its own title or key, or that of a
 * sub-ticket nested under it, contains the search text. A searched list shows
 * only the sub-tickets on the way to a match, so searching for one sub-ticket
 * does not also list its siblings.
 */

type SearchableTicket = {
  title: string;
  key: string;
  subTickets?: readonly SearchableTicket[] | null;
};

/** Whether the ticket's own title or key contains the search, case ignored. */
export function matchesSearch(ticket: SearchableTicket, search: string): boolean {
  const text = search.trim().toLowerCase();
  return ticket.title.toLowerCase().includes(text) || ticket.key.toLowerCase().includes(text);
}

/** Whether the ticket matches the search or has a sub-ticket, at any depth, that does. */
export function isOnSearchPath(ticket: SearchableTicket, search: string): boolean {
  return (
    matchesSearch(ticket, search) ||
    (ticket.subTickets ?? []).some((sub) => isOnSearchPath(sub, search))
  );
}

/**
 * The sub-row filter of a searched list: keeps the sub-tickets on the way to a
 * match. Undefined without a search, so every sub-ticket is kept. Stable for
 * one search, as `SenseyTable`'s `subRowFilter` needs.
 */
export function useSearchPathFilter<T extends SearchableTicket>(
  search: string | null | undefined
): ((ticket: T) => boolean) | undefined {
  return useMemo(() => {
    const text = search?.trim();
    return text ? (ticket: T) => isOnSearchPath(ticket, text) : undefined;
  }, [search]);
}

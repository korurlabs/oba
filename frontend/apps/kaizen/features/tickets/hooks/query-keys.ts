/**
 * Query-key factory for ticket queries.
 *
 * This module is intentionally NOT marked "use client". Server components
 * (page.tsx prefetch) import these helpers to set cache entries under the
 * EXACT same keys the client hooks use. If this lived in the "use client"
 * hook file, importing it from a server component would turn every export
 * into a client reference proxy -- the function members (e.g. `list`) would
 * stop being callable on the server and throw "TICKET_KEYS.list is not a
 * function" during prefetch (see server-prefetch-convention).
 *
 * NOTE: the list key embeds nuqs filter params and the localStorage-backed
 * includeAllBoards flag, which are not reproducible server-side -- ticket
 * lists stay client-fetched for now.
 *
 * The "use client" hook file re-exports these so existing client imports keep
 * working unchanged.
 */
export const TICKET_KEYS = {
  all: ["tickets"] as const,
  list: (boardId: string, params: Record<string, unknown>) =>
    [...TICKET_KEYS.all, "list", boardId, params] as const,
  detail: (boardId: string, ticketId: string) =>
    [...TICKET_KEYS.all, "detail", boardId, ticketId] as const,
};

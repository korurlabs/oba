/**
 * Query-key factory for backlog queries.
 *
 * This module is intentionally NOT marked "use client". Server components
 * (page.tsx prefetch) import these helpers to set cache entries under the
 * EXACT same keys the client hooks use. If this lived in the "use client"
 * hook file, importing it from a server component would turn every export
 * into a client reference proxy -- the function members (e.g. `detail`) would
 * stop being callable on the server and throw "BACKLOG_KEYS.detail is not a
 * function" during prefetch (see server-prefetch-convention).
 *
 * The "use client" hook file re-exports these so existing client imports keep
 * working unchanged.
 */
export const BACKLOG_KEYS = {
  all: ["backlog"] as const,
  detail: (boardId: string) => [...BACKLOG_KEYS.all, "detail", boardId] as const,
};

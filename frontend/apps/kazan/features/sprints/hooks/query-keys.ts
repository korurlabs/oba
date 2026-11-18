/**
 * Query-key factory for sprint queries.
 *
 * This module is intentionally NOT marked "use client". Server components
 * (page.tsx prefetch) import these helpers to set cache entries under the
 * EXACT same keys the client hooks use. If this lived in the "use client"
 * hook file, importing it from a server component would turn every export
 * into a client reference proxy -- the function members (e.g. `list`) would
 * stop being callable on the server and throw "SPRINT_KEYS.list is not a
 * function" during prefetch (see server-prefetch-convention).
 *
 * The "use client" hook file re-exports these so existing client imports keep
 * working unchanged.
 */
export const SPRINT_KEYS = {
  all: ["sprints"] as const,
  list: (boardId: string) => [...SPRINT_KEYS.all, "list", boardId] as const,
  childBoards: (boardId: string) =>
    [...SPRINT_KEYS.all, "childBoards", boardId] as const,
  detail: (boardId: string, sprintId: string) =>
    [...SPRINT_KEYS.all, "detail", boardId, sprintId] as const,
  startPreview: (boardId: string, sprintId: string) =>
    [...SPRINT_KEYS.all, "startPreview", boardId, sprintId] as const,
};

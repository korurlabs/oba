/**
 * Query-key factory for board queries.
 *
 * This module is intentionally NOT marked "use client". Server components
 * (page.tsx prefetch) import these helpers to set cache entries under the
 * EXACT same keys the client hooks use. If this lived in the "use client"
 * hook file, importing it from a server component would turn every export
 * into a client reference proxy -- the function members (e.g. `list`) would
 * stop being callable on the server and throw "BOARD_KEYS.list is not a
 * function" during prefetch (see server-prefetch-convention).
 *
 * The "use client" hook file re-exports these so existing client imports keep
 * working unchanged.
 */
export const BOARD_KEYS = {
  all: ["boards"] as const,
  list: (params: Record<string, unknown>) =>
    [...BOARD_KEYS.all, "list", params] as const,
  bySlug: (slug: string) => [...BOARD_KEYS.all, "by-slug", slug] as const,
  detail: (boardId: string) => [...BOARD_KEYS.all, "detail", boardId] as const,
};

/**
 * The canonical params used by the sidebar to fetch the full board tree.
 * Exported so server prefetch can reproduce the cached-board tree query key.
 */
export const BOARD_TREE_PARAMS = { includeChildren: true, amount: 100 } as const;

/**
 * Server-side helpers shared by the pages under `/board/<slug>/`: building
 * the redirect targets that turn an old id-based board URL into its slug
 * URL, and the prefetches that more than one tab needs. Imported by server
 * components only.
 */

import type { makeQueryClient } from "@oba/shared/providers/query-client";
import { listBoards } from "@/features/boards/api";
import { BOARD_KEYS, BOARD_TREE_PARAMS } from "@/features/boards/hooks/query-keys";
import { listSprints } from "@/features/sprints/api";
import { SPRINT_KEYS } from "@/features/sprints/hooks/query-keys";
import type { SprintListResponse } from "@/features/sprints/schemas";

/** The search params a page receives, as Next.js passes them. */
export type RouteSearchParams = Record<string, string | string[] | undefined>;

type QueryClient = ReturnType<typeof makeQueryClient>;

/** Sprint selection of the old id-based Sprint and All tab URLs. */
export const LEGACY_SPRINT_ID_PARAM = "sprintId";

/** Backlog id the old Backlog tab wrote into its URL; a board has one backlog, so it selects nothing. */
export const LEGACY_BACKLOG_ID_PARAM = "backlogId";

/**
 * `path` with the request's query string, minus the `drop` keys and with
 * the `set` values. A redirect keeps the remaining query, so filters and
 * an open ticket dialog survive the move to the slug URL.
 */
export function withQuery(
  path: string,
  searchParams: RouteSearchParams,
  change: { drop?: readonly string[]; set?: Record<string, string> } = {},
): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(searchParams)) {
    if (value === undefined || change.drop?.includes(key)) continue;
    for (const item of Array.isArray(value) ? value : [value]) query.append(key, item);
  }
  for (const [key, value] of Object.entries(change.set ?? {})) query.set(key, value);
  const serialized = query.toString();
  return serialized ? `${path}?${serialized}` : path;
}

/** The single value of a search param, or undefined when it is absent or repeated. */
export function singleValue(value: string | string[] | undefined): string | undefined {
  return typeof value === "string" ? value : undefined;
}

/**
 * Loads the board's sprints and caches them under the `useSprints` key.
 * Returns null when the request fails, after logging it: the page then
 * renders its view and the client fetches the list itself.
 */
export async function loadBoardSprints(
  queryClient: QueryClient,
  boardId: string,
  label: string,
): Promise<SprintListResponse[] | null> {
  try {
    const response = await listSprints(boardId);
    queryClient.setQueryData(SPRINT_KEYS.list(boardId), response);
    return response.data ?? [];
  } catch (error) {
    const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
    console.error(`[${label}] sprints prefetch failed — client will refetch:`, message);
    return null;
  }
}

/**
 * Prefetches the board tree under the key `useCachedChildBoards` reads.
 * Best-effort: a failure is logged and the client fetches the tree itself.
 */
export async function prefetchBoardTree(queryClient: QueryClient, label: string): Promise<void> {
  const params = BOARD_TREE_PARAMS as unknown as Record<string, unknown>;
  try {
    queryClient.setQueryData(BOARD_KEYS.list(params), await listBoards(params));
  } catch (error) {
    const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
    console.error(`[${label}] board tree prefetch failed — client will refetch:`, message);
  }
}

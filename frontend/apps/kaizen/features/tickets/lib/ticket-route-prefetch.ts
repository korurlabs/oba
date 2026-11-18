/**
 * Server prefetch for the ticket routes (boundary-safe, server-only use).
 *
 * The three ticket routes that hydrate anything — both full pages and the
 * intercepted detail — draw from the same two queries. (The intercepted
 * create route prefetches nothing: every page able to open it has already
 * cached the board tree.) Keeping the fetch + exact-key + best-effort-failure
 * logic here means the route files cannot drift apart on a cache key, and the
 * "never silent" error handling exists once.
 *
 * NOT marked "use client": route files are server components and must be able
 * to call these. See server-prefetch-convention.
 */

import { dehydrate, type DehydratedState } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { getTicketById } from "@/features/tickets/api";
import { listBoards } from "@/features/boards/api";
import { TICKET_KEYS } from "@/features/tickets/hooks/query-keys";
import { BOARD_KEYS, BOARD_TREE_PARAMS } from "@/features/boards/hooks/query-keys";
import { isKaizenClosedOnServer } from "@/components/layout/kaizen-access-server";

const BOARD_TREE_QUERY_PARAMS = BOARD_TREE_PARAMS as unknown as Record<string, unknown>;

export interface TicketRoutePrefetchOptions {
  /**
   * Edit routes: the ticket to hydrate. The key matches `useTicket` exactly —
   * on these routes the form has no ticket list to resolve a foreign board
   * from, so `useTicketForm` falls back to the route's own `boardId`.
   */
  detail?: { boardId: string; ticketId: string };
  /**
   * Hydrate the board tree that the create form's board selector and the
   * header's board chip read on mount. Skipped on the intercepted routes,
   * where every page able to open the dialog already has it cached.
   */
  boardTree?: boolean;
  /** Prefix for the failure log, e.g. "ticket-detail-page". */
  label: string;
}

/**
 * Fetches whatever the route asked for and returns the dehydrated cache.
 * Prefetch is best-effort: on failure the client refetches after hydration,
 * and the reason is logged rather than swallowed. Nothing is fetched when the
 * selected organization does not grant Kaizen: the API would refuse it, and
 * the no-access panel takes the place of the page.
 */
export async function ticketRouteHydrationState({
  detail,
  boardTree = false,
  label,
}: TicketRoutePrefetchOptions): Promise<DehydratedState> {
  const queryClient = makeQueryClient();
  if (await isKaizenClosedOnServer()) return dehydrate(queryClient);

  try {
    // Independent fetches -> run them together.
    const [detailResponse, boardTreeResponse] = await Promise.all([
      detail ? getTicketById(detail.boardId, detail.ticketId) : undefined,
      boardTree ? listBoards(BOARD_TREE_QUERY_PARAMS) : undefined,
    ]);

    if (detail && detailResponse) {
      queryClient.setQueryData(
        TICKET_KEYS.detail(detail.boardId, detail.ticketId),
        detailResponse,
      );
    }
    if (boardTreeResponse) {
      queryClient.setQueryData(BOARD_KEYS.list(BOARD_TREE_QUERY_PARAMS), boardTreeResponse);
    }
  } catch (error) {
    const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
    console.error(`[${label}] prefetch failed — client will refetch:`, message);
  }

  return dehydrate(queryClient);
}

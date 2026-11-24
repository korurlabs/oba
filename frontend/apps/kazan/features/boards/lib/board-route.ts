/**
 * Board route contract (boundary-safe).
 *
 * This module is intentionally NOT marked "use client": the server
 * components under `app/[locale]/(dashboard)/board/…` resolve their route
 * param through it. In a "use client" module every export would become a
 * client-reference proxy and stop being callable on the server (see
 * server-prefetch-convention). Client components import the path builders
 * from here too, so every board URL is built in one place.
 */

import { cache } from "react";
import { notFound } from "next/navigation";
import type { ApiResponse } from "@oba/shared/schema";
import { isApiError } from "@oba/shared/api/axios";
import { getBoard, getBoardBySlug } from "../api";
import type { BoardListResponse } from "../schemas";

/** The tabs of a board page, each a fixed segment after the board slug. */
export const BOARD_TAB = {
  sprint: "sprint",
  backlog: "backlog",
  roadmap: "roadmap",
  all: "all",
} as const;

export type BoardTab = (typeof BOARD_TAB)[keyof typeof BOARD_TAB];

/**
 * The order in which a board lists its tabs. The work of the current sprint
 * comes first, then what waits for a sprint and the plan over time; All, the
 * sprint and the backlog on one page, comes last.
 */
export const BOARD_TAB_ORDER: readonly BoardTab[] = [
  BOARD_TAB.sprint,
  BOARD_TAB.backlog,
  BOARD_TAB.roadmap,
  BOARD_TAB.all,
];

/**
 * The tab a board opens on when a link names no tab (the board URL itself,
 * the sidebar, the board cards). It is the first tab, the current sprint.
 */
export const BOARD_DEFAULT_TAB: BoardTab = BOARD_TAB.sprint;

/** The segment of the ticket page, `/board/<slug>/ticket/<KEY>`. */
export const BOARD_TICKET_SEGMENT = "ticket";

/**
 * Every fixed segment under `/board/<slug>/`. A sprint slug sits in the same
 * position, and the App Router matches a static segment before a dynamic
 * one, so the server never gives a sprint one of these slugs. A new fixed
 * segment must also join the reserved sprint slugs of the backend, or an
 * existing sprint with that name would become unreachable.
 */
export const BOARD_FIXED_SEGMENTS: ReadonlySet<string> = new Set<string>([
  ...Object.values(BOARD_TAB),
  BOARD_TICKET_SEGMENT,
]);

const UUID_SHAPE = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

/**
 * True for a UUID-shaped route value. The server never gives a board,
 * sprint or channel a UUID-shaped slug, so such a value is always a record
 * id from a link made before slugs existed.
 */
export function isUuid(value: string): boolean {
  return UUID_SHAPE.test(value);
}

/** `/board/<slug>`, or `/board/<slug>/<tab>` when a tab is given. */
export function boardPath(boardSlug: string, tab?: BoardTab): string {
  return tab ? `/board/${boardSlug}/${tab}` : `/board/${boardSlug}`;
}

/** The sprint tab of one sprint, `/board/<board-slug>/<sprint-slug>`. */
export function sprintPath(boardSlug: string, sprintSlug: string): string {
  return `/board/${boardSlug}/${sprintSlug}`;
}

export interface BoardRouteResolution {
  board: BoardListResponse;
  /**
   * True when the route value was the board's id rather than its slug. The
   * page then redirects to the same view under the slug, so an old link
   * still lands on its content and the address bar shows the clean URL.
   */
  legacy: boolean;
}

/**
 * Looks up the board of the `[boardSlug]` route value among the boards the
 * user is a member of: by slug, or by id when the value is UUID-shaped.
 * Null when there is no such board. The backend answers 404 alike for an
 * unknown board and for a board the user may not see, so a caller cannot
 * reveal which one it was. Wrapped in `cache()` so the layout and the page
 * of one request share a single lookup.
 */
export const findBoardRoute = cache(
  async (routeValue: string): Promise<BoardRouteResolution | null> => {
    const legacy = isUuid(routeValue);
    const board = await findBoard(() =>
      legacy ? getBoard(routeValue) : getBoardBySlug(routeValue)
    );
    return board ? { board, legacy } : null;
  }
);

/**
 * Resolves the `[boardSlug]` route value to a board the user is a member of,
 * or renders the not-found page. Every board page resolves its board this
 * way; only the ticket page can do without one (see `findBoardRoute`).
 */
export async function resolveBoardRoute(routeValue: string): Promise<BoardRouteResolution> {
  const resolution = await findBoardRoute(routeValue);
  if (!resolution) notFound();
  return resolution;
}

/**
 * Runs one board lookup and reads a 404 as "no such board". Every other
 * failure is re-thrown so that the route's error boundary shows it instead
 * of a misleading not-found page.
 */
async function findBoard(
  load: () => Promise<ApiResponse<BoardListResponse>>
): Promise<BoardListResponse | null> {
  try {
    const response = await load();
    return response.data ?? null;
  } catch (error) {
    if (isApiError(error) && error.status === 404) return null;
    throw error;
  }
}

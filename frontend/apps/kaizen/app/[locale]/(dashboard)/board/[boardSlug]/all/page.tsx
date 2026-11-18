import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { redirect } from "@oba/shared/libs/i18n/navigation";
import { listSprints } from "@/features/sprints/api";
import { getBacklog } from "@/features/backlog/api";
import { listBoards } from "@/features/boards/api";
import { SPRINT_KEYS } from "@/features/sprints/hooks/query-keys";
import { BACKLOG_KEYS } from "@/features/backlog/hooks/query-keys";
import { BOARD_KEYS, BOARD_TREE_PARAMS } from "@/features/boards/hooks/query-keys";
import { AllPageView } from "@/features/all/components/all-page-view";
import { BOARD_TAB, boardPath, resolveBoardRoute } from "@/features/boards/lib/board-route";
import type { BoardListResponse } from "@/features/boards/schemas";
import { ALL_TAB_SPRINT_PARAM, defaultSprint } from "@/features/sprints/lib/sprint-selection";
import { isKaizenClosedOnServer } from "@/components/layout/kaizen-access-server";
import {
  LEGACY_SPRINT_ID_PARAM,
  singleValue,
  withQuery,
  type RouteSearchParams,
} from "../board-page-data";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("all");
  return { title: t("title") };
}

interface AllPageProps {
  params: Promise<{ locale: string; boardSlug: string }>;
  searchParams: Promise<RouteSearchParams>;
}

/**
 * The slug URL of an id-based All tab link. Its `sprintId` becomes the
 * sprint's slug, left out for the default sprint, which the tab shows
 * without a parameter; a sprint that no longer exists on the board is
 * dropped, so the tab opens on the default sprint.
 */
async function legacyAllTabHref(board: BoardListResponse, searchParams: RouteSearchParams): Promise<string> {
  const path = boardPath(board.slug, BOARD_TAB.all);
  const drop = [LEGACY_SPRINT_ID_PARAM];
  const sprintId = singleValue(searchParams[LEGACY_SPRINT_ID_PARAM]);
  if (!sprintId) return withQuery(path, searchParams, { drop });

  const sprints = (await listSprints(board.id)).data ?? [];
  const sprint = sprints.find((candidate) => candidate.id === sprintId);
  if (!sprint || sprint === defaultSprint(sprints)) return withQuery(path, searchParams, { drop });
  return withQuery(path, searchParams, { drop, set: { [ALL_TAB_SPRINT_PARAM]: sprint.slug } });
}

/**
 * Server-prefetch the stable, on-mount queries for the All tab:
 *
 *   - sprints list     (useSprints)          -> ["sprints", "list", boardId]
 *   - backlog detail   (useBacklog)          -> ["backlog", "detail", boardId]
 *   - board tree list  (useCachedChildBoards) -> ["boards", "list", { includeChildren: true, amount: 100 }]
 *
 * Skipped (left client-side):
 *   - both tickets lists (sprint section + backlog section, useTickets):
 *     their params depend on `currentSprint.id` (client-derived from the
 *     sprint list + nuqs `?sprintId` + auto-select), the backlog id, the
 *     localStorage-backed includeAllBoards flag, and nuqs filters -- none
 *     byte-reproducible on the server. They stay client-fetched.
 *
 * All three fetches are independent -> Promise.all. Prefetch is best-effort.
 *
 * A link by board id redirects to the slug URL first.
 */
export default async function AllPage({ params, searchParams }: AllPageProps) {
  const { locale, boardSlug } = await params;
  // The no-access panel takes the place of the page, and the API would refuse
  // the lookup, so the page renders nothing (see `AppAccessGate`).
  if (await isKaizenClosedOnServer()) return null;
  const { board, legacy } = await resolveBoardRoute(boardSlug);
  if (legacy) {
    redirect({ href: await legacyAllTabHref(board, await searchParams), locale });
  }

  const boardId = board.id;
  const queryClient = makeQueryClient();

  try {
    const [sprints, backlog, boardTree] = await Promise.all([
      listSprints(boardId),
      getBacklog(boardId),
      listBoards(BOARD_TREE_PARAMS as unknown as Record<string, unknown>),
    ]);
    queryClient.setQueryData(SPRINT_KEYS.list(boardId), sprints);
    queryClient.setQueryData(BACKLOG_KEYS.detail(boardId), backlog);
    queryClient.setQueryData(
      BOARD_KEYS.list(BOARD_TREE_PARAMS as unknown as Record<string, unknown>),
      boardTree,
    );
  } catch (error) {
    const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
    console.error("[all-page] prefetch failed — client will refetch:", message);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <AllPageView boardId={boardId} />
    </HydrationBoundary>
  );
}

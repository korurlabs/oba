import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { redirect } from "@oba/shared/libs/i18n/navigation";
import { BOARD_TAB, boardPath, resolveBoardRoute, sprintPath } from "@/features/boards/lib/board-route";
import { SprintPageView } from "@/features/sprints/components/sprint-page-view";
import { defaultSprint } from "@/features/sprints/lib/sprint-selection";
import { isKazanClosedOnServer } from "@/components/layout/kazan-access-server";
import {
  LEGACY_SPRINT_ID_PARAM,
  loadBoardSprints,
  prefetchBoardTree,
  singleValue,
  withQuery,
  type RouteSearchParams,
} from "../board-page-data";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("sprint");
  return { title: t("title") };
}

interface SprintTabPageProps {
  params: Promise<{ locale: string; boardSlug: string }>;
  searchParams: Promise<RouteSearchParams>;
}

/**
 * The Sprint tab without a sprint: redirects to the page of the board's
 * default sprint (active, else the first planned, else the first), so every
 * sprint URL names its sprint.
 *
 * A `sprintId` parameter, from a link made before sprint slugs or from a
 * ticket card that opens a child board's sprint, selects that sprint
 * instead; an id the board no longer has falls back to the default sprint.
 * A link by board id lands on the slug URL in the same hop.
 *
 * A board without sprints renders the sprint view, whose empty state offers
 * to create one. When the sprint list cannot be loaded the view renders too,
 * after the failure is logged, and the client fetches the list itself.
 */
export default async function SprintTabPage({ params, searchParams }: SprintTabPageProps) {
  const { locale, boardSlug } = await params;
  // The no-access panel takes the place of the page, and the API would refuse
  // the lookup, so the page renders nothing (see `AppAccessGate`).
  if (await isKazanClosedOnServer()) return null;
  const { board, legacy } = await resolveBoardRoute(boardSlug);
  const query = await searchParams;
  const queryClient = makeQueryClient();

  const sprints = await loadBoardSprints(queryClient, board.id, "sprint-tab-page");
  if (sprints) {
    const requestedId = singleValue(query[LEGACY_SPRINT_ID_PARAM]);
    const target =
      sprints.find((sprint) => sprint.id === requestedId) ?? defaultSprint(sprints);
    if (target) {
      redirect({
        href: withQuery(sprintPath(board.slug, target.slug), query, { drop: [LEGACY_SPRINT_ID_PARAM] }),
        locale,
      });
    }
  }
  if (legacy) {
    redirect({ href: withQuery(boardPath(board.slug, BOARD_TAB.sprint), query), locale });
  }

  await prefetchBoardTree(queryClient, "sprint-tab-page");

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <SprintPageView boardId={board.id} />
    </HydrationBoundary>
  );
}

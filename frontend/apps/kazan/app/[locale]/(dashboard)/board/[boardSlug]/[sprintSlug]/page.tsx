import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { redirect } from "@oba/shared/libs/i18n/navigation";
import { resolveBoardRoute, sprintPath } from "@/features/boards/lib/board-route";
import { SprintPageView } from "@/features/sprints/components/sprint-page-view";
import { isKazanClosedOnServer } from "@/components/layout/kazan-access-server";
import {
  loadBoardSprints,
  prefetchBoardTree,
  withQuery,
  type RouteSearchParams,
} from "../board-page-data";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("sprint");
  return { title: t("title") };
}

interface SprintPageProps {
  params: Promise<{ locale: string; boardSlug: string; sprintSlug: string }>;
  searchParams: Promise<RouteSearchParams>;
}

/**
 * The Sprint tab of one sprint, `/board/<board-slug>/<sprint-slug>`. The
 * fixed tab segments (`all`, `backlog`, `roadmap`, `sprint`, `ticket`) are
 * static routes and win over this one, and no sprint gets one of them as
 * its slug.
 *
 * Server-prefetches the stable, on-mount queries:
 *
 *   - sprints list     (useSprints)          -> ["sprints", "list", boardId]
 *   - board tree list  (useCachedChildBoards) -> ["boards", "list", { includeChildren: true, amount: 100 }]
 *
 * The sprint list also decides the page: a slug that is not on the board
 * renders the not-found page. When the list cannot be loaded the failure is
 * logged and the view renders, and the client fetches the list itself. The
 * tickets list stays client-side: its key embeds the localStorage-backed
 * includeAllBoards flag and nuqs filters.
 */
export default async function SprintPage({ params, searchParams }: SprintPageProps) {
  const { locale, boardSlug, sprintSlug } = await params;
  // The no-access panel takes the place of the page, and the API would refuse
  // the lookup, so the page renders nothing (see `AppAccessGate`).
  if (await isKazanClosedOnServer()) return null;
  const { board, legacy } = await resolveBoardRoute(boardSlug);
  if (legacy) {
    redirect({ href: withQuery(sprintPath(board.slug, sprintSlug), await searchParams), locale });
  }

  const queryClient = makeQueryClient();
  const [sprints] = await Promise.all([
    loadBoardSprints(queryClient, board.id, "sprint-page"),
    prefetchBoardTree(queryClient, "sprint-page"),
  ]);
  if (sprints && !sprints.some((sprint) => sprint.slug === sprintSlug)) {
    notFound();
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <SprintPageView boardId={board.id} />
    </HydrationBoundary>
  );
}

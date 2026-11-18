import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { redirect } from "@oba/shared/libs/i18n/navigation";
import { getRoadmap } from "@/features/milestones/api";
import { ROADMAP_KEYS, ROADMAP_PAGE_PARAMS } from "@/features/milestones/hooks/query-keys";
import { RoadmapPageView } from "@/features/roadmap/components/roadmap-page-view";
import { BOARD_TAB, boardPath, resolveBoardRoute } from "@/features/boards/lib/board-route";
import { isKaizenClosedOnServer } from "@/components/layout/kaizen-access-server";
import { withQuery, type RouteSearchParams } from "../board-page-data";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("roadmap");
  return { title: t("title") };
}

interface RoadmapPageProps {
  params: Promise<{ locale: string; boardSlug: string }>;
  searchParams: Promise<RouteSearchParams>;
}

/**
 * Server-prefetches the roadmap (useRoadmap) under
 * ["roadmap", "board", boardId, { includeChildBoards: true }], the key the
 * view asks for on mount. The zoom lives only in the URL and changes no query.
 * Prefetch is best-effort: on failure the client fetches it.
 *
 * A link by board id redirects to the slug URL first.
 */
export default async function RoadmapPage({ params, searchParams }: RoadmapPageProps) {
  const { locale, boardSlug } = await params;
  // The no-access panel takes the place of the page, and the API would refuse
  // the lookup, so the page renders nothing (see `AppAccessGate`).
  if (await isKaizenClosedOnServer()) return null;
  const { board, legacy } = await resolveBoardRoute(boardSlug);
  if (legacy) {
    redirect({ href: withQuery(boardPath(board.slug, BOARD_TAB.roadmap), await searchParams), locale });
  }

  const boardId = board.id;
  const queryClient = makeQueryClient();

  try {
    const roadmap = await getRoadmap(boardId, ROADMAP_PAGE_PARAMS);
    queryClient.setQueryData(ROADMAP_KEYS.board(boardId, ROADMAP_PAGE_PARAMS), roadmap);
  } catch (error) {
    const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
    console.error("[roadmap-page] prefetch failed — client will refetch:", message);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <RoadmapPageView boardId={boardId} />
    </HydrationBoundary>
  );
}

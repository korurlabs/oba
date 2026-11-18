import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { redirect } from "@oba/shared/libs/i18n/navigation";
import { getBacklog } from "@/features/backlog/api";
import { listBoards } from "@/features/boards/api";
import { BACKLOG_KEYS } from "@/features/backlog/hooks/query-keys";
import { BOARD_KEYS, BOARD_TREE_PARAMS } from "@/features/boards/hooks/query-keys";
import { BacklogPageView } from "@/features/backlog/components/backlog-page-view";
import { BOARD_TAB, boardPath, resolveBoardRoute } from "@/features/boards/lib/board-route";
import { isKaizenClosedOnServer } from "@/components/layout/kaizen-access-server";
import { LEGACY_BACKLOG_ID_PARAM, withQuery, type RouteSearchParams } from "../board-page-data";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("backlog");
  return { title: t("title") };
}

interface BacklogPageProps {
  params: Promise<{ locale: string; boardSlug: string }>;
  searchParams: Promise<RouteSearchParams>;
}

/**
 * Server-prefetch the stable, on-mount queries for the Backlog tab:
 *
 *   - backlog detail   (useBacklog)         -> ["backlog", "detail", boardId]
 *   - board tree list  (useCachedChildBoards) -> ["boards", "list", { includeChildren: true, amount: 100 }]
 *
 * Skipped (left client-side):
 *   - tickets list (useTickets): its key embeds the localStorage-backed
 *     includeAllBoards flag and nuqs filter params, which cannot be
 *     reproduced byte-identically on the server. A wrong key would be dead
 *     weight, so the tickets query stays client-fetched.
 *   - sprints list (useSprints): deferred on this page (enabled only after
 *     a "move to sprint" interaction), so it does not fire on mount.
 *
 * Both fetches are independent -> Promise.all. Prefetch is best-effort.
 *
 * A link by board id redirects to the slug URL first, without the
 * `backlogId` the old tab wrote: a board has one backlog, so it selected
 * nothing.
 */
export default async function BacklogPage({ params, searchParams }: BacklogPageProps) {
  const { locale, boardSlug } = await params;
  // The no-access panel takes the place of the page, and the API would refuse
  // the lookup, so the page renders nothing (see `AppAccessGate`).
  if (await isKaizenClosedOnServer()) return null;
  const { board, legacy } = await resolveBoardRoute(boardSlug);
  if (legacy) {
    redirect({
      href: withQuery(boardPath(board.slug, BOARD_TAB.backlog), await searchParams, {
        drop: [LEGACY_BACKLOG_ID_PARAM],
      }),
      locale,
    });
  }

  const boardId = board.id;
  const queryClient = makeQueryClient();

  try {
    const [backlog, boardTree] = await Promise.all([
      getBacklog(boardId),
      listBoards(BOARD_TREE_PARAMS as unknown as Record<string, unknown>),
    ]);
    queryClient.setQueryData(BACKLOG_KEYS.detail(boardId), backlog);
    queryClient.setQueryData(
      BOARD_KEYS.list(BOARD_TREE_PARAMS as unknown as Record<string, unknown>),
      boardTree,
    );
  } catch (error) {
    const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
    console.error("[backlog-page] prefetch failed — client will refetch:", message);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <BacklogPageView boardId={boardId} />
    </HydrationBoundary>
  );
}

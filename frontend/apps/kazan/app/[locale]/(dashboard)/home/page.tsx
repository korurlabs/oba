import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary, type QueryClient } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { listBoards } from "@/features/boards/api";
import { BOARD_KEYS } from "@/features/boards/hooks/query-keys";
import { getHomeDeadlines, getHomeSprint, getHomeSummary } from "@/features/home/api";
import { HOME_KEYS } from "@/features/home/hooks/query-keys";
import { isKazanClosedOnServer } from "@/components/layout/kazan-access-server";
import { DashboardView } from "./dashboard-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("metadata");
  return {
    title: t("dashboard"),
  };
}

function describeError(error: unknown): string {
  return error instanceof Error ? `${error.name}: ${error.message}` : String(error);
}

/** Mirrors `useBoards()` (no params): key ["boards", "list", {}], queryFn listBoards(undefined). */
async function prefetchBoards(queryClient: QueryClient): Promise<void> {
  try {
    queryClient.setQueryData(BOARD_KEYS.list({}), await listBoards());
  } catch (error) {
    console.error("[dashboard-page] boards prefetch failed, client will refetch:", describeError(error));
  }
}

/** Mirrors `useHomeSummary()`: key ["home", "summary"], queryFn getHomeSummary(). */
async function prefetchHomeSummary(queryClient: QueryClient): Promise<void> {
  try {
    queryClient.setQueryData(HOME_KEYS.summary(), await getHomeSummary());
  } catch (error) {
    console.error("[dashboard-page] home summary prefetch failed, client will refetch:", describeError(error));
  }
}

/** Mirrors `useHomeSprint()` without a focus hint: key ["home", "sprint", null], queryFn getHomeSprint(). */
async function prefetchHomeSprint(queryClient: QueryClient): Promise<void> {
  try {
    queryClient.setQueryData(HOME_KEYS.sprint(), await getHomeSprint());
  } catch (error) {
    console.error("[dashboard-page] home sprint prefetch failed, client will refetch:", describeError(error));
  }
}

/** Mirrors `useHomeDeadlines()`: key ["home", "deadlines"], queryFn getHomeDeadlines(). */
async function prefetchHomeDeadlines(queryClient: QueryClient): Promise<void> {
  try {
    queryClient.setQueryData(HOME_KEYS.deadlines(), await getHomeDeadlines());
  } catch (error) {
    console.error("[dashboard-page] home deadlines prefetch failed, client will refetch:", describeError(error));
  }
}

/**
 * Server-prefetch the data the Home page reads on mount: the boards list (for
 * the "create your first board" card), the open counts of the links menu, the
 * default focus sprint with its burndown, and the deadlines, so the top of the
 * page renders without a client-side waterfall. The leaderboard and the stale
 * backlog sit lower on the page and load on the client.
 *
 * Each prefetch is best-effort and independent: on failure the client simply
 * refetches that query. Nothing is prefetched when the selected organization
 * does not grant Kazan: the API would refuse every call, and the no-access
 * panel takes the place of the page.
 */
export default async function DashboardPage() {
  const queryClient = makeQueryClient();

  if (!(await isKazanClosedOnServer())) {
    await Promise.all([
      prefetchBoards(queryClient),
      prefetchHomeSummary(queryClient),
      prefetchHomeSprint(queryClient),
      prefetchHomeDeadlines(queryClient),
    ]);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <DashboardView />
    </HydrationBoundary>
  );
}

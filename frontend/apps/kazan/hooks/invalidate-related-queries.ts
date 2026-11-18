import { QueryClient, type QueryKey } from "@tanstack/react-query";

interface InvalidateOptions {
  /**
   * Query keys to exclude from invalidation. Useful when deleting an
   * entity whose detail query may still be mounted — invalidating it
   * would trigger a refetch for a resource that no longer exists (404).
   *
   * Matching uses `JSON.stringify` equality on the full key array.
   */
  excludeKeys?: readonly QueryKey[];
}

/**
 * Invalidates all ticket/sprint/backlog queries across every board, the
 * milestone and roadmap queries whose done/total counts and health are
 * derived from those tickets, and the home dashboard, whose counts, burndown,
 * leaderboard, deadlines and stale backlog are all read from them.
 *
 * Active queries (mounted components) are refetched immediately via
 * `invalidateQueries`. Inactive queries (e.g. Board B while you are
 * on Board A) have their cache removed so that TanStack Query is
 * forced to fetch fresh data when the component mounts — even when
 * `refetchOnMount` is disabled globally.
 */
export const invalidateRelatedQueries = async (
  queryClient: QueryClient,
  options?: InvalidateOptions,
) => {
  const keys = [
    ["tickets"],
    ["sprints"],
    ["backlog"],
    ["milestones"],
    ["roadmap"],
    ["home"],
  ] as const;
  const excludeSet = new Set(
    options?.excludeKeys?.map((key) => JSON.stringify(key)),
  );

  await Promise.all(
    keys.map((queryKey) =>
      excludeSet.size > 0
        ? queryClient.invalidateQueries({
            queryKey,
            predicate: (query) =>
              !excludeSet.has(JSON.stringify(query.queryKey)),
          })
        : queryClient.invalidateQueries({ queryKey }),
    ),
  );

  // Remove cached data for queries that have no active observers.
  // Without this, navigating to another board would serve stale cache
  // because the global QueryClient sets refetchOnMount: false.
  keys.forEach((queryKey) => {
    queryClient.removeQueries({ queryKey, type: "inactive" });
  });
};

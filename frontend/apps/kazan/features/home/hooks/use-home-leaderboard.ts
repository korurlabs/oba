"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useKazanOpen } from "@/components/layout/use-kazan-access";
import { getHomeLeaderboard } from "../api";
import type { HomeLeaderboardPeriod } from "../schemas";
import { HOME_KEYS } from "./query-keys";

/**
 * The size-weighted leaderboard for `period`. The previous period's ranking
 * stays on screen while the other one loads, so switching the select does not
 * collapse the list to a skeleton.
 */
export function useHomeLeaderboard(period: HomeLeaderboardPeriod) {
  const kazanOpen = useKazanOpen();
  return useQuery({
    queryKey: HOME_KEYS.leaderboard(period),
    queryFn: () => getHomeLeaderboard(period),
    enabled: kazanOpen,
    placeholderData: keepPreviousData,
  });
}

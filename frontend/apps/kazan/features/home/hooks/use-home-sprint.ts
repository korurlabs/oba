"use client";

import { useQuery } from "@tanstack/react-query";
import { useKazanOpen } from "@/components/layout/use-kazan-access";
import { getHomeSprint } from "../api";
import { HOME_KEYS } from "./query-keys";
import { HOME_POLL_MS } from "./use-home-summary";

/**
 * The focus sprint with its countdown dates and burndown. `boardId` is the
 * focus hint from the board select; without it, or for a board the server
 * does not accept, the server picks its default focus board. Polled
 * while the tab is visible, so the burndown follows tickets finished by
 * others during the day.
 */
export function useHomeSprint(boardId?: string) {
  const kazanOpen = useKazanOpen();
  return useQuery({
    queryKey: HOME_KEYS.sprint(boardId),
    queryFn: () => getHomeSprint(boardId),
    enabled: kazanOpen,
    refetchInterval: HOME_POLL_MS,
    refetchIntervalInBackground: false,
  });
}

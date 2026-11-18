"use client";

import { useQuery } from "@tanstack/react-query";
import { useKazanOpen } from "@/components/layout/use-kazan-access";
import { getHomeSummary } from "../api";
import { HOME_KEYS } from "./query-keys";

export { HOME_KEYS };

/** How often the live home sections (counts, sprint) are fetched while the tab is visible. */
export const HOME_POLL_MS = 60_000;

/**
 * Open incident and active sprint ticket counts for the home page. Polled
 * only while the tab is visible, because nobody reads the counts of a hidden
 * tab and focusing it refetches them anyway.
 */
export function useHomeSummary() {
  const kazanOpen = useKazanOpen();
  return useQuery({
    queryKey: HOME_KEYS.summary(),
    queryFn: getHomeSummary,
    enabled: kazanOpen,
    refetchInterval: HOME_POLL_MS,
    refetchIntervalInBackground: false,
  });
}

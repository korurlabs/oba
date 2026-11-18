"use client";

import { useQuery } from "@tanstack/react-query";
import { useKazanOpen } from "@/components/layout/use-kazan-access";
import { getHomeStaleBacklog } from "../api";
import { HOME_KEYS } from "./query-keys";

/**
 * Backlog tickets on the user's boards with no activity for 30 days. Ticket
 * deletes and restores refresh it through `invalidateRelatedQueries`.
 */
export function useHomeStaleBacklog() {
  const kazanOpen = useKazanOpen();
  return useQuery({
    queryKey: HOME_KEYS.staleBacklog(),
    queryFn: getHomeStaleBacklog,
    enabled: kazanOpen,
  });
}

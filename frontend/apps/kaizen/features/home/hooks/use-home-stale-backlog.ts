"use client";

import { useQuery } from "@tanstack/react-query";
import { useKaizenOpen } from "@/components/layout/use-kaizen-access";
import { getHomeStaleBacklog } from "../api";
import { HOME_KEYS } from "./query-keys";

/**
 * Backlog tickets on the user's boards with no activity for 30 days. Ticket
 * deletes and restores refresh it through `invalidateRelatedQueries`.
 */
export function useHomeStaleBacklog() {
  const kaizenOpen = useKaizenOpen();
  return useQuery({
    queryKey: HOME_KEYS.staleBacklog(),
    queryFn: getHomeStaleBacklog,
    enabled: kaizenOpen,
  });
}

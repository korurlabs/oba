"use client";

import { useQuery } from "@tanstack/react-query";
import { useKaizenOpen } from "@/components/layout/use-kaizen-access";
import { getHomeDeadlines } from "../api";
import { HOME_KEYS } from "./query-keys";

/** OPEN milestones on the user's boards that are overdue or due within 60 days. */
export function useHomeDeadlines() {
  const kaizenOpen = useKaizenOpen();
  return useQuery({
    queryKey: HOME_KEYS.deadlines(),
    queryFn: getHomeDeadlines,
    enabled: kaizenOpen,
  });
}

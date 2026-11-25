"use client";

import { useQuery } from "@tanstack/react-query";
import { getCyberscore, getCyberscoreTrend } from "../api";
import { CYBERSCORE_TREND_DAYS } from "../schemas";
import { CYBERSCORE_KEYS } from "./query-keys";

export { CYBERSCORE_KEYS };

/** The organization's current score; the not-yet-scored shape when it has none. */
export function useCyberscore() {
  return useQuery({
    queryKey: CYBERSCORE_KEYS.current(),
    queryFn: getCyberscore,
  });
}

/** One point per business day over the last `days` days, oldest first. */
export function useCyberscoreTrend(days: number = CYBERSCORE_TREND_DAYS) {
  return useQuery({
    queryKey: CYBERSCORE_KEYS.trend(days),
    queryFn: () => getCyberscoreTrend(days),
  });
}

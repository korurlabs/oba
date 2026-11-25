import { CYBERSCORE_TREND_DAYS, type CheckStatus } from "../schemas";

/*
 * Query keys of the cyberscore pages. Kept out of the "use client" hook files
 * so the pages can prefetch under the exact keys the hooks read; imported
 * from a client module, every export would become a client reference that the
 * server cannot call.
 *
 * Score and checks have separate roots: a status change touches only the
 * check list, while every other check write also moves the score and trend.
 */

export const CYBERSCORE_KEYS = {
  all: ["cyberscore"] as const,
  current: () => [...CYBERSCORE_KEYS.all, "current"] as const,
  trend: (days: number = CYBERSCORE_TREND_DAYS) => [...CYBERSCORE_KEYS.all, "trend", days] as const,
};

/** The list key spells "no filter" as `ALL`, so a prefetch and the hook agree on it. */
export const CHECK_KEYS = {
  all: ["cyberscore-checks"] as const,
  list: (status?: CheckStatus) => [...CHECK_KEYS.all, "list", status ?? "ALL"] as const,
};

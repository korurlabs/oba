import type { HomeLeaderboardPeriod } from "../schemas";

/**
 * Query-key factory for the home page.
 *
 * Kept out of the "use client" hook file so a server component can import it
 * to prefetch under the same key the hook uses; from a "use client" module the
 * export would be a client reference and not usable on the server.
 *
 * Every key starts with `["home"]`, which `invalidateRelatedQueries` sweeps, so
 * a ticket, sprint or milestone mutation anywhere refreshes the dashboard.
 */
export const HOME_KEYS = {
  all: ["home"] as const,
  summary: () => [...HOME_KEYS.all, "summary"] as const,
  staleBacklog: () => [...HOME_KEYS.all, "stale-backlog"] as const,
  leaderboard: (period: HomeLeaderboardPeriod) => [...HOME_KEYS.all, "leaderboard", period] as const,
  // `null` stands for "no hint", so the server's default focus has one key
  // whether the caller passes undefined or nothing.
  sprint: (boardId?: string) => [...HOME_KEYS.all, "sprint", boardId ?? null] as const,
  deadlines: () => [...HOME_KEYS.all, "deadlines"] as const,
};

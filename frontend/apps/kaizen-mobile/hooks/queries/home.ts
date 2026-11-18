import { useQuery } from "@tanstack/react-query";
import { useIsFocused } from "expo-router";

import { keepPreviousWithin } from "@/hooks/queries/placeholder";
import { api, readData } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import {
  homeDeadlinesSchema,
  homeLeaderboardSchema,
  homeStaleBacklogSchema,
  homeSprintSchema,
  homeSummarySchema,
  type HomeDeadline,
  type HomeLeaderboard,
  type HomeLeaderboardPeriod,
  type HomeSprint,
  type HomeStaleBacklog,
  type HomeSummary,
} from "@/lib/schemas/home";

/**
 * How often a Home section refetches while Home is on screen, so the burndown,
 * leaderboard and counts follow what others finish during the day.
 */
export const HOME_REFETCH_MS = 60_000;

/**
 * Query keys of the Home dashboard, scoped by organization so a switch never
 * shows another organization's data. A ticket, sprint or milestone write
 * invalidates `homeKeys.all(orgId)` to refresh every section.
 */
export const homeKeys = {
  all: (orgId: string) => ["home", orgId] as const,
  summary: (orgId: string) => [...homeKeys.all(orgId), "summary"] as const,
  sprint: (orgId: string, boardId: string | null) =>
    [...homeKeys.all(orgId), "sprint", boardId] as const,
  leaderboard: (orgId: string, period: HomeLeaderboardPeriod) =>
    [...homeKeys.all(orgId), "leaderboard", period] as const,
  deadlines: (orgId: string) => [...homeKeys.all(orgId), "deadlines"] as const,
  staleBacklog: (orgId: string) => [...homeKeys.all(orgId), "stale-backlog"] as const,
};

/** Refetches every minute while the screen is focused, and not at all behind another screen. */
function useHomeRefetchInterval(): number | false {
  const focused: boolean = useIsFocused();
  return focused ? HOME_REFETCH_MS : false;
}

/** `GET /kaizen/home/summary`: the counts of the Links sheet. */
export function useHomeSummary() {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<HomeSummary>({
    queryKey: homeKeys.summary(orgId ?? ""),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.home.summary);
      return readData(homeSummarySchema, response.data);
    },
    enabled: !!orgId,
    refetchInterval: useHomeRefetchInterval(),
  });
}

/**
 * `GET /kaizen/home/sprint`: the active sprint of the focus board with its
 * burndown. Without `boardId` the server picks the focus board.
 */
export function useHomeSprint(boardId: string | null) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<HomeSprint>({
    queryKey: homeKeys.sprint(orgId ?? "", boardId),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.home.sprint, {
        params: boardId ? { boardId } : undefined,
      });
      return readData(homeSprintSchema, response.data);
    },
    enabled: !!orgId,
    refetchInterval: useHomeRefetchInterval(),
    placeholderData: keepPreviousWithin(homeKeys.all(orgId ?? "")),
  });
}

/** `GET /kaizen/home/leaderboard`: every member ranked by points in `period`. */
export function useHomeLeaderboard(period: HomeLeaderboardPeriod) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<HomeLeaderboard>({
    queryKey: homeKeys.leaderboard(orgId ?? "", period),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.home.leaderboard, { params: { period } });
      return readData(homeLeaderboardSchema, response.data);
    },
    enabled: !!orgId,
    refetchInterval: useHomeRefetchInterval(),
    placeholderData: keepPreviousWithin(homeKeys.all(orgId ?? "")),
  });
}

/** `GET /kaizen/home/deadlines`: up to 8 open milestones due within 60 days or overdue. */
export function useHomeDeadlines() {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<HomeDeadline[]>({
    queryKey: homeKeys.deadlines(orgId ?? ""),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.home.deadlines);
      return readData(homeDeadlinesSchema, response.data);
    },
    enabled: !!orgId,
    refetchInterval: useHomeRefetchInterval(),
  });
}

/** `GET /kaizen/home/stale-backlog`: backlog tickets untouched for 30 days. */
export function useHomeStaleBacklog() {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<HomeStaleBacklog>({
    queryKey: homeKeys.staleBacklog(orgId ?? ""),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.home.staleBacklog);
      return readData(homeStaleBacklogSchema, response.data);
    },
    enabled: !!orgId,
    refetchInterval: useHomeRefetchInterval(),
  });
}

import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  HomeDeadlineResponse,
  HomeLeaderboardPeriod,
  HomeLeaderboardResponse,
  HomeSprintResponse,
  HomeStaleBacklogResponse,
  HomeSummaryResponse,
} from "../schemas";

export async function getHomeSummary() {
  const res = await api.get<ApiResponse<HomeSummaryResponse>>(API_ROUTES.home.summary);
  return res.data;
}

export async function getHomeStaleBacklog() {
  const res = await api.get<ApiResponse<HomeStaleBacklogResponse>>(API_ROUTES.home.staleBacklog);
  return res.data;
}

export async function getHomeLeaderboard(period: HomeLeaderboardPeriod) {
  const res = await api.get<ApiResponse<HomeLeaderboardResponse>>(API_ROUTES.home.leaderboard, {
    params: { period },
  });
  return res.data;
}

/**
 * The focus sprint. `boardId` is only a hint: the server ignores a board the
 * user is not a member of or one without an ACTIVE sprint and picks its
 * default focus board instead, so the hint is left out when absent.
 */
export async function getHomeSprint(boardId?: string) {
  const res = await api.get<ApiResponse<HomeSprintResponse>>(API_ROUTES.home.sprint, {
    params: boardId ? { boardId } : undefined,
  });
  return res.data;
}

export async function getHomeDeadlines() {
  const res = await api.get<ApiResponse<HomeDeadlineResponse[]>>(API_ROUTES.home.deadlines);
  return res.data;
}

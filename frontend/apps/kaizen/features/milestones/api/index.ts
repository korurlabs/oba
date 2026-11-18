import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  MilestoneCreateRequest,
  MilestoneDetailResponse,
  MilestoneListParams,
  MilestoneListResponse,
  MilestoneTicketsRequest,
  MilestoneUpdateRequest,
  RoadmapParams,
  RoadmapResponse,
} from "../schemas";

export async function listMilestones(boardId: string, params?: MilestoneListParams) {
  const res = await api.get<ApiResponse<MilestoneListResponse[]>>(
    API_ROUTES.milestones.list(boardId),
    { params },
  );
  return res.data;
}

export async function getMilestoneById(boardId: string, milestoneId: string) {
  const res = await api.get<ApiResponse<MilestoneDetailResponse>>(
    API_ROUTES.milestones.detail(boardId, milestoneId),
  );
  return res.data;
}

export async function createMilestone(boardId: string, data: MilestoneCreateRequest) {
  const res = await api.post<ApiResponse<MilestoneDetailResponse>>(
    API_ROUTES.milestones.list(boardId),
    data,
  );
  return res.data;
}

export async function updateMilestone(
  boardId: string,
  milestoneId: string,
  data: MilestoneUpdateRequest,
) {
  const res = await api.put<ApiResponse<MilestoneDetailResponse>>(
    API_ROUTES.milestones.detail(boardId, milestoneId),
    data,
  );
  return res.data;
}

export async function closeMilestone(boardId: string, milestoneId: string) {
  const res = await api.post<ApiResponse<MilestoneDetailResponse>>(
    API_ROUTES.milestones.close(boardId, milestoneId),
  );
  return res.data;
}

export async function reopenMilestone(boardId: string, milestoneId: string) {
  const res = await api.post<ApiResponse<MilestoneDetailResponse>>(
    API_ROUTES.milestones.reopen(boardId, milestoneId),
  );
  return res.data;
}

export async function linkMilestoneTickets(
  boardId: string,
  milestoneId: string,
  data: MilestoneTicketsRequest,
) {
  const res = await api.post<ApiResponse<MilestoneDetailResponse>>(
    API_ROUTES.milestones.link(boardId, milestoneId),
    data,
  );
  return res.data;
}

export async function unlinkMilestoneTickets(
  boardId: string,
  milestoneId: string,
  data: MilestoneTicketsRequest,
) {
  const res = await api.post<ApiResponse<MilestoneDetailResponse>>(
    API_ROUTES.milestones.unlink(boardId, milestoneId),
    data,
  );
  return res.data;
}

export async function deleteMilestone(boardId: string, milestoneId: string): Promise<void> {
  await api.delete(API_ROUTES.milestones.detail(boardId, milestoneId));
}

export async function restoreMilestone(boardId: string, milestoneId: string): Promise<void> {
  await api.post(API_ROUTES.milestones.restore(boardId, milestoneId));
}

/** Records that `milestoneId` depends on `dependsOnId`; an existing pair is left as is. */
export async function addMilestoneDependency(
  boardId: string,
  milestoneId: string,
  dependsOnId: string,
) {
  const res = await api.post<ApiResponse<MilestoneDetailResponse>>(
    API_ROUTES.milestones.dependency(boardId, milestoneId, dependsOnId),
  );
  return res.data;
}

export async function removeMilestoneDependency(
  boardId: string,
  milestoneId: string,
  dependsOnId: string,
) {
  const res = await api.delete<ApiResponse<MilestoneDetailResponse>>(
    API_ROUTES.milestones.dependency(boardId, milestoneId, dependsOnId),
  );
  return res.data;
}

export async function getRoadmap(boardId: string, params?: RoadmapParams) {
  const res = await api.get<ApiResponse<RoadmapResponse>>(API_ROUTES.roadmap(boardId), {
    params,
  });
  return res.data;
}

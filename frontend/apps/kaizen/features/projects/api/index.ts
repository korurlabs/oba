import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  ProjectCreateRequest,
  ProjectUpdateRequest,
  ProjectDetailResponse,
  ProjectListResponse,
} from "../schemas";

export async function createProject(
  boardId: string,
  data: ProjectCreateRequest,
) {
  const res = await api.post<ApiResponse<ProjectDetailResponse>>(
    API_ROUTES.projects.list(boardId),
    data,
  );
  return res.data;
}

export async function listProjects(boardId: string) {
  const res = await api.get<ApiResponse<ProjectListResponse[]>>(
    API_ROUTES.projects.list(boardId),
  );
  return res.data;
}

export async function getProjectById(boardId: string, projectId: string) {
  const res = await api.get<ApiResponse<ProjectDetailResponse>>(
    API_ROUTES.projects.detail(boardId, projectId),
  );
  return res.data;
}

export async function updateProject(
  boardId: string,
  projectId: string,
  data: ProjectUpdateRequest,
) {
  const res = await api.put<ApiResponse<ProjectDetailResponse>>(
    API_ROUTES.projects.detail(boardId, projectId),
    data,
  );
  return res.data;
}

export async function deleteProject(
  boardId: string,
  projectId: string,
): Promise<void> {
  await api.delete(API_ROUTES.projects.detail(boardId, projectId));
}

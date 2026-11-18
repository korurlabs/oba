import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  LabelCreateRequest,
  LabelUpdateRequest,
  LabelResponse,
} from "../schemas";

export async function createLabel(boardId: string, data: LabelCreateRequest) {
  const res = await api.post<ApiResponse<LabelResponse>>(
    API_ROUTES.labels.list(boardId),
    data,
  );
  return res.data;
}

export async function listLabels(boardId: string) {
  const res = await api.get<ApiResponse<LabelResponse[]>>(
    API_ROUTES.labels.list(boardId),
  );
  return res.data;
}

export async function updateLabel(
  boardId: string,
  labelId: string,
  data: LabelUpdateRequest,
) {
  const res = await api.put<ApiResponse<LabelResponse>>(
    API_ROUTES.labels.detail(boardId, labelId),
    data,
  );
  return res.data;
}

export async function deleteLabel(
  boardId: string,
  labelId: string,
): Promise<void> {
  await api.delete(API_ROUTES.labels.detail(boardId, labelId));
}

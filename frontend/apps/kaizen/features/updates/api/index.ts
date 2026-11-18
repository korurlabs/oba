import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  UpdateCreateRequest,
  UpdateEditRequest,
  UpdateMissingParams,
  UpdateMissingResponse,
  UpdatePreviewResponse,
  UpdateResponse,
} from "../schemas";

export async function getUpdateById(updateId: string) {
  const res = await api.get<ApiResponse<UpdateResponse>>(API_ROUTES.updates.detail(updateId));
  return res.data;
}

/** Renders the text of a new update; saves nothing. */
export async function previewUpdate(data: UpdateCreateRequest) {
  const res = await api.post<ApiResponse<UpdatePreviewResponse>>(
    API_ROUTES.updates.preview,
    data,
  );
  return res.data;
}

/** Renders the text of an edit of the caller's own update; saves nothing. */
export async function previewUpdateEdit(updateId: string, data: UpdateEditRequest) {
  const res = await api.post<ApiResponse<UpdatePreviewResponse>>(
    API_ROUTES.updates.previewEdit(updateId),
    data,
  );
  return res.data;
}

export async function createUpdate(data: UpdateCreateRequest) {
  const res = await api.post<ApiResponse<UpdateResponse>>(API_ROUTES.updates.list, data);
  return res.data;
}

export async function editUpdate(updateId: string, data: UpdateEditRequest) {
  const res = await api.put<ApiResponse<UpdateResponse>>(
    API_ROUTES.updates.detail(updateId),
    data,
  );
  return res.data;
}

export async function deleteUpdate(updateId: string): Promise<void> {
  await api.delete(API_ROUTES.updates.detail(updateId));
}

/**
 * Expected members of one organization who have not posted by the deadline
 * of `date`, or of today when `date` is null. Only an OWNER or ADMIN of that
 * organization may read it; anyone else gets 403.
 */
export async function getMissingUpdates({ organizationId, date }: UpdateMissingParams) {
  const res = await api.get<ApiResponse<UpdateMissingResponse>>(API_ROUTES.updates.missing, {
    params: date ? { organizationId, date } : { organizationId },
  });
  return res.data;
}

/**
 * The author's latest update created on or before `beforeDay` (a report day,
 * `YYYY-MM-DD`). The list is newest first, so one row is the latest; an empty
 * list means the author has no update by then.
 */
export async function getPreviousUpdate(authorId: string, beforeDay: string) {
  const res = await api.get<ApiResponse<UpdateResponse[]>>(API_ROUTES.updates.list, {
    params: { authorIds: authorId, to: beforeDay, amount: 1, page: 1 },
  });
  return res.data;
}

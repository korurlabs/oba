import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { VersionResponse } from "@/features/versions/schemas";
import type { ApiResponse } from "@/types/api";

import type {
  ChangeDetailResponse,
  ChangeQueryRequest,
  ChangeResponse,
  ChangeSubmitRequest,
  FileHistoryQueryRequest,
  FileHistoryResponse,
  ReviewRequest,
  ReviewResultResponse,
} from "../schemas";

/** The organization's changes, most recently changed first, each with its required role and reviews. */
export async function listChanges(params: ChangeQueryRequest) {
  const res = await api.get<ApiResponse<ChangeResponse[]>>(API_ROUTES.changes.list, { params });
  return res.data;
}

/** One change with what the caller may do on it now. */
export async function getChange(versionId: string) {
  const res = await api.get<ApiResponse<ChangeDetailResponse>>(
    API_ROUTES.changes.detail(versionId)
  );
  return res.data;
}

/**
 * The versions of a pack that added, changed or deleted one path,
 * newest first: approved, superseded and in-review versions and the caller's
 * own drafts, never another member's draft.
 */
export async function getFileHistory(params: FileHistoryQueryRequest) {
  const res = await api.get<ApiResponse<FileHistoryResponse[]>>(API_ROUTES.changes.history, {
    params,
  });
  return res.data;
}

/** Puts a draft in review with the proposer's reason. */
export async function submitChange(versionId: string, data: ChangeSubmitRequest) {
  const res = await api.post<ApiResponse<VersionResponse>>(
    API_ROUTES.changes.submit(versionId),
    data
  );
  return res.data;
}

/** Approves, rejects or comments on a version in review; admins and owners, never its proposer. */
export async function reviewChange(versionId: string, data: ReviewRequest) {
  const res = await api.post<ApiResponse<ReviewResultResponse>>(
    API_ROUTES.changes.reviews(versionId),
    data
  );
  return res.data;
}

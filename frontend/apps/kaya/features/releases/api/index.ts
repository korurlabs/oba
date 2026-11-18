import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type { ReleaseDetailResponse, ReleaseQueryRequest, ReleaseResponse } from "../schemas";

/** A repository's releases, newest first. */
export async function listReleases(repositoryId: string, params: ReleaseQueryRequest) {
  const res = await api.get<ApiResponse<ReleaseResponse[]>>(
    API_ROUTES.releases.list(repositoryId),
    { params }
  );
  return res.data;
}

/** One release with its delivered paths. */
export async function getRelease(repositoryId: string, number: number) {
  const res = await api.get<ApiResponse<ReleaseDetailResponse>>(
    API_ROUTES.releases.detail(repositoryId, number)
  );
  return res.data;
}

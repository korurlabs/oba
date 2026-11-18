import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { VersionResponse } from "@/features/versions/schemas";
import type { ApiResponse } from "@/types/api";

import type { PackVersionQueryRequest } from "../schemas";

/**
 * A pack's versions, newest first and paged: every member's drafts, the
 * versions in review and the released ones.
 */
export async function listPackVersions(params: PackVersionQueryRequest) {
  const res = await api.get<ApiResponse<VersionResponse[]>>(API_ROUTES.versions.list, { params });
  return res.data;
}

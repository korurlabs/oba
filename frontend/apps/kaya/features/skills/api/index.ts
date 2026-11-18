import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type { SkillListResponse, SkillQueryRequest } from "../schemas";

/** The skills of the named versions, by version and then by name; not paged. */
export async function listSkills(params: SkillQueryRequest) {
  const res = await api.get<ApiResponse<SkillListResponse[]>>(API_ROUTES.skills.list, {
    params,
    // The server reads a repeated `versionId=` and not the `versionId[]=` form.
    paramsSerializer: { indexes: null },
  });
  return res.data;
}

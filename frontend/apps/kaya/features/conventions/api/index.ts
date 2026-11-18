import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type { ConventionListResponse, ConventionQueryRequest } from "../schemas";

/** The convention docs of the named versions, by version and then by path; not paged. */
export async function listConventions(params: ConventionQueryRequest) {
  const res = await api.get<ApiResponse<ConventionListResponse[]>>(API_ROUTES.conventions.list, {
    params,
    // The server reads a repeated `versionId=` and not the `versionId[]=` form.
    paramsSerializer: { indexes: null },
  });
  return res.data;
}

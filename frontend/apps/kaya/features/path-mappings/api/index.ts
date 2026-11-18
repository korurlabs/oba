import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type { PathMappingQueryRequest, PathMappingResponse, ToolMappingResponse } from "../schemas";

/** Where every kind lives, in the server's order, with its file count in the named versions. */
export async function listPathMappings(params: PathMappingQueryRequest) {
  const res = await api.get<ApiResponse<PathMappingResponse[]>>(API_ROUTES.pathMappings.list, {
    params,
    // The server reads a repeated `versionId=` and not the `versionId[]=` form.
    paramsSerializer: { indexes: null },
  });
  return res.data;
}

/** What every AI tool reads, one entry per tool in the server's order, with each read's state in the named versions. */
export async function listToolMappings(params: PathMappingQueryRequest) {
  const res = await api.get<ApiResponse<ToolMappingResponse[]>>(API_ROUTES.pathMappings.tools, {
    params,
    paramsSerializer: { indexes: null },
  });
  return res.data;
}

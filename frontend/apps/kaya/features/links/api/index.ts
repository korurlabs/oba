import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type {
  DependencyQueryRequest,
  DependencyResponse,
  LinkQueryRequest,
  LinkResponse,
} from "../schemas";

/**
 * The links between the Markdown files of one version, of a repository's
 * newest release, or of that release with an open draft in place of its
 * owner's current version; a target the scope does not hold is BROKEN.
 */
export async function listLinks(params: LinkQueryRequest) {
  const res = await api.get<ApiResponse<LinkResponse[]>>(API_ROUTES.links.list, { params });
  return res.data;
}

/**
 * What one path of a scope links to and what uses it: Markdown links, a
 * rule's conventions, and a skill's parts and the skills sharing a part.
 */
export async function getDependencies(params: DependencyQueryRequest) {
  const res = await api.get<ApiResponse<DependencyResponse>>(API_ROUTES.links.dependencies, {
    params,
    // The server reads a repeated `versionId=` and not the `versionId[]=` form.
    paramsSerializer: { indexes: null },
  });
  return res.data;
}

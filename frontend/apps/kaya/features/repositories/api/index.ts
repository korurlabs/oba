import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type { RepositoryPackResponse, RepositoryPackUpdateRequest } from "@/features/scope/schemas";

import type { RepositoryListResponse, RepositoryUpdateRequest } from "../schemas";

/** Every repository, archived ones included. */
export async function listAllRepositories() {
  const res = await api.get<ApiResponse<RepositoryListResponse[]>>(API_ROUTES.repositories.list);
  return res.data;
}

/** Renames a repository, sets its ticket URL template or archives it; admins only. */
export async function updateRepository(id: string, data: RepositoryUpdateRequest) {
  const res = await api.patch<ApiResponse<RepositoryListResponse>>(
    API_ROUTES.repositories.detail(id),
    data
  );
  return res.data;
}

/**
 * Replaces the packs a repository uses, each at its root or mounted in a
 * folder, in display order; owners only. The server publishes a new release
 * of the repository and answers its new list.
 */
export async function updateRepositoryPacks(
  repositoryId: string,
  data: RepositoryPackUpdateRequest
) {
  const res = await api.put<ApiResponse<RepositoryPackResponse>>(
    API_ROUTES.repositoryPacks.list(repositoryId),
    data
  );
  return res.data;
}

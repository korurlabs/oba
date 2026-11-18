import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type { RepositoryPackResponse, RepositoryPackUpdateRequest } from "@/features/scope/schemas";

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

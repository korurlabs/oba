import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type { RepositorySettingsResponse, RepositoryUpdateRequest } from "../schemas";

/** Every repository, archived ones included. */
export async function listRepositorySettings() {
  const res = await api.get<ApiResponse<RepositorySettingsResponse[]>>(
    API_ROUTES.repositories.list
  );
  return res.data;
}

export async function updateRepository(id: string, data: RepositoryUpdateRequest) {
  const res = await api.patch<ApiResponse<RepositorySettingsResponse>>(
    API_ROUTES.repositories.detail(id),
    data
  );
  return res.data;
}

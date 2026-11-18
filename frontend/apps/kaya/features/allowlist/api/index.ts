import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type { SettingsQueryRequest, SettingsResponse } from "../schemas";

/** The parsed `.claude/settings.json` of each named version that has one. */
export async function listSettings(params: SettingsQueryRequest) {
  const res = await api.get<ApiResponse<SettingsResponse[]>>(API_ROUTES.settings.list, {
    params,
    // The server reads a repeated `versionId=` and not the `versionId[]=` form.
    paramsSerializer: { indexes: null },
  });
  return res.data;
}

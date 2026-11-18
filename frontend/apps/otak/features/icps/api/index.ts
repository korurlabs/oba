import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type { IcpDetail, IcpListApiResponse, IcpQueryRequest, IcpRequest } from "../schemas";

/** One page of the organization's ICPs, ordered by name. */
export async function listIcps(params: IcpQueryRequest) {
  const res = await api.get<IcpListApiResponse>(API_ROUTES.icps.list, { params });
  return res.data;
}

export async function getIcp(icpId: string) {
  const res = await api.get<ApiResponse<IcpDetail>>(API_ROUTES.icps.detail(icpId));
  return res.data;
}

/** A second ICP with the same name, case ignored, is refused with `OTK_STA_005`. */
export async function createIcp(data: IcpRequest) {
  const res = await api.post<ApiResponse<IcpDetail>>(API_ROUTES.icps.list, data);
  return res.data;
}

/** A full replace: a null optional field is cleared. */
export async function updateIcp(icpId: string, data: IcpRequest) {
  const res = await api.put<ApiResponse<IcpDetail>>(API_ROUTES.icps.detail(icpId), data);
  return res.data;
}

/** Removes the ICP; its relations stay and lose the link. ADMIN or OWNER only. */
export async function deleteIcp(icpId: string): Promise<void> {
  await api.delete(API_ROUTES.icps.detail(icpId));
}

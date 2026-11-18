import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type {
  MachineCreateRequest,
  MachineCreatedResponse,
  MachineQueryRequest,
  MachineResponse,
  MachineTokenCreatedResponse,
} from "../schemas";

/** Every machine of the organization, by user and machine name; not paged. */
export async function listMachines(params: MachineQueryRequest) {
  const res = await api.get<ApiResponse<MachineResponse[]>>(API_ROUTES.machines.list, { params });
  return res.data;
}

export async function getMachine(id: string) {
  const res = await api.get<ApiResponse<MachineResponse>>(API_ROUTES.machines.detail(id));
  return res.data;
}

/** Registers a machine; the answer holds the recorder token, which Kaya never shows again. */
export async function createMachine(data: MachineCreateRequest) {
  const res = await api.post<ApiResponse<MachineCreatedResponse>>(API_ROUTES.machines.list, data);
  return res.data;
}

/** Replaces the machine's token; only the machine's own user may, since the answer holds the new token. */
export async function rotateMachineToken(id: string) {
  const res = await api.post<ApiResponse<MachineTokenCreatedResponse>>(
    API_ROUTES.machines.token(id)
  );
  return res.data;
}

export async function revokeMachineToken(id: string) {
  const res = await api.delete<ApiResponse<null>>(API_ROUTES.machines.token(id));
  return res.data;
}

/** Retires the machine, which also revokes its token. */
export async function retireMachine(id: string) {
  const res = await api.patch<ApiResponse<MachineResponse>>(API_ROUTES.machines.detail(id), {
    status: "RETIRED",
  });
  return res.data;
}

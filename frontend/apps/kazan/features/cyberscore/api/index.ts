import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  Check,
  CheckListParams,
  CheckOutcomeRequest,
  CheckRequest,
  CheckStatusRequest,
  Cyberscore,
  CyberscoreTrendPoint,
} from "../schemas";

export async function getCyberscore() {
  const res = await api.get<ApiResponse<Cyberscore>>(API_ROUTES.cyberscore.current);
  return res.data;
}

export async function getCyberscoreTrend(days: number) {
  const res = await api.get<ApiResponse<CyberscoreTrendPoint[]>>(API_ROUTES.cyberscore.trend, {
    params: { days },
  });
  return res.data;
}

export async function listChecks(params?: CheckListParams) {
  const res = await api.get<ApiResponse<Check[]>>(API_ROUTES.cyberscore.checks, {
    params: params?.status ? { status: params.status } : undefined,
  });
  return res.data;
}

export async function createCheck(data: CheckRequest) {
  const res = await api.post<ApiResponse<Check>>(API_ROUTES.cyberscore.checks, data);
  return res.data;
}

export async function updateCheck(checkId: string, data: CheckRequest) {
  const res = await api.put<ApiResponse<Check>>(API_ROUTES.cyberscore.checkDetail(checkId), data);
  return res.data;
}

export async function setCheckOutcome(checkId: string, data: CheckOutcomeRequest) {
  const res = await api.put<ApiResponse<Check>>(API_ROUTES.cyberscore.checkOutcome(checkId), data);
  return res.data;
}

export async function setCheckStatus(checkId: string, data: CheckStatusRequest) {
  const res = await api.put<ApiResponse<Check>>(API_ROUTES.cyberscore.checkStatus(checkId), data);
  return res.data;
}

export async function deleteCheck(checkId: string): Promise<void> {
  await api.delete(API_ROUTES.cyberscore.checkDetail(checkId));
}

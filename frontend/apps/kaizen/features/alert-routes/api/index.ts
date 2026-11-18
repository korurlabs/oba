import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  AlertRoute,
  AlertRouteCreateRequest,
  AlertRouteUpdateRequest,
} from "../schemas";

export async function listAlertRoutes() {
  const res = await api.get<ApiResponse<AlertRoute[]>>(API_ROUTES.alertRoutes.list);
  return res.data;
}

export async function createAlertRoute(data: AlertRouteCreateRequest) {
  const res = await api.post<ApiResponse<AlertRoute>>(API_ROUTES.alertRoutes.list, data);
  return res.data;
}

export async function updateAlertRoute(routeId: string, data: AlertRouteUpdateRequest) {
  const res = await api.put<ApiResponse<AlertRoute>>(
    API_ROUTES.alertRoutes.detail(routeId),
    data,
  );
  return res.data;
}

export async function deleteAlertRoute(routeId: string): Promise<void> {
  await api.delete(API_ROUTES.alertRoutes.detail(routeId));
}

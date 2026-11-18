import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type { OrganizationUser } from "../schemas";

export async function listOrganizationUsers() {
  const res = await api.get<ApiResponse<OrganizationUser[]>>(
    API_ROUTES.users.list,
  );
  return res.data;
}

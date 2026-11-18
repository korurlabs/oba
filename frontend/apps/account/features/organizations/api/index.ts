import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  OrganizationCreateRequest,
  OrganizationDetail,
} from "@oba/shared/features/organizations/schemas";

export async function createOrganization(data: OrganizationCreateRequest) {
  const res = await api.post<ApiResponse<OrganizationDetail>>(
    API_ROUTES.organizations.list,
    data,
  );
  return res.data;
}

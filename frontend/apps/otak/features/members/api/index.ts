import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type { OrganizationMember } from "@oba/shared/features/organizations/schemas";

/** Every member of the organization, active and disabled. */
export async function listOrganizationMembers(organizationId: string) {
  const res = await api.get<ApiResponse<OrganizationMember[]>>(
    API_ROUTES.organizationMembers.list(organizationId)
  );
  return res.data;
}

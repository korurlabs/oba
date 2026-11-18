import { z } from "zod";

import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import { memberResponseSchema, type MemberResponse } from "../schemas";

/** Every member of the organization, disabled ones included, as the platform lists them. */
export async function listMembers(organizationId: string): Promise<ApiResponse<MemberResponse[]>> {
  const res = await api.get<ApiResponse<unknown>>(API_ROUTES.organizations.members(organizationId));
  return { ...res.data, data: z.array(memberResponseSchema).parse(res.data.data ?? []) };
}

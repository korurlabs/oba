import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import { userMeResponseSchema, type UserMeResponse } from "./schemas";

/**
 * The first call after signing in: the platform creates its user on the first
 * visit and lists the user's organizations. It is the one call made without
 * `X-Organization-ID`, since it is how the client learns which to send.
 */
export async function syncMe(): Promise<UserMeResponse> {
  const res = await api.post<ApiResponse<unknown>>(API_ROUTES.users.me, undefined, {
    withoutOrganization: true,
  });
  const me = res.data.data;
  if (me === undefined || me === null) {
    throw new Error("The platform answered POST /users/me without a user");
  }
  return userMeResponseSchema.parse(me);
}

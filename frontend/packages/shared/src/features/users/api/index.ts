import type { ApiResponse } from "@oba/shared/schema";
import type { UserMe, UserUpdate } from "../schema";
import { getRuntimeConfig } from "../../../lib/runtime-config";

export type UserMeResponse = ApiResponse<UserMe>;

/**
 * Edge-compatible fetch for the JWT callback context.
 * Cannot use axios here because the JWT callback runs in the Edge Runtime.
 */
async function edgeFetch(
  path: string,
  options: RequestInit = {},
): Promise<UserMeResponse> {
  const fullUrl = `${getRuntimeConfig().apiBaseUrl ?? ""}${path}`;

  try {
    const response = await fetch(fullUrl, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        ...options.headers,
      },
    });

    return (await response.json()) as UserMeResponse;
  } catch {
    return { success: false, message: "Network error", error: { code: "FETCH_ERROR", message: "Network error", errorValues: {} } };
  }
}

export const usersApi = {
  getMe: async (token?: string): Promise<UserMeResponse> => {
    return edgeFetch("/users/me", {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
  },
  syncMe: async (token?: string): Promise<UserMeResponse> => {
    return edgeFetch("/users/me", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
  },
  updateMe: async (token?: string, data?: UserUpdate): Promise<UserMeResponse> => {
    return edgeFetch("/users/me", {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(data),
    });
  },
  /**
   * Clears the default organization, which selects the personal workspace.
   * `PUT /users/me` keeps the current default for an absent, null or empty
   * `defaultOrganizationId`, so that callers saving only their profile or
   * preferences never clear it by accident; clearing has its own endpoint.
   * Answers the updated user, with `defaultOrganizationId: null`.
   */
  clearDefaultOrganization: async (token?: string): Promise<UserMeResponse> => {
    return edgeFetch("/users/me/default-organization", {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
  },
} as const;

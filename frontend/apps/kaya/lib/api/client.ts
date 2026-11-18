import axios, { type InternalAxiosRequestConfig } from "axios";

import { getRuntimeConfig } from "@oba/shared/lib/runtime-config";

import { sessionAccessToken } from "@/lib/auth/session-token";
import { currentOrganizationId } from "@/lib/organization";

import { toKayaApiError } from "./errors";

declare module "axios" {
  interface AxiosRequestConfig {
    /** True for the calls made before an organization is chosen, such as `POST /users/me`. */
    withoutOrganization?: boolean;
  }
}

/**
 * The client for Kaya's API. It sends the signed-in user's access token and
 * the chosen organization as `X-Organization-ID`, and rejects with a
 * KayaApiError, so callers branch on `status` and `code`. A call whose session
 * read fails is not sent at all: sent without a token it would earn a 401 for a
 * session that may be fine, and the identity gate would start a sign-in.
 * The API origin belongs to the deployment, not to the build, so it is taken
 * per request from the run-time config.
 */
export const api = axios.create({
  headers: { Accept: "application/json" },
});

api.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  if (config.baseURL === undefined) {
    config.baseURL = getRuntimeConfig().apiBaseUrl;
  }
  // Rejects with SessionUnavailableError when the session cannot be read; the
  // response interceptor turns it into the KayaApiError the caller shows.
  const token = await sessionAccessToken();
  if (token !== null) {
    config.headers.set("Authorization", `Bearer ${token}`);
  }
  if (!config.withoutOrganization) {
    const organizationId = currentOrganizationId();
    if (organizationId !== null) {
      config.headers.set("X-Organization-ID", organizationId);
    }
  }
  return config;
});

api.interceptors.response.use(undefined, (error: unknown) => Promise.reject(toKayaApiError(error)));

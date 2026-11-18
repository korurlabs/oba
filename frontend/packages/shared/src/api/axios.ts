import axios, { AxiosError, CreateAxiosDefaults } from "axios";
import { auth } from "../libs/auth";
import { getSession } from "next-auth/react";
import { recoverSessionAfterUnauthorized } from "../libs/auth/session-recovery";
import { devError, devLog } from "../helpers/dev-log";
import type { ApiResponse } from "../schema";
import { resolveOrganizationHeader } from "./organization-header";
import { getRuntimeConfig } from "../lib/runtime-config";

/**
 * A request the API refused or could not complete. `message` is the text the
 * client rejects with for each kind of failure, so a caller that shows or logs
 * it needs nothing else. `status` and `code` let a feature react to one
 * specific refusal, such as a 409 whose `errorValues` name the record that
 * already exists, or a 429 after which the user's input must be kept.
 * `status` is undefined when no response arrived; `code` is undefined when the
 * response carried no error envelope (a proxy or servlet container page).
 */
export class ApiError extends Error {
  readonly status: number | undefined;
  readonly code: string | undefined;
  readonly errorValues: Record<string, unknown>;

  constructor(
    message: string,
    details: {
      status?: number;
      code?: string;
      errorValues?: Record<string, unknown>;
      cause?: unknown;
    } = {},
  ) {
    super(message, { cause: details.cause });
    this.name = "ApiError";
    this.status = details.status;
    this.code = details.code;
    this.errorValues = details.errorValues ?? {};
  }
}

/** True for an `ApiError`, and, when `code` is given, only for that error code. */
export function isApiError(error: unknown, code?: string): error is ApiError {
  return error instanceof ApiError && (code === undefined || error.code === code);
}

/** The status and the envelope's error code and values of a refused request. */
function toApiError(message: string, error: AxiosError): ApiError {
  const body: unknown = error.response?.data;
  const envelopeError =
    body && typeof body === "object" ? (body as Partial<ApiResponse>).error : undefined;
  const code = typeof envelopeError?.code === "string" ? envelopeError.code : undefined;
  const errorValues =
    envelopeError?.errorValues && typeof envelopeError.errorValues === "object"
      ? envelopeError.errorValues
      : {};

  return new ApiError(message, {
    status: error.response?.status,
    code,
    errorValues,
    cause: error,
  });
}

/** Status codes that should never be retried */
const NON_RETRYABLE_STATUSES = new Set([401, 403, 404, 409, 422, 429]);

/** Max retries for transient server errors (500, 502, 503, 504) */
const MAX_RETRIES = 2;

/** Delay between retries in ms */
const RETRY_DELAY_MS = 1000;

function isNetworkError(error: AxiosError): boolean {
  return !error.response && Boolean(error.code) && error.code !== "ECONNABORTED";
}

function isRetryableServerError(error: AxiosError): boolean {
  const status = error.response?.status;
  return !!status && status >= 500 && !NON_RETRYABLE_STATUSES.has(status);
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function createApiClient(config?: CreateAxiosDefaults) {
  const client = axios.create({ timeout: 30000, ...config });

  client.interceptors.request.use(async (req) => {
    // The API origin is part of the deployment, not of the build, so a client
    // without an explicit baseURL takes it per request from the run-time config.
    if (req.baseURL === undefined) {
      req.baseURL = getRuntimeConfig().apiBaseUrl;
    }

    const session =
      typeof window === "undefined" ? await auth() : await getSession({ broadcast: false });

    if (session?.accessToken) {
      req.headers.Authorization = `Bearer ${session.accessToken}`;
    }

    const organizationId = resolveOrganizationHeader(
      req.url,
      req.headers.get("X-Organization-ID"),
      session?.user?.defaultOrganizationId,
    );

    if (organizationId !== undefined) {
      req.headers["X-Organization-ID"] = organizationId;
    } else {
      delete req.headers["X-Organization-ID"];
    }

    return req;
  });

  client.interceptors.response.use(
    (res) => res,
    async (error: AxiosError) => {
      if (!axios.isAxiosError(error)) {
        return Promise.reject(error);
      }

      const status = error.response?.status;
      const config = error.config;

      // Track retry count on the config object
      const retryCount =
        ((config as unknown as Record<string, unknown>)?.__retryCount as number) ?? 0;

      // Network errors (ECONNREFUSED, DNS failures, etc.) — fail immediately
      if (isNetworkError(error)) {
        devError(
          `[API Network Error] ${config?.method?.toUpperCase()} ${config?.url}:`,
          error.code ?? "connection failed",
        );

        return Promise.reject(
          toApiError(`Network error: ${error.code ?? "connection failed"}`, error),
        );
      }

      // 429 Rate Limited — fail immediately, do not retry
      if (status === 429) {
        devError(
          `[API Rate Limited] ${config?.method?.toUpperCase()} ${config?.url}`,
        );

        return Promise.reject(
          toApiError("Rate limit exceeded. Please try again later.", error),
        );
      }

      // 401 Unauthorized: in the browser, force one token refresh and send the
      // request once more with the new token. A refresh that fails leaves the
      // session marked REFRESH_TOKEN_ERROR, and the session guard asks the user
      // to sign in again. A 401 on the second attempt belongs to this endpoint,
      // not to the session, so it fails without a further refresh.
      if (status === 401) {
        if (
          typeof window !== "undefined" &&
          config &&
          !(config as unknown as Record<string, unknown>).__authRetried
        ) {
          const outcome = await recoverSessionAfterUnauthorized();
          if (outcome === "refreshed") {
            (config as unknown as Record<string, unknown>).__authRetried = true;
            return client.request(config);
          }
        }

        devError(
          `[API Unauthorized] ${config?.method?.toUpperCase()} ${config?.url}`,
        );

        return Promise.reject(toApiError("Unauthorized. Please sign in again.", error));
      }

      // Retry transient server errors (500, 502, 503, 504) up to MAX_RETRIES
      if (isRetryableServerError(error) && config && retryCount < MAX_RETRIES) {
        (config as unknown as Record<string, unknown>).__retryCount = retryCount + 1;

        devLog(
          `[API Retry ${retryCount + 1}/${MAX_RETRIES}] ${config.method?.toUpperCase()} ${config.url}`,
        );

        await delay(RETRY_DELAY_MS * (retryCount + 1));
        return client.request(config);
      }

      // All other errors — format and reject
      const responseData = error.response?.data as
        | Record<string, string>
        | undefined;
      const message =
        responseData?.message ||
        responseData?.error ||
        error.message ||
        "An unexpected error occurred";

      devError(
        `[API Error] ${config?.method?.toUpperCase()} ${config?.url}:`,
        message,
      );

      return Promise.reject(toApiError(message, error));
    },
  );

  return client;
}

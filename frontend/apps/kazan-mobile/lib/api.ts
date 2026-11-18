import axios, { isAxiosError, type InternalAxiosRequestConfig } from "axios";
import * as SecureStore from "expo-secure-store";
import { Platform } from "react-native";
import { z } from "zod";
import { resolveOrganizationHeader } from "@oba/shared/api/organization-header";
import { createApiResponseSchema, type Pagination } from "@oba/shared/schema";

import { ENV } from "@/lib/env";
import { getDiscovery, getKeycloakClientId } from "@/lib/oauthConfig";
import { getActiveOrganizationId } from "@/lib/organizationStore";

// Per-request flags the interceptors read.
//   - `_silentErrors`: the failure is not announced to the error listeners (no
//     global toast) because the caller shows it itself. The error is still
//     rejected, and the caller must log or show it.
//   - `_retry`: set on the replay after a 401 refresh, so a second 401 is not
//     refreshed again.
declare module "axios" {
  interface AxiosRequestConfig {
    _silentErrors?: boolean;
    _retry?: boolean;
  }
}

/** Header that names the organization of a request, as the backend reads it. */
export const ORGANIZATION_HEADER = "X-Organization-ID";

/** Backend code of a request body that failed bean validation (HTTP 422). */
export const VALIDATION_ERROR_CODE = "GEN_VAL_001";

/** Code of an ApiError raised when the token refresh could not reach Keycloak. */
export const AUTH_REFRESH_TRANSIENT = "AUTH_REFRESH_TRANSIENT";

/** Code of an ApiError raised when the session ended and the user must sign in again. */
export const AUTH_SESSION_ENDED = "AUTH_SESSION_ENDED";

/**
 * SecureStore keys of the session. Only SecureStore (Keychain, Keystore) holds
 * them; the access token is the Bearer of every API call, the refresh token is
 * exchanged for a new one and revoked at sign-out, and the id token is kept for
 * its identity claims only. The push device id is written by the push
 * registration and read at sign-out to unregister this phone.
 */
export const SECURE_STORE_KEYS = {
  accessToken: "auth_token",
  refreshToken: "refresh_token",
  idToken: "id_token",
  pushDeviceId: "kazan_push_device_id",
} as const;

// ── Errors ───────────────────────────────────────────────────────

/** What kind of failure an ApiError is, for the UI to choose its copy. */
export type ApiErrorKind =
  | "validation"
  | "unauthorized"
  | "forbidden"
  | "not-found"
  | "conflict"
  | "rate-limited"
  | "client"
  | "server"
  | "network"
  | "unknown";

/**
 * A request the API refused or that did not complete. `status` is undefined
 * when no response arrived; `code` is the backend's `error.code`, or one of the
 * AUTH_* codes above. A 422 with `GEN_VAL_001` has kind `validation`: a form
 * shows `fieldErrors` under its fields instead of a toast.
 */
export class ApiError extends Error {
  readonly status: number | undefined;
  readonly code: string | undefined;
  readonly errorValues: Record<string, unknown>;
  readonly kind: ApiErrorKind;

  constructor(
    message: string,
    details: {
      status?: number;
      code?: string;
      errorValues?: Record<string, unknown>;
      kind: ApiErrorKind;
      cause?: unknown;
    }
  ) {
    super(message, { cause: details.cause });
    this.name = "ApiError";
    this.status = details.status;
    this.code = details.code;
    this.errorValues = details.errorValues ?? {};
    this.kind = details.kind;
  }

  /** The validation message of each field (`errorValues` entries that are strings). */
  get fieldErrors(): Record<string, string> {
    const fields: Record<string, string> = {};
    for (const [field, value] of Object.entries(this.errorValues)) {
      if (typeof value === "string") fields[field] = value;
    }
    return fields;
  }
}

/** True for an ApiError, and, when `code` is given, only for that code. */
export function isApiError(error: unknown, code?: string): error is ApiError {
  return error instanceof ApiError && (code === undefined || error.code === code);
}

/** True for a 422 `GEN_VAL_001`, the field validation failure of a form. */
export function isValidationError(error: unknown): error is ApiError {
  return error instanceof ApiError && error.kind === "validation";
}

function kindOf(status: number | undefined, code: string | undefined): ApiErrorKind {
  if (status === undefined) return "network";
  if (status === 422 && code === VALIDATION_ERROR_CODE) return "validation";
  if (status === 401) return "unauthorized";
  if (status === 403) return "forbidden";
  if (status === 404) return "not-found";
  if (status === 409) return "conflict";
  if (status === 429) return "rate-limited";
  if (status >= 500) return "server";
  return "client";
}

/**
 * The ApiError for any failure of a request: the CustomResponse error envelope
 * when the backend sent one, the HTTP status otherwise, and kind `network` when
 * no response arrived.
 */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  if (!isAxiosError(error)) {
    return new ApiError(error instanceof Error ? error.message : "Request failed", {
      kind: "unknown",
      cause: error,
    });
  }
  const status: number | undefined = error.response?.status;
  const body: unknown = error.response?.data;
  const envelope: { message?: unknown; error?: unknown } | undefined =
    body !== null && typeof body === "object"
      ? (body as { message?: unknown; error?: unknown })
      : undefined;
  const envelopeError: { code?: unknown; message?: unknown; errorValues?: unknown } | undefined =
    envelope?.error !== null && typeof envelope?.error === "object"
      ? (envelope.error as { code?: unknown; message?: unknown; errorValues?: unknown })
      : undefined;
  const code: string | undefined =
    typeof envelopeError?.code === "string" ? envelopeError.code : undefined;
  const errorValues: Record<string, unknown> =
    envelopeError?.errorValues !== null && typeof envelopeError?.errorValues === "object"
      ? (envelopeError.errorValues as Record<string, unknown>)
      : {};
  const message: string =
    (typeof envelopeError?.message === "string" && envelopeError.message) ||
    (typeof envelope?.message === "string" && envelope.message) ||
    error.message ||
    "Request failed";
  return new ApiError(message, {
    status,
    code,
    errorValues,
    kind: kindOf(status, code),
    cause: error,
  });
}

// ── Response envelope ────────────────────────────────────────────

const envelopeSchema = createApiResponseSchema(z.unknown());

/**
 * The `data` of a CustomResponse body, checked against `dataSchema`. Axios
 * wraps the body in `response.data`, so a call site passes `response.data`.
 * A body that does not match throws the ZodError, which a query surfaces as its
 * error state.
 */
export function readData<T extends z.ZodType>(dataSchema: T, body: unknown): z.infer<T> {
  return dataSchema.parse(envelopeSchema.parse(body).data);
}

/** The `data` and `pagination` of a paged CustomResponse body. */
export function readPage<T extends z.ZodType>(
  itemSchema: T,
  body: unknown
): { items: z.infer<T>[]; pagination: Pagination | undefined } {
  const envelope = envelopeSchema.parse(body);
  return {
    items: z.array(itemSchema).parse(envelope.data),
    pagination: envelope.pagination ?? undefined,
  };
}

// ── Events ───────────────────────────────────────────────────────

/** A failed request, announced to the app's toast layer. */
export type ApiErrorEvent = {
  error: ApiError;
  method: string | undefined;
  url: string | undefined;
};

type ApiErrorListener = (event: ApiErrorEvent) => void;
const apiErrorListeners = new Set<ApiErrorListener>();

/**
 * Subscribes to failed requests (all but `_silentErrors` ones and a definitive
 * session end, which `onSessionEnded` reports). Returns the unsubscribe
 * function. The listener decides the copy: a validation error belongs to its
 * form, a load error to its screen's error state.
 */
export function addApiErrorListener(listener: ApiErrorListener): () => void {
  apiErrorListeners.add(listener);
  return () => {
    apiErrorListeners.delete(listener);
  };
}

function emitApiError(event: ApiErrorEvent): void {
  for (const listener of apiErrorListeners) {
    try {
      listener(event);
    } catch (error: unknown) {
      console.warn("[api] error listener threw", error instanceof Error ? error.message : error);
    }
  }
}

type OrganizationForbiddenListener = (organizationId: string) => void;
const organizationForbiddenListeners = new Set<OrganizationForbiddenListener>();

/**
 * Subscribes to 403 answers of requests sent with an `X-Organization-ID`,
 * also those marked `_silentErrors`. The listener gets that organization id
 * and decides whether the user lost the organization or only lacks a
 * permission inside it. Returns the unsubscribe function.
 */
export function onOrganizationForbidden(listener: OrganizationForbiddenListener): () => void {
  organizationForbiddenListeners.add(listener);
  return () => {
    organizationForbiddenListeners.delete(listener);
  };
}

function emitOrganizationForbidden(organizationId: string): void {
  for (const listener of organizationForbiddenListeners) {
    try {
      listener(organizationId);
    } catch (error: unknown) {
      console.warn(
        "[api] organization forbidden listener threw",
        error instanceof Error ? error.message : error
      );
    }
  }
}

type SessionEndedListener = () => void;
const sessionEndedListeners = new Set<SessionEndedListener>();

/**
 * Subscribes to the end of the session: Keycloak rejected the refresh token
 * (revoked, expired, or the session was ended elsewhere), and the stored
 * tokens are already cleared. AuthProvider listens and returns to Sign in.
 */
export function onSessionEnded(listener: SessionEndedListener): () => void {
  sessionEndedListeners.add(listener);
  return () => {
    sessionEndedListeners.delete(listener);
  };
}

function emitSessionEnded(): void {
  for (const listener of sessionEndedListeners) {
    try {
      listener();
    } catch (error: unknown) {
      console.warn(
        "[api] session ended listener threw",
        error instanceof Error ? error.message : error
      );
    }
  }
}

// ── Token storage ────────────────────────────────────────────────

/**
 * The web has no store a script cannot read, so a web build fails loudly here
 * instead of writing the long-lived refresh token to localStorage.
 */
function assertNativeSecureStorage(operation: string): void {
  if (Platform.OS === "web") {
    throw new Error(`[api] ${operation} is not supported on web: tokens live only in SecureStore`);
  }
}

async function readSecure(key: string): Promise<string | null> {
  assertNativeSecureStorage(`read ${key}`);
  return SecureStore.getItemAsync(key);
}

async function writeSecure(key: string, value: string): Promise<void> {
  assertNativeSecureStorage(`write ${key}`);
  await SecureStore.setItemAsync(key, value);
}

async function deleteSecure(key: string): Promise<void> {
  assertNativeSecureStorage(`delete ${key}`);
  await SecureStore.deleteItemAsync(key);
}

export function getAccessToken(): Promise<string | null> {
  return readSecure(SECURE_STORE_KEYS.accessToken);
}

export function getRefreshToken(): Promise<string | null> {
  return readSecure(SECURE_STORE_KEYS.refreshToken);
}

export function getIdToken(): Promise<string | null> {
  return readSecure(SECURE_STORE_KEYS.idToken);
}

/** The tokens of a sign-in or a refresh. A missing id token deletes the stored one. */
export interface SessionTokens {
  accessToken: string;
  refreshToken: string;
  idToken: string | null;
}

/** Stores the tokens of a sign-in or a refresh. */
export async function storeTokens(tokens: SessionTokens): Promise<void> {
  await writeSecure(SECURE_STORE_KEYS.accessToken, tokens.accessToken);
  await writeSecure(SECURE_STORE_KEYS.refreshToken, tokens.refreshToken);
  if (tokens.idToken !== null) {
    await writeSecure(SECURE_STORE_KEYS.idToken, tokens.idToken);
  } else {
    await deleteSecure(SECURE_STORE_KEYS.idToken);
  }
}

/** Deletes the three tokens. Each delete runs even when an earlier one fails; the first failure is re-thrown. */
export async function clearTokens(): Promise<void> {
  const keys: string[] = [
    SECURE_STORE_KEYS.accessToken,
    SECURE_STORE_KEYS.refreshToken,
    SECURE_STORE_KEYS.idToken,
  ];
  const results: PromiseSettledResult<void>[] = await Promise.allSettled(keys.map(deleteSecure));
  const failure: PromiseRejectedResult | undefined = results.find(
    (result): result is PromiseRejectedResult => result.status === "rejected"
  );
  if (failure) throw failure.reason;
}

export function getPushDeviceId(): Promise<string | null> {
  return readSecure(SECURE_STORE_KEYS.pushDeviceId);
}

export function setPushDeviceId(deviceId: string): Promise<void> {
  return writeSecure(SECURE_STORE_KEYS.pushDeviceId, deviceId);
}

export function clearPushDeviceId(): Promise<void> {
  return deleteSecure(SECURE_STORE_KEYS.pushDeviceId);
}

// ── Axios instances ──────────────────────────────────────────────

/** The Oba API client. Paths come from lib/apiRoutes.ts. */
export const api = axios.create({
  baseURL: ENV.apiBaseUrl,
  timeout: 60_000,
  headers: { "Content-Type": "application/json" },
});

/**
 * Client for the Keycloak token and end-session endpoints. It has no
 * interceptors, so a refresh can never trigger another refresh.
 */
export const keycloakAxios = axios.create({ timeout: 60_000 });

// ── Token refresh ────────────────────────────────────────────────

/**
 * Keycloak answers that mean the refresh grant itself is dead and can never be
 * exchanged again. `invalid_client` and `unauthorized_client` are left out: a
 * misconfigured client is fixed on the server, and treating it as a dead grant
 * would sign out every user at once.
 */
const DEFINITIVE_GRANT_ERRORS: ReadonlySet<string> = new Set(["invalid_grant", "invalid_token"]);

/**
 * True only when Keycloak rejected the refresh grant explicitly (HTTP 400 or
 * 401 with `invalid_grant` or `invalid_token`). A timeout, no connection, a 5xx
 * or an unreadable body is transient: the tokens are kept, because a tunnel or a
 * lift must not sign the user out.
 */
export function isDefinitiveGrantDeath(error: unknown): boolean {
  if (!isAxiosError(error)) return false;
  const status: number | undefined = error.response?.status;
  if (status !== 400 && status !== 401) return false;
  const oauthError: unknown = (error.response?.data as { error?: unknown } | undefined)?.error;
  return typeof oauthError === "string" && DEFINITIVE_GRANT_ERRORS.has(oauthError);
}

/** The result of one refresh. Only `definitive: true` ends the session. */
export type RefreshOutcome =
  | { ok: true; accessToken: string }
  | { ok: false; definitive: boolean; reason: string };

/** Bound of the discovery wait and of the token POST of one refresh. */
const REFRESH_TIMEOUT_MS = 15_000;

/**
 * `work`, or a rejection once `timeoutMs` has passed. The discovery fetch has no
 * timeout of its own, and an unbounded wait would hold every 401 behind it.
 */
export async function settleWithin<T>(
  work: Promise<T>,
  timeoutMs: number,
  what: string
): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const deadline = new Promise<never>((_resolve, reject) => {
    timer = setTimeout(() => {
      reject(new Error(`${what} did not settle within ${timeoutMs} ms`));
    }, timeoutMs);
  });
  try {
    return await Promise.race([work, deadline]);
  } finally {
    clearTimeout(timer);
  }
}

async function exchangeRefreshToken(): Promise<RefreshOutcome> {
  let refreshToken: string | null;
  let accessToken: string | null;
  try {
    refreshToken = await getRefreshToken();
    accessToken = await getAccessToken();
  } catch (error: unknown) {
    const message: string = error instanceof Error ? error.message : String(error);
    console.warn("[api] could not read the stored tokens for a refresh", message);
    return { ok: false, definitive: false, reason: `secure store read failed: ${message}` };
  }

  if (refreshToken === null) {
    // Nothing can ever be exchanged. When an access token is left over, the
    // session ends now; with no token at all the user is already signed out.
    if (accessToken !== null) {
      return { ok: false, definitive: true, reason: "no refresh token stored" };
    }
    return { ok: false, definitive: false, reason: "not signed in" };
  }

  let tokenEndpoint: string;
  try {
    const discovery = await settleWithin(getDiscovery(), REFRESH_TIMEOUT_MS, "Keycloak discovery");
    if (!discovery.tokenEndpoint) {
      console.warn("[api] Keycloak discovery has no token endpoint");
      return { ok: false, definitive: false, reason: "discovery has no token endpoint" };
    }
    tokenEndpoint = discovery.tokenEndpoint;
  } catch (error: unknown) {
    const message: string = error instanceof Error ? error.message : String(error);
    console.warn("[api] Keycloak discovery unavailable, cannot refresh", message);
    return { ok: false, definitive: false, reason: `discovery unavailable: ${message}` };
  }

  try {
    const body = new URLSearchParams({
      grant_type: "refresh_token",
      client_id: getKeycloakClientId(),
      refresh_token: refreshToken,
    });
    const response = await keycloakAxios.post(tokenEndpoint, body.toString(), {
      timeout: REFRESH_TIMEOUT_MS,
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
    });
    const data = response.data as {
      access_token?: unknown;
      refresh_token?: unknown;
      id_token?: unknown;
    };
    if (typeof data?.access_token !== "string") {
      console.warn("[api] Keycloak refresh response has no access token");
      return { ok: false, definitive: false, reason: "refresh response has no access token" };
    }
    const storedIdToken: string | null = await getIdToken();
    await storeTokens({
      accessToken: data.access_token,
      // Keycloak may keep the refresh token; the stored one then stays valid.
      refreshToken: typeof data.refresh_token === "string" ? data.refresh_token : refreshToken,
      idToken: typeof data.id_token === "string" ? data.id_token : storedIdToken,
    });
    return { ok: true, accessToken: data.access_token };
  } catch (error: unknown) {
    const definitive: boolean = isDefinitiveGrantDeath(error);
    const oauthError: unknown = isAxiosError(error)
      ? (error.response?.data as { error?: unknown } | undefined)?.error
      : undefined;
    const message: string = error instanceof Error ? error.message : String(error);
    console.warn(
      `[api] Keycloak refresh failed (${definitive ? "grant rejected" : "transient"})`,
      typeof oauthError === "string" ? `oauth_error=${oauthError}` : message
    );
    return {
      ok: false,
      definitive,
      reason: typeof oauthError === "string" ? oauthError : message,
    };
  }
}

let refreshInFlight: Promise<RefreshOutcome> | null = null;

/**
 * Exchanges the refresh token for a new access token, at most one exchange at a
 * time: every caller during a running refresh awaits the same promise, so a
 * burst of 401s refreshes once. On a definitive failure the stored tokens are
 * cleared and `onSessionEnded` listeners run. Never rejects.
 *
 * Also used by the realtime client, which refreshes before reconnecting.
 */
export function refreshAccessToken(): Promise<RefreshOutcome> {
  if (refreshInFlight !== null) return refreshInFlight;
  const run = async (): Promise<RefreshOutcome> => {
    const outcome: RefreshOutcome = await exchangeRefreshToken();
    if (!outcome.ok && outcome.definitive) {
      try {
        await clearTokens();
      } catch (error: unknown) {
        // The session is over either way; the next start finds the remaining
        // token rejected again and ends the session once more.
        console.warn(
          "[api] clearing the tokens of an ended session failed",
          error instanceof Error ? error.message : error
        );
      }
      emitSessionEnded();
    }
    return outcome;
  };
  const promise: Promise<RefreshOutcome> = run().finally(() => {
    if (refreshInFlight === promise) refreshInFlight = null;
  });
  refreshInFlight = promise;
  return promise;
}

/** Test-only: forgets a running refresh so each test starts clean. */
export function resetRefreshStateForTests(): void {
  refreshInFlight = null;
}

// ── Interceptors ─────────────────────────────────────────────────

/**
 * Bearer from SecureStore, and `X-Organization-ID`: the value the caller set
 * itself, else the organization in the path, else the active organization.
 * A 401 replay reuses the header of the first attempt, so a switch during the
 * refresh cannot move the replay into another organization.
 */
api.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const token: string | null = await getAccessToken();
  if (token !== null) {
    config.headers.set("Authorization", `Bearer ${token}`);
  }
  const organizationId: string | undefined = resolveOrganizationHeader(
    config.url,
    config.headers.get(ORGANIZATION_HEADER),
    getActiveOrganizationId()
  );
  if (organizationId !== undefined) {
    config.headers.set(ORGANIZATION_HEADER, organizationId);
  } else {
    config.headers.delete(ORGANIZATION_HEADER);
  }
  return config;
});

function describeRequest(config: InternalAxiosRequestConfig | undefined): string {
  return `${config?.method?.toUpperCase() ?? "REQUEST"} ${config?.url ?? "(unknown url)"}`;
}

/**
 * Every failure is rejected as an ApiError. A 401 refreshes the token once
 * (single-flight) and replays the request. Every failure the caller did not
 * mark `_silentErrors` is announced to the error listeners, except a definitive
 * session end, which `onSessionEnded` reports, and a 401 that joined another
 * request's failed refresh, whose leader already reported it. A 403 of an
 * organization-scoped request is also announced to `onOrganizationForbidden`.
 */
api.interceptors.response.use(
  (response) => response,
  async (rawError: unknown) => {
    const config: InternalAxiosRequestConfig | undefined = isAxiosError(rawError)
      ? rawError.config
      : undefined;

    if (
      isAxiosError(rawError) &&
      rawError.response?.status === 401 &&
      config !== undefined &&
      config._retry !== true
    ) {
      config._retry = true;
      const joined: boolean = refreshInFlight !== null;
      const outcome: RefreshOutcome = await refreshAccessToken();
      if (outcome.ok) {
        config.headers.set("Authorization", `Bearer ${outcome.accessToken}`);
        return api(config);
      }
      if (outcome.definitive || outcome.reason === "not signed in") {
        return Promise.reject(
          new ApiError("Your session ended. Sign in again.", {
            status: 401,
            code: AUTH_SESSION_ENDED,
            kind: "unauthorized",
            cause: rawError,
          })
        );
      }
      const transient = new ApiError("The session could not be refreshed. Try again.", {
        code: AUTH_REFRESH_TRANSIENT,
        kind: "network",
        cause: rawError,
      });
      console.warn(
        `[api] ${describeRequest(config)} failed: token refresh failed (${outcome.reason})`
      );
      if (!joined && config._silentErrors !== true) {
        emitApiError({ error: transient, method: config.method, url: config.url });
      }
      return Promise.reject(transient);
    }

    const apiError: ApiError = toApiError(rawError);
    console.warn(
      `[api] ${describeRequest(config)} failed:`,
      apiError.status ?? "no response",
      apiError.code ?? apiError.message
    );
    if (config?._silentErrors !== true) {
      emitApiError({ error: apiError, method: config?.method, url: config?.url });
    }
    const organizationId: unknown = config?.headers.get(ORGANIZATION_HEADER);
    if (apiError.status === 403 && typeof organizationId === "string" && organizationId !== "") {
      emitOrganizationForbidden(organizationId);
    }
    return Promise.reject(apiError);
  }
);

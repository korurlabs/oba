import { afterEach, beforeEach, describe, expect, it, mock } from "bun:test";
import {
  AxiosError,
  AxiosHeaders,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from "axios";
import { z } from "zod";

// Bun cannot parse react-native's Flow source; lib/api.ts reads only Platform.OS.
mock.module("react-native", () => ({
  Platform: { OS: "ios" },
  AppState: { addEventListener: () => ({ remove: () => undefined }) },
}));

const secureStore = new Map<string, string>();
mock.module("expo-secure-store", () => ({
  getItemAsync: async (key: string) => secureStore.get(key) ?? null,
  setItemAsync: async (key: string, value: string) => {
    secureStore.set(key, value);
  },
  deleteItemAsync: async (key: string) => {
    secureStore.delete(key);
  },
}));

mock.module("@/lib/env", () => ({
  ENV: {
    apiBaseUrl: "https://api.test",
    keycloakIssuer: "https://auth.test/realms/sensey",
    keycloakClientId: "kazan-mobile",
    accountUrl: "https://account.test",
    adminUrl: "https://admin.test",
    kazanWebUrl: "https://kazan.test",
  },
}));

mock.module("expo-auth-session", () => ({
  fetchDiscoveryAsync: async (issuer: string) => ({
    authorizationEndpoint: `${issuer}/protocol/openid-connect/auth`,
    tokenEndpoint: `${issuer}/protocol/openid-connect/token`,
    endSessionEndpoint: `${issuer}/protocol/openid-connect/logout`,
  }),
  makeRedirectUri: ({ scheme, path }: { scheme: string; path: string }) => `${scheme}://${path}`,
}));

const {
  ApiError,
  AUTH_REFRESH_TRANSIENT,
  AUTH_SESSION_ENDED,
  ORGANIZATION_HEADER,
  SECURE_STORE_KEYS,
  addApiErrorListener,
  api,
  isDefinitiveGrantDeath,
  isValidationError,
  keycloakAxios,
  onOrganizationForbidden,
  onSessionEnded,
  readData,
  readPage,
  refreshAccessToken,
  resetRefreshStateForTests,
  toApiError,
} = await import("@/lib/api");
const { clearActiveOrganizationId, setActiveOrganizationId } =
  await import("@/lib/organizationStore");

type Adapter = (config: InternalAxiosRequestConfig) => Promise<AxiosResponse>;

function respond(config: InternalAxiosRequestConfig, status: number, data: unknown): AxiosResponse {
  return { config, status, statusText: String(status), headers: {}, data };
}

function fail(config: InternalAxiosRequestConfig, status: number, data: unknown): AxiosError {
  return new AxiosError(
    `Request failed with status code ${status}`,
    status >= 500 ? AxiosError.ERR_BAD_RESPONSE : AxiosError.ERR_BAD_REQUEST,
    config,
    {},
    respond(config, status, data)
  );
}

function axiosFailure(status: number, data: unknown): AxiosError {
  const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig;
  return fail(config, status, data);
}

describe("toApiError", () => {
  it("reads the envelope of a 422 GEN_VAL_001 as a validation error with field messages", () => {
    const error = toApiError(
      axiosFailure(422, {
        success: false,
        message: "Validation failed",
        data: null,
        error: {
          code: "GEN_VAL_001",
          message: "Title is required",
          errorValues: { title: "must not be blank", count: 3 },
        },
      })
    );
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(422);
    expect(error.code).toBe("GEN_VAL_001");
    expect(error.kind).toBe("validation");
    expect(error.message).toBe("Title is required");
    expect(error.fieldErrors).toEqual({ title: "must not be blank" });
    expect(isValidationError(error)).toBe(true);
  });

  it("treats a 422 with another code as a client error", () => {
    const error = toApiError(axiosFailure(422, { error: { code: "TKT_VAL_002" } }));
    expect(error.kind).toBe("client");
    expect(isValidationError(error)).toBe(false);
  });

  it("maps statuses to kinds", () => {
    expect(toApiError(axiosFailure(403, {})).kind).toBe("forbidden");
    expect(toApiError(axiosFailure(404, {})).kind).toBe("not-found");
    expect(toApiError(axiosFailure(409, {})).kind).toBe("conflict");
    expect(toApiError(axiosFailure(429, {})).kind).toBe("rate-limited");
    expect(toApiError(axiosFailure(503, "<html>")).kind).toBe("server");
  });

  it("reports a request without a response as a network error", () => {
    const error = toApiError(new AxiosError("Network Error", AxiosError.ERR_NETWORK));
    expect(error.kind).toBe("network");
    expect(error.status).toBeUndefined();
  });

  it("wraps a non-axios failure as unknown", () => {
    const error = toApiError(new Error("boom"));
    expect(error.kind).toBe("unknown");
    expect(error.message).toBe("boom");
  });
});

describe("isDefinitiveGrantDeath", () => {
  it("is true only for a 400 or 401 with invalid_grant or invalid_token", () => {
    expect(isDefinitiveGrantDeath(axiosFailure(400, { error: "invalid_grant" }))).toBe(true);
    expect(isDefinitiveGrantDeath(axiosFailure(401, { error: "invalid_token" }))).toBe(true);
    expect(isDefinitiveGrantDeath(axiosFailure(400, { error: "invalid_client" }))).toBe(false);
    expect(isDefinitiveGrantDeath(axiosFailure(503, { error: "invalid_grant" }))).toBe(false);
    expect(isDefinitiveGrantDeath(new AxiosError("timeout", AxiosError.ECONNABORTED))).toBe(false);
    expect(isDefinitiveGrantDeath(new Error("x"))).toBe(false);
  });
});

describe("readData and readPage", () => {
  it("returns the checked data of the envelope", () => {
    const body = { success: true, message: "ok", data: { id: "a" } };
    expect(readData(z.object({ id: z.string() }), body)).toEqual({ id: "a" });
  });

  it("throws when the data does not match", () => {
    expect(() =>
      readData(z.object({ id: z.string() }), { success: true, message: "ok", data: 1 })
    ).toThrow();
  });

  // The backend serializes every CustomResponse field, so these bodies carry
  // the nulls a real response carries.
  it("reads a success body with null pagination and error", () => {
    const body = {
      success: true,
      message: "User profile retrieved successfully",
      data: { id: "a" },
      pagination: null,
      error: null,
    };
    expect(readData(z.object({ id: z.string() }), body)).toEqual({ id: "a" });
  });

  it("returns items and pagination of a paged body", () => {
    const page = readPage(z.string(), {
      success: true,
      message: "Boards retrieved successfully",
      data: ["a", "b"],
      pagination: {
        totalElements: 5,
        totalPages: 3,
        currentPage: 1,
        pageSize: 2,
        first: true,
        last: false,
        empty: false,
      },
      error: null,
    });
    expect(page.items).toEqual(["a", "b"]);
    expect(page.pagination?.totalElements).toBe(5);
  });

  it("returns no pagination when a list body has null pagination", () => {
    const page = readPage(z.string(), {
      success: true,
      message: "ok",
      data: [],
      pagination: null,
      error: null,
    });
    expect(page.pagination).toBeUndefined();
  });
});

describe("request and refresh interceptors", () => {
  const tokenEndpoint = "https://auth.test/realms/sensey/protocol/openid-connect/token";
  let apiCalls: InternalAxiosRequestConfig[];
  let tokenCalls: InternalAxiosRequestConfig[];
  let apiAdapter: Adapter;
  let tokenAdapter: Adapter;
  let sessionEnded: number;
  let errorEvents: string[];
  let unsubscribe: (() => void)[];
  const originalWarn = console.warn;

  beforeEach(async () => {
    console.warn = () => undefined;
    secureStore.clear();
    resetRefreshStateForTests();
    await clearActiveOrganizationId();
    apiCalls = [];
    tokenCalls = [];
    sessionEnded = 0;
    errorEvents = [];
    api.defaults.adapter = (config: InternalAxiosRequestConfig) => {
      apiCalls.push(config);
      return apiAdapter(config);
    };
    keycloakAxios.defaults.adapter = (config: InternalAxiosRequestConfig) => {
      tokenCalls.push(config);
      return tokenAdapter(config);
    };
    unsubscribe = [
      onSessionEnded(() => {
        sessionEnded += 1;
      }),
      addApiErrorListener((event) => {
        errorEvents.push(event.error.code ?? event.error.kind);
      }),
    ];
  });

  afterEach(() => {
    console.warn = originalWarn;
    for (const off of unsubscribe) off();
  });

  function signIn(accessToken: string = "old-access"): void {
    secureStore.set(SECURE_STORE_KEYS.accessToken, accessToken);
    secureStore.set(SECURE_STORE_KEYS.refreshToken, "old-refresh");
  }

  /** 401 for the old token, 200 for the new one. */
  function apiRejectsOldToken(): void {
    apiAdapter = async (config) => {
      if (config.headers.get("Authorization") === "Bearer old-access") {
        throw fail(config, 401, {});
      }
      return respond(config, 200, { success: true, message: "ok", data: config.url });
    };
  }

  it("sets the bearer and the active organization header", async () => {
    signIn();
    await setActiveOrganizationId("org-active");
    apiAdapter = async (config) => respond(config, 200, {});
    await api.get("/kazan/boards");
    expect(apiCalls[0].headers.get("Authorization")).toBe("Bearer old-access");
    expect(apiCalls[0].headers.get(ORGANIZATION_HEADER)).toBe("org-active");
  });

  it("prefers the organization in the path over the active one", async () => {
    signIn();
    await setActiveOrganizationId("org-active");
    apiAdapter = async (config) => respond(config, 200, {});
    await api.get("/organizations/org-path/members");
    expect(apiCalls[0].headers.get(ORGANIZATION_HEADER)).toBe("org-path");
  });

  it("omits the organization header when none is active", async () => {
    signIn();
    apiAdapter = async (config) => respond(config, 200, {});
    await api.get("/users/me");
    expect(apiCalls[0].headers.has(ORGANIZATION_HEADER)).toBe(false);
  });

  it("refreshes once for concurrent 401s and replays each request", async () => {
    signIn();
    apiRejectsOldToken();
    tokenAdapter = async (config) => {
      await new Promise((resolve) => setTimeout(resolve, 10));
      return respond(config, 200, { access_token: "new-access", refresh_token: "new-refresh" });
    };

    const results = await Promise.all([api.get("/a"), api.get("/b"), api.get("/c")]);

    expect(results.map((response) => response.data.data)).toEqual(["/a", "/b", "/c"]);
    expect(tokenCalls.length).toBe(1);
    expect(tokenCalls[0].url).toBe(tokenEndpoint);
    expect(String(tokenCalls[0].data)).toContain("grant_type=refresh_token");
    expect(String(tokenCalls[0].data)).toContain("client_id=kazan-mobile");
    expect(secureStore.get(SECURE_STORE_KEYS.accessToken)).toBe("new-access");
    expect(secureStore.get(SECURE_STORE_KEYS.refreshToken)).toBe("new-refresh");
    expect(sessionEnded).toBe(0);
  });

  it("keeps the stored refresh token when Keycloak does not rotate it", async () => {
    signIn();
    tokenAdapter = async (config) => respond(config, 200, { access_token: "new-access" });
    const outcome = await refreshAccessToken();
    expect(outcome).toEqual({ ok: true, accessToken: "new-access" });
    expect(secureStore.get(SECURE_STORE_KEYS.refreshToken)).toBe("old-refresh");
  });

  it("clears the tokens and ends the session when the grant is rejected", async () => {
    signIn();
    apiRejectsOldToken();
    tokenAdapter = async (config) => {
      throw fail(config, 400, { error: "invalid_grant" });
    };

    const results = await Promise.allSettled([api.get("/a"), api.get("/b")]);

    for (const result of results) {
      expect(result.status).toBe("rejected");
      const reason: unknown = (result as PromiseRejectedResult).reason;
      expect(reason).toBeInstanceOf(ApiError);
      expect((reason as InstanceType<typeof ApiError>).code).toBe(AUTH_SESSION_ENDED);
    }
    expect(tokenCalls.length).toBe(1);
    expect(sessionEnded).toBe(1);
    expect(secureStore.has(SECURE_STORE_KEYS.accessToken)).toBe(false);
    expect(secureStore.has(SECURE_STORE_KEYS.refreshToken)).toBe(false);
    expect(errorEvents).toEqual([]);
  });

  it("keeps the tokens on a transient refresh failure and reports it once", async () => {
    signIn();
    apiRejectsOldToken();
    tokenAdapter = async (config) => {
      await new Promise((resolve) => setTimeout(resolve, 10));
      throw fail(config, 503, "unavailable");
    };

    const results = await Promise.allSettled([api.get("/a"), api.get("/b")]);

    for (const result of results) {
      const reason = (result as PromiseRejectedResult).reason as InstanceType<typeof ApiError>;
      expect(reason.code).toBe(AUTH_REFRESH_TRANSIENT);
      expect(reason.kind).toBe("network");
    }
    expect(sessionEnded).toBe(0);
    expect(secureStore.get(SECURE_STORE_KEYS.refreshToken)).toBe("old-refresh");
    expect(errorEvents).toEqual([AUTH_REFRESH_TRANSIENT]);
  });

  it("does not replay a request twice when the new token is rejected too", async () => {
    signIn();
    apiAdapter = async (config) => {
      throw fail(config, 401, {});
    };
    tokenAdapter = async (config) => respond(config, 200, { access_token: "new-access" });

    const error: unknown = await api.get("/a").catch((reason: unknown) => reason);

    expect((error as InstanceType<typeof ApiError>).kind).toBe("unauthorized");
    expect(apiCalls.length).toBe(2);
    expect(tokenCalls.length).toBe(1);
  });

  it("rejects a validation failure as an ApiError and announces it", async () => {
    signIn();
    apiAdapter = async (config) => {
      throw fail(config, 422, { error: { code: "GEN_VAL_001", errorValues: { title: "blank" } } });
    };
    const error: unknown = await api.post("/kazan/boards", {}).catch((reason: unknown) => reason);
    expect(isValidationError(error)).toBe(true);
    expect(errorEvents).toEqual(["GEN_VAL_001"]);
  });

  it("does not announce a failure marked _silentErrors, but still rejects it", async () => {
    signIn();
    apiAdapter = async (config) => {
      throw fail(config, 404, { error: { code: "PSH_NOT_001" } });
    };
    const error: unknown = await api
      .delete("/push/devices/d1", { _silentErrors: true })
      .catch((reason: unknown) => reason);
    expect((error as InstanceType<typeof ApiError>).code).toBe("PSH_NOT_001");
    expect(errorEvents).toEqual([]);
  });

  it("reports a 403 of an organization-scoped request with its organization", async () => {
    signIn();
    await setActiveOrganizationId("org-active");
    const forbidden: string[] = [];
    unsubscribe.push(onOrganizationForbidden((organizationId) => forbidden.push(organizationId)));
    apiAdapter = async (config) => {
      throw fail(config, 403, { error: { code: "ORG_SEC_001" } });
    };

    await expect(api.get("/kazan/boards", { _silentErrors: true })).rejects.toBeInstanceOf(
      ApiError
    );

    expect(forbidden).toEqual(["org-active"]);
  });

  it("does not report a 403 of a request without an organization", async () => {
    signIn();
    const forbidden: string[] = [];
    unsubscribe.push(onOrganizationForbidden((organizationId) => forbidden.push(organizationId)));
    apiAdapter = async (config) => {
      throw fail(config, 403, {});
    };

    await expect(api.get("/users/me")).rejects.toBeInstanceOf(ApiError);

    expect(forbidden).toEqual([]);
  });
});

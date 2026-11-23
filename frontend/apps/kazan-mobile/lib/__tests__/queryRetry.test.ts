import { describe, expect, it, mock } from "bun:test";

// The same stubs as lib/__tests__/api.test.ts: lib/api.ts loads the env,
// SecureStore and Keycloak discovery at import.
mock.module("react-native", () => ({
  Platform: { OS: "ios" },
  AppState: { addEventListener: () => ({ remove: () => undefined }) },
}));

mock.module("expo-secure-store", () => ({
  getItemAsync: async () => null,
  setItemAsync: async () => undefined,
  deleteItemAsync: async () => undefined,
}));

mock.module("@/lib/env", () => ({
  ENV: {
    apiBaseUrl: "https://api.test",
    keycloakIssuer: "https://auth.test/realms/sensey",
    keycloakClientId: "kazan-mobile",
    accountUrl: "https://account.test",
    ulakUrl: "https://ulak.test",
    kazanWebUrl: "https://kazan.test",
  },
}));

mock.module("expo-auth-session", () => ({
  fetchDiscoveryAsync: async (issuer: string) => ({
    tokenEndpoint: `${issuer}/protocol/openid-connect/token`,
  }),
  makeRedirectUri: ({ scheme, path }: { scheme: string; path: string }) => `${scheme}://${path}`,
}));

const { ApiError } = await import("@/lib/api");
const { shouldRetryQuery } = await import("@/lib/queryRetry");

type Kind = ConstructorParameters<typeof ApiError>[1]["kind"];

function apiError(status: number | undefined, kind: Kind): InstanceType<typeof ApiError> {
  return new ApiError("Request failed", { status, kind });
}

describe("shouldRetryQuery", () => {
  it("never retries a 401, 403 or 404", () => {
    expect(shouldRetryQuery(0, apiError(401, "unauthorized"))).toBe(false);
    expect(shouldRetryQuery(0, apiError(403, "forbidden"))).toBe(false);
    expect(shouldRetryQuery(0, apiError(404, "not-found"))).toBe(false);
  });

  it("retries other failures up to three times", () => {
    const serverError = apiError(503, "server");
    expect(shouldRetryQuery(0, serverError)).toBe(true);
    expect(shouldRetryQuery(2, serverError)).toBe(true);
    expect(shouldRetryQuery(3, serverError)).toBe(false);
    expect(shouldRetryQuery(0, apiError(undefined, "network"))).toBe(true);
    expect(shouldRetryQuery(0, apiError(409, "conflict"))).toBe(true);
  });

  it("retries a failure that is not an ApiError, such as a body that does not parse", () => {
    expect(shouldRetryQuery(0, new Error("Invalid input"))).toBe(true);
    expect(shouldRetryQuery(3, new Error("Invalid input"))).toBe(false);
  });
});

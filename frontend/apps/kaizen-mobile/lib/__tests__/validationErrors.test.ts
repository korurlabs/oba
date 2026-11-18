import { describe, expect, it, mock } from "bun:test";
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from "axios";

// The same stubs as lib/__tests__/api.test.ts: lib/api.ts loads the env,
// SecureStore and Keycloak discovery at import.
mock.module("react-native", () => ({
  Platform: { OS: "ios" },
  AppState: { currentState: "active", addEventListener: () => ({ remove: () => undefined }) },
}));

mock.module("@react-native-community/netinfo", () => ({
  default: { addEventListener: () => () => undefined },
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
    keycloakClientId: "kaizen-mobile",
    accountUrl: "https://account.test",
    adminUrl: "https://admin.test",
    kaizenWebUrl: "https://kaizen.test",
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

const { isApiError, isValidationError, toApiError } = await import("@/lib/api");
const { apiErrorToastMessage } = await import("@/components/auth/apiErrorToastModel");

function refused(status: number, code: string, message: string, errorValues?: unknown) {
  const config = { headers: new AxiosHeaders(), method: "post" } as InternalAxiosRequestConfig;
  return toApiError(
    new AxiosError(
      "failed",
      AxiosError.ERR_BAD_REQUEST,
      config,
      {},
      {
        config,
        status,
        statusText: String(status),
        headers: {},
        data: { success: false, message, data: null, error: { code, message, errorValues } },
      }
    )
  );
}

function toast(error: ReturnType<typeof toApiError>): string | null {
  return apiErrorToastMessage(
    { error, method: "post", url: "/x" } as never,
    (key: string) => `t:${key}`
  );
}

describe("a 422 GEN_VAL_001 reaches the form fields", () => {
  it("maps create board errors to the name and key fields", () => {
    const error = refused(422, "GEN_VAL_001", "Validation failed", {
      name: "must not be blank",
      prefix: "size must be between 1 and 5",
    });
    expect(isValidationError(error)).toBe(true);
    expect(error.fieldErrors.name).toBe("must not be blank");
    expect(error.fieldErrors.prefix).toBe("size must be between 1 and 5");
    expect(toast(error)).toBeNull();
  });

  it("maps new channel errors to the name field", () => {
    const error = refused(422, "GEN_VAL_001", "Validation failed", { name: "size must be <= 80" });
    expect(isValidationError(error)).toBe(true);
    expect(error.fieldErrors).toEqual({ name: "size must be <= 80" });
  });

  it("maps new channel errors to the visibility, purpose and member fields without a toast", () => {
    const error = refused(422, "GEN_VAL_001", "Validation failed", {
      visibility: "Visibility is required",
      purpose: "Purpose is required",
      memberIds: "At most 200 members per request",
    });
    expect(isValidationError(error)).toBe(true);
    expect(error.fieldErrors).toEqual({
      visibility: "Visibility is required",
      purpose: "Purpose is required",
      memberIds: "At most 200 members per request",
    });
    expect(toast(error)).toBeNull();
  });

  it("maps Share PR errors to the status, note and ticket fields", () => {
    const error = refused(422, "GEN_VAL_001", "Validation failed", {
      status: "must not be null",
      note: "size must be <= 500",
      relatedTicketIds: "size must be <= 10",
      ignored: 3,
    });
    expect(error.fieldErrors).toEqual({
      status: "must not be null",
      note: "size must be <= 500",
      relatedTicketIds: "size must be <= 10",
    });
  });

  it("gives a validation error without values no fields", () => {
    const error = refused(422, "GEN_VAL_001", "Validation failed");
    expect(isValidationError(error)).toBe(true);
    expect(error.fieldErrors).toEqual({});
  });
});

describe("a 422 with another code", () => {
  it("is not a validation error and shows error.message in a toast", () => {
    const error = refused(422, "DLY_VAL_002", "You already posted today.", { name: "ignored" });
    expect(isValidationError(error)).toBe(false);
    expect(isApiError(error, "DLY_VAL_002")).toBe(true);
    expect(error.message).toBe("You already posted today.");
    expect(toast(error)).toBe("You already posted today.");
  });
});

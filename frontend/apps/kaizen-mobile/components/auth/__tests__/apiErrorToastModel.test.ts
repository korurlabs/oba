import { describe, expect, it, mock } from "bun:test";

// The same stubs as lib/__tests__/api.test.ts: lib/api.ts loads the env,
// SecureStore and Keycloak discovery at import.
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

const { ApiError, AUTH_REFRESH_TRANSIENT, VALIDATION_ERROR_CODE } = await import("@/lib/api");
const { apiErrorToastMessage, isRuleRefusal } =
  await import("@/components/auth/apiErrorToastModel");

type Kind = ConstructorParameters<typeof ApiError>[1]["kind"];

const t = (key: string): string => `t:${key}`;

function event(
  method: string | undefined,
  kind: Kind,
  details: { status?: number; code?: string; message?: string } = {}
) {
  return {
    error: new ApiError(details.message ?? "Request failed with status code 500", {
      kind,
      status: details.status,
      code: details.code,
    }),
    method,
    url: "/kaizen/tickets",
  };
}

describe("apiErrorToastMessage", () => {
  it("leaves validation errors to the form", () => {
    const failure = event("post", "validation", { status: 422, code: VALIDATION_ERROR_CODE });
    expect(apiErrorToastMessage(failure, t)).toBeNull();
  });

  it("leaves a board key in use to the New board form", () => {
    const failure = event("post", "conflict", {
      status: 409,
      code: "KAI_STA_007",
      message: "Board prefix already in use.",
    });
    expect(apiErrorToastMessage(failure, t)).toBeNull();
  });

  it("leaves failed reads to the screen's error state", () => {
    expect(apiErrorToastMessage(event("get", "server", { status: 500 }), t)).toBeNull();
    expect(apiErrorToastMessage(event("GET", "network"), t)).toBeNull();
    expect(apiErrorToastMessage(event(undefined, "forbidden", { status: 403 }), t)).toBeNull();
  });

  it("shows the backend's message for a refused write that carries a code", () => {
    const failure = event("post", "client", {
      status: 422,
      code: "DLY_VAL_002",
      message: "You already posted today.",
    });
    expect(apiErrorToastMessage(failure, t)).toBe("You already posted today.");
  });

  it("names the kind of failure when the write has no backend message", () => {
    expect(apiErrorToastMessage(event("post", "network"), t)).toBe("t:apiErrorNetwork");
    expect(apiErrorToastMessage(event("put", "server", { status: 503, code: "X" }), t)).toBe(
      "t:apiErrorServer"
    );
    expect(apiErrorToastMessage(event("patch", "rate-limited", { status: 429 }), t)).toBe(
      "t:apiErrorRateLimited"
    );
    expect(apiErrorToastMessage(event("delete", "forbidden", { status: 403 }), t)).toBe(
      "t:apiErrorForbidden"
    );
    expect(apiErrorToastMessage(event("delete", "not-found", { status: 404 }), t)).toBe(
      "t:apiErrorNotFound"
    );
    expect(apiErrorToastMessage(event("post", "unknown"), t)).toBe("t:apiErrorGeneric");
  });

  it("explains a refresh that could not reach Keycloak", () => {
    const failure = event("post", "network", { code: AUTH_REFRESH_TRANSIENT, message: "timeout" });
    expect(apiErrorToastMessage(failure, t)).toBe("t:apiErrorSessionRefresh");
  });

  it("words the backlog status rule in the app's language", () => {
    const failure = event("put", "client", {
      status: 422,
      code: "KAI_VAL_062",
      message: "A ticket in the backlog can only be To Do.",
    });
    expect(apiErrorToastMessage(failure, t)).toBe("t:ticketBacklogStatusNotAllowed");
  });

  it("words the incident close rule in the app's language", () => {
    const failure = event("put", "client", {
      status: 422,
      code: "KAI_VAL_063",
      message: "An incident can only be set to Done once its RCA is filled in.",
    });
    expect(apiErrorToastMessage(failure, t)).toBe("t:ticketIncidentCloseRequiresRca");
  });

  it("words a refused attach of a member without Kaizen in the app's language", () => {
    const failure = event("post", "client", {
      status: 422,
      code: "APP_VAL_002",
      message:
        "This member has no access to this app in this organization. Ask an owner or admin of the organization to grant it.",
    });
    expect(apiErrorToastMessage(failure, t)).toBe("t:memberAccessRefused");
    expect(apiErrorToastMessage({ ...failure, method: "put" }, t)).toBe("t:memberAccessRefused");
  });
});

describe("isRuleRefusal", () => {
  it("is true for a 422, which the same request meets again", () => {
    expect(isRuleRefusal(event("put", "client", { status: 422, code: "KAI_VAL_063" }).error)).toBe(
      true
    );
  });

  it("is false for failures a resend can get past", () => {
    expect(isRuleRefusal(event("put", "network").error)).toBe(false);
    expect(isRuleRefusal(event("put", "server", { status: 503 }).error)).toBe(false);
    expect(isRuleRefusal(event("put", "conflict", { status: 409 }).error)).toBe(false);
  });
});

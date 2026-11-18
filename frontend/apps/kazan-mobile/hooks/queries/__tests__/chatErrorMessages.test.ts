import { describe, expect, it, mock } from "bun:test";

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

// The mutations import the toast host and the translator, which pull in the native UI.
mock.module("@/components/ui/toast", () => ({
  toast: { error: () => undefined, success: () => undefined, info: () => undefined },
}));
mock.module("expo-crypto", () => ({ randomUUID: () => "uuid-1" }));
mock.module("@/i18n", () => ({ translate: (key: string) => key }));
mock.module("expo-router", () => ({ useIsFocused: () => true }));

const { ApiError } = await import("@/lib/api");
const { chatChannelErrorMessage, chatSendErrorMessage } =
  await import("@/hooks/queries/chatMutations");

function refusal(code: string, message: string = "Refused by the server"): Error {
  return new ApiError(message, { status: 422, code, kind: "client" });
}

describe("chatSendErrorMessage", () => {
  it("explains a mention of a member without Kazan in the app's words", () => {
    expect(chatSendErrorMessage(refusal("APP_VAL_002"))).toBe("chatMentionWithoutKazanAccess");
  });

  it("explains a closed thread in the app's words", () => {
    expect(chatSendErrorMessage(refusal("KAZ_STA_014"))).toBe("chatThreadClosedError");
  });

  it("shows the server's message for any other coded refusal", () => {
    expect(chatSendErrorMessage(refusal("KAZ_VAL_999", "Too long"))).toBe("Too long");
  });

  it("falls back to the generic text without a code", () => {
    expect(chatSendErrorMessage(new Error("socket hang up"))).toBe("chatSendFailed");
  });
});

describe("chatChannelErrorMessage", () => {
  it("explains a member without Kazan in the app's words", () => {
    expect(chatChannelErrorMessage(refusal("APP_VAL_002"))).toBe("chatChannelErrorNoKazanAccess");
  });

  it("explains another refusal the app maps in its own words", () => {
    expect(chatChannelErrorMessage(refusal("KAZ_VAL_051"))).toBe(
      "chatChannelErrorMembersNotExpected"
    );
  });

  it("shows the server's message for an unmapped refusal", () => {
    expect(chatChannelErrorMessage(refusal("KAZ_VAL_999", "Name too long"))).toBe("Name too long");
  });

  it("falls back to the generic text when the server sends no message", () => {
    expect(chatChannelErrorMessage(new Error(" "))).toBe("apiErrorGeneric");
  });
});

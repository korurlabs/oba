import { describe, expect, it, mock } from "bun:test";

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

const { buildRedirectUri, buildSignInExtraParams, OAUTH_SCOPES } =
  await import("@/lib/oauthConfig");

describe("oauthConfig", () => {
  it("redirects to kazan://oauthredirect and asks for offline_access", () => {
    expect(buildRedirectUri()).toBe("kazan://oauthredirect");
    expect(OAUTH_SCOPES).toContain("offline_access");
  });

  it("sends the language as ui_locales and prompt=login only when forced", () => {
    expect(buildSignInExtraParams({})).toEqual({});
    expect(buildSignInExtraParams({ uiLocale: "nl" })).toEqual({ ui_locales: "nl" });
    expect(buildSignInExtraParams({ uiLocale: "tr", forceLoginForm: true })).toEqual({
      ui_locales: "tr",
      prompt: "login",
    });
  });
});

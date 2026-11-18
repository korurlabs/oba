import { describe, expect, it, mock } from "bun:test";

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

const { redirectSystemPath } = await import("@/app/+native-intent");

const DEV_CLIENT_URL =
  "com.korur.kaizen://expo-development-client/?url=http%3A%2F%2Flocalhost%3A8082";

describe("redirectSystemPath", () => {
  it("keeps the open screen when the development client URL arrives while the app runs", () => {
    expect(redirectSystemPath({ path: DEV_CLIENT_URL, initial: false })).toBeNull();
  });

  it("opens Home when the development client URL starts the app", () => {
    expect(redirectSystemPath({ path: DEV_CLIENT_URL, initial: true })).toBe("/home");
  });

  it("opens Home for the app root link", () => {
    expect(redirectSystemPath({ path: "kaizen:///", initial: true })).toBe("/home");
    expect(redirectSystemPath({ path: "kaizen:///", initial: false })).toBe("/home");
  });

  it("opens the ticket screen for app and web ticket links", () => {
    expect(redirectSystemPath({ path: "kaizen://ticket/ALP-4", initial: false })).toBe(
      "/ticket/ALP-4"
    );
    expect(
      redirectSystemPath({ path: "https://kaizen.test/en/ticket/ALP-4", initial: false })
    ).toBe("/ticket/ALP-4");
  });
});

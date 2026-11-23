import { describe, expect, test } from "bun:test";

import { authCookieOverrides, sessionCookieName } from "./session-cookie";
import {
  callbackUrlCookieName,
  displayableErrorCode,
  expiredCookieHeader,
  isSecureAuthUrl,
  signInRetryCookieName,
  signInUrl,
  writesSessionCookie,
} from "./sign-in-retry";

const LOCAL_AUTH_URLS = ["http://localhost:3002", "http://localhost:3016", "http://localhost"];
const SECURE_AUTH_URLS = ["https://kazan.korur.com", "https://ulak.korur.com", "https://kazan-dev.korur.com"];

describe("signInRetryCookieName", () => {
  test("names the mark after the app's port on a plain-http AUTH_URL", () => {
    expect(signInRetryCookieName("http://localhost:3002")).toBe("oba-3002.signin-retry");
    expect(signInRetryCookieName("http://localhost:3016")).toBe("oba-3016.signin-retry");
    expect(signInRetryCookieName("http://localhost")).toBe("oba-80.signin-retry");
  });

  test("shares the prefix of the app's handshake cookies on a plain-http AUTH_URL", () => {
    for (const authUrl of LOCAL_AUTH_URLS) {
      const handshakePrefix = authCookieOverrides(authUrl)?.state?.name?.split(".")[0];
      expect(signInRetryCookieName(authUrl).split(".")[0]).toBe(handshakePrefix ?? "");
    }
  });

  test("uses a __Secure- name on an https AUTH_URL", () => {
    for (const authUrl of SECURE_AUTH_URLS) {
      expect(signInRetryCookieName(authUrl)).toBe("__Secure-oba.signin-retry");
      expect(isSecureAuthUrl(authUrl)).toBe(true);
    }
  });

  test("falls back to a fixed name without a usable AUTH_URL", () => {
    expect(signInRetryCookieName(undefined)).toBe("oba.signin-retry");
    expect(signInRetryCookieName("not a url")).toBe("oba.signin-retry");
    expect(isSecureAuthUrl(undefined)).toBe(false);
  });

  test("never falls inside the session cookie family", () => {
    for (const authUrl of [...LOCAL_AUTH_URLS, ...SECURE_AUTH_URLS, undefined]) {
      expect(signInRetryCookieName(authUrl).startsWith(sessionCookieName(authUrl))).toBe(false);
    }
  });
});

describe("callbackUrlCookieName", () => {
  test("matches the per-app callback URL cookie on a plain-http AUTH_URL", () => {
    expect(callbackUrlCookieName("http://localhost:3002")).toBe("oba-3002.callback-url");
    expect(callbackUrlCookieName("http://localhost:3016")).toBe("oba-3016.callback-url");
  });

  test("matches the Auth.js default on an https AUTH_URL", () => {
    expect(callbackUrlCookieName("https://kazan.korur.com")).toBe("__Secure-authjs.callback-url");
  });

  test("matches the Auth.js default without a usable AUTH_URL", () => {
    expect(callbackUrlCookieName(undefined)).toBe("authjs.callback-url");
  });
});

describe("signInUrl", () => {
  test("keeps the deep link and marks only the automatic retry", () => {
    expect(signInUrl("http://localhost:3002/home", true)).toBe(
      "/api/auth/login?callbackUrl=http%3A%2F%2Flocalhost%3A3002%2Fhome&retry=1",
    );
    expect(signInUrl("/en/board/x?tab=1")).toBe("/api/auth/login?callbackUrl=%2Fen%2Fboard%2Fx%3Ftab%3D1");
  });

  test("is the bare login route without a deep link", () => {
    expect(signInUrl(undefined)).toBe("/api/auth/login");
    expect(signInUrl(undefined, true)).toBe("/api/auth/login?retry=1");
  });
});

describe("displayableErrorCode", () => {
  test("shows Auth.js error codes", () => {
    expect(displayableErrorCode("Configuration")).toBe("Configuration");
    expect(displayableErrorCode("OAuthCallbackError")).toBe("OAuthCallbackError");
  });

  test("hides anything that is not an identifier", () => {
    expect(displayableErrorCode(undefined)).toBeUndefined();
    expect(displayableErrorCode("")).toBeUndefined();
    expect(displayableErrorCode("Call +1 555 0100 to restore access")).toBeUndefined();
    expect(displayableErrorCode("<script>")).toBeUndefined();
  });
});

describe("writesSessionCookie", () => {
  const sessionName = "oba.session-token";

  test("detects the session cookie written whole or as chunks", () => {
    expect(writesSessionCookie([`${sessionName}=abc; Path=/; HttpOnly`], sessionName)).toBe(true);
    expect(
      writesSessionCookie(["oba-3002.pkce.code_verifier=; Max-Age=0", `${sessionName}.0=abc; Path=/`], sessionName),
    ).toBe(true);
  });

  test("ignores a failed callback, which writes no session value", () => {
    expect(writesSessionCookie([], sessionName)).toBe(false);
    expect(writesSessionCookie(["oba-3002.pkce.code_verifier=; Path=/; Max-Age=0"], sessionName)).toBe(false);
    expect(writesSessionCookie([`${sessionName}.0=; Path=/; Max-Age=0`], sessionName)).toBe(false);
  });

  test("ignores cookies that only share the name's beginning", () => {
    expect(writesSessionCookie([`${sessionName}x=abc; Path=/`], sessionName)).toBe(false);
    expect(writesSessionCookie(["oba-3002.callback-url=http%3A%2F%2Flocalhost%3A3002"], sessionName)).toBe(false);
  });
});

describe("expiredCookieHeader", () => {
  test("expires the cookie on path /", () => {
    expect(expiredCookieHeader("oba-3002.signin-retry", false)).toBe(
      "oba-3002.signin-retry=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax",
    );
  });

  test("keeps Secure, which a __Secure- name needs to be replaced", () => {
    expect(expiredCookieHeader("__Secure-oba.signin-retry", true)).toBe(
      "__Secure-oba.signin-retry=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax; Secure",
    );
  });
});

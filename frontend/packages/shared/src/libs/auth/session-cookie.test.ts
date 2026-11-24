import { describe, expect, test } from "bun:test";

import { authCookieOverrides, sessionCookieName } from "./session-cookie";

const HANDSHAKE_COOKIES = ["csrfToken", "callbackUrl", "pkceCodeVerifier", "state", "nonce"] as const;

function handshakeNames(authUrl: string): string[] {
  const cookies = authCookieOverrides(authUrl);
  return HANDSHAKE_COOKIES.map((key) => cookies?.[key]?.name ?? "");
}

describe("authCookieOverrides on a plain-http AUTH_URL", () => {
  test("names the handshake cookies after the app's port", () => {
    expect(handshakeNames("http://localhost:3002")).toEqual([
      "oba-3002.csrf-token",
      "oba-3002.callback-url",
      "oba-3002.pkce.code_verifier",
      "oba-3002.state",
      "oba-3002.nonce",
    ]);
  });

  test("uses port 80 for a URL without an explicit port", () => {
    expect(handshakeNames("http://localhost")).toEqual(handshakeNames("http://localhost:80"));
    expect(authCookieOverrides("http://localhost")?.pkceCodeVerifier?.name).toBe("oba-80.pkce.code_verifier");
  });

  test("gives two local apps disjoint handshake cookies and one shared session cookie", () => {
    const kazan = authCookieOverrides("http://localhost:3002");
    const ulak = authCookieOverrides("http://localhost:3016");

    expect(kazan?.sessionToken?.name).toBe("oba.session-token");
    expect(ulak?.sessionToken?.name).toBe("oba.session-token");
    const kazanNames = handshakeNames("http://localhost:3002");
    for (const name of handshakeNames("http://localhost:3016")) {
      expect(kazanNames).not.toContain(name);
    }
  });

  test.each(["http://localhost:3002", "http://localhost:3016", "http://localhost"])(
    "keeps every handshake name of %s outside the session name family",
    (authUrl) => {
      const session = sessionCookieName(authUrl);
      for (const name of handshakeNames(authUrl)) {
        expect(name.startsWith(session)).toBe(false);
        expect(session.startsWith(name)).toBe(false);
      }
    },
  );
});

describe("authCookieOverrides on an https AUTH_URL", () => {
  test("overrides only the parent-domain session cookie in production", () => {
    expect(authCookieOverrides("https://kazan.korur.com")).toEqual({
      sessionToken: {
        name: "__Secure-authjs.session-token",
        options: { httpOnly: true, sameSite: "lax", path: "/", secure: true, domain: ".korur.com" },
      },
    });
  });

  test("overrides only the dev session cookie on a dev host", () => {
    expect(authCookieOverrides("https://kazan-dev.korur.com")).toEqual({
      sessionToken: {
        name: "__Secure-authjs-dev.session-token",
        options: { httpOnly: true, sameSite: "lax", path: "/", secure: true, domain: ".korur.com" },
      },
    });
  });
});

describe("authCookieOverrides without a usable AUTH_URL", () => {
  test.each([undefined, ""])("leaves the Auth.js defaults for %p", (authUrl) => {
    expect(authCookieOverrides(authUrl)).toBeUndefined();
  });
});

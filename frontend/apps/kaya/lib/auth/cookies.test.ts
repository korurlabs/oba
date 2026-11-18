import { describe, expect, test } from "bun:test";

import { kayaAuthCookies, legacyAuthCookieNames } from "./cookies";

/** Session names the Oba apps write (packages/shared session-cookie.ts) and the Auth.js defaults. */
const FOREIGN_SESSION_NAMES = [
  "oba.session-token",
  "__Secure-authjs.session-token",
  "__Secure-authjs-dev.session-token",
  "authjs.session-token",
];
/** Other cookie names Kaya and the local Oba apps write. */
const OTHER_NAMES = ["kaya.reauth", "oba-3002.pkce.code_verifier", "oba-3016.state"];

function names(authUrl?: string): string[] {
  return Object.values(kayaAuthCookies(authUrl)).flatMap((cookie) =>
    cookie.name ? [cookie.name] : []
  );
}

describe("kayaAuthCookies", () => {
  test("uses the __Secure- names, and __Host- for the CSRF cookie, on https", () => {
    const cookies = kayaAuthCookies("https://kaya.korur.com");
    expect(cookies.sessionToken?.name).toBe("__Secure-kaya.session-token");
    expect(cookies.csrfToken?.name).toBe("__Host-kaya.csrf-token");
    expect(cookies.callbackUrl?.name).toBe("__Secure-kaya.callback-url");
    expect(cookies.pkceCodeVerifier?.name).toBe("__Secure-kaya.pkce.code_verifier");
    expect(cookies.state?.name).toBe("__Secure-kaya.state");
    expect(cookies.nonce?.name).toBe("__Secure-kaya.nonce");
    expect(cookies.sessionToken?.options).toEqual({ secure: true });
  });

  test("uses the plain kaya names on http and without AUTH_URL", () => {
    for (const authUrl of ["http://localhost:3014", undefined, "not a url"]) {
      const cookies = kayaAuthCookies(authUrl);
      expect(cookies.sessionToken?.name).toBe("kaya.session-token");
      expect(cookies.csrfToken?.name).toBe("kaya.csrf-token");
      expect(cookies.pkceCodeVerifier?.name).toBe("kaya.pkce.code_verifier");
    }
  });

  test("never sets a domain, so every cookie stays host-only", () => {
    for (const cookie of Object.values(kayaAuthCookies("https://kaya.korur.com"))) {
      expect(cookie.options).not.toHaveProperty("domain");
    }
  });

  test("no name falls into another session family, which Auth.js joins by name prefix", () => {
    for (const authUrl of ["https://kaya.korur.com", "http://localhost:3014"]) {
      const all = names(authUrl);
      const session = kayaAuthCookies(authUrl).sessionToken?.name ?? "";
      for (const name of [...all.filter((other) => other !== session), ...OTHER_NAMES]) {
        expect(name.startsWith(session)).toBe(false);
      }
      for (const name of all) {
        for (const foreign of FOREIGN_SESSION_NAMES) {
          expect(name.startsWith(foreign)).toBe(false);
          expect(foreign.startsWith(name)).toBe(false);
        }
      }
    }
  });
});

describe("legacyAuthCookieNames", () => {
  const request = [
    "__Secure-authjs.session-token.0",
    "__Secure-authjs.session-token.1",
    "__Host-authjs.csrf-token",
    "__Secure-authjs.pkce.code_verifier",
    "__Secure-authjs-dev.session-token.0",
    "__Secure-kaya.session-token.0",
    "authjs.session-token",
    "kaya.reauth",
  ];

  test("picks the https Auth.js defaults Kaya used to write", () => {
    expect(legacyAuthCookieNames(request, "https://kaya.korur.com")).toEqual([
      "__Secure-authjs.session-token.0",
      "__Secure-authjs.session-token.1",
      "__Host-authjs.csrf-token",
      "__Secure-authjs.pkce.code_verifier",
    ]);
  });

  test("touches nothing on http or localhost, where the names may be another local app's session", () => {
    expect(legacyAuthCookieNames(request, "http://localhost:3014")).toEqual([]);
    expect(legacyAuthCookieNames(request, "https://localhost:3014")).toEqual([]);
    expect(legacyAuthCookieNames(request, undefined)).toEqual([]);
  });
});

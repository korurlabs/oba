import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { encode } from "next-auth/jwt";
import { NextRequest } from "next/server";

import { GET, POST } from "./route";

const SECRET = "test-secret-of-at-least-thirty-two-characters";
const ISSUER = "http://localhost:8086/realms/sensey";
const ENV_KEYS = ["AUTH_URL", "AUTH_SECRET", "AUTH_KEYCLOAK_ISSUER", "AUTH_KEYCLOAK_ID"] as const;

let savedEnv: Record<string, string | undefined>;

beforeEach(() => {
  savedEnv = Object.fromEntries(ENV_KEYS.map((key) => [key, process.env[key]]));
  process.env.AUTH_URL = "http://localhost:3014";
  process.env.AUTH_SECRET = SECRET;
  process.env.AUTH_KEYCLOAK_ISSUER = ISSUER;
  process.env.AUTH_KEYCLOAK_ID = "oba";
});

afterEach(() => {
  for (const key of ENV_KEYS) {
    if (savedEnv[key] === undefined) {
      delete process.env[key];
    } else {
      process.env[key] = savedEnv[key];
    }
  }
});

/** A Kaya session cookie value, encrypted the way Auth.js writes it. */
function session(cookieName: string, idToken?: string): Promise<string> {
  return encode({ token: { sub: "u1", idToken }, secret: SECRET, salt: cookieName });
}

function post(url: string, headers: Record<string, string>): Promise<Response> {
  return POST(new NextRequest(url, { method: "POST", headers }));
}

function location(response: Response): URL {
  return new URL(response.headers.get("location") ?? "");
}

/** Every Set-Cookie of the response as name -> attributes (lower case). */
function setCookies(response: Response): Map<string, string> {
  return new Map(
    response.headers.getSetCookie().map((header): [string, string] => {
      const [pair = "", ...attributes] = header.split(";");
      return [pair.split("=")[0] ?? "", attributes.join(";").toLowerCase()];
    })
  );
}

function expectSessionExpired(response: Response, base: string): void {
  const cookies = setCookies(response);
  for (const name of [base, `${base}.0`, `${base}.1`, `${base}.11`]) {
    expect(cookies.get(name)).toContain("max-age=0");
  }
  for (const [name, attributes] of cookies) {
    expect(name.startsWith(base)).toBe(true);
    expect(attributes).not.toContain("domain=");
  }
}

describe("sign-out route", () => {
  test("sends the browser to Keycloak's end-session with the id token, client and return URI", async () => {
    const cookie = `kaya.session-token=${await session("kaya.session-token", "id-token-1")}`;
    const response = await post("http://localhost:3014/api/auth/logout", {
      cookie,
      "sec-fetch-site": "same-origin",
    });

    expect(response.status).toBe(303);
    const target = location(response);
    expect(`${target.origin}${target.pathname}`).toBe(`${ISSUER}/protocol/openid-connect/logout`);
    expect(target.searchParams.get("client_id")).toBe("oba");
    expect(target.searchParams.get("post_logout_redirect_uri")).toBe(
      "http://localhost:3014/api/auth/logout"
    );
    expect(target.searchParams.get("id_token_hint")).toBe("id-token-1");
    expect(target.searchParams.has("redirect_uri")).toBe(false);
    expectSessionExpired(response, "kaya.session-token");
  });

  test("reads a session split into chunks", async () => {
    const value = await session("kaya.session-token", "id-token-2");
    const half = Math.floor(value.length / 2);
    const response = await post("http://localhost:3014/api/auth/logout", {
      cookie: `kaya.session-token.0=${value.slice(0, half)}; kaya.session-token.1=${value.slice(half)}`,
    });

    expect(location(response).searchParams.get("id_token_hint")).toBe("id-token-2");
  });

  test("without a readable session it still ends the SSO session by client id", async () => {
    const response = await post("http://localhost:3014/api/auth/logout", {
      cookie: "kaya.session-token=not-a-jwe; oba.session-token.0=oba-session",
    });

    const target = location(response);
    expect(target.searchParams.get("client_id")).toBe("oba");
    expect(target.searchParams.has("id_token_hint")).toBe(false);
    expectSessionExpired(response, "kaya.session-token");
  });

  test("ignores the Oba session, which lives under another name", async () => {
    const obaSession = await session("oba.session-token", "oba-id-token");
    const response = await post("http://localhost:3014/api/auth/logout", {
      cookie: `oba.session-token=${obaSession}`,
    });

    expect(location(response).searchParams.has("id_token_hint")).toBe(false);
    expect([...setCookies(response).keys()].some((name) => name.startsWith("oba"))).toBe(false);
  });

  test("uses the production host and the secure cookie names on an https AUTH_URL", async () => {
    process.env.AUTH_URL = "https://kaya.korur.com";
    const name = "__Secure-kaya.session-token";
    const response = await post("http://10.0.0.7:3014/api/auth/logout", {
      cookie: `${name}=${await session(name, "id-token-3")}`,
    });

    const target = location(response);
    expect(target.searchParams.get("post_logout_redirect_uri")).toBe(
      "https://kaya.korur.com/api/auth/logout"
    );
    expect(target.searchParams.get("id_token_hint")).toBe("id-token-3");
    expectSessionExpired(response, name);
    expect(setCookies(response).get(name)).toContain("secure");
  });

  test("refuses a sign-out started by another site", async () => {
    const response = await post("http://localhost:3014/api/auth/logout", {
      "sec-fetch-site": "cross-site",
    });

    expect(response.status).toBe(403);
    expect(response.headers.getSetCookie()).toEqual([]);
  });

  test("without the Keycloak settings it still clears the session and lands on the home page", async () => {
    delete process.env.AUTH_KEYCLOAK_ISSUER;
    const response = await post("http://localhost:3014/api/auth/logout", {});

    expect(response.status).toBe(303);
    expect(location(response).toString()).toBe("http://localhost:3014/");
    expectSessionExpired(response, "kaya.session-token");
  });

  test("the return from Keycloak clears the session again and lands on the home page", async () => {
    const response = await GET(new NextRequest("http://localhost:3014/api/auth/logout"));

    expect(response.status).toBe(303);
    expect(location(response).toString()).toBe("http://localhost:3014/");
    expectSessionExpired(response, "kaya.session-token");
  });
});

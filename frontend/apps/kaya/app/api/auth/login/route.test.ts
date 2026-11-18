import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { NextRequest } from "next/server";

import { REAUTH_COOKIE, REAUTH_WINDOW_SECONDS, SESSION_REJECTED } from "@/lib/auth/reauth";

// The route reads the session through `auth()`, starts the Keycloak sign-in
// through `signIn()` and sets the forced sign-in's cookie through `cookies()`;
// each is replaced so the test sees which of them the route chose.
type CookieSet = { name: string; value: string; options: Record<string, unknown> };

let session: { error?: string } | null;
let signIns: { provider: string; redirectTo: string }[];
let cookieSets: CookieSet[];

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualAuth = { ...(await import("@/lib/auth")) };
const actualHeaders = { ...(await import("next/headers")) };

mock.module("@/lib/auth", () => ({
  ...actualAuth,
  auth: async () => session,
  signIn: async (provider: string, options: { redirectTo: string }) => {
    signIns.push({ provider, redirectTo: options.redirectTo });
    return new Response(null, { status: 303, headers: { location: "http://localhost:8087/auth" } });
  },
}));
mock.module("next/headers", () => ({
  ...actualHeaders,
  cookies: async () => ({
    set: (name: string, value: string, options: Record<string, unknown>) =>
      cookieSets.push({ name, value, options }),
  }),
}));

afterAll(() => {
  mock.module("@/lib/auth", () => actualAuth);
  mock.module("next/headers", () => actualHeaders);
});

const { GET } = await import("./route");

function get(query: string, cookie?: string): Promise<Response> {
  return GET(
    new NextRequest(`http://localhost:3014/api/auth/login?${query}`, {
      headers: cookie === undefined ? {} : { cookie },
    })
  );
}

/** Runs `run` with AUTH_URL set to `authUrl`, which the route reads per request. */
async function withAuthUrl<T>(authUrl: string, run: () => Promise<T>): Promise<T> {
  const previous = process.env.AUTH_URL;
  process.env.AUTH_URL = authUrl;
  try {
    return await run();
  } finally {
    if (previous === undefined) {
      delete process.env.AUTH_URL;
    } else {
      process.env.AUTH_URL = previous;
    }
  }
}

function location(response: Response): URL {
  return new URL(response.headers.get("location") ?? "");
}

beforeEach(() => {
  session = {};
  signIns = [];
  cookieSets = [];
});

describe("sign-in route", () => {
  test("sends a signed-in visitor straight back to the page", async () => {
    const response = await get("callbackUrl=%2Fusers");
    expect(response.status).toBe(307);
    expect(location(response).pathname).toBe("/users");
    expect(signIns).toEqual([]);
  });

  test("starts the Keycloak sign-in for a signed-out visitor", async () => {
    session = null;
    await get("callbackUrl=%2Fusers");
    expect(signIns).toEqual([{ provider: "keycloak", redirectTo: "/users" }]);
    expect(cookieSets).toEqual([]);
  });

  test("starts the Keycloak sign-in for a session that can no longer be refreshed", async () => {
    session = { error: "REFRESH_TOKEN_ERROR" };
    await get("callbackUrl=%2F");
    expect(signIns).toEqual([{ provider: "keycloak", redirectTo: "/" }]);
  });

  test("a session the API refused gets a fresh sign-in, not a redirect back to be refused again", async () => {
    const response = await get("callbackUrl=%2Fsessions&reauth=1");
    expect(response.headers.get("location")).toBe("http://localhost:8087/auth");
    expect(signIns).toEqual([{ provider: "keycloak", redirectTo: "/sessions" }]);
    expect(cookieSets).toHaveLength(1);
    expect(cookieSets[0]?.name).toBe(REAUTH_COOKIE);
    expect(cookieSets[0]?.options).toMatchObject({
      httpOnly: true,
      path: "/",
      maxAge: REAUTH_WINDOW_SECONDS,
    });
  });

  test("a second refusal right after a fresh sign-in shows the error instead of looping", async () => {
    const response = await get("callbackUrl=%2Fsessions&reauth=1", `${REAUTH_COOKIE}=1`);
    expect(response.status).toBe(307);
    const target = location(response);
    expect(target.pathname).toBe("/");
    expect(target.searchParams.get("error")).toBe(SESSION_REJECTED);
    expect(signIns).toEqual([]);
    // The cookie is cleared, so the user's own "sign in again" is not refused as well.
    expect(response.headers.get("set-cookie")).toContain(`${REAUTH_COOKIE}=;`);
  });

  test("the loop seen after a database reset ends after one fresh sign-in", async () => {
    // The gate answers every 401 with a forced sign-in; a browser keeps the
    // cookie the first one sets and sends it with the second.
    const first = await get("callbackUrl=%2F&reauth=1");
    expect(first.headers.get("location")).toBe("http://localhost:8087/auth");
    const cookie = `${cookieSets[0]?.name}=${cookieSets[0]?.value}`;
    const second = await get("callbackUrl=%2F&reauth=1", cookie);
    expect(location(second).searchParams.get("error")).toBe(SESSION_REJECTED);
    expect(signIns).toHaveLength(1);
  });

  test("a failed sign-in lands on the home page with its error", async () => {
    const response = await get("error=AccessDenied&reauth=1");
    const target = location(response);
    expect(target.pathname).toBe("/");
    expect(target.searchParams.get("error")).toBe("AccessDenied");
    expect(signIns).toEqual([]);
  });

  test("a sign-in on https expires the cookies Kaya wrote under the Auth.js default names", async () => {
    session = null;
    await withAuthUrl("https://kaya.korur.com", () =>
      get(
        "callbackUrl=%2F",
        "__Secure-authjs.session-token.0=a; __Host-authjs.csrf-token=b; __Secure-kaya.session-token=c; kaya.reauth=1"
      )
    );
    expect(signIns).toHaveLength(1);
    expect(cookieSets.map((cookie) => cookie.name)).toEqual([
      "__Secure-authjs.session-token.0",
      "__Host-authjs.csrf-token",
    ]);
    for (const cookie of cookieSets) {
      expect(cookie.value).toBe("");
      // No domain: only Kaya's host-only copy goes, never Oba's parent-domain session.
      expect(cookie.options).toEqual({
        httpOnly: true,
        sameSite: "lax",
        secure: true,
        path: "/",
        maxAge: 0,
      });
    }
  });

  test("a local sign-in leaves the plain Auth.js cookies of other local apps alone", async () => {
    session = null;
    await withAuthUrl("http://localhost:3014", () =>
      get("callbackUrl=%2F", "authjs.session-token=a; __Secure-authjs.session-token=b")
    );
    expect(signIns).toHaveLength(1);
    expect(cookieSets).toEqual([]);
  });

  test("a callback to another site is replaced by the home page", async () => {
    const response = await get("callbackUrl=https%3A%2F%2Fevil.example");
    expect(location(response).origin).toBe("http://localhost:3014");
    expect(location(response).pathname).toBe("/");
  });
});

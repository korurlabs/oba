import { afterAll, describe, expect, mock, test } from "bun:test";

import type { NextAuthRequest } from "next-auth";

// `auth()` wraps the handler with the session check; the test calls the
// handler itself with the session it wants the request to carry.
// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualAuth = { ...(await import("@/lib/auth")) };

mock.module("@/lib/auth", () => ({
  SIGN_IN_PATH: "/api/auth/login",
  auth: (handler: unknown) => handler,
}));

afterAll(() => {
  mock.module("@/lib/auth", () => actualAuth);
});

const proxy = await import("./proxy");
const handler = proxy.default as unknown as (request: NextAuthRequest) => Response;

function requestFor(url: string, authState: { error?: string } | null): NextAuthRequest {
  const nextUrl = new URL(url, "https://kaya.korur.com");
  return { auth: authState, nextUrl } as unknown as NextAuthRequest;
}

describe("proxy", () => {
  test("sends a signed-out visitor to the sign-in, back to the same page and query", () => {
    const response = handler(requestFor("/sessions/s1?stage=plan&x=1", null));
    expect(response.status).toBe(307);
    const location = new URL(response.headers.get("location") ?? "");
    expect(location.origin).toBe("https://kaya.korur.com");
    expect(location.pathname).toBe("/api/auth/login");
    expect(location.searchParams.get("callbackUrl")).toBe("/sessions/s1?stage=plan&x=1");
  });

  test("sends the root to the sign-in with a root callback", () => {
    const response = handler(requestFor("/", null));
    const location = new URL(response.headers.get("location") ?? "");
    expect(location.searchParams.get("callbackUrl")).toBe("/");
  });

  test("sends a session that can no longer be refreshed to the sign-in", () => {
    const response = handler(requestFor("/users", { error: "RefreshTokenError" }));
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toContain("/api/auth/login?callbackUrl=%2Fusers");
  });

  test("lets a signed-in visitor through", () => {
    const response = handler(requestFor("/users", {}));
    expect(response.status).toBe(200);
    expect(response.headers.get("location")).toBeNull();
    expect(response.headers.get("x-middleware-next")).toBe("1");
  });

  test("lets a failed sign-in's landing page render, so it cannot loop", () => {
    const response = handler(requestFor("/?error=AccessDenied", null));
    expect(response.headers.get("location")).toBeNull();
    expect(response.headers.get("x-middleware-next")).toBe("1");
  });

  test("matches pages but not Auth.js routes, Next.js assets or files with a dot", () => {
    const matcher = new RegExp(`^${proxy.config.matcher}$`);
    for (const path of ["/", "/sessions", "/sessions/abc", "/settings/members"]) {
      expect(matcher.test(path)).toBe(true);
    }
    for (const path of [
      "/api/auth/login",
      "/api/auth/session",
      "/_next/static/chunk.js",
      "/favicon.ico",
      "/korur-logo.svg",
    ]) {
      expect(matcher.test(path)).toBe(false);
    }
  });
});

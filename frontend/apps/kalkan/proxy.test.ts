import { afterAll, describe, expect, mock, test } from "bun:test";

import type { NextAuthRequest } from "next-auth";
import { NextRequest } from "next/server";
import { unstable_doesMiddlewareMatch } from "next/experimental/testing/server";

import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";
import { AuthError } from "@oba/shared/libs/auth/types";

// `auth()` wraps the handler with the session check; the test calls the
// handler itself with the session it wants the request to carry.
// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualAuth = { ...(await import("@oba/shared/libs/auth")) };

mock.module("@oba/shared/libs/auth", () => ({
  ...actualAuth,
  auth: (handler: unknown) => handler,
}));

afterAll(() => {
  mock.module("@oba/shared/libs/auth", () => actualAuth);
});

const proxyModule = await import("./proxy");
const { config } = proxyModule;
const handler = proxyModule.default as unknown as (request: NextAuthRequest) => Promise<Response>;

type AuthState = {
  user?: { id: string };
  error?: string;
  pendingInvitationIds?: string[];
  remindLaterInvitations?: Record<string, number>;
} | null;

function requestFor(url: string, authState: AuthState): NextAuthRequest {
  const request = new NextRequest(new URL(url, "https://kalkan.korur.com"));
  return Object.assign(request, { auth: authState }) as unknown as NextAuthRequest;
}

const SIGNED_IN: AuthState = { user: { id: "u1" } };

describe("kalkan proxy", () => {
  test("sends a signed-out visitor of a private page to the sign-in with the deep link", async () => {
    const response = await handler(requestFor("/isms/A.5.1?mode=edit", null));

    expect(response.status).toBe(307);
    const location = new URL(response.headers.get("location") ?? "");
    expect(location.pathname).toBe("/api/auth/login");
    expect(location.searchParams.get("callbackUrl")).toBe(
      "https://kalkan.korur.com/isms/A.5.1?mode=edit"
    );
  });

  test("lets a signed-out visitor reach the public root", async () => {
    const response = await handler(requestFor("/", null));

    expect(response.headers.get("location")).toBeNull();
  });

  test("sends a signed-out visitor of /onboarding to the sign-in, as onboarding lives in account", async () => {
    const response = await handler(requestFor("/onboarding", null));

    expect(new URL(response.headers.get("location") ?? "").pathname).toBe("/api/auth/login");
  });

  test("lets a signed-in user through a private page without invitations", async () => {
    const response = await handler(requestFor("/isms/A.5.1", SIGNED_IN));

    expect(response.headers.get("location")).toBeNull();
  });

  test("sends a session error on a private page to the root with the error and the deep link", async () => {
    const response = await handler(
      requestFor("/isms/A.5.1", { ...SIGNED_IN, error: AuthError.REFRESH_TOKEN_ERROR })
    );

    const location = new URL(response.headers.get("location") ?? "");
    expect(location.pathname).toBe("/");
    expect(location.searchParams.get("error")).toBe(AuthError.REFRESH_TOKEN_ERROR);
    expect(location.searchParams.get("callbackUrl")).toBe("https://kalkan.korur.com/isms/A.5.1");
  });

  test("leaves a session error on the public root to the page, which avoids a redirect loop", async () => {
    const response = await handler(
      requestFor("/?error=RefreshTokenError", {
        ...SIGNED_IN,
        error: AuthError.REFRESH_TOKEN_ERROR,
      })
    );

    expect(response.headers.get("location")).toBeNull();
  });

  test("sends a user with a pending invitation to the account invitations page, back to the same page", async () => {
    const response = await handler(
      requestFor("/isms/A.5.1", { ...SIGNED_IN, pendingInvitationIds: ["inv-1"] })
    );

    const location = new URL(response.headers.get("location") ?? "");
    expect(`${location.origin}${location.pathname}`).toBe(ACCOUNT_LINKS.invitations);
    expect(location.searchParams.get("callbackUrl")).toBe("https://kalkan.korur.com/isms/A.5.1");
  });

  test("does not redirect while every pending invitation is snoozed", async () => {
    const response = await handler(
      requestFor("/isms/A.5.1", {
        ...SIGNED_IN,
        pendingInvitationIds: ["inv-1"],
        remindLaterInvitations: { "inv-1": Date.now() + 60_000 },
      })
    );

    expect(response.headers.get("location")).toBeNull();
  });

  test("redirects again once the snooze of a pending invitation has expired", async () => {
    const response = await handler(
      requestFor("/isms/A.5.1", {
        ...SIGNED_IN,
        pendingInvitationIds: ["inv-1"],
        remindLaterInvitations: { "inv-1": Date.now() - 1_000 },
      })
    );

    const location = new URL(response.headers.get("location") ?? "");
    expect(`${location.origin}${location.pathname}`).toBe(ACCOUNT_LINKS.invitations);
  });
});

describe("kalkan proxy matcher", () => {
  const runsOn = (url: string) => unstable_doesMiddlewareMatch({ config, url });

  test("runs on page paths, those with dotted slugs included", () => {
    expect(runsOn("/")).toBe(true);
    expect(runsOn("/isms")).toBe(true);
    expect(runsOn("/isms/A.5.1")).toBe(true);
    expect(runsOn("/isms/kontroller/A.8.24.md")).toBe(true);
  });

  test("runs on root pages whose slug only starts like an excluded path", () => {
    expect(runsOn("/api-guide")).toBe(true);
    expect(runsOn("/assets-register")).toBe(true);
    expect(runsOn("/icon.png-notes")).toBe(true);
  });

  test("runs on the Asset section, whose route shares its name with the static files' folder", () => {
    expect(runsOn("/assets")).toBe(true);
    expect(runsOn("/assets?view=tree")).toBe(true);
  });

  test("skips the auth routes, Next.js internals and the app's own static files", () => {
    expect(runsOn("/api/auth/login")).toBe(false);
    expect(runsOn("/_next/static/chunks/main.js")).toBe(false);
    expect(runsOn("/_vercel/insights/script.js")).toBe(false);
    expect(runsOn("/assets/korur-mark.png")).toBe(false);
    expect(runsOn("/icon.png")).toBe(false);
    expect(runsOn("/favicon.ico")).toBe(false);
  });
});

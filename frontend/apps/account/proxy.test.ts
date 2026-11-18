import { afterAll, describe, expect, mock, test } from "bun:test";

import type { NextAuthRequest } from "next-auth";
import { NextRequest } from "next/server";

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
const handler = proxyModule.default as unknown as (
  request: NextAuthRequest,
) => Promise<Response>;

type AuthState = {
  user?: { id: string };
  error?: string;
  pendingInvitationIds?: string[];
  remindLaterInvitations?: Record<string, number>;
} | null;

function requestFor(url: string, authState: AuthState): NextAuthRequest {
  const request = new NextRequest(new URL(url, "https://account.korur.com"));
  return Object.assign(request, { auth: authState }) as unknown as NextAuthRequest;
}

const SIGNED_IN: AuthState = { user: { id: "u1" } };

describe("account proxy", () => {
  test("sends a signed-out visitor of a private page to the sign-in with the deep link", async () => {
    const response = await handler(requestFor("/settings?tab=language", null));

    expect(response.status).toBe(307);
    const location = new URL(response.headers.get("location") ?? "");
    expect(location.pathname).toBe("/api/auth/login");
    expect(location.searchParams.get("callbackUrl")).toBe(
      "https://account.korur.com/settings?tab=language",
    );
  });

  test("lets a signed-out visitor reach the public root", async () => {
    const response = await handler(requestFor("/", null));

    expect(response.headers.get("location")).toBeNull();
  });

  test("lets a signed-out visitor reach /onboarding, which is public", async () => {
    const response = await handler(requestFor("/onboarding", null));

    expect(response.headers.get("location")).toBeNull();
  });

  test("lets a signed-out visitor reach the nested public route /onboarding/step", async () => {
    const response = await handler(requestFor("/onboarding/step", null));

    expect(response.headers.get("location")).toBeNull();
  });

  test("lets a signed-in user through a private page without invitations", async () => {
    const response = await handler(requestFor("/settings", SIGNED_IN));

    expect(response.headers.get("location")).toBeNull();
  });

  test("sends a session error on a private page to the root with the error and the deep link", async () => {
    const response = await handler(
      requestFor("/settings", { ...SIGNED_IN, error: AuthError.REFRESH_TOKEN_ERROR }),
    );

    const location = new URL(response.headers.get("location") ?? "");
    expect(location.pathname).toBe("/");
    expect(location.searchParams.get("error")).toBe(AuthError.REFRESH_TOKEN_ERROR);
    expect(location.searchParams.get("callbackUrl")).toBe("https://account.korur.com/settings");
  });

  test("leaves a session error on the public root to the page, which avoids a redirect loop", async () => {
    const response = await handler(requestFor("/?error=RefreshTokenError", { ...SIGNED_IN, error: AuthError.REFRESH_TOKEN_ERROR }));

    expect(response.headers.get("location")).toBeNull();
  });

  test("sends a user with a pending invitation to its own invitations page, back to the same page", async () => {
    const response = await handler(
      requestFor("/settings", { ...SIGNED_IN, pendingInvitationIds: ["inv-1"] }),
    );

    const location = new URL(response.headers.get("location") ?? "");
    expect(location.pathname).toBe("/invitations");
    expect(location.searchParams.get("callbackUrl")).toBe("https://account.korur.com/settings");
  });

  test("does not redirect a user with a pending invitation who is already on the invitations page", async () => {
    const response = await handler(requestFor("/invitations", { ...SIGNED_IN, pendingInvitationIds: ["inv-1"] }));

    expect(response.headers.get("location")).toBeNull();
  });

  test("does not redirect while every pending invitation is snoozed", async () => {
    const response = await handler(
      requestFor("/settings", {
        ...SIGNED_IN,
        pendingInvitationIds: ["inv-1"],
        remindLaterInvitations: { "inv-1": Date.now() + 60_000 },
      }),
    );

    expect(response.headers.get("location")).toBeNull();
  });

  test("redirects again once the snooze of a pending invitation has expired", async () => {
    const response = await handler(
      requestFor("/settings", {
        ...SIGNED_IN,
        pendingInvitationIds: ["inv-1"],
        remindLaterInvitations: { "inv-1": Date.now() - 1_000 },
      }),
    );

    const location = new URL(response.headers.get("location") ?? "");
    expect(location.pathname).toBe("/invitations");
  });

  describe("locale cookie", () => {
    function withCookie(path: string, cookie: string, acceptLanguage = "en") {
      const request = new NextRequest(new URL(path, "https://account.korur.com"), {
        headers: { cookie, "accept-language": acceptLanguage },
      });
      return Object.assign(request, { auth: SIGNED_IN }) as unknown as NextAuthRequest;
    }

    function rewriteOf(response: Response): string | null {
      const header = response.headers.get("x-middleware-rewrite");
      return header ? new URL(header).pathname : null;
    }

    test("selects the locale from OBA_LOCALE", async () => {
      const response = await handler(withCookie("/settings", "OBA_LOCALE=nl"));

      expect(rewriteOf(response)).toBe("/nl/settings");
    });

    test("ignores a NEXT_LOCALE-only cookie left by an older deploy", async () => {
      const response = await handler(withCookie("/settings", "NEXT_LOCALE=tr", "en"));

      expect(rewriteOf(response)).toBe("/en/settings");
    });

    test("prefers OBA_LOCALE when both cookies are present", async () => {
      const response = await handler(withCookie("/settings", "NEXT_LOCALE=tr; OBA_LOCALE=nl"));

      expect(rewriteOf(response)).toBe("/nl/settings");
    });

    function localeHeaderOf(response: Response): string | null {
      return response.headers.get("x-middleware-request-x-next-intl-locale");
    }

    test("sends a typed /nl/ path in another locale to the unprefixed path and stores nl", async () => {
      const response = await handler(withCookie("/nl/settings?tab=language", "OBA_LOCALE=en"));

      expect(response.status).toBe(307);
      const location = new URL(response.headers.get("location") ?? "");
      expect(location.pathname).toBe("/settings");
      expect(location.searchParams.get("tab")).toBe("language");
      expect(response.headers.get("set-cookie")).toContain("OBA_LOCALE=nl");
    });

    test("sends a typed /nl/ path to the unprefixed path for an RSC request too", async () => {
      const request = new NextRequest(new URL("/nl/settings", "https://account.korur.com"), {
        headers: { cookie: "OBA_LOCALE=en", "accept-language": "en", rsc: "1" },
      });
      const response = await handler(
        Object.assign(request, { auth: SIGNED_IN }) as unknown as NextAuthRequest,
      );

      expect(response.status).toBe(307);
      expect(new URL(response.headers.get("location") ?? "").pathname).toBe("/settings");
      expect(response.headers.get("set-cookie")).toContain("OBA_LOCALE=nl");
    });

    test("stores the typed locale for a visitor without a cookie whose browser asks for another", async () => {
      const response = await handler(withCookie("/nl/settings", "", "en"));

      expect(new URL(response.headers.get("location") ?? "").pathname).toBe("/settings");
      expect(response.headers.get("set-cookie")).toContain("OBA_LOCALE=nl");
    });

    test("then renders /settings in the stored locale without a redirect", async () => {
      const response = await handler(withCookie("/settings", "OBA_LOCALE=nl"));

      expect(response.headers.get("location")).toBeNull();
      expect(rewriteOf(response)).toBe("/nl/settings");
      expect(localeHeaderOf(response)).toBe("nl");
    });

    // The `/` <-> `/<locale>` loop: next-intl rewrites `/` to `/en`, and when
    // that rewrite target reaches the proxy again it must not be redirected
    // back to `/`.
    test("serves /<locale>/ through when its locale is the cookie's, so `/` cannot loop", async () => {
      for (const path of ["/en", "/en/settings"]) {
        const response = await handler(withCookie(path, "OBA_LOCALE=en"));

        expect(response.headers.get("location")).toBeNull();
        expect(response.headers.get("set-cookie")).toBeNull();
        expect(response.headers.get("x-middleware-next")).toBe("1");
        expect(localeHeaderOf(response)).toBe("en");
      }
    });

    test("serves /<locale>/ through for a client that sends no cookie and no Accept-Language", async () => {
      const request = new NextRequest(new URL("/en", "https://account.korur.com"));
      const response = await handler(
        Object.assign(request, { auth: SIGNED_IN }) as unknown as NextAuthRequest,
      );

      expect(response.headers.get("location")).toBeNull();
      expect(localeHeaderOf(response)).toBe("en");
    });

    test("serves /<locale>/ through for a visitor without a cookie whose browser asks for it", async () => {
      const response = await handler(withCookie("/nl/settings", "", "nl"));

      expect(response.headers.get("location")).toBeNull();
      expect(localeHeaderOf(response)).toBe("nl");
    });

    test("serves /<locale>/ through when an unknown cookie value falls back to the same locale", async () => {
      const response = await handler(withCookie("/en/settings", "OBA_LOCALE=de", "en"));

      expect(response.headers.get("location")).toBeNull();
      expect(localeHeaderOf(response)).toBe("en");
    });

    test("serves a typed /nl/ path in Dutch, not in the default locale", async () => {
      const response = await handler(withCookie("/nl/settings", "OBA_LOCALE=nl"));

      expect(localeHeaderOf(response)).toBe("nl");
    });

    test("does not mistake /enabled for the en locale", async () => {
      const response = await handler(withCookie("/enabled", "OBA_LOCALE=nl"));

      expect(rewriteOf(response)).toBe("/nl/enabled");
    });
  });

  describe("locale prefix before the session checks", () => {
    test("sends a signed-out visitor of /nl/settings to the sign-in, not to /settings", async () => {
      const response = await handler(requestFor("/nl/settings", null));

      const location = new URL(response.headers.get("location") ?? "");
      expect(location.pathname).toBe("/api/auth/login");
      expect(response.headers.get("set-cookie")).toBeNull();
    });

    test("lets a signed-out visitor reach the public /nl/onboarding", async () => {
      const request = new NextRequest(new URL("/nl/onboarding", "https://account.korur.com"), {
        headers: { cookie: "OBA_LOCALE=en", "accept-language": "en" },
      });
      const response = await handler(
        Object.assign(request, { auth: null }) as unknown as NextAuthRequest,
      );

      expect(new URL(response.headers.get("location") ?? "").pathname).toBe("/onboarding");
      expect(response.headers.get("set-cookie")).toContain("OBA_LOCALE=nl");
    });

    test("sends a user with a pending invitation on /nl/settings to the invitations page", async () => {
      const response = await handler(
        requestFor("/nl/settings", { ...SIGNED_IN, pendingInvitationIds: ["inv-1"] }),
      );

      expect(new URL(response.headers.get("location") ?? "").pathname).toBe("/invitations");
    });

    test("does not redirect a user with a pending invitation on /en/invitations away from it", async () => {
      const response = await handler(
        requestFor("/en/invitations", { ...SIGNED_IN, pendingInvitationIds: ["inv-1"] }),
      );

      expect(response.headers.get("location")).toBeNull();
    });
  });
});

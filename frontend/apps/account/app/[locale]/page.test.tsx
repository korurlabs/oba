import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { AuthError } from "@oba/shared/libs/auth/types";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderToStaticMarkup } from "react-dom/server";

// The root page is the profile and the sign-in gateway: it turns the
// session and the `callbackUrl` the proxy preserved into a redirect. Each
// redirect records its target and throws, as the real ones do, so the test reads
// where the page sent the user.
type SessionState = {
  user?: { id: string };
  error?: string;
  userStatus?: string;
  accessToken?: string;
} | null;

class Redirected extends Error {
  constructor(
    readonly kind: "plain" | "localized",
    readonly target: unknown,
  ) {
    super("redirected");
  }
}

let session: SessionState;
let signOutCalls: unknown[];
let prefetched: number;

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualAuth = { ...(await import("@oba/shared/libs/auth")) };
const actualNavigation = { ...(await import("next/navigation")) };
const actualI18nNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualPrefetch = { ...(await import("@oba/shared/features/users/api/prefetch-me")) };
const actualServerIntl = { ...(await import("next-intl/server")) };
const actualShell = { ...(await import("@/components/layout/account-shell")) };
const actualProfile = { ...(await import("@/features/profile/components/profile-view")) };

mock.module("@oba/shared/libs/auth", () => ({
  ...actualAuth,
  auth: async () => session,
  signOut: async (options: unknown) => {
    signOutCalls.push(options);
  },
}));
mock.module("next/navigation", () => ({
  ...actualNavigation,
  redirect: (target: string) => {
    throw new Redirected("plain", target);
  },
}));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualI18nNavigation,
  redirect: (target: unknown) => {
    throw new Redirected("localized", target);
  },
}));
mock.module("@oba/shared/features/users/api/prefetch-me", () => ({
  ...actualPrefetch,
  prefetchMe: async () => {
    prefetched += 1;
  },
}));
mock.module("next-intl/server", () => ({
  ...actualServerIntl,
  getTranslations: async () => (key: string) => key,
}));
mock.module("@/components/layout/account-shell", () => ({
  ...actualShell,
  AccountShell: ({ children }: { children: React.ReactNode }) => <main>{children}</main>,
}));
mock.module("@/features/profile/components/profile-view", () => ({
  ...actualProfile,
  ProfileView: () => <p>profile-view</p>,
}));

afterAll(() => {
  mock.module("@oba/shared/libs/auth", () => actualAuth);
  mock.module("next/navigation", () => actualNavigation);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualI18nNavigation);
  mock.module("@oba/shared/features/users/api/prefetch-me", () => actualPrefetch);
  mock.module("next-intl/server", () => actualServerIntl);
  mock.module("@/components/layout/account-shell", () => actualShell);
  mock.module("@/features/profile/components/profile-view", () => actualProfile);
});

const { default: RootPage } = await import("./page");

const ACTIVE: SessionState = { user: { id: "u1" }, userStatus: "ACTIVE", accessToken: "token" };

async function visit(searchParams: { error?: string; callbackUrl?: string } = {}) {
  return RootPage({
    params: Promise.resolve({ locale: "en" }),
    searchParams: Promise.resolve(searchParams),
  });
}

/** The page renders a HydrationBoundary, which needs a query client above it. */
function markupOf(page: unknown): string {
  return renderToStaticMarkup(
    <QueryClientProvider client={new QueryClient()}>{page as React.ReactElement}</QueryClientProvider>,
  );
}

async function redirectOf(searchParams: { error?: string; callbackUrl?: string } = {}): Promise<Redirected> {
  try {
    await visit(searchParams);
  } catch (error) {
    if (error instanceof Redirected) return error;
    throw error;
  }
  throw new Error("expected a redirect");
}

beforeEach(() => {
  session = ACTIVE;
  signOutCalls = [];
  prefetched = 0;
});

describe("root page redirects", () => {
  test("sends a visitor without a session to the sign-in, keeping a safe callbackUrl", async () => {
    session = null;

    const redirected = await redirectOf({ callbackUrl: "/settings?tab=language" });

    expect(redirected.kind).toBe("plain");
    expect(redirected.target).toBe(
      `/api/auth/login?callbackUrl=${encodeURIComponent("/settings?tab=language")}`,
    );
  });

  test("sends a visitor without a session to the plain sign-in when there is no callbackUrl", async () => {
    session = null;

    expect((await redirectOf()).target).toBe("/api/auth/login");
  });

  test("drops an unsafe callbackUrl instead of carrying it through the sign-in", async () => {
    session = null;

    expect((await redirectOf({ callbackUrl: "https://evil.example/steal" })).target).toBe("/api/auth/login");
    expect((await redirectOf({ callbackUrl: "//evil.example" })).target).toBe("/api/auth/login");
  });

  test("sends a session with a refresh error to the sign-in", async () => {
    session = { ...ACTIVE, error: AuthError.REFRESH_TOKEN_ERROR };

    const redirected = await redirectOf({ callbackUrl: "https://kazan.korur.com/home" });

    expect(redirected.target).toBe(
      `/api/auth/login?callbackUrl=${encodeURIComponent("https://kazan.korur.com/home")}`,
    );
  });

  test("sends the refresh error the proxy forwards to the sign-in without reading the session", async () => {
    session = ACTIVE;

    const redirected = await redirectOf({ error: AuthError.REFRESH_TOKEN_ERROR, callbackUrl: "/settings" });

    expect(redirected.kind).toBe("plain");
    expect(redirected.target).toBe(`/api/auth/login?callbackUrl=${encodeURIComponent("/settings")}`);
  });

  test("sends a user who has to onboard to /onboarding, keeping the callbackUrl", async () => {
    session = { ...ACTIVE, userStatus: "PENDING_ONBOARDING" };

    const redirected = await redirectOf({ callbackUrl: "https://kazan.korur.com/home" });

    expect(redirected.kind).toBe("localized");
    expect(redirected.target).toEqual({
      href: { pathname: "/onboarding", query: { callbackUrl: "https://kazan.korur.com/home" } },
      locale: "en",
    });
  });

  test("sends a user who has to onboard to /onboarding without a query when there is no callbackUrl", async () => {
    session = { ...ACTIVE, userStatus: "PENDING_ONBOARDING" };

    const redirected = await redirectOf();

    expect(redirected.kind).toBe("localized");
    expect(redirected.target).toEqual({ href: { pathname: "/onboarding", query: {} }, locale: "en" });
  });

  test("sends the onboarding-required error the proxy forwards to /onboarding", async () => {
    const redirected = await redirectOf({
      error: AuthError.ONBOARDING_REQUIRED,
      callbackUrl: "/settings",
    });

    expect(redirected.kind).toBe("localized");
    expect(redirected.target).toEqual({
      href: { pathname: "/onboarding", query: { callbackUrl: "/settings" } },
      locale: "en",
    });
  });

  test("sends an active user to the safe callbackUrl", async () => {
    const redirected = await redirectOf({ callbackUrl: "https://kazan.korur.com/home" });

    expect(redirected.kind).toBe("plain");
    expect(redirected.target).toBe("https://kazan.korur.com/home");
  });

  test("drops a failed sign-in's error from the URL of an active user", async () => {
    const redirected = await redirectOf({ error: "Configuration" });

    expect(redirected.kind).toBe("localized");
    expect(redirected.target).toEqual({ href: { pathname: "/", query: {} }, locale: "en" });
    expect(prefetched).toBe(0);
  });

  test("sends an active user with a failed sign-in's error to the safe callbackUrl", async () => {
    const redirected = await redirectOf({ error: "Configuration", callbackUrl: "/settings" });

    expect(redirected.kind).toBe("plain");
    expect(redirected.target).toBe("/settings");
  });

  test("ignores an unsafe callbackUrl for an active user and shows the list", async () => {
    const page = await visit({ callbackUrl: "https://evil.example/" });

    expect(markupOf(page)).toContain("profile-view");
  });
});

describe("root page content", () => {
  test("shows the profile to an active user and prefetches the current user", async () => {
    const page = await visit();

    expect(markupOf(page)).toContain("profile-view");
    expect(prefetched).toBe(1);
  });

  test("signs a deleted user out and renders nothing", async () => {
    session = { ...ACTIVE, userStatus: "DELETED" };

    const page = await visit();

    expect(page).toBeNull();
    expect(signOutCalls).toEqual([{ redirectTo: "/" }]);
    expect(prefetched).toBe(0);
  });
});

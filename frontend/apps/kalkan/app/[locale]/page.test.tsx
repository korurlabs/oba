import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { AuthError } from "@oba/shared/libs/auth/types";
import { accountOnboardingUrl, appUrl } from "@oba/shared/constants/apps";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderToStaticMarkup } from "react-dom/server";
import { OVERVIEW_RECENT_LIMIT } from "@/features/recent/lib/recent-limits";

// The root page is the home page and the sign-in gateway: it turns the
// session and the `callbackUrl` the proxy preserved into a redirect. Each
// redirect records its target and throws, as the real ones do, so the test reads
// where the page sent the user.
type SessionState = {
  user?: { id: string; defaultOrganizationId?: string | null };
  error?: string;
  userStatus?: string;
  accessToken?: string;
} | null;

class Redirected extends Error {
  constructor(
    readonly kind: "plain" | "localized",
    readonly target: unknown
  ) {
    super("redirected");
  }
}

let session: SessionState;
let signOutCalls: unknown[];
let prefetched: number;
let prefetchedTrees: string[];
let prefetchedRecent: unknown[][];

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualAuth = { ...(await import("@oba/shared/libs/auth")) };
const actualNavigation = { ...(await import("next/navigation")) };
const actualI18nNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualPrefetch = { ...(await import("@oba/shared/features/users/api/prefetch-me")) };
const actualServerIntl = { ...(await import("next-intl/server")) };
const actualShell = { ...(await import("@/components/layout/kalkan-app-shell")) };
const actualPagePrefetch = { ...(await import("@/features/pages/lib/prefetch")) };

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
mock.module("@/components/layout/kalkan-app-shell", () => ({
  ...actualShell,
  KalkanAppShell: ({ organizationId }: { organizationId: string | null }) => (
    <main>kalkan-shell:{organizationId ?? "none"}</main>
  ),
}));
mock.module("@/features/pages/lib/prefetch", () => ({
  ...actualPagePrefetch,
  prefetchPageTree: async (_queryClient: unknown, organizationId: string) => {
    prefetchedTrees.push(organizationId);
  },
  prefetchRecentPages: async (
    _queryClient: unknown,
    organizationId: string,
    limit: number,
    uiLocale: string
  ) => {
    prefetchedRecent.push([organizationId, limit, uiLocale]);
  },
}));

afterAll(() => {
  mock.module("@oba/shared/libs/auth", () => actualAuth);
  mock.module("next/navigation", () => actualNavigation);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualI18nNavigation);
  mock.module("@oba/shared/features/users/api/prefetch-me", () => actualPrefetch);
  mock.module("next-intl/server", () => actualServerIntl);
  mock.module("@/components/layout/kalkan-app-shell", () => actualShell);
  mock.module("@/features/pages/lib/prefetch", () => actualPagePrefetch);
});

const { default: RootPage } = await import("./page");

const ACTIVE: SessionState = {
  user: { id: "u1", defaultOrganizationId: "org-1" },
  userStatus: "ACTIVE",
  accessToken: "token",
};

async function visit(searchParams: { error?: string; callbackUrl?: string } = {}) {
  return RootPage({
    params: Promise.resolve({ locale: "en" }),
    searchParams: Promise.resolve(searchParams),
  });
}

/** The page renders a HydrationBoundary, which needs a query client above it. */
function markupOf(page: unknown): string {
  return renderToStaticMarkup(
    <QueryClientProvider client={new QueryClient()}>
      {page as React.ReactElement}
    </QueryClientProvider>
  );
}

async function redirectOf(
  searchParams: { error?: string; callbackUrl?: string } = {}
): Promise<Redirected> {
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
  prefetchedTrees = [];
  prefetchedRecent = [];
});

describe("root page redirects", () => {
  test("sends a visitor without a session to the sign-in, keeping a safe callbackUrl", async () => {
    session = null;

    const redirected = await redirectOf({ callbackUrl: "/isms/A.5.1?mode=edit" });

    expect(redirected.kind).toBe("plain");
    expect(redirected.target).toBe(
      `/api/auth/login?callbackUrl=${encodeURIComponent("/isms/A.5.1?mode=edit")}`
    );
  });

  test("sends a visitor without a session to the plain sign-in when there is no callbackUrl", async () => {
    session = null;

    expect((await redirectOf()).target).toBe("/api/auth/login");
  });

  test("drops an unsafe callbackUrl instead of carrying it through the sign-in", async () => {
    session = null;

    expect((await redirectOf({ callbackUrl: "https://evil.example/steal" })).target).toBe(
      "/api/auth/login"
    );
    expect((await redirectOf({ callbackUrl: "//evil.example" })).target).toBe("/api/auth/login");
  });

  test("sends a session with a refresh error to the sign-in", async () => {
    session = { ...ACTIVE, error: AuthError.REFRESH_TOKEN_ERROR };

    const redirected = await redirectOf({ callbackUrl: "https://kazan.korur.com/home" });

    expect(redirected.target).toBe(
      `/api/auth/login?callbackUrl=${encodeURIComponent("https://kazan.korur.com/home")}`
    );
  });

  test("sends the refresh error the proxy forwards to the sign-in without reading the session", async () => {
    session = ACTIVE;

    const redirected = await redirectOf({
      error: AuthError.REFRESH_TOKEN_ERROR,
      callbackUrl: "/isms/A.5.1",
    });

    expect(redirected.kind).toBe("plain");
    expect(redirected.target).toBe(
      `/api/auth/login?callbackUrl=${encodeURIComponent("/isms/A.5.1")}`
    );
  });

  test("sends a user who has to onboard to account onboarding, keeping the callbackUrl", async () => {
    session = { ...ACTIVE, userStatus: "PENDING_ONBOARDING" };

    const redirected = await redirectOf({ callbackUrl: "https://kazan.korur.com/home" });

    expect(redirected.kind).toBe("plain");
    expect(redirected.target).toBe(
      accountOnboardingUrl("https://kazan.korur.com/home", appUrl("kalkan"))
    );
  });

  test("sends a user who has to onboard to account onboarding with the home page as callbackUrl when there is none", async () => {
    session = { ...ACTIVE, userStatus: "PENDING_ONBOARDING" };

    const redirected = await redirectOf();

    expect(redirected.kind).toBe("plain");
    expect(redirected.target).toBe(accountOnboardingUrl("/", appUrl("kalkan")));
  });

  test("sends the onboarding-required error the proxy forwards to account onboarding", async () => {
    const redirected = await redirectOf({
      error: AuthError.ONBOARDING_REQUIRED,
      callbackUrl: "/isms/A.5.1",
    });

    expect(redirected.kind).toBe("plain");
    expect(redirected.target).toBe(accountOnboardingUrl("/isms/A.5.1", appUrl("kalkan")));
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
    const redirected = await redirectOf({ error: "Configuration", callbackUrl: "/isms/A.5.1" });

    expect(redirected.kind).toBe("plain");
    expect(redirected.target).toBe("/isms/A.5.1");
  });

  test("ignores an unsafe callbackUrl for an active user and shows the home page", async () => {
    const page = await visit({ callbackUrl: "https://evil.example/" });

    expect(markupOf(page)).toContain("kalkan-shell");
  });
});

describe("root page content", () => {
  test("shows the shell to an active user with the session's organization and prefetches the current user, the page tree and the recent changes", async () => {
    const page = await visit();

    expect(markupOf(page)).toContain("kalkan-shell:org-1");
    expect(prefetched).toBe(1);
    expect(prefetchedTrees).toEqual(["org-1"]);
    expect(prefetchedRecent).toEqual([["org-1", OVERVIEW_RECENT_LIMIT, "en"]]);
  });

  test("hands the shell no organization when the session has none", async () => {
    session = { ...ACTIVE, user: { id: "u1", defaultOrganizationId: null } };

    const page = await visit();

    expect(markupOf(page)).toContain("kalkan-shell:none");
    expect(prefetchedTrees).toEqual([]);
    expect(prefetchedRecent).toEqual([]);
  });

  test("signs a deleted user out and renders nothing", async () => {
    session = { ...ACTIVE, userStatus: "DELETED" };

    const page = await visit();

    expect(page).toBeNull();
    expect(signOutCalls).toEqual([{ redirectTo: "/" }]);
    expect(prefetched).toBe(0);
  });
});

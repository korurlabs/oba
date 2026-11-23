import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { AuthError } from "@oba/shared/libs/auth/types";
import { accountOnboardingUrl, appUrl } from "@oba/shared/constants/apps";
import { USERS_KEYS } from "@oba/shared/features/users/hooks/use-get-me";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderToStaticMarkup } from "react-dom/server";

// The dashboard layout guards every Ulak page but the list: it turns the
// session and the prefetched current user into a redirect. Each redirect records
// its target and throws, as the real ones do, so the test reads where it went.
type SessionState = { user?: { id: string }; error?: string; userStatus?: string; accessToken?: string } | null;
type Membership = { id: string; name: string; role: string; organizationStatus: string };

class Redirected extends Error {
  constructor(
    readonly kind: "plain" | "localized" | "auth-app",
    readonly target: unknown,
  ) {
    super("redirected");
  }
}

let session: SessionState;
let prefetched: number;
// The organizations the prefetched current user holds; undefined means the prefetch failed.
let prefetchedMe: Membership[] | undefined;

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualAuth = { ...(await import("@oba/shared/libs/auth")) };
const actualNavigation = { ...(await import("next/navigation")) };
const actualI18nNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualPrefetch = { ...(await import("@oba/shared/features/users/api/prefetch-me")) };
const actualShell = { ...(await import("@/components/layout/ulak-shell")) };

mock.module("@oba/shared/libs/auth", () => ({
  ...actualAuth,
  auth: async () => session,
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
  redirectToAuthApp: (locale: string, route: string) => {
    throw new Redirected("auth-app", { locale, route });
  },
}));
mock.module("@oba/shared/features/users/api/prefetch-me", () => ({
  ...actualPrefetch,
  // Like the real one, it fills the query cache the layout reads the user from.
  prefetchMe: async (queryClient: QueryClient) => {
    prefetched += 1;
    if (prefetchedMe !== undefined) {
      queryClient.setQueryData(USERS_KEYS.me, {
        success: true,
        message: "",
        data: { organizations: prefetchedMe },
      });
    }
  },
}));
mock.module("@/components/layout/ulak-shell", () => ({
  ...actualShell,
  UlakShell: ({ children }: { children: React.ReactNode }) => <main>{children}</main>,
}));

afterAll(() => {
  mock.module("@oba/shared/libs/auth", () => actualAuth);
  mock.module("next/navigation", () => actualNavigation);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualI18nNavigation);
  mock.module("@oba/shared/features/users/api/prefetch-me", () => actualPrefetch);
  mock.module("@/components/layout/ulak-shell", () => actualShell);
});

const { default: DashboardLayout } = await import("./layout");

const ACTIVE: SessionState = { user: { id: "u1" }, userStatus: "ACTIVE", accessToken: "token" };

async function visit() {
  return DashboardLayout({
    children: <p>the page</p>,
    params: Promise.resolve({ locale: "en" }),
  });
}

async function redirectOf(): Promise<Redirected> {
  try {
    await visit();
  } catch (error) {
    if (error instanceof Redirected) return error;
    throw error;
  }
  throw new Error("expected a redirect");
}

beforeEach(() => {
  session = ACTIVE;
  prefetched = 0;
  prefetchedMe = [{ id: "org-1", name: "Korur", role: "OWNER", organizationStatus: "ACTIVE" }];
});

describe("dashboard layout", () => {
  test("renders the page in the shell for an ACTIVE OWNER", async () => {
    const html = renderToStaticMarkup(
      <QueryClientProvider client={new QueryClient()}>{(await visit()) as React.ReactElement}</QueryClientProvider>,
    );

    expect(html).toContain("the page");
    expect(prefetched).toBe(1);
  });

  test("redirects a member-only user to /no-access", async () => {
    prefetchedMe = [{ id: "org-1", name: "Korur", role: "MEMBER", organizationStatus: "ACTIVE" }];

    const redirected = await redirectOf();

    expect(redirected.kind).toBe("localized");
    expect(redirected.target).toEqual({ href: "/no-access", locale: "en" });
  });

  test("redirects an owner whose only organization is DISABLED to /no-access", async () => {
    prefetchedMe = [{ id: "org-1", name: "Korur", role: "OWNER", organizationStatus: "DISABLED" }];

    expect((await redirectOf()).target).toEqual({ href: "/no-access", locale: "en" });
  });

  test("renders the page when the prefetch failed, so the client gate decides", async () => {
    prefetchedMe = undefined;

    const html = renderToStaticMarkup(
      <QueryClientProvider client={new QueryClient()}>{(await visit()) as React.ReactElement}</QueryClientProvider>,
    );

    expect(html).toContain("the page");
  });

  test("sends a visitor without a session to the sign-in of the auth app", async () => {
    session = null;

    const redirected = await redirectOf();

    expect(redirected.kind).toBe("auth-app");
    expect(redirected.target).toEqual({ locale: "en", route: "login" });
    expect(prefetched).toBe(0);
  });

  test("sends a session with a refresh error to the sign-in", async () => {
    session = { ...ACTIVE, error: AuthError.REFRESH_TOKEN_ERROR };

    expect((await redirectOf()).kind).toBe("auth-app");
  });

  test("sends a user who has to onboard to account onboarding", async () => {
    session = { ...ACTIVE, userStatus: "PENDING_ONBOARDING" };

    const redirected = await redirectOf();

    expect(redirected.kind).toBe("plain");
    expect(redirected.target).toBe(accountOnboardingUrl("/", appUrl("ulak")));
    expect(prefetched).toBe(0);
  });
});

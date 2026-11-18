import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { AuthError } from "@oba/shared/libs/auth/types";
import { ACCOUNT_LINKS, accountOnboardingUrl, appUrl } from "@oba/shared/constants/apps";
import { USERS_KEYS } from "@oba/shared/features/users/hooks/use-get-me";
import type { QueryClient } from "@tanstack/react-query";
import { renderToStaticMarkup } from "react-dom/server";

// The page is where Admin sends a user who manages no organization. A manager
// who opens it goes to the list. Each redirect records its target and throws, as
// the real ones do, so the test reads where the page sent the user.
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
const actualServerIntl = { ...(await import("next-intl/server")) };

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
  // Like the real one, it fills the query cache the page reads the user from.
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
mock.module("next-intl/server", () => ({
  ...actualServerIntl,
  getTranslations: async () => (key: string) => key,
}));

afterAll(() => {
  mock.module("@oba/shared/libs/auth", () => actualAuth);
  mock.module("next/navigation", () => actualNavigation);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualI18nNavigation);
  mock.module("@oba/shared/features/users/api/prefetch-me", () => actualPrefetch);
  mock.module("next-intl/server", () => actualServerIntl);
});

const { default: NoAccessPage } = await import("./page");

const ACTIVE: SessionState = { user: { id: "u1" }, userStatus: "ACTIVE", accessToken: "token" };

async function visit() {
  return NoAccessPage({ params: Promise.resolve({ locale: "en" }) });
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
  prefetchedMe = [{ id: "org-1", name: "Korur", role: "MEMBER", organizationStatus: "ACTIVE" }];
});

describe("no-access page", () => {
  test("redirects a manager to the organization list", async () => {
    prefetchedMe = [{ id: "org-1", name: "Korur", role: "ADMIN", organizationStatus: "ACTIVE" }];

    const redirected = await redirectOf();

    expect(redirected.kind).toBe("localized");
    expect(redirected.target).toEqual({ href: "/", locale: "en" });
  });

  test("shows the refusal with the account links to a member-only user", async () => {
    const html = renderToStaticMarkup((await visit()) as React.ReactElement);

    expect(html).toContain("<h1");
    expect(html).toContain("title");
    expect(html).toContain("description");
    expect(html).toContain(`href="${ACCOUNT_LINKS.profile}"`);
    expect(html).toContain(`href="${ACCOUNT_LINKS.newOrganization}"`);
  });

  test("shows the refusal to an owner whose only organization is DISABLED", async () => {
    prefetchedMe = [{ id: "org-1", name: "Korur", role: "OWNER", organizationStatus: "DISABLED" }];

    expect(renderToStaticMarkup((await visit()) as React.ReactElement)).toContain("<h1");
  });

  test("shows the refusal when the user could not be loaded", async () => {
    prefetchedMe = undefined;

    expect(renderToStaticMarkup((await visit()) as React.ReactElement)).toContain("<h1");
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
    expect(redirected.target).toBe(accountOnboardingUrl("/", appUrl("admin")));
    expect(prefetched).toBe(0);
  });
});

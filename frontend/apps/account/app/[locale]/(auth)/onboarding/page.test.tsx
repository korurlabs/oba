import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderToStaticMarkup } from "react-dom/server";

import { AuthError } from "@oba/shared/libs/auth/types";

// The onboarding page gates on the session on the server and carries the
// `callbackUrl` through each redirect. A redirect records its target and throws,
// as the real ones do.
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
let prefetched: number;

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualAuth = { ...(await import("@oba/shared/libs/auth")) };
const actualNavigation = { ...(await import("next/navigation")) };
const actualI18nNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualPrefetch = { ...(await import("@oba/shared/features/users/api/prefetch-me")) };
const actualStepper = { ...(await import("@/features/onboarding/components/onboarding-stepper")) };
const actualSkeleton = { ...(await import("@/features/onboarding/components/onboarding-skeleton")) };

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
}));
mock.module("@oba/shared/features/users/api/prefetch-me", () => ({
  ...actualPrefetch,
  prefetchMe: async () => {
    prefetched += 1;
  },
}));
mock.module("@/features/onboarding/components/onboarding-stepper", () => ({
  ...actualStepper,
  OnboardingStepper: () => <p>onboarding-stepper</p>,
}));
mock.module("@/features/onboarding/components/onboarding-skeleton", () => ({
  ...actualSkeleton,
  OnboardingSkeleton: () => <p>onboarding-skeleton</p>,
}));

afterAll(() => {
  mock.module("@oba/shared/libs/auth", () => actualAuth);
  mock.module("next/navigation", () => actualNavigation);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualI18nNavigation);
  mock.module("@oba/shared/features/users/api/prefetch-me", () => actualPrefetch);
  mock.module("@/features/onboarding/components/onboarding-stepper", () => actualStepper);
  mock.module("@/features/onboarding/components/onboarding-skeleton", () => actualSkeleton);
});

const { default: OnboardingPage } = await import("./page");

const PENDING: SessionState = { user: { id: "u1" }, userStatus: "PENDING_ONBOARDING", accessToken: "token" };

async function visit(callbackUrl?: string) {
  return OnboardingPage({
    params: Promise.resolve({ locale: "en" }),
    searchParams: Promise.resolve({ callbackUrl }),
  });
}

async function redirectOf(callbackUrl?: string): Promise<Redirected> {
  try {
    await visit(callbackUrl);
  } catch (error) {
    if (error instanceof Redirected) return error;
    throw error;
  }
  throw new Error("expected a redirect");
}

beforeEach(() => {
  session = PENDING;
  prefetched = 0;
});

describe("onboarding page", () => {
  test("sends a visitor without a session to the sign-in, back to onboarding's callbackUrl", async () => {
    session = null;

    const redirected = await redirectOf("https://kazan.korur.com/home");

    expect(redirected.kind).toBe("plain");
    expect(redirected.target).toBe(
      `/api/auth/login?callbackUrl=${encodeURIComponent("https://kazan.korur.com/home")}`,
    );
  });

  test("returns to the root after sign-in when there is no callbackUrl", async () => {
    session = null;

    expect((await redirectOf()).target).toBe(`/api/auth/login?callbackUrl=${encodeURIComponent("/")}`);
  });

  test("does not carry an unsafe callbackUrl through the sign-in", async () => {
    session = null;

    expect((await redirectOf("https://evil.example/steal")).target).toBe(
      `/api/auth/login?callbackUrl=${encodeURIComponent("/")}`,
    );
  });

  test("sends a session with a refresh error to the sign-in", async () => {
    session = { ...PENDING, error: AuthError.REFRESH_TOKEN_ERROR };

    expect((await redirectOf("/invitations")).target).toBe(
      `/api/auth/login?callbackUrl=${encodeURIComponent("/invitations")}`,
    );
  });

  test("sends an already onboarded user to the safe callbackUrl", async () => {
    session = { ...PENDING, userStatus: "ACTIVE" };

    const redirected = await redirectOf("https://kazan.korur.com/home");

    expect(redirected.kind).toBe("plain");
    expect(redirected.target).toBe("https://kazan.korur.com/home");
  });

  test("sends an already onboarded user to the organizations when there is no safe callbackUrl", async () => {
    session = { ...PENDING, userStatus: "ACTIVE" };

    const redirected = await redirectOf("//evil.example");

    expect(redirected.kind).toBe("localized");
    expect(redirected.target).toEqual({ href: { pathname: "/", query: {} }, locale: "en" });
  });

  test("shows the stepper to a user who still has to onboard and prefetches the current user", async () => {
    const page = await visit("/invitations");

    const html = renderToStaticMarkup(
      <QueryClientProvider client={new QueryClient()}>{page as React.ReactElement}</QueryClientProvider>,
    );
    expect(html).toContain("onboarding-stepper");
    expect(prefetched).toBe(1);
  });
});

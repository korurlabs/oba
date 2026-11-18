import { redirect as nextRedirect } from "next/navigation";
import { createNavigation } from "next-intl/navigation";
import { routing } from "./routing";
import { Locale } from "./available-languages";
import { getRuntimeConfig } from "../../lib/runtime-config";

// Lightweight wrappers around Next.js' navigation
// APIs that consider the routing configuration
export const { Link, redirect, usePathname, useRouter, getPathname, permanentRedirect } =
  createNavigation(routing);

/**
 * Routes an unauthenticated/onboarding-required user away from a page of the
 * current app. Both destinations are local to that app; the name is kept from
 * the time a separate auth app hosted them.
 *
 * The destination is split by purpose:
 * - login: the app's local /api/auth/login route, which initiates the Keycloak
 *   OAuth flow directly. This must NOT be locale-prefixed — /en/api/auth/login
 *   would 404 — so we use the plain next/navigation redirect, which never adds
 *   a locale segment.
 * - onboarding: the app's own /onboarding page, reached through the next-intl
 *   redirect. No callbackUrl is carried; a caller that has one to preserve
 *   builds the redirect itself (see the app's locale root page).
 */
export const redirectToAuthApp = (locale: Locale, path?: "login" | "onboarding"): never => {
  if (path !== "onboarding") {
    // Login: stay on this app, hand off to the local direct-Keycloak route.
    const callbackUrl = getRuntimeConfig().baseUrl;
    const loginUrl = callbackUrl
      ? `/api/auth/login?callbackUrl=${encodeURIComponent(callbackUrl)}`
      : "/api/auth/login";
    return nextRedirect(loginUrl);
  }

  // Onboarding: a route of this app.
  redirect({ href: "/onboarding", locale });
  throw new Error("redirect should not return");
};

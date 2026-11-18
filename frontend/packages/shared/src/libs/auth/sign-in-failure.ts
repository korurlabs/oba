import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { sanitizeCallbackUrl } from "./callback-url";
import {
  callbackUrlCookieName,
  isSecureAuthUrl,
  SIGN_IN_RETRY_WINDOW_SECONDS,
  signInRetryCookieName,
  signInUrl,
} from "./sign-in-retry";

/**
 * Handles a failed sign-in on a root page that has no usable session.
 *
 * Without the retry mark it redirects to one automatic sign-in that sets the
 * mark, back to the deep link. With the mark it returns the URL of a
 * user-started sign-in, which the page shows as "Sign in again" instead of
 * redirecting, so a persistent failure ends on that page rather than looping
 * through Keycloak. The user-started sign-in leaves the mark in place: when it
 * fails as well, the page shows the failure again at once.
 *
 * The deep link is `callbackUrl` when the page got one, otherwise the
 * destination Auth.js kept in its callback URL cookie for the failed sign-in.
 */
export async function retrySignInOnce(callbackUrl?: string): Promise<string> {
  const cookieStore = await cookies();
  const authUrl = process.env.AUTH_URL;
  const deepLink = callbackUrl ?? sanitizeCallbackUrl(cookieStore.get(callbackUrlCookieName(authUrl))?.value);
  if (!cookieStore.has(signInRetryCookieName(authUrl))) {
    redirect(signInUrl(deepLink, true));
  }
  return signInUrl(deepLink);
}

/**
 * Sets the retry mark for the automatic sign-in that is about to start. Only
 * a route handler can call it: a page cannot set cookies.
 */
export async function markSignInRetry(): Promise<void> {
  const authUrl = process.env.AUTH_URL;
  (await cookies()).set(signInRetryCookieName(authUrl), "1", {
    httpOnly: true,
    sameSite: "lax",
    secure: isSecureAuthUrl(authUrl),
    path: "/",
    maxAge: SIGN_IN_RETRY_WINDOW_SECONDS,
  });
}

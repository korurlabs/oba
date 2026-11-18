/**
 * Names and URLs of the guarded sign-in retry, derived from AUTH_URL.
 *
 * Auth.js sends a failed OAuth callback to `pages.error` (the root page) with
 * only `?error=<code>`. A root page that simply started a new sign-in would
 * loop forever on a persistent failure while the Keycloak SSO session is
 * alive: login -> Keycloak -> callback -> `/?error=` -> login. The root page
 * therefore starts one automatic sign-in, marked by a short-lived cookie that
 * the login route sets, and shows "Signing in failed" when the next failure
 * arrives while the cookie is still there. One retry heals a transient failure
 * such as two tabs racing for the PKCE verifier.
 *
 * Kept free of `next/headers`, like `./session-cookie`, so it can be unit tested.
 */

import { authCookieOverrides } from "./session-cookie";

/** Query parameter that asks the login route to mark its sign-in as the retry. */
export const SIGN_IN_RETRY_PARAM = "retry";

/**
 * Lifetime of the retry mark. It covers a silent Keycloak round trip with the
 * SSO session alive; a failure later than that earns one more automatic retry,
 * which still cannot loop.
 */
export const SIGN_IN_RETRY_WINDOW_SECONDS = 60;

const LOGIN_ROUTE = "/api/auth/login";
const LOCAL_COOKIE_PREFIX = "oba";
const HTTP_DEFAULT_PORT = "80";
const RETRY_COOKIE_SUFFIX = "signin-retry";
const SECURE_RETRY_COOKIE_NAME = `__Secure-${LOCAL_COOKIE_PREFIX}.${RETRY_COOKIE_SUFFIX}`;
const FALLBACK_RETRY_COOKIE_NAME = `${LOCAL_COOKIE_PREFIX}.${RETRY_COOKIE_SUFFIX}`;
/** Auth.js's own callback URL cookie names, used where `./session-cookie` keeps the default. */
const SECURE_CALLBACK_URL_COOKIE_NAME = "__Secure-authjs.callback-url";
const DEFAULT_CALLBACK_URL_COOKIE_NAME = "authjs.callback-url";

/** Auth.js error codes are identifiers; anything else in `?error=` is not shown. */
const ERROR_CODE_PATTERN = /^[A-Za-z][A-Za-z0-9_]{0,63}$/;

/**
 * Parses AUTH_URL; a missing or malformed value yields undefined. It is not
 * logged here because `./session-cookie` already reports a malformed value
 * once per process and Auth.js rejects it on the first auth request.
 */
function parseAuthUrl(authUrl?: string): URL | undefined {
  if (!authUrl || !URL.canParse(authUrl)) return undefined;
  return new URL(authUrl);
}

/**
 * Name of the retry mark.
 *
 *  - https AUTH_URL -> "__Secure-oba.signin-retry", host-only, so each app on
 *    its own subdomain keeps its own mark
 *  - http AUTH_URL  -> "oba-<port>.signin-retry", next to the per-app
 *    handshake cookies, because browsers share one cookie jar across all
 *    localhost ports
 *  - missing        -> "oba.signin-retry"
 *
 * No name starts with the session cookie name, which Auth.js joins by prefix.
 */
export function signInRetryCookieName(authUrl?: string): string {
  const url = parseAuthUrl(authUrl);
  if (!url) return FALLBACK_RETRY_COOKIE_NAME;
  if (url.protocol === "https:") return SECURE_RETRY_COOKIE_NAME;
  return `${LOCAL_COOKIE_PREFIX}-${url.port || HTTP_DEFAULT_PORT}.${RETRY_COOKIE_SUFFIX}`;
}

/** True when the retry mark must carry `Secure`, which its `__Secure-` name requires. */
export function isSecureAuthUrl(authUrl?: string): boolean {
  return parseAuthUrl(authUrl)?.protocol === "https:";
}

/**
 * Name of the Auth.js cookie that holds the destination of the sign-in in
 * progress. It outlives a failed callback, so the root page reads the deep
 * link back from it: Auth.js does not put it on `?error=`.
 */
export function callbackUrlCookieName(authUrl?: string): string {
  const url = parseAuthUrl(authUrl);
  if (!url) return DEFAULT_CALLBACK_URL_COOKIE_NAME;
  const overridden = authCookieOverrides(authUrl)?.callbackUrl?.name;
  if (overridden) return overridden;
  return url.protocol === "https:" ? SECURE_CALLBACK_URL_COOKIE_NAME : DEFAULT_CALLBACK_URL_COOKIE_NAME;
}

/**
 * True when a response's Set-Cookie headers write the session cookie, whole
 * or as one of its `.<n>` chunks, with a value. Auth.js writes it only when a
 * callback signed the user in; a failed callback leaves it alone.
 */
export function writesSessionCookie(setCookieHeaders: string[], sessionName: string): boolean {
  const chunkPrefix = `${sessionName}.`;
  return setCookieHeaders.some((header) => {
    const separator = header.indexOf("=");
    if (separator <= 0) return false;
    const name = header.slice(0, separator).trim();
    const value = (header.slice(separator + 1).split(";", 1)[0] ?? "").trim();
    return (name === sessionName || name.startsWith(chunkPrefix)) && value !== "";
  });
}

/**
 * A Set-Cookie header that removes a host-only cookie on path "/". A
 * `__Secure-` cookie can only be replaced, and so removed, with `Secure`.
 */
export function expiredCookieHeader(name: string, secure: boolean): string {
  return `${name}=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax${secure ? "; Secure" : ""}`;
}

/** The login route, back to `callbackUrl` afterwards; `retry` marks the automatic retry. */
export function signInUrl(callbackUrl?: string, retry = false): string {
  const params = new URLSearchParams();
  if (callbackUrl) params.set("callbackUrl", callbackUrl);
  if (retry) params.set(SIGN_IN_RETRY_PARAM, "1");
  const query = params.toString();
  return query ? `${LOGIN_ROUTE}?${query}` : LOGIN_ROUTE;
}

/**
 * The `?error=` value when it looks like an Auth.js error code, otherwise
 * undefined, so a crafted link cannot put arbitrary text on the page.
 */
export function displayableErrorCode(error: string | undefined): string | undefined {
  return error && ERROR_CODE_PATTERN.test(error) ? error : undefined;
}

import type { NextAuthRequest, Session } from "next-auth";
import createMiddleware from "next-intl/middleware";
import { NextRequest, NextResponse } from "next/server";
import { auth } from "./src/libs/auth";
import { AuthError } from "./src/libs/auth/types";
import { extractLocale, stripLocale } from "./src/libs/i18n/locale-prefix";
import { routing } from "./src/libs/i18n/routing";
import { ProxyOptions } from "./src/types";

// Example: https://gist.github.com/pangeaos/39b1d95a5131a3849d55fb75d6faf98d
//
// The handler is wrapped with next-auth's `auth()` middleware form, NOT a bare
// `await auth()` call. The wrapper injects the session as `request.auth` and —
// crucially — merges the session Set-Cookie headers into WHATEVER response the
// handler returns (redirects and the next-intl response included). With a bare
// call, a token refresh triggered by the middleware would be thrown away: the
// rotated refresh token never reaches the browser cookie, and the next refresh
// attempt fails against Keycloak (token desync -> forced re-login).
//
// CAUTION — the `auth()` wrapper runs next-auth's `reqWithEnvURL`, which rewrites
// the incoming request's ORIGIN to AUTH_URL before our handler sees it. In prod
// AUTH_URL is the PUBLIC origin (https://kazan.korur.com), which is NOT the
// internal origin Next's middleware adapter sees behind Traefik. next-intl
// builds its `/` -> `/<locale>` rewrite as an ABSOLUTE url against the request
// origin it is given; if that origin is the AUTH_URL (public) host, the adapter
// finds `rewriteDestination.host !== request.nextUrl.host` (the internal host)
// and treats the rewrite as EXTERNAL — which (with localePrefix "never") it
// downgrades to a 307 whose Location normalizes back to `/`, i.e. an infinite
// same-url self-redirect on every app, in prod only (in dev AUTH_URL == origin
// so the hosts match). To avoid this we ALWAYS hand next-intl a request rebuilt
// against the REAL incoming origin (Host header), never the AUTH_URL-rewritten
// one. See runIntlMiddleware below.
export const proxy = (options?: ProxyOptions) => {
  return auth(async (request: NextAuthRequest): Promise<NextResponse | null> => {
    const { nextUrl } = request;
    const { loginPath, publicRoutes, authErrorPath, externalLoginUrl } =
      options || {};

    if ((!loginPath && !externalLoginUrl) || !publicRoutes) {
      return runIntlMiddleware(request);
    }

    // We NEVER emit locale-prefixed URLs (localePrefix: "never"). next-intl
    // still rewrites `/` -> `/<locale>` INTERNALLY to reach the app/[locale]
    // segment, and because the matcher also matches `/<locale>`, the middleware
    // is re-invoked on that rewrite target. Strip the locale before every
    // routing decision so a re-invoked `/en` is treated as the canonical `/`
    // (its real public/private identity) instead of an unknown private route.
    // Without this, an authenticated `/` loops forever: `/` -> rewrite `/en`
    // -> proxy sees `/en` as private -> runIntlMiddleware -> next-intl 307s
    // `/en` -> `/` -> rewrite `/en` -> ... (ERR_TOO_MANY_REDIRECTS).
    const pathname = stripLocale(nextUrl.pathname);

    const isPublic = isPublicPathname(pathname, publicRoutes);

    // Session for both public and private routes, injected by the wrapper.
    const session = request.auth;
    const isAuthenticated = !!session?.user;

    // If user is authenticated but has an error, redirect to error page
    // But only if we're not already on the error page (to prevent redirect loops)
    if (isAuthenticated && session?.error && authErrorPath) {
      if (session.error === AuthError.UNEXPECTED_ERROR) {
        return runIntlMiddleware(request);
      }

      // When authErrorPath is an absolute URL (e.g., external auth app),
      // redirect to the external error handler with the error param.
      // The external app (auth app) is responsible for handling the error.
      if (authErrorPath.startsWith("http")) {
        const url = new URL(authErrorPath);
        url.searchParams.set("error", String(session.error));
        url.searchParams.set("callbackUrl", request.nextUrl.href);
        return NextResponse.redirect(url.toString());
      }

      // For local authErrorPath (same app handles the error):
      // If we're on any public route, let the page component handle the error.
      // This prevents redirect loops where the proxy keeps redirecting
      // between error page and other public pages (e.g., /login <-> /).
      if (isPublic) {
        return runIntlMiddleware(request);
      }

      const isOnAuthErrorPath = pathname === authErrorPath;
      const hasErrorParam = nextUrl.searchParams.get("error") === String(session.error);

      // Redirect if not on error page, or on error page but missing error query param
      if (!isOnAuthErrorPath || !hasErrorParam) {
        const url = new URL(authErrorPath, nextUrl);
        // Preserve explicit error reason for UI handling
        url.searchParams.set("error", String(session.error));
        // Preserve the original deep link so it survives the error round-trip
        // (e.g. for a "retry" that re-initiates sign-in back to where the user was).
        url.searchParams.set("callbackUrl", request.nextUrl.href);
        return NextResponse.redirect(url);
      }
    }

    // If it's a public route, allow access
    if (isPublic) {
      return runIntlMiddleware(request);
    }

    // For private routes, check authentication
    if (!isAuthenticated) {
      if (externalLoginUrl) {
        // Use the URL API so any pre-baked query string in externalLoginUrl
        // is preserved and our callbackUrl param is set (not appended via a
        // stray "?", which would produce a malformed URL).
        const url = new URL(externalLoginUrl);
        url.searchParams.set("callbackUrl", request.nextUrl.href);
        return NextResponse.redirect(url.toString());
      }
      if (loginPath) {
        // Preserve the original deep link so the local login route (which
        // initiates the Keycloak flow) can return the user here after sign-in.
        const url = new URL(loginPath, nextUrl);
        url.searchParams.set("callbackUrl", request.nextUrl.href);
        return NextResponse.redirect(url);
      }
      return runIntlMiddleware(request);
    }

    // Guard: pending invitations
    const invitationRedirect = pendingInvitationGuard(session, request, options);
    if (invitationRedirect) return invitationRedirect;

    return runIntlMiddleware(request);
  });
};

const intlMiddleware = createMiddleware(routing);

// Runs next-intl's middleware, which resolves the locale (path prefix, then
// cookie, then Accept-Language) and rewrites the path to its `[locale]` route.
function runIntlMiddleware(request: NextRequest): NextResponse {
  // With localePrefix "never", next-intl answers every `/<locale>/...` path
  // with a 307 to the unprefixed path and stores the prefix in the locale
  // cookie. That is what a typed or linked `/nl/settings` needs when the user
  // reads another language: they land on `/settings` in Dutch.
  //
  // A prefix that is already the locale of the unprefixed path is served
  // through instead. Such a path is exactly what next-intl rewrites the
  // unprefixed path to; if that rewrite target comes back through the proxy (an
  // origin mismatch makes Next.js fetch it as an external URL), a 307 here
  // sends the browser back to `/`, which rewrites to the same prefixed path
  // again: an endless `/` <-> `/<locale>` loop. The comparison uses the locale
  // next-intl resolves, not whether it writes a cookie, so a client that keeps
  // no cookies cannot loop either. The cost is that a typed prefix in the
  // user's own language stays in the address bar.
  const prefixLocale = extractLocale(request.nextUrl.pathname);
  if (prefixLocale && prefixLocale === localeOfUnprefixedPath(request)) {
    return serveWithLocale(request, prefixLocale);
  }
  return intlMiddleware(withRealOrigin(request));
}

// next-intl's server functions read the request locale from this header, which
// its middleware sets on every request it lets through. The `[locale]` route
// segment is not read: without the header the messages fall back to the
// default locale while the segment, and with it `<html lang>`, says otherwise.
const NEXT_INTL_LOCALE_HEADER = "X-NEXT-INTL-LOCALE";

// How NextResponse.next({ request: { headers } }) carries an overridden request
// header to Next.js.
const OVERRIDDEN_LOCALE_HEADER = `x-middleware-request-${NEXT_INTL_LOCALE_HEADER.toLowerCase()}`;

// The locale next-intl gives the same request without its prefix: the locale
// cookie, then Accept-Language, then the default locale.
function localeOfUnprefixedPath(request: NextRequest): string | null {
  const url = request.nextUrl.clone();
  url.pathname = stripLocale(url.pathname);
  const response = intlMiddleware(new NextRequest(url, request));
  return response.headers.get(OVERRIDDEN_LOCALE_HEADER);
}

function serveWithLocale(request: NextRequest, locale: string): NextResponse {
  const headers = new Headers(request.headers);
  headers.set(NEXT_INTL_LOCALE_HEADER, locale);
  return NextResponse.next({ request: { headers } });
}

// The request rebuilt against the REAL incoming origin, undoing the AUTH_URL
// origin substitution that next-auth's `auth()` wrapper applies via
// `reqWithEnvURL`. next-intl emits its `/` -> `/<locale>` rewrite as an absolute
// URL built from `request.url`'s origin; that origin must match the internal
// host Next's middleware adapter sees, or the adapter classifies the rewrite as
// external and downgrades it to a 307 back to `/` (the prod-only, all-apps,
// cookie-independent self-redirect loop). We rebuild the request URL from the
// incoming Host header (what the adapter uses for `request.nextUrl.host`), so
// the rewrite stays internal and is honored. Path, query, and headers (cookies
// included) are preserved; only the origin is corrected. If no Host header is
// present (should not happen for a real HTTP request) we fall back to the
// request as-is.
function withRealOrigin(request: NextRequest): NextRequest {
  const host = request.headers.get("host");
  if (!host) {
    return request;
  }

  const realOrigin = `${request.nextUrl.protocol}//${host}`;
  const currentOrigin = request.nextUrl.origin;
  if (realOrigin === currentOrigin) {
    // Origins already agree (e.g. local dev where AUTH_URL == the real origin):
    // nothing to correct.
    return request;
  }

  const correctedUrl = request.nextUrl.href.replace(currentOrigin, realOrigin);
  return new NextRequest(correctedUrl, request);
}

// Public route detection using regex
const isPublicPathname = (
  pathname: string,
  publicRoutes: ProxyOptions["publicRoutes"]
): boolean => {
  // Check exact matches first
  const publicPathnameRegex = RegExp(
    `^(${publicRoutes.flatMap((p) => (p === "/" ? ["", "/"] : p)).join("|")})/?$`,
    "i"
  );

  if (publicPathnameRegex.test(pathname)) {
    return true;
  }

  // Check if pathname starts with any public route (for dynamic routes like /products/[slug])
  // This allows /products and all its sub-routes to be public
  // Note: pathname is already without locale at this point
  for (const route of publicRoutes) {
    // Skip root route for this check
    if (route === "/") continue;

    // Check if pathname starts with the public route
    if (pathname === route || pathname.startsWith(`${route}/`)) {
      return true;
    }
  }

  return false;
};

function pendingInvitationGuard(
  session: Session | null,
  request: NextRequest,
  options: ProxyOptions | undefined,
): NextResponse | null {
  const { invitationsPath } = options ?? {};

  // Only fire for authenticated users that have pending invitations
  if (!session?.pendingInvitationIds?.length) return null;

  const now = Date.now();
  const remindLater = session.remindLaterInvitations ?? {};

  // Compute which invitations have no valid remind-later
  const activePending = session.pendingInvitationIds.filter((id) => {
    const remindAt = remindLater[id];
    return !remindAt || remindAt <= now; // expired or never set
  });

  if (activePending.length === 0) return null;

  // Prevent redirect loops: if user is already on the invitations path, pass through
  const pathname = request.nextUrl.pathname;
  const strippedPathname = stripLocale(pathname);
  if (invitationsPath && strippedPathname.startsWith(invitationsPath)) {
    return null;
  }

  // Build redirect URL
  const callbackUrl = request.nextUrl.href;

  if (invitationsPath) {
    // Redirect to the invitations page: a path of this app, or the absolute
    // URL of Ulak for an app that has none. No locale segment: routing is
    // localePrefix "never", so next-intl resolves the user's language from
    // the cookie as it does for every other route.
    const url = new URL(invitationsPath, request.nextUrl);
    url.searchParams.set("callbackUrl", callbackUrl);
    return NextResponse.redirect(url);
  }

  return null;
}

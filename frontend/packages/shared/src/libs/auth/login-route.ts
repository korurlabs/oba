import { NextRequest, NextResponse } from "next/server";
import { auth, sanitizeCallbackUrl, signIn } from "./index";
import { AuthError } from "./types";
import { clearSessionCookies } from "./clear-session-cookies";
import { sessionCookieName } from "./session-cookie";
import { markSignInRetry } from "./sign-in-failure";
import { SIGN_IN_RETRY_PARAM } from "./sign-in-retry";

/**
 * Direct-to-Keycloak login route.
 *
 * Apps mount this at app/api/auth/login/route.ts via:
 *
 *   export { GET } from "@oba/shared/libs/auth/login-route";
 *
 * Hitting GET /api/auth/login immediately initiates the Keycloak OAuth flow
 * server-side, so the browser lands straight on the Keycloak realm login UI.
 * There is no separate login app: each app owns its own sign-in entry point.
 *
 * The route lives under /api on purpose: the next-intl middleware/proxy matcher
 * excludes /api, so there is no locale rewriting (no /en/api/auth/login 404),
 * and the PKCE/state/nonce cookies are set on the app's own origin.
 *
 * Contract with next-auth 5.0.0-beta.29:
 * The server-side signIn exported from our NextAuth() result, when called inside
 * a route handler with redirect defaulting to true, sets the PKCE/state/nonce
 * cookies via the cookies() store and then performs the provider redirect by
 * THROWING a NEXT_REDIRECT error. Next.js catches that throw and converts it
 * into the 3xx response — so it must propagate. We never wrap the signIn call in
 * a try/catch that could swallow NEXT_REDIRECT.
 */
export async function GET(request: NextRequest): Promise<Response> {
  const { searchParams } = request.nextUrl;

  // 1. Resolve + sanitize the post-login destination.
  const rawCallbackUrl: string | null = searchParams.get("callbackUrl");
  const callbackUrl: string = sanitizeCallbackUrl(rawCallbackUrl) ?? "/";

  // 2. Loop protection: Auth.js redirects failed sign-ins back to pages.signIn
  // (this route) with ?error=<reason>. If we re-initiated sign-in here we would
  // bounce app <-> Keycloak forever on a persistent OAuth error. Instead send
  // the user to the app home (public) with the error preserved — root pages
  // already render error query params.
  const error: string | null = searchParams.get("error");
  if (error) {
    const errorUrl: URL = new URL("/", request.nextUrl);
    errorUrl.searchParams.set("error", error);
    return NextResponse.redirect(errorUrl);
  }

  // 3. Already authenticated with a healthy session — skip Keycloak, go straight
  // to the destination. Resolve relative callbackUrls against this app's origin.
  const session = await auth();
  if (session?.user && !session.error) {
    return NextResponse.redirect(new URL(callbackUrl, request.nextUrl));
  }

  // 3a. Poisoned-cookie guard — THE single source of truth for breaking
  // ERR_TOO_MANY_REDIRECTS. Before (re)initiating sign-in we must guarantee the
  // browser is not holding a session cookie that will corrupt the NEXT read.
  //
  // Two distinct poison states reach this point, and BOTH loop without a purge:
  //
  //  (a) session.user populated but session.error === REFRESH_TOKEN_ERROR — the
  //      JWT still decodes, but its refresh token is dead (rotated/revoked/
  //      expired, or AUTH_SECRET/realm change).
  //  (b) session === null while a session-token cookie IS present — the chunked
  //      cookie no longer decrypts at all. This is the common prod case: the
  //      cookie is split across `${name}.0/.1/…`; @auth/core concatenates EVERY
  //      chunk whose name starts with the base name on read, so a single stale
  //      chunk (e.g. a host-only orphan left by an older deploy, or a longer
  //      chunk set from a sibling app under the shared .sensey.io domain) makes
  //      the whole value undecryptable -> auth() returns null -> the proxy
  //      treats the user as unauthenticated -> /api/auth/login -> Keycloak SSO
  //      still live -> fresh code -> a NEW chunk set is written on top of the
  //      orphan -> still corrupt -> loop forever. Manually deleting the cookie
  //      was the only escape (the reported workaround).
  //
  // signOut() alone is insufficient for (b): it only deletes the chunks its
  // SessionStore parsed, under the single configured Domain — it cannot evict a
  // HOST-ONLY orphan. clearSessionCookies() expires every chunk index under BOTH
  // the apex Domain and host-only, so signIn() runs on a genuinely clean slate
  // and the new cookie cannot collide with a stale chunk. We only purge when a
  // session-token cookie is actually present, so a first-time visitor with no
  // cookie takes the fast path straight to signIn().
  const hasSessionCookie = hasAnySessionCookie(request);
  const isPoisoned =
    session?.error === AuthError.REFRESH_TOKEN_ERROR ||
    (!session?.user && hasSessionCookie);

  if (isPoisoned) {
    await clearSessionCookies();
  }

  // 4. A root page that received a failed callback asks for its one automatic
  // retry with ?retry=1. The mark lets that page show the failure instead of
  // retrying again when this sign-in fails too (see ./sign-in-failure).
  if (searchParams.has(SIGN_IN_RETRY_PARAM)) {
    await markSignInRetry();
  }

  // 5. Initiate the Keycloak OAuth flow. signIn sets the auth-flow cookies and
  // throws NEXT_REDIRECT to issue the provider redirect; that throw is the
  // success path and must propagate out of this handler unchanged.
  return await signIn("keycloak", { redirectTo: callbackUrl });
}

/**
 * True when the request carries this app's session cookie: the unchunked
 * cookie or any `${name}.<n>` chunk.
 *
 * Detects a leftover orphan chunk even when no decodable session exists, since
 * that orphan is precisely what poisons the next read. The match is exact on
 * the base name so that the session cookie of another app in the same cookie
 * jar never triggers a purge here.
 */
function hasAnySessionCookie(request: NextRequest): boolean {
  const baseName: string = sessionCookieName(process.env.AUTH_URL);
  const chunkPrefix: string = `${baseName}.`;
  return request.cookies
    .getAll()
    .some((cookie) => cookie.name === baseName || cookie.name.startsWith(chunkPrefix));
}

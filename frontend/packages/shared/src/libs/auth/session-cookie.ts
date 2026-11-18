import type { NextAuthConfig } from "next-auth";

/**
 * Auth.js cookie names and options, derived from AUTH_URL.
 *
 * Kept free of `next/headers` and of the NextAuth setup because both the
 * NextAuth config (loaded by the edge proxy) and the server-only cookie purge
 * need the same names.
 *
 * Auth.js reads the session by joining every cookie whose name STARTS WITH the
 * configured session name, so no name this app writes may be a prefix of
 * another one: a foreign cookie in the family makes the joined value
 * undecryptable and ends the session.
 *
 *  - https AUTH_URL       -> "__Secure-authjs.session-token"
 *  - https AUTH_URL "-dev" -> "__Secure-authjs-dev.session-token"
 *  - http AUTH_URL        -> "oba.session-token"
 *  - missing or invalid   -> "authjs.session-token" (the Auth.js default)
 */

const DEFAULT_SESSION_COOKIE_NAME = "authjs.session-token";
const SECURE_SESSION_COOKIE_NAME = "__Secure-authjs.session-token";
const SECURE_DEV_SESSION_COOKIE_NAME = "__Secure-authjs-dev.session-token";
const DEV_ENVIRONMENT_MARKER = "-dev";

/**
 * Prefix of every Auth.js cookie on a plain-http (local) origin. Browsers do not
 * isolate cookies by port, so every Auth.js server on `localhost` shares one
 * jar, and most use the default `authjs.*` names. The platform name keeps
 * Kaya (which runs its own Auth.js setup) and any other local Auth.js app
 * from reading, rewriting or deleting the Oba session.
 *
 * The session cookie carries the platform name alone, not an app's, because
 * every app built on this package (Kaizen, Admin) reads the one shared Oba
 * session, as production does through the parent-domain cookie; they share
 * AUTH_SECRET for that reason. It carries no port on purpose: each signed-in
 * session family adds about 5 KB to every localhost request, and Node rejects
 * requests whose headers exceed 16 KB (HTTP 431). One session for the platform
 * keeps the jar small enough for several local apps at once; the cost is that
 * all local Oba app instances share one session, so signing out or in as
 * another user on one changes the others.
 *
 * The sign-in handshake cookies are the opposite case: they belong to one
 * app's sign-in in progress, so they add the app's port (see
 * {@link localHandshakePrefix}).
 */
const LOCAL_COOKIE_PREFIX = "oba";
const LOCALHOST = "localhost";
/** The port a plain-http URL without an explicit port is served on. */
const HTTP_DEFAULT_PORT = "80";

type AuthCookies = NonNullable<NextAuthConfig["cookies"]>;

/**
 * Parses AUTH_URL. A malformed value is reported in the server log and treated
 * like a missing one, so the cookie config falls back to the Auth.js defaults
 * instead of crashing the edge proxy at module load; Auth.js itself rejects
 * the same value loudly on the first auth request.
 */
function parseAuthUrl(authUrl?: string): URL | undefined {
  if (!authUrl) return undefined;
  try {
    return new URL(authUrl);
  } catch (error) {
    console.warn("[session-cookie] AUTH_URL is not a valid URL; using the Auth.js cookie defaults:", authUrl, error);
    return undefined;
  }
}

/** Base name of the (possibly chunked) session cookie for this AUTH_URL. */
export function sessionCookieName(authUrl?: string): string {
  const url = parseAuthUrl(authUrl);
  if (!url) return DEFAULT_SESSION_COOKIE_NAME;
  if (url.protocol === "https:") {
    return authUrl?.includes(DEV_ENVIRONMENT_MARKER) ? SECURE_DEV_SESSION_COOKIE_NAME : SECURE_SESSION_COOKIE_NAME;
  }
  return `${LOCAL_COOKIE_PREFIX}.session-token`;
}

/**
 * Parent domain of the AUTH_URL host (kaizen.korur.com -> ".korur.com"), or
 * undefined for localhost and two-label hosts.
 *
 * The session cookie is scoped to the parent domain so every app on a
 * subdomain of it uses the same JWT. Without this, each subdomain gets its own
 * refresh token and Keycloak token rotation breaks one when the other
 * refreshes. Narrowing the domain signs every user out.
 */
export function sessionCookieDomain(authUrl?: string): string | undefined {
  const url = parseAuthUrl(authUrl);
  if (!url) return undefined;
  const hostname = url.hostname;
  const parts = hostname.split(".");
  if (parts.length < 3 || hostname === LOCALHOST) return undefined;
  return `.${parts.slice(-2).join(".")}`;
}

/**
 * Prefix of the sign-in handshake cookies on a plain-http origin: the platform
 * name and the AUTH_URL port, "oba-3002" for http://localhost:3002 and
 * "oba-80" for a URL without an explicit port.
 *
 * Each local Oba app runs its own sign-in against Keycloak, and the handshake
 * cookies (PKCE code verifier, state, nonce, CSRF token, callback URL) must
 * survive until that app's callback. Under one name shared by all local apps,
 * a sign-in started on a second app would overwrite the first app's verifier
 * and make the first callback fail. The port is what tells the local apps
 * apart in the one localhost jar.
 *
 * The "-" separator keeps every handshake name outside the session name family
 * ("oba.session-token" and its chunks), which Auth.js joins by name prefix.
 */
function localHandshakePrefix(url: URL): string {
  return `${LOCAL_COOKIE_PREFIX}-${url.port || HTTP_DEFAULT_PORT}`;
}

/**
 * The `cookies` option for NextAuth. Without a usable AUTH_URL it returns
 * undefined, so Auth.js keeps its own defaults.
 *
 * On a plain-http origin the session cookie gets the shared platform name, so
 * every local Oba app reads one session, while the CSRF, callback URL, PKCE,
 * state and nonce cookies get the per-app handshake prefix, so concurrent
 * sign-ins on two local apps do not overwrite each other's handshake. Both
 * stay clear of the default names, which another local Auth.js app would use.
 * Their options stay the Auth.js defaults, which NextAuth deep-merges under
 * these names.
 */
export function authCookieOverrides(authUrl?: string): NextAuthConfig["cookies"] {
  const url = parseAuthUrl(authUrl);
  if (!url) return undefined;

  const secure = url.protocol === "https:";
  const domain = sessionCookieDomain(authUrl);
  const sessionToken: AuthCookies["sessionToken"] = {
    name: sessionCookieName(authUrl),
    options: {
      httpOnly: true,
      sameSite: "lax",
      path: "/",
      secure,
      ...(domain && { domain }),
    },
  };
  if (secure) return { sessionToken };

  const handshakePrefix = localHandshakePrefix(url);
  return {
    sessionToken,
    csrfToken: { name: `${handshakePrefix}.csrf-token` },
    callbackUrl: { name: `${handshakePrefix}.callback-url` },
    pkceCodeVerifier: { name: `${handshakePrefix}.pkce.code_verifier` },
    state: { name: `${handshakePrefix}.state` },
    nonce: { name: `${handshakePrefix}.nonce` },
  };
}

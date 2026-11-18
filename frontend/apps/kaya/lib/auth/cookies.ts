import type { NextAuthConfig } from "next-auth";

/**
 * Kaya's own Auth.js cookie names, derived from AUTH_URL.
 *
 * Kaya runs on a subdomain of the domain the Oba apps scope their session
 * cookie to (kaya.korur.com under `Domain=.korur.com`), so the browser sends
 * Oba's session to Kaya as well. Under the Auth.js default names both apps
 * would use `__Secure-authjs.session-token`: Auth.js keeps the first cookie of
 * a name and the browser sends the older one first, so Kaya would read Oba's
 * session, fail to decrypt it under its own secret and send the user back to
 * Keycloak forever. Locally every app shares the one localhost jar, with the
 * same effect. A `kaya` prefix keeps the two families apart.
 *
 * Auth.js reads the session by joining every cookie whose name STARTS WITH the
 * session name, so no other name may start with it: `kaya.session-token` is
 * not a prefix of the handshake names, nor of `kaya.reauth`, and no Oba name
 * (`oba.*`, `oba-<port>.*`, `__Secure-authjs.*`, `__Secure-authjs-dev.*`)
 * starts with a Kaya name or the other way round.
 *
 *  - https AUTH_URL       -> "__Secure-kaya.session-token", "__Host-kaya.csrf-token"
 *  - http AUTH_URL        -> "kaya.session-token", "kaya.csrf-token"
 *  - missing or invalid   -> the http names; Auth.js still sets `Secure` per request
 *
 * Every cookie stays host-only (no `domain`): Kaya keeps its own session and
 * never shares it with the Oba apps.
 *
 * Kept free of `next/headers` because the edge proxy loads the NextAuth
 * config that uses it.
 */

const COOKIE_PREFIX = "kaya";
const SECURE_PREFIX = "__Secure-";
/** Auth.js gives the CSRF cookie the stricter prefix: host-only, path `/`, Secure. */
const HOST_PREFIX = "__Host-";
const LOCALHOST = "localhost";

/**
 * Names under which Kaya wrote its cookies before it had its own: the https
 * Auth.js defaults. The plain `authjs.*` defaults are not listed, because on
 * localhost they may belong to another local Auth.js app.
 */
const LEGACY_SECURE_PREFIXES = [`${SECURE_PREFIX}authjs.`, `${HOST_PREFIX}authjs.`] as const;

type AuthCookies = NonNullable<NextAuthConfig["cookies"]>;

/**
 * Parses AUTH_URL. A malformed value is reported in the server log and treated
 * like a missing one, so the edge proxy does not crash at module load; Auth.js
 * itself rejects the same value loudly on the first auth request.
 */
function parseAuthUrl(authUrl?: string): URL | undefined {
  if (!authUrl) return undefined;
  try {
    return new URL(authUrl);
  } catch (error) {
    console.warn(
      "[auth] AUTH_URL is not a valid URL; using the plain kaya cookie names:",
      authUrl,
      error
    );
    return undefined;
  }
}

function isHttps(url: URL | undefined): boolean {
  return url?.protocol === "https:";
}

/**
 * The base name of Kaya's session cookie. Auth.js splits a large session into
 * `<name>.0`, `<name>.1` and so on, and joins every cookie that starts with it.
 */
export function kayaSessionCookieName(authUrl?: string): string {
  const prefix = isHttps(parseAuthUrl(authUrl)) ? SECURE_PREFIX : "";
  return `${prefix}${COOKIE_PREFIX}.session-token`;
}

/**
 * The `cookies` option for NextAuth. Only the names are set; NextAuth
 * deep-merges them over its default options, which carry no `domain`. The
 * prefixed names also pin `secure`, which a browser requires for them.
 */
export function kayaAuthCookies(authUrl?: string): AuthCookies {
  const secure = isHttps(parseAuthUrl(authUrl));
  const prefix = secure ? SECURE_PREFIX : "";
  const options = secure ? { secure: true } : {};
  const cookie = (name: string, namePrefix: string = prefix) => ({
    name: `${namePrefix}${COOKIE_PREFIX}.${name}`,
    options,
  });
  return {
    sessionToken: { name: kayaSessionCookieName(authUrl), options },
    callbackUrl: cookie("callback-url"),
    csrfToken: cookie("csrf-token", secure ? HOST_PREFIX : ""),
    pkceCodeVerifier: cookie("pkce.code_verifier"),
    state: cookie("state"),
    nonce: cookie("nonce"),
    webauthnChallenge: cookie("challenge"),
  };
}

/**
 * The request cookies that Kaya wrote under the https Auth.js defaults before
 * it had its own names, and that the sign-in route expires. Left in place they
 * add about 5 KB to every request to Kaya, next to Oba's session and Kaya's
 * own, which can push the request headers over the server's limit.
 *
 * A name in this list may also be Oba's parent-domain session, which the
 * browser sends under the same name. Expiring it is still safe: the
 * expiring Set-Cookie carries no `Domain`, and a browser replaces only the
 * cookie with the same name, domain and path, so it removes Kaya's host-only
 * copy on kaya.korur.com and leaves Oba's `.korur.com` cookie untouched.
 *
 * Only on an https AUTH_URL with a real host name: on localhost there is no
 * parent-domain cookie, so a host-only cookie of that name could be another
 * local app's session.
 */
export function legacyAuthCookieNames(
  requestCookieNames: readonly string[],
  authUrl?: string
): string[] {
  const url = parseAuthUrl(authUrl);
  if (!isHttps(url) || url?.hostname === LOCALHOST) {
    return [];
  }
  return requestCookieNames.filter((name) =>
    LEGACY_SECURE_PREFIXES.some((prefix) => name.startsWith(prefix))
  );
}

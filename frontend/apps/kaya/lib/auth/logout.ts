import { kayaSessionCookieName } from "./cookies";

/**
 * Kaya's sign-out route. A POST from the page starts it; Keycloak sends the
 * browser back to a GET of the same path once it has ended its session.
 */
export const LOGOUT_PATH = "/api/auth/logout";

/** Where a signed-out user lands; the proxy sends it on to the Keycloak sign-in. */
export const SIGNED_OUT_LANDING = "/";

/**
 * Chunk suffixes expired on sign-out. Kaya's session (access, refresh and id
 * token) takes two chunks today; expiring absent names costs nothing, and a
 * leftover chunk would corrupt the joined session of the next sign-in.
 */
const SESSION_CHUNKS = 12;

/**
 * Every name Kaya's session can be stored under: the unchunked name and its
 * chunks. All of them start with Kaya's own prefix, so expiring them never
 * touches the Oba apps' session, which the browser sends to Kaya as well.
 */
export function kayaSessionCookieNames(authUrl?: string): string[] {
  const base = kayaSessionCookieName(authUrl);
  return [base, ...Array.from({ length: SESSION_CHUNKS }, (_, index) => `${base}.${index}`)];
}

/**
 * The public origin of Kaya. AUTH_URL comes first, because Keycloak accepts
 * only the post-logout redirect URIs registered for the client, and those are
 * the public URLs; the request origin is the fallback for a missing or
 * invalid AUTH_URL.
 */
export function kayaOrigin(authUrl: string | undefined, requestOrigin: string): string {
  if (authUrl && URL.canParse(authUrl)) {
    return new URL(authUrl).origin;
  }
  return requestOrigin;
}

/**
 * Keycloak's end-session endpoint, opened in the browser so that Keycloak ends
 * the SSO session its own cookie names, which may be newer than the session
 * that issued Kaya's id token. `client_id` lets Keycloak check the return URI
 * without an id token; `id_token_hint`, when Kaya still has one, spares the
 * user the logout confirmation. `post_logout_redirect_uri`, never
 * `redirect_uri`: Keycloak 18 and later reject the latter here.
 */
export function endSessionUrl(
  issuer: string,
  clientId: string,
  origin: string,
  idToken: string | undefined
): URL {
  const url = new URL(`${issuer}/protocol/openid-connect/logout`);
  url.searchParams.set("client_id", clientId);
  url.searchParams.set("post_logout_redirect_uri", new URL(LOGOUT_PATH, origin).toString());
  if (idToken !== undefined) {
    url.searchParams.set("id_token_hint", idToken);
  }
  return url;
}

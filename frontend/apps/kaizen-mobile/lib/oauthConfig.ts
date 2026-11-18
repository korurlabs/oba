import * as AuthSession from "expo-auth-session";

import { ENV } from "@/lib/env";

/**
 * Keycloak settings of the Authorization Code + PKCE flow against realm
 * `sensey` (client `kaizen-mobile`), shared by sign-in, the token refresh in
 * lib/api.ts and the revocation at sign-out.
 *
 * The redirect URI and the scopes live here once: a redirect URI that differs
 * from the one registered on the Keycloak client fails with "Invalid parameter:
 * redirect_uri", and a refresh must use the client id the grant was issued to.
 */

/** URL scheme of the app, the same as `scheme` in app.config.ts. */
export const APP_SCHEME = "kaizen";

/**
 * Path of the redirect URI. Keycloak rejects a bare `kaizen://`, so the
 * redirect is `kaizen://oauthredirect`, which must be listed in the client's
 * redirect URIs.
 */
export const OAUTH_REDIRECT_PATH = "oauthredirect";

/**
 * `offline_access` gives the long-lived refresh token, so a member stays signed
 * in between app launches; the access token itself lives 900 seconds.
 */
export const OAUTH_SCOPES: readonly string[] = ["openid", "profile", "email", "offline_access"];

/** The redirect URI of every authorize call, `kaizen://oauthredirect`. */
export function buildRedirectUri(): string {
  return AuthSession.makeRedirectUri({ scheme: APP_SCHEME, path: OAUTH_REDIRECT_PATH });
}

/** The public client id of this app in realm `sensey`. */
export function getKeycloakClientId(): string {
  return ENV.keycloakClientId;
}

/** The realm issuer, which already ends in `/realms/sensey`. */
export function getIssuer(): string {
  return ENV.keycloakIssuer;
}

// Sign-in and the token refresh both need the discovery document. One cached
// promise means a refresh never starts a second fetch beside a running one.
let discoveryPromise: Promise<AuthSession.DiscoveryDocument> | null = null;
let discoveryResolvedAtMs: number | null = null;
const DISCOVERY_TTL_MS = 60 * 60 * 1000;

/**
 * The OIDC discovery document of the realm, cached for an hour. A failed fetch
 * is logged and re-thrown, and the cache is dropped so the next call fetches
 * again. `useAutoDiscovery` is not used: it returns null on failure, which a
 * caller cannot tell from "still loading".
 */
export async function getDiscovery(): Promise<AuthSession.DiscoveryDocument> {
  if (
    discoveryPromise !== null &&
    discoveryResolvedAtMs !== null &&
    Date.now() - discoveryResolvedAtMs > DISCOVERY_TTL_MS
  ) {
    clearDiscoveryCache();
  }
  if (discoveryPromise === null) {
    const issuer: string = getIssuer();
    discoveryPromise = AuthSession.fetchDiscoveryAsync(issuer)
      .then((document: AuthSession.DiscoveryDocument) => {
        discoveryResolvedAtMs = Date.now();
        return document;
      })
      .catch((error: unknown) => {
        clearDiscoveryCache();
        console.warn(
          "[oauth] discovery fetch failed for",
          issuer,
          error instanceof Error ? error.message : error
        );
        throw error;
      });
  }
  return discoveryPromise;
}

/** Drops the cached discovery document, so the next call fetches it again. */
export function clearDiscoveryCache(): void {
  discoveryPromise = null;
  discoveryResolvedAtMs = null;
}

/** Options of one authorize request. */
export interface SignInParamOptions {
  /** The app language, sent as `ui_locales` so Keycloak shows its pages in it. */
  uiLocale?: string;
  /**
   * Shows the Keycloak login form even when a Keycloak session cookie is still
   * alive (`prompt=login`). Set after the user signed out, so the next sign-in
   * cannot silently return the previous account through a browser that kept
   * the cookie.
   */
  forceLoginForm?: boolean;
}

/** The extra query parameters of the authorize request. */
export function buildSignInExtraParams(options: SignInParamOptions): Record<string, string> {
  const extraParams: Record<string, string> = {};
  if (options.uiLocale) extraParams.ui_locales = options.uiLocale;
  if (options.forceLoginForm) extraParams.prompt = "login";
  return extraParams;
}

import type { JWT } from "next-auth/jwt";

import { AuthError } from "./types";

/** Seconds before expiry at which an access token already counts as expired. */
const EXPIRY_MARGIN_SECONDS = 30;

/**
 * Refreshes in flight, by refresh token. A page that fires several queries as
 * the token expires runs the jwt callback once per query; with Keycloak's
 * refresh token rotation every grant after the first would fail and sign the
 * user out, so concurrent refreshes of one token share one grant.
 */
const inFlight = new Map<string, Promise<JWT>>();

/** Whether the access token is present and outlives the margin. */
export function isTokenValid(token: JWT): boolean {
  if (token.accessToken === undefined || token.expiresAt === undefined) {
    return false;
  }
  return Date.now() / 1000 < token.expiresAt - EXPIRY_MARGIN_SECONDS;
}

function failed(token: JWT, dropRefreshToken: boolean): JWT {
  return {
    ...token,
    accessToken: undefined,
    expiresAt: undefined,
    refreshToken: dropRefreshToken ? undefined : token.refreshToken,
    error: AuthError.REFRESH_TOKEN_ERROR,
  };
}

async function refresh(token: JWT, refreshToken: string): Promise<JWT> {
  const issuer = process.env.AUTH_KEYCLOAK_ISSUER;
  const clientId = process.env.AUTH_KEYCLOAK_ID;
  const clientSecret = process.env.AUTH_KEYCLOAK_SECRET;
  if (!issuer || !clientId || !clientSecret) {
    console.error(
      "[auth] AUTH_KEYCLOAK_ISSUER, AUTH_KEYCLOAK_ID or AUTH_KEYCLOAK_SECRET is not set"
    );
    return failed(token, false);
  }
  try {
    // The body and the response hold the client secret and the tokens: neither is logged.
    const response = await fetch(`${issuer}/protocol/openid-connect/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        client_id: clientId,
        client_secret: clientSecret,
        refresh_token: refreshToken,
      }),
    });
    if (!response.ok) {
      console.error(`[auth] Token refresh refused with status ${response.status}`);
      return failed(token, true);
    }
    const body = (await response.json()) as {
      access_token: string;
      refresh_token?: string;
      expires_in?: number;
    };
    return {
      ...token,
      accessToken: body.access_token,
      refreshToken: body.refresh_token ?? refreshToken,
      expiresAt: Math.floor(Date.now() / 1000) + (body.expires_in ?? 300),
      error: undefined,
    };
  } catch (error) {
    console.error("[auth] Token refresh failed", error);
    return failed(token, true);
  }
}

/**
 * A token with a fresh access token, or the same token marked
 * REFRESH_TOKEN_ERROR when Keycloak refuses the refresh; the client then
 * sends the user to sign in again.
 */
export function refreshAccessToken(token: JWT): Promise<JWT> {
  const refreshToken = token.refreshToken;
  if (refreshToken === undefined) {
    return Promise.resolve(failed(token, false));
  }
  const running = inFlight.get(refreshToken);
  if (running !== undefined) {
    return running;
  }
  const promise = refresh(token, refreshToken).finally(() => inFlight.delete(refreshToken));
  inFlight.set(refreshToken, promise);
  return promise;
}

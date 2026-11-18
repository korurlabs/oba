import type { JWT } from "next-auth/jwt";
import { AuthError } from "./types";

/**
 * In-flight refresh deduplication. The axios interceptor reads the session per
 * API call, so a page firing N parallel queries at token expiry produces N
 * concurrent jwt-callback refreshes for the SAME refresh token. Without dedup,
 * each one sends its own refresh_token grant to Keycloak — with token
 * rotation/reuse-detection enabled the losers fail, the refresh token gets
 * cleared, and the user is force-logged-out. Sharing one in-flight promise per
 * refresh token collapses the herd into a single grant.
 */
const refreshPromiseCache = new Map<string, Promise<JWT>>();

/**
 * Refreshes an expired access token using the refresh token
 * @param token - JWT token containing refresh token
 * @returns Promise resolving to refreshed JWT or error JWT
 */
export async function refreshAccessToken(token: JWT): Promise<JWT> {
  const refreshToken = token.refreshToken;
  if (!refreshToken) {
    return createErrorToken(token);
  }

  const inFlight = refreshPromiseCache.get(refreshToken);
  if (inFlight) {
    return inFlight;
  }

  const refreshPromise = doRefreshAccessToken(token, refreshToken).finally(() => {
    refreshPromiseCache.delete(refreshToken);
  });
  refreshPromiseCache.set(refreshToken, refreshPromise);
  return refreshPromise;
}

async function doRefreshAccessToken(token: JWT, refreshToken: string): Promise<JWT> {
  try {
    // Read env vars at call time (not module load) to avoid stale/undefined values
    const keycloakIssuer = process.env.AUTH_KEYCLOAK_ISSUER;
    const keycloakClientId = process.env.AUTH_KEYCLOAK_ID;
    const keycloakClientSecret = process.env.AUTH_KEYCLOAK_SECRET;

    // Validate required environment variables
    if (!keycloakIssuer || !keycloakClientId || !keycloakClientSecret) {
      console.error("[Token Refresh] Missing Keycloak env vars (AUTH_KEYCLOAK_ISSUER/ID/SECRET)");
      return createErrorToken(token);
    }

    const tokenEndpoint = `${keycloakIssuer}/protocol/openid-connect/token`;

    // NOTE: never log the request body or token response here — they contain
    // the client secret, refresh token, and access token.
    const response = await fetch(tokenEndpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        client_id: keycloakClientId,
        client_secret: keycloakClientSecret,
        refresh_token: refreshToken,
      }),
    });

    if (!response.ok) {
      const errorBody = await response.text().catch(() => "unable to read body");
      console.error(`[Token Refresh] Failed: status=${response.status} body=${errorBody}`);
      return createErrorToken(token, true);
    }

    const refreshedTokens: {
      access_token: string;
      refresh_token?: string;
      expires_in: number;
    } = await response.json();

    const nowInSeconds = Math.floor(Date.now() / 1000);

    return {
      ...token,
      accessToken: refreshedTokens.access_token,
      refreshToken: refreshedTokens.refresh_token ?? refreshToken,
      expiresAt: nowInSeconds + (refreshedTokens.expires_in || 300),
      error: token.error === AuthError.REFRESH_TOKEN_ERROR ? undefined : token.error,
    };
  } catch (error) {
    console.error("[Token Refresh] Request error:", error);
    return createErrorToken(token, true);
  }
}

/**
 * Creates an error token with cleared authentication data
 * @param token - Original token to preserve non-auth data
 * @param clearRefreshToken - Whether to clear the refresh token as well
 * @returns JWT with cleared auth data and error flag
 */
function createErrorToken(token: JWT, clearRefreshToken = false): JWT {
  return {
    ...token,
    accessToken: undefined,
    expiresAt: undefined,
    refreshToken: clearRefreshToken ? undefined : token.refreshToken,
    error: AuthError.REFRESH_TOKEN_ERROR,
  };
}

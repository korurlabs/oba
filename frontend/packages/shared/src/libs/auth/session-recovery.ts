import type { Session } from "next-auth";
import { getSession } from "next-auth/react";
import { AuthError } from "./types";

/**
 * Client-side session state shared by the API client, the session guard and
 * the logout helper.
 *
 * Module level on purpose: the axios interceptors live outside React, so the
 * state cannot sit in a context or a hook.
 */

/**
 * How a recovery after a rejected token ended:
 * - "refreshed": the session holds a new access token; the request may be sent again.
 * - "expired": the refresh token was refused; the user has to sign in again.
 * - "unavailable": no refresh could be attempted or it gave no answer (signing
 *   out, no session provider mounted, provider busy, network failure).
 */
export type SessionRecoveryOutcome = "refreshed" | "expired" | "unavailable";

/**
 * Resolves to the current session as the server sees it. null or undefined
 * means it gave no session: the request failed or the provider was busy.
 */
type SessionReader = () => Promise<Session | null | undefined>;

/**
 * Forces a token refresh through the session provider and resolves to the
 * session the server answered with.
 */
type SessionRefresher = SessionReader;

/**
 * How long a recovery outcome covers later 401s.
 *
 * After a successful refresh, a request sent with the old token before the
 * refresh finished can still come back 401; it is retried with the new token
 * instead of starting another refresh, so a burst of 401s costs one refresh,
 * and an endpoint that keeps answering 401 cannot drive more than one refresh
 * per window.
 *
 * After an expired session, every query that refetches or polls gets a 401
 * too. A forced refresh cannot help them, because the server dropped the
 * refresh token when Keycloak refused it, so they only read the session
 * instead. That read still notices a new sign-in, from another tab or another
 * local Oba app sharing the session cookie, and lets the request be
 * sent again with the new token.
 */
const RECOVERY_COOLDOWN_MS = 30_000;

/**
 * True from the moment the user chose Log out until the page unloads.
 *
 * The logout form submit and the page unload are not instant. A session read
 * or a token refresh started in that window would write a fresh session
 * cookie back after the server cleared it, so anything that would start one
 * checks this flag first.
 */
let signingOut = false;

let sessionRefresher: SessionRefresher | null = null;

/** The recovery every caller waits on while one is running. */
let inFlightRecovery: Promise<SessionRecoveryOutcome> | null = null;

/** When the last refresh succeeded, in epoch milliseconds; 0 when none counts. */
let lastRefreshedAt = 0;

/** When a recovery last found the session expired, in epoch milliseconds; 0 when none counts. */
let lastExpiredAt = 0;

/** Records that a logout is in progress in this tab. Never reset: the page unloads next. */
export function markSigningOut(): void {
  signingOut = true;
}

/** Whether this tab is on its way out of the app through Log out. */
export function isSigningOut(): boolean {
  return signingOut;
}

/**
 * Sets the function that forces a token refresh, or removes it with null. The
 * session guard registers the provider's update() here, because only the
 * provider can refresh the session and hand the result to every useSession()
 * consumer at once.
 */
export function registerSessionRefresher(refresher: SessionRefresher | null): void {
  sessionRefresher = refresher;
}

/**
 * Tries to recover a session whose access token the API refused, by forcing
 * one token refresh. Parallel callers share the same refresh, and a refresh
 * that succeeded within RECOVERY_COOLDOWN_MS counts for later callers too.
 * Within RECOVERY_COOLDOWN_MS of an expired session, the session is only read
 * again instead of refreshed.
 *
 * On "expired" the provider now holds a session with REFRESH_TOKEN_ERROR,
 * which makes the session guard ask the user to sign in again; the caller only
 * has to fail its request.
 */
export function recoverSessionAfterUnauthorized(): Promise<SessionRecoveryOutcome> {
  if (signingOut || sessionRefresher === null) {
    return Promise.resolve("unavailable");
  }
  if (inFlightRecovery !== null) {
    return inFlightRecovery;
  }
  const now: number = Date.now();
  if (now - lastRefreshedAt < RECOVERY_COOLDOWN_MS) {
    return Promise.resolve("refreshed");
  }

  const readSession: SessionReader =
    now - lastExpiredAt < RECOVERY_COOLDOWN_MS ? () => getSession({ broadcast: false }) : sessionRefresher;
  inFlightRecovery = runRecovery(readSession).finally(() => {
    inFlightRecovery = null;
  });
  return inFlightRecovery;
}

async function runRecovery(readSession: SessionReader): Promise<SessionRecoveryOutcome> {
  let session: Session | null | undefined;
  try {
    session = await readSession();
  } catch (error) {
    // The caller rejects its request with the original 401 on "unavailable",
    // so the failure still reaches the feature that sent it.
    console.warn("[SessionRecovery] session recovery failed", error);
    return "unavailable";
  }

  if (session === null || session === undefined) {
    return "unavailable";
  }
  if (!session.accessToken || session.error === AuthError.REFRESH_TOKEN_ERROR) {
    lastExpiredAt = Date.now();
    lastRefreshedAt = 0;
    return "expired";
  }
  // A usable token, whether refreshed or from a new sign-in, ends any expired
  // window at once.
  lastRefreshedAt = Date.now();
  lastExpiredAt = 0;
  return "refreshed";
}

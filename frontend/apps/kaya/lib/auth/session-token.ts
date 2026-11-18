import type { Session } from "next-auth";

/** The Auth.js session endpoint, which `getSession()` of next-auth/react reads too. */
const SESSION_PATH = "/api/auth/session";

/** The session could not be read, so whether the user is signed in is unknown. */
export class SessionUnavailableError extends Error {
  constructor(cause: unknown) {
    super("Kaya could not read your session. Check your connection and try again.", { cause });
    this.name = "SessionUnavailableError";
  }
}

/**
 * The access token to send to Kaya's API, or null when the user is signed out.
 *
 * `getSession()` of next-auth/react answers null both for a signed-out user
 * and for a session request that failed, for example one cut off by a page
 * reload. Sending the API call without a token in the second case earns a 401
 * for a session that is fine, and the identity gate then starts a sign-in the
 * sign-in route sends straight back. A failed read therefore rejects with
 * SessionUnavailableError instead, which callers show as an ordinary error.
 */
export async function sessionAccessToken(
  fetchSession: (input: string) => Promise<Response> = (input) =>
    fetch(input, { headers: { "Content-Type": "application/json" } })
): Promise<string | null> {
  let response: Response;
  let session: Session | null;
  try {
    response = await fetchSession(SESSION_PATH);
    session = response.ok ? ((await response.json()) as Session | null) : null;
  } catch (error) {
    throw new SessionUnavailableError(error);
  }
  if (!response.ok) {
    throw new SessionUnavailableError(new Error(`${SESSION_PATH} answered ${response.status}`));
  }
  if (session === null || session.error !== undefined) {
    return null;
  }
  return session.accessToken ?? null;
}

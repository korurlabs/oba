/**
 * A forced sign-in: the page asks for it when Kaya's API refused the
 * session's token, which the sign-in route would otherwise call a valid
 * session and send straight back to the page that was refused.
 */
export const REAUTH_PARAM = "reauth";

/**
 * Set while a forced sign-in runs. A second forced sign-in inside its
 * lifetime means fresh tokens were refused too, so the route stops with
 * SESSION_REJECTED instead of starting a third one.
 */
export const REAUTH_COOKIE = "kaya.reauth";

/** Long enough to cover a silent Keycloak round trip and the page's first API call. */
export const REAUTH_WINDOW_SECONDS = 60;

/** The `?error=` a page opens with when Kaya's API refused a session that was just renewed. */
export const SESSION_REJECTED = "SessionRejected";

/** The sign-in route, back to `callbackPath` afterwards; `reauth` skips the signed-in shortcut. */
export function signInUrl(callbackPath: string, reauth = false): string {
  const params = new URLSearchParams({ callbackUrl: callbackPath });
  if (reauth) {
    params.set(REAUTH_PARAM, "1");
  }
  return `/api/auth/login?${params.toString()}`;
}

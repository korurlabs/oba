import { markSigningOut } from "./session-recovery";

const LOGOUT_ROUTE = "/api/auth/logout";

/**
 * Starts Log out: a top-level POST to the app's logout route, which clears the
 * session cookies and sends the browser on to the Keycloak end-session page.
 *
 * A real form submit, not fetch, so that the browser navigates away: the
 * unload cancels every session read still in flight, which could otherwise
 * write the session cookie back after the server cleared it. The route only
 * accepts same-origin POSTs, so another site cannot end the user's session
 * with a link.
 *
 * No `signout` message goes to other tabs first: they would read the session
 * at once and could rewrite the cookie before the server clears it. They
 * notice the lost session through their own checks instead.
 */
export function submitLogout(): void {
  markSigningOut();

  const form: HTMLFormElement = document.createElement("form");
  form.method = "post";
  form.action = LOGOUT_ROUTE;
  form.hidden = true;
  document.body.appendChild(form);
  form.submit();
}

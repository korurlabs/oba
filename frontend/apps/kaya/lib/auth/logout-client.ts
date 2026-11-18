import { LOGOUT_PATH } from "./logout";

/**
 * Starts the sign-out: a top-level POST to Kaya's sign-out route, which clears
 * Kaya's session and sends the browser on to Keycloak's end-session page.
 *
 * A real form submit, not fetch, so that the browser navigates away: the
 * unload cancels every session read still in flight, which could otherwise
 * write the session cookie back after the route cleared it. The route accepts
 * same-origin POSTs only, so another site cannot sign the user out with a link.
 */
export function submitLogout(): void {
  const form = document.createElement("form");
  form.method = "post";
  form.action = LOGOUT_PATH;
  form.hidden = true;
  document.body.appendChild(form);
  form.submit();
}

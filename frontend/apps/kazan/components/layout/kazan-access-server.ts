/**
 * Kazan access on the Next server, for the dashboard layout and for every
 * page that calls the Kazan API while it renders. Imported by server
 * components only.
 *
 * The API refuses every Kazan call without a selected organization and in an
 * organization that does not grant the app. The browser then shows the
 * choose-an-organization screen or the no-access panel, so a server page that
 * still calls the API there only fills the server log with refusals, or fails
 * its render. Each such page asks `isKazanClosedOnServer` first.
 */

import { cache } from "react";
import { auth } from "@oba/shared/libs/auth";
import { usersApi } from "@oba/shared/features/users/hooks/use-get-me";
import {
  isKazanShut,
  kazanAccessOf,
  NO_ORGANIZATION_KAZAN_ACCESS,
  PENDING_KAZAN_ACCESS,
  type KazanAccess,
} from "./kazan-access";

/** The value `useGetMe` caches; the users api module has no package export of its own. */
type UserMeResponse = Awaited<ReturnType<typeof usersApi.getMe>>;

/**
 * The current user ("me") as `useGetMe` caches it, or null when it could not
 * be loaded. Once per request: the layout and the page of one request share
 * the call, and it reads the session itself, so every caller gets the same
 * answer. Only a successful response is returned, so a caller never seeds a
 * failure into the cache (see `prefetchMe`).
 */
export const loadServerMe = cache(async (): Promise<UserMeResponse | null> => {
  const accessToken = (await auth())?.accessToken;
  // Without a token the dashboard layout sends the user to sign in.
  if (!accessToken) return null;

  // `usersApi.getMe` never rejects: a failure resolves with `success: false`.
  const response = await usersApi.getMe(accessToken);
  if (response.success && response.data) return response;

  console.error(
    "[kazan-access] me load failed, Kazan access is decided on the client:",
    response.error ?? response.message
  );
  return null;
});

/**
 * The server's answer to "is Kazan open in the selected organization", with
 * the same rules as `useKazanAccess`. `pending` means the server could not
 * load the user; the browser then decides once it has loaded the user itself.
 * The user is loaded only when an organization is selected: without one the
 * answer is `noOrganization`.
 */
export const getServerKazanAccess = cache(async (): Promise<KazanAccess> => {
  const session = await auth();
  const selectedOrganizationId = session?.user?.defaultOrganizationId;
  if (!selectedOrganizationId) return NO_ORGANIZATION_KAZAN_ACCESS;

  const me = await loadServerMe();
  return me?.data ? kazanAccessOf(selectedOrganizationId, me.data) : PENDING_KAZAN_ACCESS;
});

/**
 * True when a server component must not call the Kazan API: no organization
 * is selected, or the selected one does not grant Kazan to the user. False
 * when the answer is not known: the call then goes out, and the API decides,
 * as before.
 */
export async function isKazanClosedOnServer(): Promise<boolean> {
  return isKazanShut(await getServerKazanAccess());
}

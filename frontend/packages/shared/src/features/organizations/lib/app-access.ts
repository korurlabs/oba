import { GRANTED_APP_OF, type ObaAppId } from "../../../constants/apps";
import type { GrantedApp, UserMe, UserOrganization } from "../../users/schema";
import { canManageOrganizations } from "./managed-organizations";

/**
 * True when the user can open `app` in this organization. A membership without
 * an `apps` list comes from an API that does not report app access, and counts
 * as no access: the API is the enforcement, so a client never shows more than
 * the API confirmed.
 */
export function hasApp(organization: UserOrganization, app: GrantedApp): boolean {
  return organization.apps?.includes(app) ?? false;
}

/**
 * The ACTIVE organizations where the user can open `app`. The API refuses every
 * call in a disabled organization, so a disabled one never counts. `/users/me`
 * lists only the user's active memberships.
 */
export function organizationsWithApp(
  me: UserMe | null | undefined,
  app: GrantedApp
): UserOrganization[] {
  return (me?.organizations ?? []).filter(
    (organization) => organization.organizationStatus === "ACTIVE" && hasApp(organization, app)
  );
}

/**
 * True when the app has something to show the user. Account is open to every
 * user. Ulak needs an organization the user manages. Every other app needs an
 * ACTIVE organization that grants it. False while the user is unknown (not
 * loaded, or the load failed); a caller that must not hide an app in that state
 * checks for a loaded user first.
 */
export function canOpenApp(me: UserMe | null | undefined, appId: ObaAppId): boolean {
  if (appId === "account") return true;
  if (appId === "ulak") return canManageOrganizations(me);
  const app = GRANTED_APP_OF[appId];
  return app !== undefined && organizationsWithApp(me, app).length > 0;
}

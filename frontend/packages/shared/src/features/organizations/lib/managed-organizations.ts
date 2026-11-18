import type { UserMe, UserOrganization } from "../../users/schema";

/**
 * The organizations the user manages: ACTIVE organizations where the user is
 * OWNER or ADMIN. The API refuses every admin call on a disabled organization,
 * so a disabled one never counts, whatever the role. `/users/me` lists only
 * the user's active memberships, so a disabled membership never counts either.
 * Admin shows only these organizations, and only their managers see its links.
 */
export function managedOrganizations(me: UserMe | null | undefined): UserOrganization[] {
  return (me?.organizations ?? []).filter(
    (organization) =>
      organization.organizationStatus === "ACTIVE" &&
      (organization.role === "OWNER" || organization.role === "ADMIN")
  );
}

/**
 * True when the user manages one or more organizations, so Admin has something
 * to show. False while the user is unknown (not loaded, or the load failed).
 */
export function canManageOrganizations(me: UserMe | null | undefined): boolean {
  return managedOrganizations(me).length > 0;
}

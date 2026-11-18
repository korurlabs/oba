import type {
  OrganizationRole,
  UserMe,
  UserOrganization,
} from "@oba/shared/features/users/schema";

/**
 * Roles that may see who has not posted a daily update. The server enforces
 * the same rule (403 otherwise); the client checks it first so that other
 * readers never send the request.
 */
const ORGANIZATION_ADMIN_ROLES: readonly OrganizationRole[] = ["OWNER", "ADMIN"];

/** The reader's memberships with role OWNER or ADMIN, sorted by name. */
export function adminOrganizations(me: UserMe | null | undefined): UserOrganization[] {
  return (me?.organizations ?? [])
    .filter((organization) => ORGANIZATION_ADMIN_ROLES.includes(organization.role))
    .sort((a, b) => a.name.localeCompare(b.name));
}

/** True only when the reader's role in `organizationId` is OWNER or ADMIN. */
export function isOrganizationAdmin(
  me: UserMe | null | undefined,
  organizationId: string | null | undefined,
): boolean {
  if (!organizationId) return false;
  const role = me?.organizations?.find((organization) => organization.id === organizationId)?.role;
  return !!role && ORGANIZATION_ADMIN_ROLES.includes(role);
}

/**
 * The organization a report opens on: the reader's default organization when
 * the reader administers it, else the first administered one by name.
 */
export function defaultAdminOrganizationId(me: UserMe | null | undefined): string | undefined {
  const defaultId = me?.defaultOrganizationId;
  if (defaultId && isOrganizationAdmin(me, defaultId)) return defaultId;
  return adminOrganizations(me)[0]?.id;
}

import type { OrganizationRole, UserMe } from "@oba/shared/features/users/schema";

/**
 * Roles that may read the cyberscore. It rates the organization's own
 * security posture and names its weak points, so the server refuses every
 * cyberscore read by a guest (403 `ORG_SEC_003`). The client checks the same
 * rule first, so a guest sees why the page is closed instead of a load error.
 */
const CYBERSCORE_READER_ROLES: readonly OrganizationRole[] = ["OWNER", "ADMIN", "MEMBER"];

/**
 * True only when it is known that the reader may not read the cyberscore of
 * the selected organization: the reader's role there is not a reader role.
 * False while the user is unknown or the organization is not among the
 * reader's memberships (a stale default); the API decides then.
 *
 * Not marked "use client": the cyberscore pages call it on the server to skip
 * their prefetch for a guest.
 */
export function isCyberscoreClosed(
  me: UserMe | null | undefined,
  organizationId: string | null | undefined
): boolean {
  if (!me || !organizationId) return false;
  const role = me.organizations?.find((organization) => organization.id === organizationId)?.role;
  return !!role && !CYBERSCORE_READER_ROLES.includes(role);
}

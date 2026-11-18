"use client";

import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import type { OrganizationRole } from "@oba/shared/features/users/schema";
import { useOrganizationId } from "./use-organization-id";

/** Roles that may delete a relation. The server enforces the same rule (403 otherwise). */
const ADMIN_ROLES: readonly OrganizationRole[] = ["OWNER", "ADMIN"];

/** Roles that may write a relation's attachments; a GUEST only reads. The server enforces the same rule. */
const MEMBER_ROLES: readonly OrganizationRole[] = ["OWNER", "ADMIN", "MEMBER"];

/** The user's role in the current organization; undefined while the user loads. */
function useOrganizationRole(): OrganizationRole | undefined {
  const organizationId = useOrganizationId();
  const { data: meResponse } = useGetMe();
  return meResponse?.data?.organizations?.find((organization) => organization.id === organizationId)
    ?.role;
}

/**
 * True when the user is OWNER or ADMIN of the current organization. False
 * while the user loads, so admin controls appear only once the role is known.
 */
export function useIsOrganizationAdmin(): boolean {
  const role = useOrganizationRole();
  return role !== undefined && ADMIN_ROLES.includes(role);
}

/**
 * True when the user is OWNER, ADMIN or MEMBER of the current organization.
 * False while the user loads, so write controls appear only once the role is
 * known.
 */
export function useIsOrganizationMember(): boolean {
  const role = useOrganizationRole();
  return role !== undefined && MEMBER_ROLES.includes(role);
}

/**
 * The decision "is Kaizen open in the selected organization", shared by the
 * client hook `useKaizenAccess` and the server loader in
 * `kaizen-access-server.ts`, so the server and the browser answer alike.
 *
 * Not marked "use client": a server component calls `kaizenAccessOf`, and in a
 * "use client" module it would be a client reference that cannot be called.
 */

import { hasApp } from "@oba/shared/features/organizations/lib/app-access";
import { GrantedApp, type UserMe, type UserOrganization } from "@oba/shared/features/users/schema";

/**
 * Whether Kaizen is open in the selected organization.
 *
 * - `pending`: the session or the user is still loading, so it is not known yet.
 * - `open`: Kaizen calls may go out.
 * - `noOrganization`: no organization is selected (the personal workspace).
 *   Every Kaizen board, ticket and chat belongs to an organization, and the
 *   API refuses those calls without one.
 * - `closed`: the selected organization does not grant Kaizen to the user;
 *   `organization` is that organization.
 */
export type KaizenAccess =
  | { status: "pending" }
  | { status: "open" }
  | { status: "noOrganization" }
  | { status: "closed"; organization: UserOrganization };

export const PENDING_KAIZEN_ACCESS: KaizenAccess = { status: "pending" };
export const OPEN_KAIZEN_ACCESS: KaizenAccess = { status: "open" };
export const NO_ORGANIZATION_KAIZEN_ACCESS: KaizenAccess = { status: "noOrganization" };

/**
 * True when it is known that no Kaizen call may go out: no organization is
 * selected, or the selected one does not grant Kaizen. False while pending,
 * because the answer may still be `open`.
 */
export function isKaizenShut(access: KaizenAccess): boolean {
  return access.status === "noOrganization" || access.status === "closed";
}

/**
 * The answer for a loaded user. Kaizen needs a selected organization. It is
 * open when the selected organization is not among the user's memberships (a
 * stale default; the API decides), and closed only in a membership that does
 * not grant Kaizen.
 */
export function kaizenAccessOf(
  selectedOrganizationId: string | null | undefined,
  me: UserMe
): KaizenAccess {
  if (!selectedOrganizationId) return NO_ORGANIZATION_KAIZEN_ACCESS;
  const organization = me.organizations?.find((org) => org.id === selectedOrganizationId);
  if (!organization || hasApp(organization, GrantedApp.KAIZEN)) return OPEN_KAIZEN_ACCESS;
  return { status: "closed", organization };
}

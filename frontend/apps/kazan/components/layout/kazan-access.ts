/**
 * The decision "is Kazan open in the selected organization", shared by the
 * client hook `useKazanAccess` and the server loader in
 * `kazan-access-server.ts`, so the server and the browser answer alike.
 *
 * Not marked "use client": a server component calls `kazanAccessOf`, and in a
 * "use client" module it would be a client reference that cannot be called.
 */

import { hasApp } from "@oba/shared/features/organizations/lib/app-access";
import { GrantedApp, type UserMe, type UserOrganization } from "@oba/shared/features/users/schema";

/**
 * Whether Kazan is open in the selected organization.
 *
 * - `pending`: the session or the user is still loading, so it is not known yet.
 * - `open`: Kazan calls may go out.
 * - `noOrganization`: no organization is selected (the personal workspace).
 *   Every Kazan board, ticket and chat belongs to an organization, and the
 *   API refuses those calls without one.
 * - `closed`: the selected organization does not grant Kazan to the user;
 *   `organization` is that organization.
 */
export type KazanAccess =
  | { status: "pending" }
  | { status: "open" }
  | { status: "noOrganization" }
  | { status: "closed"; organization: UserOrganization };

export const PENDING_KAZAN_ACCESS: KazanAccess = { status: "pending" };
export const OPEN_KAZAN_ACCESS: KazanAccess = { status: "open" };
export const NO_ORGANIZATION_KAZAN_ACCESS: KazanAccess = { status: "noOrganization" };

/**
 * True when it is known that no Kazan call may go out: no organization is
 * selected, or the selected one does not grant Kazan. False while pending,
 * because the answer may still be `open`.
 */
export function isKazanShut(access: KazanAccess): boolean {
  return access.status === "noOrganization" || access.status === "closed";
}

/**
 * The answer for a loaded user. Kazan needs a selected organization. It is
 * open when the selected organization is not among the user's memberships (a
 * stale default; the API decides), and closed only in a membership that does
 * not grant Kazan.
 */
export function kazanAccessOf(
  selectedOrganizationId: string | null | undefined,
  me: UserMe
): KazanAccess {
  if (!selectedOrganizationId) return NO_ORGANIZATION_KAZAN_ACCESS;
  const organization = me.organizations?.find((org) => org.id === selectedOrganizationId);
  if (!organization || hasApp(organization, GrantedApp.KAZAN)) return OPEN_KAZAN_ACCESS;
  return { status: "closed", organization };
}

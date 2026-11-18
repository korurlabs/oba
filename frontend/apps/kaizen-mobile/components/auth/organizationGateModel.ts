import type { UserOrganization } from "@oba/shared/features/users/schema";

import { grantsKaizen } from "@/lib/kaizenAccess";
import { pickActiveOrganizationId } from "@/lib/organizationStore";

/** The state of `GET /users/me` as the gate needs it. */
export type MeState =
  | { status: "pending" }
  | { status: "error" }
  | {
      status: "success";
      /** The user's memberships (`organizations` of `GET /users/me`). */
      organizations: readonly UserOrganization[];
      defaultOrganizationId: string | null;
    };

/**
 * - `app`: the signed-in app, for the active organization.
 * - `loading`: no organization is known yet and the membership list is on its way.
 * - `error`: no organization is known and the membership list failed to load.
 * - `no-organization`: the user belongs to no organization (onboarding).
 * - `no-kaizen`: the user belongs to organizations, but none grants Kaizen.
 */
export type OrganizationGateView = "app" | "loading" | "error" | "no-organization" | "no-kaizen";

export interface OrganizationGateDecision {
  view: OrganizationGateView;
  /**
   * The organization that should be active: an id, null when no organization
   * of the user grants Kaizen, or undefined while the membership list is
   * unknown. When it differs from the active one, the gate switches (or
   * clears) the organization.
   */
  target: string | null | undefined;
  /**
   * The name of the active organization when the user still belongs to it but
   * it no longer grants Kaizen, so the gate can say why it moves the user out;
   * null otherwise.
   */
  kaizenLostIn: string | null;
}

/**
 * Decides what the signed-in app shows and which organization it should use.
 *
 * Only an organization that grants Kaizen can be active, because the API
 * refuses every Kaizen call in any other. An organization stored on this
 * phone opens the app at once and is checked against the membership list as
 * it arrives, so a restart does not wait for the network. Without one, the
 * app waits for the list, then makes the stored, the default or the first
 * organization that grants Kaizen active. A user with no organization sees the
 * onboarding state, and a user whose organizations all withhold Kaizen sees
 * the no-access state; both also when the stored organization no longer
 * qualifies.
 */
export function decideOrganizationGate(
  activeOrganizationId: string | null,
  me: MeState
): OrganizationGateDecision {
  if (me.status === "success") {
    const kaizenLostIn: string | null = nameOfClosedActive(activeOrganizationId, me.organizations);
    if (me.organizations.length === 0) {
      return { view: "no-organization", target: null, kaizenLostIn };
    }
    const target: string | null = pickActiveOrganizationId({
      memberOrganizationIds: me.organizations
        .filter(grantsKaizen)
        .map((organization) => organization.id),
      storedOrganizationId: activeOrganizationId,
      defaultOrganizationId: me.defaultOrganizationId,
    });
    if (target === null) return { view: "no-kaizen", target, kaizenLostIn };
    return { view: activeOrganizationId === null ? "loading" : "app", target, kaizenLostIn };
  }
  if (activeOrganizationId !== null) return { view: "app", target: undefined, kaizenLostIn: null };
  return {
    view: me.status === "pending" ? "loading" : "error",
    target: undefined,
    kaizenLostIn: null,
  };
}

/** The name of the active organization when it is a membership without Kaizen. */
function nameOfClosedActive(
  activeOrganizationId: string | null,
  organizations: readonly UserOrganization[]
): string | null {
  if (activeOrganizationId === null) return null;
  const active: UserOrganization | undefined = organizations.find(
    (organization) => organization.id === activeOrganizationId
  );
  return active !== undefined && !grantsKaizen(active) ? active.name : null;
}

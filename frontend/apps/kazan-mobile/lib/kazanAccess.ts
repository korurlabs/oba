import { GrantedApp, type UserMe, type UserOrganization } from "@oba/shared/features/users/schema";

/*
 * Who may use Kazan. An owner or admin grants Kazan per member, and the API
 * refuses every Kazan call of a member without it. The app reads the same
 * rule as the web (`hasApp` and `organizationsWithApp` of the shared package)
 * to open only organizations that grant Kazan and to mark people who cannot
 * be added to a board, a ticket or a channel. The shared module is not
 * imported: its app constants bring web icons and web URLs into the native
 * bundle. No native or React import, so `bun test` covers it.
 */

/**
 * The 422 refusal of a write that attaches a member who cannot open Kazan in
 * the organization: a board member, a ticket assignee, a private channel
 * member, a chat mention or a pull request reviewer.
 */
export const MEMBER_WITHOUT_KAZAN_ACCESS_CODE = "APP_VAL_002";

/**
 * True when the user can open Kazan in this organization: it is ACTIVE and
 * its `apps` list holds Kazan. The API refuses every call in a disabled
 * organization. A membership without an `apps` list comes from an API that
 * does not report app access and counts as no access, because the API is the
 * enforcement and the app never offers more than the API confirmed.
 */
export function grantsKazan(organization: UserOrganization): boolean {
  return (
    organization.organizationStatus === "ACTIVE" &&
    (organization.apps?.includes(GrantedApp.KAZAN) ?? false)
  );
}

/** Ids of the user's organizations that grant Kazan, in the order of `GET /users/me`. */
export function kazanOrganizationIds(me: UserMe): string[] {
  return (me.organizations ?? []).filter(grantsKazan).map((organization) => organization.id);
}

/**
 * True when a member list of the API marks this member as able to open
 * Kazan. A response without the flag counts as no access, as a missing app
 * list of the current user does, so a picker never offers a member the
 * server may refuse.
 */
export function hasKazanAccess(member: { kazanAccess?: boolean }): boolean {
  return member.kazanAccess === true;
}

import { z } from "zod";

// ── Enum Schemas ───────────────────────────────────────────────────
/** The roles Kaya grants rights to; a platform GUEST gets no Kaya access. */
export const OrganizationRoleSchema = z.enum(["OWNER", "ADMIN", "MEMBER"]);
export type OrganizationRole = z.infer<typeof OrganizationRoleSchema>;
export const OrganizationRole = OrganizationRoleSchema.enum;

/** Every role a platform membership may hold. */
export const PlatformOrganizationRoleSchema = z.enum(["OWNER", "ADMIN", "MEMBER", "GUEST"]);
export type PlatformOrganizationRole = z.infer<typeof PlatformOrganizationRoleSchema>;
export const PlatformOrganizationRole = PlatformOrganizationRoleSchema.enum;

export const UserStatusSchema = z.enum(["PENDING_ONBOARDING", "ACTIVE", "DISABLED"]);
export type UserStatus = z.infer<typeof UserStatusSchema>;
export const UserStatus = UserStatusSchema.enum;

export const OrganizationStatusSchema = z.enum(["ACTIVE", "DISABLED"]);
export type OrganizationStatus = z.infer<typeof OrganizationStatusSchema>;
export const OrganizationStatus = OrganizationStatusSchema.enum;

// ── Response Schemas ────────────────────────────────────────────────
export const membershipResponseSchema = z.object({
  organizationId: z.string(),
  name: z.string(),
  role: OrganizationRoleSchema,
});

/** The app value of `/users/me` that opens Kaya in an organization. */
const KAYA_APP = "KAYA";

/**
 * An organization of the platform user, as `/users/me` lists it. The status is
 * optional so that Kaya still reads an API that does not send it yet; a missing
 * status counts as not ACTIVE. `apps` lists the apps the user may open there.
 * It is read as plain strings, so an app that Kaya does not know cannot break
 * the parse, and it is optional for the same reason as the status: a missing
 * list counts as no Kaya.
 */
const platformOrganizationSchema = z.object({
  id: z.string(),
  name: z.string(),
  role: PlatformOrganizationRoleSchema,
  organizationStatus: OrganizationStatusSchema.optional(),
  apps: z.array(z.string()).optional(),
});

type PlatformOrganization = z.infer<typeof platformOrganizationSchema>;

/** Whether the user may open Kaya in the organization: its `apps` must list Kaya. */
function opensKaya(organization: PlatformOrganization): boolean {
  return organization.apps?.includes(KAYA_APP) ?? false;
}

/**
 * Whether Kaya is closed to a member who would otherwise open it: a role Kaya
 * accepts in an organization that is not disabled, but no Kaya grant. A guest
 * never opens Kaya, and a disabled organization lists no app, so neither counts
 * as a withheld grant.
 */
function isKayaWithheld(organization: PlatformOrganization): boolean {
  return (
    organization.role !== PlatformOrganizationRole.GUEST &&
    organization.organizationStatus !== OrganizationStatus.DISABLED &&
    !opensKaya(organization)
  );
}

/**
 * Whether the platform organization is one the user manages in Admin: ACTIVE,
 * with role OWNER or ADMIN. The same rule as `managedOrganizations` of
 * `@oba/shared`, which Kaya does not depend on.
 */
function isManaged(organization: PlatformOrganization): boolean {
  return (
    organization.organizationStatus === OrganizationStatus.ACTIVE &&
    (organization.role === PlatformOrganizationRole.OWNER ||
      organization.role === PlatformOrganizationRole.ADMIN)
  );
}

const platformProfileSchema = z.object({
  firstName: z.string().nullish(),
  lastName: z.string().nullish(),
});

/** The first and last name of a profile, or null when it holds neither. */
export function personName(
  firstName: string | null | undefined,
  lastName: string | null | undefined
): string | null {
  const name = [firstName, lastName]
    .map((part: string | null | undefined) => part?.trim() ?? "")
    .filter((part: string) => part !== "")
    .join(" ");
  return name === "" ? null : name;
}

/**
 * The platform's `/users/me` answer, read into what Kaya needs: the user, its
 * default organization and the organizations Kaya may open. A GUEST
 * membership is left out, since Kaya refuses a guest, and so is a membership
 * whose `apps` does not list Kaya, since the API refuses it there.
 * `kayaWithheld` tells that a member was left out only for a missing Kaya
 * grant, so the gate can say that an owner or admin grants it.
 * `managesOrganizations` tells whether Admin, which only organization managers
 * may open, is offered; it reads every organization, Kaya grant or not.
 */
export const userMeResponseSchema = z
  .object({
    id: z.string(),
    email: z.string(),
    userStatus: UserStatusSchema,
    profile: platformProfileSchema.nullish(),
    defaultOrganizationId: z.string().nullish(),
    organizations: z.array(platformOrganizationSchema).nullish(),
  })
  .transform((me) => ({
    id: me.id,
    email: me.email,
    userStatus: me.userStatus,
    displayName: personName(me.profile?.firstName, me.profile?.lastName),
    defaultOrganizationId: me.defaultOrganizationId ?? null,
    memberships: (me.organizations ?? []).flatMap((organization) =>
      organization.role === PlatformOrganizationRole.GUEST || !opensKaya(organization)
        ? []
        : [{ organizationId: organization.id, name: organization.name, role: organization.role }]
    ),
    kayaWithheld: (me.organizations ?? []).some(isKayaWithheld),
    managesOrganizations: (me.organizations ?? []).some(isManaged),
  }));

// ── Inferred Types ──────────────────────────────────────────────────
export type MembershipResponse = z.infer<typeof membershipResponseSchema>;
export type UserMeResponse = z.output<typeof userMeResponseSchema>;

/** Rank of each role, so a control can ask for "at least ADMIN". */
const ROLE_LEVEL: Readonly<Record<OrganizationRole, number>> = { OWNER: 3, ADMIN: 2, MEMBER: 1 };

/** Whether a role includes the rights of another, as the server's role check decides it. */
export function hasRole(role: OrganizationRole, required: OrganizationRole): boolean {
  return ROLE_LEVEL[role] >= ROLE_LEVEL[required];
}

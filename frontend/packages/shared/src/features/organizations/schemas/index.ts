import { z } from "zod";
import { instantSchema } from "@oba/shared/lib/date-schemas";
import {
  grantedAppSchema,
  invitationResponseSchema,
  invitationStatusSchema,
  organizationRoleSchema,
  organizationStatusSchema,
} from "@oba/shared/features/users/schema";

// ── Enum Schemas ───────────────────────────────────────────────────

/** The organization status enum is owned by the shared users schema ("me" carries it too). */
export const OrganizationStatusSchema = organizationStatusSchema;
export type OrganizationStatus = z.infer<typeof OrganizationStatusSchema>;
export const OrganizationStatus = OrganizationStatusSchema.enum;

export const OrganizationMemberStatusSchema = z.enum(["ACTIVE", "DISABLED"]);
export type OrganizationMemberStatus = z.infer<typeof OrganizationMemberStatusSchema>;
export const OrganizationMemberStatus = OrganizationMemberStatusSchema.enum;

/** The role enum is owned by the shared users schema ("me" carries it too). */
export const OrganizationRoleSchema = organizationRoleSchema;
export type OrganizationRole = z.infer<typeof OrganizationRoleSchema>;
export const OrganizationRole = OrganizationRoleSchema.enum;

/** The invitation status enum is owned by the shared users schema. */
export const InvitationStatusSchema = invitationStatusSchema;
export type InvitationStatus = z.infer<typeof InvitationStatusSchema>;
export const InvitationStatus = InvitationStatusSchema.enum;

/** The grantable app enum is owned by the shared users schema ("me" lists apps too). */
export const GrantedAppSchema = grantedAppSchema;
export type GrantedApp = z.infer<typeof GrantedAppSchema>;
export const GrantedApp = GrantedAppSchema.enum;

/** EVERYONE stands for every member and cannot be renamed, deleted or given members. */
export const OrganizationGroupTypeSchema = z.enum(["EVERYONE", "CUSTOM"]);
export type OrganizationGroupType = z.infer<typeof OrganizationGroupTypeSchema>;
export const OrganizationGroupType = OrganizationGroupTypeSchema.enum;

export const AppGrantTargetTypeSchema = z.enum(["USER", "GROUP"]);
export type AppGrantTargetType = z.infer<typeof AppGrantTargetTypeSchema>;
export const AppGrantTargetType = AppGrantTargetTypeSchema.enum;

// ── Request Schemas ─────────────────────────────────────────────────

/**
 * Body of both POST /organizations and PUT /organizations/{organizationId}.
 * The update is a full update: every field is sent on every call, and null
 * clears an optional field. An empty string is rejected by the backend.
 */
export const organizationRequestSchema = z.object({
  name: z.string(),
  description: z.string().nullable(),
  industry: z.string().nullable(),
  companySize: z.string().nullable(),
  country: z.string().nullable(),
  website: z.string().nullable(),
});

export const updateMemberRoleRequestSchema = z.object({
  role: OrganizationRoleSchema,
});

/**
 * Body of both POST /groups and PUT /groups/{groupId}. The update is a full
 * update: null clears the description. An empty description is rejected by
 * the backend.
 */
export const organizationGroupRequestSchema = z.object({
  name: z.string(),
  description: z.string().nullable(),
});

export const organizationGroupMemberAddRequestSchema = z.object({
  userIds: z.array(z.guid()).min(1).max(100),
});

/**
 * Body of POST /app-grants. A grant names exactly one target, a user or a
 * group; the backend refuses a body with both or neither.
 */
export const appGrantCreateRequestSchema = z.union([
  z.object({ app: GrantedAppSchema, userId: z.guid() }).strict(),
  z.object({ app: GrantedAppSchema, groupId: z.guid() }).strict(),
]);

// ── Form Schemas ────────────────────────────────────────────────────

/**
 * Shared by the create form and the detail form. An empty optional field is
 * held as "" here and converted to null by toOrganizationRequest.
 */
export const createOrganizationFormSchema = (t: (key: string) => string) =>
  z.object({
    name: z.string().trim().min(1, t("nameRequired")).max(255, t("nameMaxLength")),
    description: z.string().max(512, t("descriptionMaxLength")).optional().or(z.literal("")),
    industry: z.string().max(100, t("industryMaxLength")).optional().or(z.literal("")),
    companySize: z.string().max(50, t("companySizeMaxLength")).optional().or(z.literal("")),
    country: z.string().max(3, t("countryMaxLength")).optional().or(z.literal("")),
    website: z.string().max(512, t("websiteMaxLength")).optional().or(z.literal("")),
  });

export const createInvitationFormSchema = (t: (key: string) => string) =>
  z.object({
    email: z.string().min(1, t("invitations.emailRequired")).email(t("invitations.emailInvalid")),
    role: OrganizationRoleSchema,
  });

/**
 * Create and rename form of a group. An empty description is held as "" here
 * and converted to null by toOrganizationGroupRequest.
 */
export const organizationGroupFormSchema = (t: (key: string) => string) =>
  z.object({
    name: z.string().trim().min(1, t("groups.nameRequired")).max(100, t("groups.nameMaxLength")),
    description: z.string().max(500, t("groups.descriptionMaxLength")).optional().or(z.literal("")),
  });

/** Grant form: one app and one target, a member or a group, picked by its type. */
export const appGrantFormSchema = (t: (key: string) => string) =>
  z.object({
    app: z.enum(GrantedAppSchema.options, { error: t("access.appRequired") }),
    targetType: z.enum(AppGrantTargetTypeSchema.options, { error: t("access.targetTypeRequired") }),
    targetId: z.guid(t("access.targetRequired")),
  });

// ── Response Schemas ────────────────────────────────────────────────

export const organizationDetailSchema = z.object({
  id: z.string().uuid(),
  createdAt: z.string(),
  updatedAt: z.string(),
  status: OrganizationStatusSchema,
  name: z.string().nullable(),
  description: z.string().nullable(),
  industry: z.string().nullable(),
  companySize: z.string().nullable(),
  country: z.string().nullable(),
  website: z.string().nullable(),
});

export const organizationMemberSchema = z.object({
  id: z.string().uuid(),
  userId: z.string().uuid(),
  firstName: z.string(),
  lastName: z.string(),
  email: z.string(),
  role: OrganizationRoleSchema,
  status: OrganizationMemberStatusSchema,
});

/** The invitation shape is owned by the shared users schema. */
export const invitationSchema = invitationResponseSchema;

// App values in responses are plain strings, so that an app this client does
// not know yet cannot fail the parse; the forms send only GrantedApp values.

export const organizationGroupSchema = z.object({
  id: z.guid(),
  name: z.string(),
  description: z.string().nullable(),
  type: OrganizationGroupTypeSchema,
  memberCount: z.number(),
  apps: z.array(z.string()),
  createdAt: instantSchema,
});

export const organizationGroupMemberSchema = z.object({
  userId: z.guid(),
  fullName: z.string(),
  email: z.string(),
  /** Null only when the membership data is inconsistent. */
  role: OrganizationRoleSchema.nullable(),
});

/** A grant to a user fills the user fields; a grant to a group fills the group fields. */
export const appGrantSchema = z.object({
  id: z.guid(),
  app: z.string(),
  targetType: AppGrantTargetTypeSchema,
  userId: z.guid().nullable(),
  userFullName: z.string().nullable(),
  groupId: z.guid().nullable(),
  groupName: z.string().nullable(),
  groupType: OrganizationGroupTypeSchema.nullable(),
  createdAt: instantSchema,
});

/** The apps an ACTIVE member can open, whatever the grant that gives them. */
export const memberAppAccessSchema = z.object({
  userId: z.guid(),
  fullName: z.string(),
  role: OrganizationRoleSchema,
  apps: z.array(z.string()),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type OrganizationDetail = z.infer<typeof organizationDetailSchema>;
export type OrganizationFormValues = z.infer<ReturnType<typeof createOrganizationFormSchema>>;
export type OrganizationRequest = z.infer<typeof organizationRequestSchema>;
export type OrganizationUpdateRequest = OrganizationRequest;
export type OrganizationCreateRequest = OrganizationRequest;
export type OrganizationMember = z.infer<typeof organizationMemberSchema>;
export type Invitation = z.infer<typeof invitationSchema>;
export type CreateInvitationForm = z.infer<ReturnType<typeof createInvitationFormSchema>>;
export type UpdateMemberRoleRequest = z.infer<typeof updateMemberRoleRequestSchema>;
export type OrganizationGroupRequest = z.infer<typeof organizationGroupRequestSchema>;
export type OrganizationGroupMemberAddRequest = z.infer<
  typeof organizationGroupMemberAddRequestSchema
>;
export type AppGrantCreateRequest = z.infer<typeof appGrantCreateRequestSchema>;
export type OrganizationGroupFormValues = z.infer<ReturnType<typeof organizationGroupFormSchema>>;
export type AppGrantFormValues = z.infer<ReturnType<typeof appGrantFormSchema>>;
export type OrganizationGroup = z.infer<typeof organizationGroupSchema>;
export type OrganizationGroupMember = z.infer<typeof organizationGroupMemberSchema>;
export type AppGrant = z.infer<typeof appGrantSchema>;
export type MemberAppAccess = z.infer<typeof memberAppAccessSchema>;

import z from "zod";

/**
 * `DISABLED` is what the backend sends for a closed account. `DELETED` was its
 * name before the account lifecycle change; the web auth code still compares
 * against it, so it stays accepted.
 */
export const userStatusSchema = z.enum([
  "PENDING_ONBOARDING",
  "ACTIVE",
  "DISABLED",
  "DELETED",
]);

export const userPositionSchema = z.enum([
  "PROCUREMENT",
  "SALES",
  "PRODUCTION",
  "MARKETING",
  "OFFICE",
  "LOGISTIC",
  "WAREHOUSE",
  "MANAGEMENT",
  "ADMINISTRATION",
  "ICT",
  "RESEARCH_AND_DEVELOPMENT",
  "OTHER",
]);

export const userPreferredLanguageSchema = z.enum([
  "DUTCH",
  "ENGLISH",
  "DEUTSCH",
  "FRENCH",
  "SPANISH",
  "ITALIAN",
  "TURKISH",
]);

export const userGenderSchema = z.enum(["MALE", "FEMALE", "UNKNOWN"]);

export const userPreferencesSchema = z.record(z.string(), z.unknown());

export const userProfileSchema = z.object({
  firstName: z.string().nullable(),
  lastName: z.string().nullable(),
  phoneNumber: z.string().nullable(),
  userPosition: userPositionSchema.nullable(),
  userPreferredLanguage: userPreferredLanguageSchema.nullable(),
  userGender: userGenderSchema.nullable(),
});

/**
 * Whether a user is a person or an agent. An agent is a user an organization
 * admin registers in Ulak; it works the tickets it is assigned to.
 */
export const userKindSchema = z.enum(["HUMAN", "AGENT"]);

/** The job an agent is registered for. */
export const agentRoleSchema = z.enum(["CYBERSECURITY_ENGINEER", "SALES_ENGINEER"]);

/** Who runs an agent: a vendor routine Oba fires, or Oba itself through a model API. */
export const agentFlowSchema = z.enum(["ROUTINE", "API"]);

/** The vendor behind an agent. */
export const agentProviderSchema = z.enum(["ANTHROPIC", "OPENAI", "XAI", "DEEPSEEK"]);

/**
 * The kind fields the member, board member, candidate and ticket assignee
 * responses carry. The three agent fields are null for a person.
 */
export const userKindFieldsSchema = z.object({
  kind: userKindSchema,
  agentRole: agentRoleSchema.nullable(),
  agentFlow: agentFlowSchema.nullable(),
  agentProvider: agentProviderSchema.nullable(),
});

export const organizationRoleSchema = z.enum(["OWNER", "ADMIN", "MEMBER", "GUEST"]);

export const organizationStatusSchema = z.enum(["ACTIVE", "DISABLED"]);

/**
 * The apps an owner or admin can grant to a member or a group. Ulak and
 * Account are not grantable: Ulak is gated by the organization role, and
 * Account is open to every user.
 */
export const grantedAppSchema = z.enum(["KAZAN", "KAYA", "KALKAN", "OTAK"]);

/**
 * Ids are checked with `z.guid()`, not `z.uuid()`: the backend accepts any
 * 8-4-4-4-12 hex id, and seeded rows such as `b0000000-0000-0000-0000-000000000001`
 * carry no RFC 9562 version, which `z.uuid()` rejects and which would fail the
 * whole `GET /users/me` response.
 */
export const userOrganizationSchema = z.object({
  id: z.guid(),
  name: z.string(),
  role: organizationRoleSchema,
  organizationStatus: organizationStatusSchema,
  /**
   * The apps the user can open in this organization. Read as plain strings, so
   * that an app value this client does not know yet cannot fail the parse of
   * the current user. Optional, because an API without app access omits it;
   * a missing list means no app.
   */
  apps: z.array(z.string()).optional(),
});

export const invitationStatusSchema = z.enum([
  "PENDING",
  "ACCEPTED",
  "EXPIRED",
  "DECLINED",
  "REVOKED",
]);

export const invitationResponseSchema = z.object({
  id: z.guid(),
  organizationId: z.guid(),
  organizationName: z.string(),
  email: z.string(),
  role: organizationRoleSchema,
  status: invitationStatusSchema,
  invitedByEmail: z.string(),
  acceptedAt: z.string().nullable(),
  expiresAt: z.string().nullable(),
  declinedAt: z.string().nullable(),
  createdAt: z.string(),
});

export const userMeSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  email: z.string(),
  userStatus: userStatusSchema,
  profile: userProfileSchema.nullable(),
  defaultOrganizationId: z.string().nullable(),
  userPreferences: userPreferencesSchema.nullable(),
  // Both lists are nullable in the backend response: the profile update
  // answers with `pendingInvitations: null`.
  organizations: z.array(userOrganizationSchema).nullish(),
  pendingInvitations: z.array(invitationResponseSchema).nullish(),
});

export const userProfileUpdateSchema = z.object({
  firstName: z.string().max(255).nullable().optional(),
  lastName: z.string().max(255).nullable().optional(),
  phoneNumber: z.string().max(30).nullable().optional(),
  userPosition: userPositionSchema.nullable().optional(),
  userPreferredLanguage: userPreferredLanguageSchema.nullable().optional(),
  userGender: userGenderSchema.nullable().optional(),
});

export const userUpdateSchema = z.object({
  email: z.string().email().max(254).nullable().optional(),
  profile: userProfileUpdateSchema.nullable().optional(),
  userPreferences: userPreferencesSchema.nullable().optional(),
  defaultOrganizationId: z.guid().nullable().optional(),
});

export type UserStatus = z.infer<typeof userStatusSchema>;
export type UserPosition = z.infer<typeof userPositionSchema>;
export type UserPreferredLanguage = z.infer<typeof userPreferredLanguageSchema>;
export type UserGender = z.infer<typeof userGenderSchema>;
export type UserPreferences = z.infer<typeof userPreferencesSchema>;
export type UserProfile = z.infer<typeof userProfileSchema>;
export type UserProfileUpdate = z.infer<typeof userProfileUpdateSchema>;
export type OrganizationRole = z.infer<typeof organizationRoleSchema>;
export type OrganizationStatus = z.infer<typeof organizationStatusSchema>;
export type GrantedApp = z.infer<typeof grantedAppSchema>;
export const GrantedApp = grantedAppSchema.enum;
export type UserOrganization = z.infer<typeof userOrganizationSchema>;
export type UserUpdate = z.infer<typeof userUpdateSchema>;
export type UserMe = z.infer<typeof userMeSchema>;
export type UserKind = z.infer<typeof userKindSchema>;
export const UserKind = userKindSchema.enum;
export type AgentRole = z.infer<typeof agentRoleSchema>;
export const AgentRole = agentRoleSchema.enum;
export type AgentFlow = z.infer<typeof agentFlowSchema>;
export const AgentFlow = agentFlowSchema.enum;
export type AgentProvider = z.infer<typeof agentProviderSchema>;
export const AgentProvider = agentProviderSchema.enum;
export type UserKindFields = z.infer<typeof userKindFieldsSchema>;
export type InvitationStatus = z.infer<typeof invitationStatusSchema>;
export type InvitationResponse = z.infer<typeof invitationResponseSchema>;
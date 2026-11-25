import { z } from "zod";
import { instantSchema } from "@oba/shared/lib/date-schemas";
import { GrantedAppSchema } from "@oba/shared/features/organizations/schemas";
import {
  agentFlowSchema,
  agentProviderSchema,
  agentRoleSchema,
} from "@oba/shared/features/users/schema";

/**
 * Agents: AI agents an organization registers as users of kind AGENT. They
 * are read and written only by an OWNER or ADMIN, through
 * `/organizations/{organizationId}/agents`.
 */

// ── Enum Schemas ────────────────────────────────────────────────────

// Flow, provider and role are the user-level agent enums in the shared users schema.

/** Status of the agent's user. An agent is created ACTIVE; PENDING_ONBOARDING belongs to people. */
export const AgentStatusSchema = z.enum(["PENDING_ONBOARDING", "ACTIVE", "DISABLED"]);
export type AgentStatus = z.infer<typeof AgentStatusSchema>;
export const AgentStatus = AgentStatusSchema.enum;

/** The statuses an ADMIN can set on an agent. */
export const AgentStatusUpdateSchema = z.enum(["ACTIVE", "DISABLED"]);
export type AgentStatusUpdate = z.infer<typeof AgentStatusUpdateSchema>;

/** The vendor's answer to the last fire of the agent's routine. */
export const RoutineFireStatusSchema = z.enum(["FIRED", "FAILED", "RATE_LIMITED", "UNAUTHORIZED"]);
export type RoutineFireStatus = z.infer<typeof RoutineFireStatusSchema>;
export const RoutineFireStatus = RoutineFireStatusSchema.enum;

// ── Field limits ───────────────────────────────────────────────────

/** The backend's bean validation limits, so the forms refuse what the API would. */
export const AGENT_LIMITS = {
  name: 100,
  routineId: 128,
  routineSecret: 512,
} as const;

// ── Request Schemas ─────────────────────────────────────────────────

/** Body of POST /organizations/{organizationId}/agents. */
export const agentCreateRequestSchema = z.object({
  name: z.string(),
  flow: agentFlowSchema,
  provider: agentProviderSchema,
  role: agentRoleSchema,
  apps: z.array(GrantedAppSchema),
});

/** Body of PUT /organizations/{organizationId}/agents/{agentId}: flow and provider are fixed. */
export const agentUpdateRequestSchema = agentCreateRequestSchema.omit({
  flow: true,
  provider: true,
});

/** Body of PUT /organizations/{organizationId}/agents/{agentId}/routine. */
export const agentRoutineRequestSchema = z.object({
  routineId: z.string(),
  routineSecret: z.string(),
});

// ── Form Schemas ────────────────────────────────────────────────────

/**
 * Create and edit form of an agent. Editing keeps the agent's own flow and
 * provider in the form, read-only, and sends neither. Values are checked
 * trimmed, because they are sent trimmed.
 */
export const agentFormSchema = (t: (key: string) => string) =>
  z.object({
    name: z
      .string()
      .trim()
      .min(1, t("form.nameRequired"))
      .max(AGENT_LIMITS.name, t("form.nameMaxLength")),
    flow: z.enum(agentFlowSchema.options, { error: t("form.flowRequired") }),
    provider: z.enum(agentProviderSchema.options, { error: t("form.providerRequired") }),
    role: z.enum(agentRoleSchema.options, { error: t("form.roleRequired") }),
    apps: z.array(z.enum(GrantedAppSchema.options)).min(1, t("form.appsRequired")),
  });

/**
 * Routine form of a ROUTINE agent. `idPattern` is the format the agent's
 * provider gives its routine ids; null when the client knows none, and the
 * API then decides.
 */
export const agentRoutineFormSchema = (t: (key: string) => string, idPattern: RegExp | null) =>
  z.object({
    routineId: z
      .string()
      .trim()
      .min(1, t("routine.idRequired"))
      .max(AGENT_LIMITS.routineId, t("routine.idMaxLength"))
      .refine((value) => idPattern === null || idPattern.test(value), t("routine.idFormat")),
    routineSecret: z
      .string()
      .trim()
      .min(1, t("routine.secretRequired"))
      .max(AGENT_LIMITS.routineSecret, t("routine.secretMaxLength")),
  });

// ── Response Schemas ────────────────────────────────────────────────

// App values in responses are plain strings, so that an app this client does
// not know yet cannot fail the parse; the forms send only GrantedApp values.

/**
 * A row of GET /organizations/{organizationId}/agents. Neither the token nor
 * the routine secret is ever part of it, only their display prefixes. The
 * routine fields are null without a routine, the token fields without a live
 * token.
 */
export const agentSchema = z.object({
  id: z.guid(),
  name: z.string(),
  flow: agentFlowSchema,
  provider: agentProviderSchema,
  role: agentRoleSchema,
  apps: z.array(z.string()),
  status: AgentStatusSchema,
  tokenPrefix: z.string().nullable(),
  tokenLastUsedAt: instantSchema.nullable(),
  tokenExpiresAt: instantSchema.nullable(),
  routineId: z.string().nullable(),
  routineSecretPrefix: z.string().nullable(),
  lastFiredAt: instantSchema.nullable(),
  lastFireStatus: RoutineFireStatusSchema.nullable(),
  lastSessionUrl: z.string().nullable(),
});

/** GET /organizations/{organizationId}/agents/options: the providers each flow accepts. */
export const agentOptionsSchema = z.object({
  flows: z.array(
    z.object({
      flow: agentFlowSchema,
      providers: z.array(agentProviderSchema),
    })
  ),
});

/** POST /organizations/{organizationId}/agents/{agentId}/token: the plaintext token, this once. */
export const agentTokenCreatedSchema = z.object({
  agentId: z.guid(),
  token: z.string(),
  tokenPrefix: z.string(),
  expiresAt: instantSchema,
});

// ── Inferred Types ──────────────────────────────────────────────────

export type AgentCreateRequest = z.infer<typeof agentCreateRequestSchema>;
export type AgentUpdateRequest = z.infer<typeof agentUpdateRequestSchema>;
export type AgentRoutineRequest = z.infer<typeof agentRoutineRequestSchema>;
export type AgentFormValues = z.infer<ReturnType<typeof agentFormSchema>>;
export type AgentRoutineFormValues = z.infer<ReturnType<typeof agentRoutineFormSchema>>;
export type Agent = z.infer<typeof agentSchema>;
export type AgentOptions = z.infer<typeof agentOptionsSchema>;
export type AgentTokenCreated = z.infer<typeof agentTokenCreatedSchema>;

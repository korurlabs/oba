import { z } from "zod";

// ── Enum Schemas ───────────────────────────────────────────────────
export const SessionLinkKindSchema = z.enum(["PULL_REQUEST", "KAIZEN_TICKET"]);
export type SessionLinkKind = z.infer<typeof SessionLinkKindSchema>;
export const SessionLinkKind = SessionLinkKindSchema.enum;

export const SessionLinkSourceSchema = z.enum(["RECORDER", "MANUAL"]);
export type SessionLinkSource = z.infer<typeof SessionLinkSourceSchema>;
export const SessionLinkSource = SessionLinkSourceSchema.enum;

export const HandoffStatusSchema = z.enum(["PARTIAL", "DONE", "BLOCKED"]);
export type HandoffStatusValue = z.infer<typeof HandoffStatusSchema>;
export const HandoffStatusValue = HandoffStatusSchema.enum;

/**
 * How a session's config release stood when it started, as the recorder
 * reported it and the server checked it: UNVERIFIED when the reported manifest
 * hash names no release of the session's repository.
 */
export const ConfigReleaseStateSchema = z.enum(["FRESH", "CACHED", "STALE", "NONE", "UNVERIFIED"]);
export type ConfigReleaseState = z.infer<typeof ConfigReleaseStateSchema>;
export const ConfigReleaseState = ConfigReleaseStateSchema.enum;

/** Whether `kaya launch` or the SessionStart hook delivered the release. */
export const ConfigDeliveredBySchema = z.enum(["LAUNCHER", "HOOK"]);
export type ConfigDeliveredBy = z.infer<typeof ConfigDeliveredBySchema>;
export const ConfigDeliveredBy = ConfigDeliveredBySchema.enum;

// ── Request Schemas ─────────────────────────────────────────────────
/** The query of `GET /sessions`; every filter is optional and `page` counts from 1. */
export const sessionQueryRequestSchema = z.object({
  page: z.number().int().min(1).optional(),
  amount: z.number().int().min(1).max(60).optional(),
  search: z.string().max(200).optional(),
  repositoryId: z.string().optional(),
  userId: z.string().optional(),
  machineId: z.string().optional(),
  model: z.string().optional(),
  accountOrganizationUuid: z.string().optional(),
  ended: z.boolean().optional(),
});

/** The forms the server accepts per link kind; a reference that breaks them answers KAY_VAL_015. */
export const KAIZEN_TICKET_KEY_PATTERN = /^[A-Z][A-Z0-9]+-[0-9]+$/;
export const PULL_REQUEST_URL_PATTERN = /^https:\/\/github\.com\/[^/\s]+\/[^/\s]+\/pull\/[0-9]+$/;

/** The body of `POST /sessions/{sessionId}/links`; the server strips the same whitespace. */
export const sessionLinkCreateRequestSchema = z
  .object({
    kind: SessionLinkKindSchema,
    reference: z
      .string()
      .trim()
      .min(1, "Enter a ticket key or a pull request URL.")
      .max(500, "A link has at most 500 characters."),
  })
  .superRefine((link, context) => {
    if (link.kind === "KAIZEN_TICKET" && !KAIZEN_TICKET_KEY_PATTERN.test(link.reference)) {
      context.addIssue({
        code: "custom",
        path: ["reference"],
        message: "A ticket key looks like KAYA-42.",
      });
    }
    if (link.kind === "PULL_REQUEST" && !PULL_REQUEST_URL_PATTERN.test(link.reference)) {
      context.addIssue({
        code: "custom",
        path: ["reference"],
        message: "A pull request URL looks like https://github.com/owner/repo/pull/42.",
      });
    }
  });

// ── Response Schemas ────────────────────────────────────────────────
export const sessionLinkResponseSchema = z.object({
  id: z.string(),
  kind: SessionLinkKindSchema,
  reference: z.string(),
  url: z.string().nullish(),
  source: SessionLinkSourceSchema,
  createdBy: z.string().nullish(),
  createdAt: z.string(),
});

export const sessionListResponseSchema = z.object({
  id: z.string(),
  sessionId: z.string(),
  title: z.string().nullish(),
  repositoryId: z.string().nullish(),
  repositoryName: z.string().nullish(),
  userId: z.string().nullish(),
  userDisplayName: z.string().nullish(),
  machineId: z.string().nullish(),
  machineName: z.string().nullish(),
  identityConflictUserId: z.string().nullish(),
  identityConflictUserDisplayName: z.string().nullish(),
  branch: z.string().nullish(),
  model: z.string().nullish(),
  claudeCodeVersion: z.string().nullish(),
  accountEmail: z.string().nullish(),
  accountOrganizationUuid: z.string().nullish(),
  accountOrganizationName: z.string().nullish(),
  billingType: z.string().nullish(),
  seatTier: z.string().nullish(),
  startedAt: z.string().nullish(),
  lastEventAt: z.string().nullish(),
  ended: z.boolean(),
  eventCount: z.number(),
  features: z.array(z.string()),
  links: z.array(sessionLinkResponseSchema),
  createdAt: z.string(),
});

export const sessionSubagentResponseSchema = z.object({
  agentId: z.string(),
  subagentType: z.string().nullish(),
  model: z.string().nullish(),
  description: z.string().nullish(),
  startedAt: z.string().nullish(),
  endedAt: z.string().nullish(),
});

export const sessionSkillUseResponseSchema = z.object({
  skill: z.string(),
  source: z.string().nullish(),
  useCount: z.number(),
  firstUsedAt: z.string().nullish(),
});

export const sessionHandoffSummaryResponseSchema = z.object({
  path: z.string(),
  status: HandoffStatusSchema.nullish(),
  capturedAt: z.string().nullish(),
});

export const sessionHandoffResponseSchema = sessionHandoffSummaryResponseSchema.extend({
  content: z.string(),
});

export const sessionDetailResponseSchema = sessionListResponseSchema.extend({
  sessionTitle: z.string().nullish(),
  firstPrompt: z.string().nullish(),
  cwd: z.string().nullish(),
  gitAuthorEmail: z.string().nullish(),
  subagents: z.array(sessionSubagentResponseSchema),
  skillUses: z.array(sessionSkillUseResponseSchema),
  handoffs: z.array(sessionHandoffSummaryResponseSchema),
  /** The release the session ran under; null when none was reported or it did not match. */
  releaseId: z.string().nullish(),
  releaseNumber: z.number().nullish(),
  /** Null when the recorder reported no release, as for sessions recorded before delivery. */
  releaseState: ConfigReleaseStateSchema.nullish(),
  deliveredBy: ConfigDeliveredBySchema.nullish(),
  updatedAt: z.string().nullish(),
});

export const sessionFilterUserSchema = z.object({ id: z.string(), displayName: z.string() });
export const sessionFilterMachineSchema = z.object({
  id: z.string(),
  machineName: z.string(),
  userId: z.string().nullish(),
});
export const sessionFilterSubscriptionSchema = z.object({
  uuid: z.string(),
  name: z.string().nullish(),
  billingType: z.string().nullish(),
  seatTier: z.string().nullish(),
});

export const sessionFiltersResponseSchema = z.object({
  users: z.array(sessionFilterUserSchema),
  machines: z.array(sessionFilterMachineSchema),
  models: z.array(z.string()),
  subscriptions: z.array(sessionFilterSubscriptionSchema),
});

export const repositoryResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  remoteUrl: z.string(),
  ticketUrlTemplate: z.string().nullish(),
  status: z.string(),
});

// ── Inferred Types ──────────────────────────────────────────────────
export type SessionQueryRequest = z.infer<typeof sessionQueryRequestSchema>;
export type SessionLinkResponse = z.infer<typeof sessionLinkResponseSchema>;
export type SessionListResponse = z.infer<typeof sessionListResponseSchema>;
export type SessionFilterUser = z.infer<typeof sessionFilterUserSchema>;
export type SessionFilterMachine = z.infer<typeof sessionFilterMachineSchema>;
export type SessionFilterSubscription = z.infer<typeof sessionFilterSubscriptionSchema>;
export type SessionFiltersResponse = z.infer<typeof sessionFiltersResponseSchema>;
export type RepositoryResponse = z.infer<typeof repositoryResponseSchema>;
export type SessionLinkCreateRequest = z.infer<typeof sessionLinkCreateRequestSchema>;
export type SessionSubagentResponse = z.infer<typeof sessionSubagentResponseSchema>;
export type SessionSkillUseResponse = z.infer<typeof sessionSkillUseResponseSchema>;
export type SessionHandoffSummaryResponse = z.infer<typeof sessionHandoffSummaryResponseSchema>;
export type SessionHandoffResponse = z.infer<typeof sessionHandoffResponseSchema>;
export type SessionDetailResponse = z.infer<typeof sessionDetailResponseSchema>;

import { z } from "zod";

// ── Enum Schemas ───────────────────────────────────────────────────
export const PlanKindSchema = z.enum(["SPEC", "SESSION"]);
export type PlanKind = z.infer<typeof PlanKindSchema>;
export const PlanKind = PlanKindSchema.enum;

export const PlanReviewStateSchema = z.enum(["OPEN", "CHANGES_REQUESTED", "APPROVED"]);
export type PlanReviewState = z.infer<typeof PlanReviewStateSchema>;
export const PlanReviewState = PlanReviewStateSchema.enum;

export const PlanFeedbackDecisionSchema = z.enum(["APPROVE", "REQUEST_CHANGES", "COMMENT"]);
export type PlanFeedbackDecision = z.infer<typeof PlanFeedbackDecisionSchema>;
export const PlanFeedbackDecision = PlanFeedbackDecisionSchema.enum;

export const PlanVersionSourceSchema = z.enum(["FILE", "PLAN_MODE", "MESSAGE"]);
export type PlanVersionSource = z.infer<typeof PlanVersionSourceSchema>;
export const PlanVersionSource = PlanVersionSourceSchema.enum;

// ── Limits ─────────────────────────────────────────────────────────
// The limits equal the server's; the server stays the authority.
export const PLAN_LIMITS = {
  pageSizeMax: 100,
  searchMax: 200,
  commentMax: 4000,
  quoteMax: 500,
} as const;

// ── Request Schemas ─────────────────────────────────────────────────
/** `GET /plans`: 1-based page, newest change first; `sessionId` is a Claude Code session id. */
export const planQueryRequestSchema = z.object({
  page: z.number().int().min(1).optional(),
  amount: z.number().int().min(1).max(PLAN_LIMITS.pageSizeMax).optional(),
  repositoryId: z.string().optional(),
  packId: z.string().optional(),
  kind: PlanKindSchema.optional(),
  reviewState: PlanReviewStateSchema.optional(),
  userId: z.string().optional(),
  sessionId: z.string().optional(),
  search: z.string().max(PLAN_LIMITS.searchMax).optional(),
});

const lineNumber = z.number().int().min(1, "Lines start at 1.");

/**
 * Feedback on one version. REQUEST_CHANGES and COMMENT need the reviewer's
 * words; a line range has both bounds or none, and ends at or after its start.
 * The server also checks the range against the version's line count.
 */
export const planFeedbackRequestSchema = z
  .object({
    versionNumber: z.number().int().min(1),
    decision: PlanFeedbackDecisionSchema,
    comment: z
      .string()
      .trim()
      .max(PLAN_LIMITS.commentMax, `A comment has at most ${PLAN_LIMITS.commentMax} characters.`)
      .transform((comment) => (comment === "" ? undefined : comment))
      .optional(),
    lineStart: lineNumber.optional(),
    lineEnd: lineNumber.optional(),
    quote: z
      .string()
      .max(PLAN_LIMITS.quoteMax, `A quote has at most ${PLAN_LIMITS.quoteMax} characters.`)
      .optional(),
  })
  .superRefine((feedback, context) => {
    if (feedback.decision !== "APPROVE" && feedback.comment === undefined) {
      context.addIssue({
        code: "custom",
        path: ["comment"],
        message:
          feedback.decision === "REQUEST_CHANGES" ? "Say what should change." : "Enter a comment.",
      });
    }
    if ((feedback.lineStart === undefined) !== (feedback.lineEnd === undefined)) {
      context.addIssue({
        code: "custom",
        path: ["lineEnd"],
        message: "A line range needs its first and its last line.",
      });
      return;
    }
    if (
      feedback.lineStart !== undefined &&
      feedback.lineEnd !== undefined &&
      feedback.lineEnd < feedback.lineStart
    ) {
      context.addIssue({
        code: "custom",
        path: ["lineEnd"],
        message: "The last line comes at or after the first.",
      });
    }
  });

// ── Response Schemas ────────────────────────────────────────────────
/** A repository, pack, user or reviewer; `name` is null once it is no longer known. */
export const planReferenceResponseSchema = z.object({
  id: z.string(),
  name: z.string().nullish(),
});

export const planListResponseSchema = z.object({
  id: z.string(),
  kind: PlanKindSchema,
  title: z.string(),
  /** Repository-relative path of a spec; null for a session plan. */
  path: z.string().nullish(),
  repository: planReferenceResponseSchema,
  /** The pack the spec is about; null when no pack of the repository matches. */
  pack: planReferenceResponseSchema.nullish(),
  /** The user of the latest version. */
  user: planReferenceResponseSchema,
  reviewState: PlanReviewStateSchema,
  latestVersionNumber: z.number(),
  /** Feedback not yet delivered to a session. */
  openFeedbackCount: z.number(),
  lastChangedAt: z.string(),
});

/** The values the list's selects offer: only those the organization's plans hold. */
export const planFiltersResponseSchema = z.object({
  repositories: z.array(planReferenceResponseSchema),
  packs: z.array(planReferenceResponseSchema),
  users: z.array(planReferenceResponseSchema),
});

export const planSessionResponseSchema = z.object({
  /** Claude Code session id, the id of the session page. */
  sessionId: z.string(),
  title: z.string().nullish(),
  ended: z.boolean(),
  branch: z.string().nullish(),
});

export const planVersionSummaryResponseSchema = z.object({
  number: z.number(),
  source: PlanVersionSourceSchema,
  /** Claude Code session id; null when that session was deleted. */
  sessionId: z.string().nullish(),
  capturedAt: z.string(),
  lineCount: z.number(),
});

export const planFeedbackResponseSchema = z.object({
  id: z.string(),
  versionNumber: z.number(),
  reviewer: planReferenceResponseSchema,
  decision: PlanFeedbackDecisionSchema,
  comment: z.string().nullish(),
  lineStart: z.number().nullish(),
  lineEnd: z.number().nullish(),
  quote: z.string().nullish(),
  createdAt: z.string(),
  /** Null while it waits for a session. */
  deliveredAt: z.string().nullish(),
  /** Claude Code session id of the session that received it. */
  deliveredSessionId: z.string().nullish(),
});

/** The newest snapshot of one handoff of the plan's feature. */
export const planHandoffResponseSchema = z.object({
  /** Path below `handoffs/`. */
  path: z.string(),
  /** PARTIAL, DONE or BLOCKED; null when the file has no status line. */
  status: z.string().nullish(),
  /** The spec unit the handoff implements, from its "Scope given" section. */
  unitNumber: z.number().nullish(),
  content: z.string(),
});

export const planDetailResponseSchema = planListResponseSchema.extend({
  /** The `handoffs/{feature}/` directory of the spec's planning handoff. */
  feature: z.string().nullish(),
  latestSession: planSessionResponseSchema.nullish(),
  /** Oldest first. */
  versions: z.array(planVersionSummaryResponseSchema),
  /** Every feedback of every version, in the order it was given. */
  feedback: z.array(planFeedbackResponseSchema),
  /** Sorted by path; empty without a feature. */
  handoffs: z.array(planHandoffResponseSchema),
  /** Path below `handoffs/` of the planning handoff that names this spec. */
  planningHandoffPath: z.string().nullish(),
  /** The caller is the plan's user, an ADMIN or an OWNER. */
  canApprove: z.boolean(),
});

export const planVersionContentResponseSchema = z.object({
  number: z.number(),
  content: z.string(),
});

/** Unified diff against the version before; empty for version 1. */
export const planVersionDiffResponseSchema = z.object({
  number: z.number(),
  diff: z.string(),
});

// ── Inferred Types ──────────────────────────────────────────────────
export type PlanQueryRequest = z.infer<typeof planQueryRequestSchema>;
export type PlanFeedbackRequest = z.output<typeof planFeedbackRequestSchema>;
export type PlanReferenceResponse = z.infer<typeof planReferenceResponseSchema>;
export type PlanListResponse = z.infer<typeof planListResponseSchema>;
export type PlanFiltersResponse = z.infer<typeof planFiltersResponseSchema>;
export type PlanSessionResponse = z.infer<typeof planSessionResponseSchema>;
export type PlanVersionSummaryResponse = z.infer<typeof planVersionSummaryResponseSchema>;
export type PlanFeedbackResponse = z.infer<typeof planFeedbackResponseSchema>;
export type PlanHandoffResponse = z.infer<typeof planHandoffResponseSchema>;
export type PlanDetailResponse = z.infer<typeof planDetailResponseSchema>;
export type PlanVersionContentResponse = z.infer<typeof planVersionContentResponseSchema>;
export type PlanVersionDiffResponse = z.infer<typeof planVersionDiffResponseSchema>;

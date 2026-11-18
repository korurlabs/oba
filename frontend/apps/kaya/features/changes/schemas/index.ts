import { z } from "zod";

import { reasonSchema } from "@/lib/forms";
import { releaseResponseSchema } from "@/features/releases/schemas";
import {
  FileChangeSchema,
  VersionSourceSchema,
  VersionStatusSchema,
  versionResponseSchema,
} from "@/features/versions/schemas";
import { OrganizationRoleSchema } from "@/lib/identity/schemas";

// ── Enum Schemas ───────────────────────────────────────────────────
export const ReviewDecisionSchema = z.enum(["APPROVE", "REJECT", "COMMENT"]);
export type ReviewDecision = z.infer<typeof ReviewDecisionSchema>;
export const ReviewDecision = ReviewDecisionSchema.enum;

// ── Limits ─────────────────────────────────────────────────────────
// The limits equal the server's; the server stays the authority.
export const CHANGE_LIMITS = {
  commentMax: 2000,
  pageSizeMax: 50,
} as const;

// ── Request Schemas ─────────────────────────────────────────────────
/** `GET /changes`: 1-based page, most recently changed first; `packId` names one pack. */
export const changeQueryRequestSchema = z.object({
  page: z.number().int().min(1).optional(),
  amount: z.number().int().min(1).max(CHANGE_LIMITS.pageSizeMax).optional(),
  status: VersionStatusSchema.optional(),
  packId: z.string().optional(),
});

/** `POST /changes/{versionId}/submit`: the proposer's reason, which the reviewer reads. */
export const changeSubmitRequestSchema = z.object({
  reason: reasonSchema,
});

/** A decision on a version in review; REJECT and COMMENT need the reviewer's words. */
export const reviewRequestSchema = z
  .object({
    decision: ReviewDecisionSchema,
    comment: z
      .string()
      .trim()
      .max(
        CHANGE_LIMITS.commentMax,
        `A comment has at most ${CHANGE_LIMITS.commentMax} characters.`
      )
      .transform((comment) => (comment === "" ? undefined : comment))
      .optional(),
  })
  .superRefine((review, context) => {
    if (review.decision !== "APPROVE" && review.comment === undefined) {
      context.addIssue({
        code: "custom",
        path: ["comment"],
        message:
          review.decision === "REJECT" ? "Say why the change is rejected." : "Enter a comment.",
      });
    }
  });

/** `GET /changes/history`: the versions of a pack that changed one path. */
export const fileHistoryQueryRequestSchema = z.object({
  versionId: z.string().min(1),
  path: z.string().min(1).max(500),
  page: z.number().int().min(1).optional(),
  amount: z.number().int().min(1).max(CHANGE_LIMITS.pageSizeMax).optional(),
});

// ── Response Schemas ────────────────────────────────────────────────
export const reviewResponseSchema = z.object({
  id: z.string(),
  versionId: z.string(),
  reviewerId: z.string(),
  decision: ReviewDecisionSchema,
  comment: z.string().nullish(),
  createdAt: z.string(),
});

/** A rule a file, a repository's composition or a pack order breaks. */
export const violationSchema = z.object({
  path: z.string().nullish(),
  rule: z.string(),
  message: z.string().nullish(),
  /** The repository whose composition breaks the rule. */
  repositoryId: z.string().nullish(),
  /** The packs the rule names, such as the two that give one path, or the locking ancestor and the overriding pack. */
  ownerIds: z.array(z.string()).nullish(),
  /** The mount folder a rule of a mounted pack must stay under (MOUNT_RULE_SCOPE only). */
  rootPath: z.string().nullish(),
});

/** A version in the change list; its pack is `version.packId`. */
export const changeResponseSchema = z.object({
  version: versionResponseSchema,
  /** OWNER for a change that touches executable code, hooks or permissions; ADMIN otherwise. */
  requiredRole: OrganizationRoleSchema,
  /** Oldest first. */
  reviews: z.array(reviewResponseSchema),
});

/** A change for its review screen; the last three fields are for the caller and only in review. */
export const changeDetailResponseSchema = changeResponseSchema.extend({
  /** Empty when it may be approved now; null when the version is not in review. */
  violations: z.array(violationSchema).nullish(),
  canApprove: z.boolean(),
  canReview: z.boolean(),
});

/** A version that added, changed or deleted the path, with its reviews oldest first; the list is newest first. */
export const fileHistoryResponseSchema = z.object({
  versionId: z.string(),
  number: z.number(),
  status: VersionStatusSchema,
  source: VersionSourceSchema,
  change: FileChangeSchema,
  /** Null when the version deleted the path. */
  sha256: z.string().nullish(),
  /** Whether it is the owner's current version. */
  current: z.boolean(),
  proposedBy: z.string(),
  reason: z.string().nullish(),
  submittedAt: z.string().nullish(),
  decidedAt: z.string().nullish(),
  reviews: z.array(reviewResponseSchema),
});

export const reviewResultResponseSchema = z.object({
  review: reviewResponseSchema,
  version: versionResponseSchema,
  /** One per composing repository; empty unless approved. */
  releases: z.array(releaseResponseSchema),
});

// ── Inferred Types ──────────────────────────────────────────────────
export type ChangeQueryRequest = z.infer<typeof changeQueryRequestSchema>;
export type FileHistoryQueryRequest = z.infer<typeof fileHistoryQueryRequestSchema>;
export type FileHistoryResponse = z.infer<typeof fileHistoryResponseSchema>;
export type ChangeSubmitRequest = z.infer<typeof changeSubmitRequestSchema>;
export type ReviewRequest = z.output<typeof reviewRequestSchema>;
export type ReviewResponse = z.infer<typeof reviewResponseSchema>;
export type Violation = z.infer<typeof violationSchema>;
export type ChangeResponse = z.infer<typeof changeResponseSchema>;
export type ChangeDetailResponse = z.infer<typeof changeDetailResponseSchema>;
export type ReviewResultResponse = z.infer<typeof reviewResultResponseSchema>;

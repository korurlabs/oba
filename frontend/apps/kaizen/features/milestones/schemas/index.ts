import { z } from "zod";
import { HEX_COLOR_PATTERN } from "@/features/boards/lib/board-colors";

// ── Enum Schemas ───────────────────────────────────────────────────

export const MilestoneStatusSchema = z.enum(["OPEN", "CLOSED"]);
export type MilestoneStatus = z.infer<typeof MilestoneStatusSchema>;
export const MilestoneStatus = MilestoneStatusSchema.enum;

/** Derived on the server from the dates and the done/total counts; never stored. */
export const MilestoneHealthSchema = z.enum(["ON_TRACK", "AT_RISK", "OVERDUE", "DONE"]);
export type MilestoneHealth = z.infer<typeof MilestoneHealthSchema>;
export const MilestoneHealth = MilestoneHealthSchema.enum;

// ── Limits (mirror MilestoneCreateRequest on the server) ────────────

export const MILESTONE_NAME_MAX = 255;
export const MILESTONE_DESCRIPTION_MAX = 2000;
export const MILESTONE_LINK_MAX = 200;
export const MILESTONE_DEPENDS_ON_MAX = 50;

// ── Request Schemas ─────────────────────────────────────────────────

// Dates are calendar dates (`YYYY-MM-DD`), without a time or timezone.
export const milestoneCreateRequestSchema = z.object({
  name: z.string().min(1).max(MILESTONE_NAME_MAX),
  description: z.string().max(MILESTONE_DESCRIPTION_MAX).optional(),
  startDate: z.string().optional(),
  targetDate: z.string(),
  /** Omitted -> the server stores the board's colour. */
  color: z.string().regex(HEX_COLOR_PATTERN).optional(),
  /** Milestones the new one depends on; any invalid id fails the whole create. */
  dependsOnIds: z.array(z.string()).max(MILESTONE_DEPENDS_ON_MAX).optional(),
});

/** An omitted field is left unchanged; the clear flags set the field to null. */
export const milestoneUpdateRequestSchema = z.object({
  name: z.string().min(1).max(MILESTONE_NAME_MAX).optional(),
  description: z.string().max(MILESTONE_DESCRIPTION_MAX).optional(),
  startDate: z.string().optional(),
  targetDate: z.string().optional(),
  clearStartDate: z.boolean().optional(),
  clearDescription: z.boolean().optional(),
  color: z.string().regex(HEX_COLOR_PATTERN).optional(),
});

export const milestoneTicketsRequestSchema = z.object({
  ticketIds: z.array(z.string()).min(1).max(MILESTONE_LINK_MAX),
});

export const milestoneListParamsSchema = z.object({
  status: MilestoneStatusSchema.optional(),
  includeRelatedBoards: z.boolean().optional(),
});

export const roadmapParamsSchema = z.object({
  includeChildBoards: z.boolean().optional(),
  from: z.string().optional(),
  to: z.string().optional(),
});

// ── Form Schema ─────────────────────────────────────────────────────

/** Messages are i18n keys, resolved by `FormMessage`. */
export const milestoneFormSchema = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, "milestone.form.nameRequired")
      .max(MILESTONE_NAME_MAX, "milestone.form.nameTooLong"),
    description: z
      .string()
      .max(MILESTONE_DESCRIPTION_MAX, "milestone.form.descriptionTooLong")
      .optional(),
    startDate: z.date().nullable().optional(),
    targetDate: z.date({ error: "milestone.form.targetDateRequired" }),
    color: z.string().regex(HEX_COLOR_PATTERN).nullable().optional(),
    dependsOnIds: z
      .array(z.string())
      .max(MILESTONE_DEPENDS_ON_MAX, "milestone.form.dependsOnTooMany"),
  })
  .refine((data) => !data.startDate || data.startDate <= data.targetDate, {
    message: "milestone.form.startAfterTarget",
    path: ["startDate"],
  });

export type MilestoneFormValues = z.infer<typeof milestoneFormSchema>;

// ── Response Schemas ────────────────────────────────────────────────

/**
 * "The owning milestone depends on this one". `conflict` is derived on the
 * server: the dependent starts (or, without a start, is due) before this
 * milestone's target date. Dates are never shifted to resolve it.
 */
export const milestoneDependencyResponseSchema = z.object({
  milestoneId: z.string(),
  name: z.string(),
  boardId: z.string(),
  targetDate: z.string(),
  conflict: z.boolean(),
});

export const milestoneListResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  status: MilestoneStatusSchema,
  startDate: z.string().nullable().optional(),
  targetDate: z.string(),
  boardId: z.string(),
  boardName: z.string(),
  boardPrefix: z.string(),
  doneCount: z.number(),
  totalCount: z.number(),
  health: MilestoneHealthSchema,
  color: z.string().nullable().optional(),
  /** Only live milestones on boards the user is a member of. */
  dependsOn: z.array(milestoneDependencyResponseSchema),
});

export const milestoneDetailResponseSchema = milestoneListResponseSchema.extend({
  description: z.string().nullable().optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export const roadmapBoardResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  prefix: z.string(),
  color: z.string().nullable().optional(),
  milestones: z.array(milestoneListResponseSchema),
});

export const roadmapResponseSchema = z.object({
  from: z.string().nullable().optional(),
  to: z.string().nullable().optional(),
  boards: z.array(roadmapBoardResponseSchema),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type MilestoneCreateRequest = z.infer<typeof milestoneCreateRequestSchema>;
export type MilestoneUpdateRequest = z.infer<typeof milestoneUpdateRequestSchema>;
export type MilestoneTicketsRequest = z.infer<typeof milestoneTicketsRequestSchema>;
export type MilestoneListParams = z.infer<typeof milestoneListParamsSchema>;
export type RoadmapParams = z.infer<typeof roadmapParamsSchema>;
export type MilestoneDependencyResponse = z.infer<typeof milestoneDependencyResponseSchema>;
export type MilestoneListResponse = z.infer<typeof milestoneListResponseSchema>;
export type MilestoneDetailResponse = z.infer<typeof milestoneDetailResponseSchema>;
export type RoadmapBoardResponse = z.infer<typeof roadmapBoardResponseSchema>;
export type RoadmapResponse = z.infer<typeof roadmapResponseSchema>;

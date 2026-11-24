import { z } from "zod";
import { type LabelResponse } from "@/features/labels/schemas";
import { projectListResponseSchema, type ProjectListResponse } from "@/features/projects/schemas";
import { MilestoneStatusSchema } from "@/features/milestones/schemas";

// ── Enum Schemas ───────────────────────────────────────────────────

export const TicketRelocationActionSchema = z.enum([
  "MOVE_TO_BACKLOG",
  "MOVE_TO_SPRINT",
  "DELETE",
]);
export type TicketRelocationAction = z.infer<typeof TicketRelocationActionSchema>;
export const TicketRelocationAction = TicketRelocationActionSchema.enum;

export const TicketTypeSchema = z.enum([
  "BUG",
  "FEATURE",
  "TECH_DEBT",
  "IMPROVEMENT",
  "INCIDENT",
  "PROBLEM",
  "REFACTOR",
]);
export type TicketType = z.infer<typeof TicketTypeSchema>;
export const TicketType = TicketTypeSchema.enum;

export const TicketStatusSchema = z.enum([
  "TODO",
  "IN_PROGRESS",
  "WAITING_FOR_THIRD_PARTY",
  "IN_REVIEW",
  "DONE",
]);
export type TicketStatus = z.infer<typeof TicketStatusSchema>;
export const TicketStatus = TicketStatusSchema.enum;

export const TicketPrioritySchema = z.enum([
  "LOWEST",
  "LOW",
  "MEDIUM",
  "HIGH",
  "HIGHEST",
  "P1",
  "P2",
  "P3",
]);
export type TicketPriority = z.infer<typeof TicketPrioritySchema>;
export const TicketPriority = TicketPrioritySchema.enum;

export const SystemActorTypeSchema = z.enum(["ALERTMANAGER"]);
export type SystemActorType = z.infer<typeof SystemActorTypeSchema>;
export const SystemActorType = SystemActorTypeSchema.enum;

/**
 * Which sub-tickets come along when a parent moves. The server treats an
 * absent mode as `UNFINISHED`.
 */
export const SubTicketMoveModeSchema = z.enum(["NONE", "UNFINISHED", "SELECTED"]);
export type SubTicketMoveMode = z.infer<typeof SubTicketMoveModeSchema>;
export const SubTicketMoveMode = SubTicketMoveModeSchema.enum;

export const MissionRankSchema = z.enum([
  "XS",
  "S",
  "M",
  "L",
  "XL",
  "XXL",
]);
export type MissionRank = z.infer<typeof MissionRankSchema>;
export const MissionRank = MissionRankSchema.enum;

// ── Error codes the client reacts to ────────────────────────────────

export const TICKET_ERROR_CODES = {
  /**
   * A field of a ticket request failed validation (422), such as a create
   * without a Mission Rank. `errorValues` maps each field to its message.
   */
  validation: "GEN_VAL_001",
} as const;

// ── Request Schemas ─────────────────────────────────────────────────

export const ticketCreateRequestSchema = z.object({
  title: z.string().min(1).max(255),
  description: z.string().max(10000).optional(),
  type: TicketTypeSchema.optional(),
  priority: TicketPrioritySchema.optional(),
  status: TicketStatusSchema.optional(),
  weight: z.number().optional(),
  missionRank: MissionRankSchema,
  dueDate: z.string().nullable().optional(),
  rca: z.string().max(10000).optional(),
  lessonsLearned: z.string().max(10000).optional(),
  sprintId: z.string().optional(),
  backlogId: z.string().optional(),
  parentTicketId: z.string().optional(),
  projectId: z.string().optional(),
  milestoneId: z.string().optional(),
  assigneeIds: z.array(z.string()).optional(),
  reviewerIds: z.array(z.string()).optional(),
  labelIds: z.array(z.string()).optional(),
});

export const ticketUpdateRequestSchema = z.object({
  title: z.string().max(255).optional(),
  description: z.string().max(10000).optional(),
  type: TicketTypeSchema.optional(),
  status: TicketStatusSchema.optional(),
  priority: TicketPrioritySchema.optional(),
  sprintId: z.string().optional(),
  backlogId: z.string().optional(),
  parentTicketId: z.string().optional(),
  /** Detaches the parent ticket; wins over `parentTicketId` when both are sent. */
  clearParent: z.boolean().optional(),
  projectId: z.string().optional(),
  milestoneId: z.string().optional(),
  /** Detaches the milestone; wins over `milestoneId` when both are sent. */
  clearMilestone: z.boolean().optional(),
  percentage: z.number().optional(),
  weight: z.number().optional(),
  missionRank: MissionRankSchema.optional(),
  dueDate: z.string().nullable().optional(),
  rca: z.string().max(10000).optional(),
  lessonsLearned: z.string().max(10000).optional(),
  assigneeIds: z.array(z.string()).optional(),
  reviewerIds: z.array(z.string()).optional(),
  labelIds: z.array(z.string()).optional(),
});

// ── Form Schema (used by the ticket form for client-side validation) ──

export const ticketFormSchema = z.object({
  title: z.string().min(1).max(255),
  description: z.string().max(10000).optional(),
  type: TicketTypeSchema,
  priority: TicketPrioritySchema,
  status: TicketStatusSchema.optional(),
  assigneeIds: z.array(z.string()).optional(),
  reviewerIds: z.array(z.string()).optional(),
  /**
   * Manual progress, edit mode only. Coerced because the form edits it in a
   * number `<input>`, which reports a string. The messages are i18n keys,
   * resolved by `FormMessage`.
   */
  percentage: z.coerce
    .number()
    .int("ticket.fields.percentageRange")
    .min(0, "ticket.fields.percentageRange")
    .max(100, "ticket.fields.percentageRange")
    .optional(),
  weight: z.coerce.number().min(1).optional(),
  /** Required: every ticket carries a size estimate. The message is an i18n key, resolved by `FormMessage`. */
  missionRank: z.enum(MissionRankSchema.options, { error: "ticket.fields.missionRankRequired" }),
  dueDate: z.date().nullable().optional(),
  projectId: z.string().optional(),
  /** A milestone id, or `TICKET_NO_MILESTONE_VALUE` for "no milestone". */
  milestoneId: z.string().optional(),
  labelIds: z.array(z.string()).optional(),
  parentId: z.string().optional(),
  /**
   * Placement control (edit mode). Either a sprint id, or
   * `TICKET_BACKLOG_SPRINT_VALUE` for "in the board's backlog, no sprint".
   * Undefined means the current placement is not known yet, in which case
   * the form leaves it alone on save rather than guessing.
   */
  sprintId: z.string().optional(),
});

export type TicketFormValues = z.infer<typeof ticketFormSchema>;

/**
 * `sprintId` form value meaning "the board's backlog, i.e. no sprint".
 *
 * A sentinel rather than an empty string because an empty string is what a
 * Radix Select reports for "nothing chosen", and the two must stay
 * distinguishable: "move to backlog" is an action, "not chosen" is not.
 * Sprint ids are UUIDs, so the literal can never collide with one.
 */
export const TICKET_BACKLOG_SPRINT_VALUE = "__backlog__";

/**
 * `milestoneId` form value meaning "no milestone". A sentinel for the same
 * reason as `TICKET_BACKLOG_SPRINT_VALUE`: an empty string is "not chosen".
 */
export const TICKET_NO_MILESTONE_VALUE = "__none__";

export const ticketMoveRequestSchema = z.object({
  sprintId: z.string().optional(),
  backlogId: z.string().optional(),
  /**
   * Display order the ticket takes on its board, with the server's shift of
   * the tickets between its old and new order. Omitted keeps its current order.
   */
  order: z.number().int().min(0).optional(),
  /** Absent means `UNFINISHED`: the unfinished sub-tickets placed with the ticket come along. */
  subTicketMode: SubTicketMoveModeSchema.optional(),
  /** Only with `SELECTED`; the server refuses ids with any other mode (KAZ_VAL_057). */
  subTicketIds: z.array(z.string()).optional(),
});

export const ticketBulkMoveRequestSchema = z.object({
  ticketIds: z.array(z.string()).min(1),
  action: z.enum(["MOVE_TO_BACKLOG", "MOVE_TO_SPRINT"]), // DELETE not allowed for bulk
  targetSprintId: z.string().optional(),
  targetBoardId: z.string().optional(),
  /** Ignored for a ticket that changes board: it always carries its whole subtree. */
  subTicketMode: SubTicketMoveModeSchema.optional(),
  /** Only with `SELECTED`; the server refuses ids with any other mode (KAZ_VAL_057). */
  subTicketIds: z.array(z.string()).optional(),
  /**
   * On a move to another board, true leaves the direct sub-tickets on their
   * board as top-level tickets instead of taking the whole subtree along.
   */
  detachSubTickets: z.boolean().optional(),
});

export const ticketBulkMoveResponseSchema = z.object({
  movedCount: z.number(),
  /** Keys (after the move) of tickets whose milestone does not relate to the target board. */
  milestoneClearedTicketKeys: z.array(z.string()),
});

export const ticketOrderUpdateRequestSchema = z.object({
  order: z.number(),
});

export const ticketQueryRequestSchema = z.object({
  search: z.string().optional(),
  statuses: z.array(TicketStatusSchema).optional(),
  types: z.array(TicketTypeSchema).optional(),
  priorities: z.array(TicketPrioritySchema).optional(),
  assigneeIds: z.array(z.string()).optional(),
  reviewerIds: z.array(z.string()).optional(),
  sprintId: z.string().optional(),
  backlogId: z.string().optional(),
  projectId: z.string().optional(),
  milestoneId: z.string().optional(),
  labelIds: z.array(z.string()).optional(),
  dueDateStart: z.string().optional(),
  dueDateEnd: z.string().optional(),
  includeAllBoards: z.boolean().optional(),
  sortBy: z.string().optional(),
  sortDirection: z.string().optional(),
});

// ── Response Schemas ────────────────────────────────────────────────

export const ticketCreatedByResponseSchema = z.object({
  userId: z.string(),
  email: z.string(),
  firstName: z.string(),
  lastName: z.string(),
});

export const ticketCreatedBySystemActorResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  type: SystemActorTypeSchema,
});

export const ticketAssigneeResponseSchema = z.object({
  id: z.string(),
  userId: z.string(),
  email: z.string(),
  firstName: z.string(),
  lastName: z.string(),
});

export const ticketReviewerResponseSchema = z.object({
  id: z.string(),
  userId: z.string(),
  email: z.string(),
  firstName: z.string(),
  lastName: z.string(),
});

export const ticketBoardResponseSchema = z.object({
  id: z.string(),
  prefix: z.string(),
  /** URL segment of the ticket's board, which the ticket page path names. */
  slug: z.string(),
  icon: z.string().nullable().optional(),
  color: z.string().nullable().optional(),
  name: z.string(),
});

export const ticketMilestoneResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  status: MilestoneStatusSchema,
  targetDate: z.string(),
  boardId: z.string(),
});

/**
 * A ticket's own sprint. `status` is a plain string to avoid a circular
 * import with the sprints feature, whose schemas import from this module.
 */
export const ticketSprintResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  status: z.string(),
});

/** Where one sub-ticket of a parent is planned: `sprint` is null in the backlog. */
export const ticketSubTicketPlacementResponseSchema = z.object({
  id: z.string(),
  status: TicketStatusSchema,
  sprint: ticketSprintResponseSchema.nullable().optional(),
});

/**
 * The parent of a sub-ticket, as a list row carries it. A sub-ticket can be
 * planned apart from its parent, so a list may hold the sub-ticket without
 * the parent; this is what the list shows above it. `subTickets` lists every
 * direct sub-ticket of the parent, wherever it is planned.
 */
export const ticketParentResponseSchema = z.object({
  id: z.string(),
  key: z.string(),
  title: z.string(),
  status: TicketStatusSchema,
  type: TicketTypeSchema,
  subTickets: z.array(ticketSubTicketPlacementResponseSchema),
});

export const ticketCommentResponseSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  content: z.string(),
  authorId: z.string(),
  authorEmail: z.string(),
  authorFirstName: z.string(),
  authorLastName: z.string(),
});

export const ticketListResponseSchema: z.ZodType<TicketListResponseRaw> = z.object({
  id: z.string(),
  createdAt: z.string(),
  title: z.string(),
  key: z.string(),
  displayOrder: z.number(),
  type: TicketTypeSchema,
  status: TicketStatusSchema,
  priority: TicketPrioritySchema,
  missionRank: MissionRankSchema,
  percentage: z.number(),
  weight: z.number().nullable().optional(),
  dueDate: z.string().nullable().optional(),
  createdBy: ticketCreatedByResponseSchema.nullable().optional(),
  createdBySystemActor: ticketCreatedBySystemActorResponseSchema.nullable().optional(),
  assignees: z.array(ticketAssigneeResponseSchema).nullable().optional(),
  reviewers: z.array(ticketReviewerResponseSchema).nullable().optional(),
  project: projectListResponseSchema.nullable().optional(),
  milestone: ticketMilestoneResponseSchema.nullable().optional(),
  board: ticketBoardResponseSchema.nullable().optional(),
  parentTicketId: z.string().nullable().optional(),
  subTickets: z.lazy(() => z.array(ticketListResponseSchema)).nullable().optional(),
  labels: z.array(z.custom<LabelResponse>()).nullable().optional(),
  /** The ticket's own sprint; null in the backlog. */
  sprint: ticketSprintResponseSchema.nullable().optional(),
  /** Null for a top-level ticket. */
  parentTicket: ticketParentResponseSchema.nullable().optional(),
  /**
   * Whether the ticket's own title, description or key holds the list's
   * search text; null when the list is not searched.
   */
  searchMatch: z.boolean().nullable().optional(),
});

// Helper type for the recursive schema definition
type TicketListResponseRaw = {
  id: string;
  createdAt: string;
  title: string;
  key: string;
  displayOrder: number;
  type: TicketType;
  status: TicketStatus;
  priority: TicketPriority;
  missionRank: MissionRank;
  percentage: number;
  weight?: number | null;
  dueDate?: string | null;
  createdBy?: z.infer<typeof ticketCreatedByResponseSchema> | null;
  createdBySystemActor?: z.infer<typeof ticketCreatedBySystemActorResponseSchema> | null;
  assignees?: z.infer<typeof ticketAssigneeResponseSchema>[] | null;
  reviewers?: z.infer<typeof ticketReviewerResponseSchema>[] | null;
  project?: ProjectListResponse | null;
  milestone?: z.infer<typeof ticketMilestoneResponseSchema> | null;
  board?: z.infer<typeof ticketBoardResponseSchema> | null;
  parentTicketId?: string | null;
  subTickets?: TicketListResponseRaw[] | null;
  labels?: LabelResponse[] | null;
  sprint?: z.infer<typeof ticketSprintResponseSchema> | null;
  parentTicket?: z.infer<typeof ticketParentResponseSchema> | null;
  searchMatch?: boolean | null;
};

// NOTE: LabelResponse is imported as a type from labels/schemas.
// We use z.custom<LabelResponse>() to avoid circular schema dependencies.
export const ticketDetailResponseSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  title: z.string(),
  description: z.string().nullable().optional(),
  key: z.string(),
  displayOrder: z.number(),
  type: TicketTypeSchema,
  status: TicketStatusSchema,
  priority: TicketPrioritySchema,
  missionRank: MissionRankSchema.nullable().optional(),
  percentage: z.number(),
  weight: z.number().nullable().optional(),
  dueDate: z.string().nullable().optional(),
  rca: z.string().nullable().optional(),
  lessonsLearned: z.string().nullable().optional(),
  createdBy: ticketCreatedByResponseSchema.nullable().optional(),
  createdBySystemActor: ticketCreatedBySystemActorResponseSchema.nullable().optional(),
  assignees: z.array(ticketAssigneeResponseSchema).nullable().optional(),
  reviewers: z.array(ticketReviewerResponseSchema).nullable().optional(),
  project: projectListResponseSchema.nullable().optional(),
  milestone: ticketMilestoneResponseSchema.nullable().optional(),
  board: ticketBoardResponseSchema.nullable().optional(),
  parentTicketId: z.string().nullable().optional(),
  subTickets: z.array(ticketListResponseSchema).nullable().optional(),
  labels: z.array(z.custom<LabelResponse>()).nullable().optional(),
  // Placement. Exactly one of these is set on a well-formed ticket (the
  // backend enforces the XOR via VAL_TICKET_PLACEMENT), which is why each is
  // individually nullable. `TicketDetailResponse` returns both components for
  // every ticket, sub-tickets included, so the ticket form reads placement
  // from here and nowhere else.
  // `status` is typed as a plain string to avoid a circular import with
  // the sprints feature, whose schemas import from this module.
  sprint: z
    .object({ id: z.string(), name: z.string(), status: z.string() })
    .nullable()
    .optional(),
  backlog: z.object({ id: z.string() }).nullable().optional(),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type TicketCreateRequest = z.infer<typeof ticketCreateRequestSchema>;
export type TicketUpdateRequest = z.infer<typeof ticketUpdateRequestSchema>;
export type TicketMoveRequest = z.infer<typeof ticketMoveRequestSchema>;
export type TicketBulkMoveRequest = z.infer<typeof ticketBulkMoveRequestSchema>;
export type TicketBulkMoveResponse = z.infer<typeof ticketBulkMoveResponseSchema>;
export type TicketMilestoneResponse = z.infer<typeof ticketMilestoneResponseSchema>;
export type TicketOrderUpdateRequest = z.infer<
  typeof ticketOrderUpdateRequestSchema
>;
export type TicketQueryRequest = z.infer<typeof ticketQueryRequestSchema>;
export type TicketDetailResponse = z.infer<typeof ticketDetailResponseSchema>;
export type TicketListResponse = z.infer<typeof ticketListResponseSchema>;
export type TicketCreatedByResponse = z.infer<
  typeof ticketCreatedByResponseSchema
>;
export type TicketCreatedBySystemActorResponse = z.infer<
  typeof ticketCreatedBySystemActorResponseSchema
>;
export type TicketAssigneeResponse = z.infer<
  typeof ticketAssigneeResponseSchema
>;
export type TicketReviewerResponse = z.infer<
  typeof ticketReviewerResponseSchema
>;
export type TicketBoardResponse = z.infer<typeof ticketBoardResponseSchema>;
export type TicketCommentResponse = z.infer<typeof ticketCommentResponseSchema>;
export type TicketSprintResponse = z.infer<typeof ticketSprintResponseSchema>;
export type TicketSubTicketPlacementResponse = z.infer<
  typeof ticketSubTicketPlacementResponseSchema
>;
export type TicketParentResponse = z.infer<typeof ticketParentResponseSchema>;

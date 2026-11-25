import { z } from "zod";
import { organizationRoleSchema, userKindFieldsSchema } from "@oba/shared/features/users/schema";
import {
  TicketRelocationActionSchema,
  type TicketRelocationAction,
  TicketRelocationAction as TicketRelocationActionEnum,
} from "@/features/tickets/schemas";

export { TicketRelocationActionSchema, type TicketRelocationAction, TicketRelocationActionEnum };

// ── Enum Schemas ───────────────────────────────────────────────────

export const BoardMemberRoleSchema = z.enum([
  "OWNER",
  "ADMIN",
  "MEMBER",
  "VIEWER",
]);
export type BoardMemberRole = z.infer<typeof BoardMemberRoleSchema>;
export const BoardMemberRole = BoardMemberRoleSchema.enum;

export const BoardMemberSpecialtyTypeSchema = z.enum([
  "DEVELOPER",
  "CYBERSECURITY_SPECIALIST",
  "PRODUCT_OWNER",
  "SCRUM_MASTER",
  "QA_ENGINEER",
  "DEVOPS_ENGINEER",
  "DESIGNER",
  "ARCHITECT",
]);
export type BoardMemberSpecialtyType = z.infer<
  typeof BoardMemberSpecialtyTypeSchema
>;
export const BoardMemberSpecialtyType = BoardMemberSpecialtyTypeSchema.enum;

export const BoardChildBoardActionSchema = z.enum(["PROMOTE", "MOVE", "DELETE"]);
export type BoardChildBoardAction = z.infer<typeof BoardChildBoardActionSchema>;
export const BoardChildBoardAction = BoardChildBoardActionSchema.enum;

// ── Request Schemas ─────────────────────────────────────────────────

export const boardDeleteRequestSchema = z.object({
  ticketAction: TicketRelocationActionSchema.optional(),
  targetBoardId: z.string().optional(),
  targetSprintId: z.string().optional(),
  childBoardAction: BoardChildBoardActionSchema.optional(),
  newParentBoardId: z.string().optional(),
});

export type BoardDeleteRequest = z.infer<typeof boardDeleteRequestSchema>;

export const boardMemberAddRequestSchema = z.object({
  userId: z.string(),
  role: BoardMemberRoleSchema,
});

export const boardCreateRequestSchema = z.object({
  name: z.string().min(1),
  description: z.string().optional(),
  prefix: z.string().min(1).max(10),
  color: z.string().optional(),
  icon: z.string().optional(),
  parentBoardId: z.string().optional(),
  members: z.array(boardMemberAddRequestSchema).optional(),
});

export const boardUpdateRequestSchema = z.object({
  name: z.string().min(1),
  description: z.string().optional(),
  prefix: z.string().min(1).max(10),
  color: z.string().optional(),
  icon: z.string().optional(),
});

/**
 * Changes only the parent of a board through the board update request.
 * `clearParent: true` makes the board a top-level board and wins over
 * `parentBoardId`; a request with neither keeps the current parent.
 */
export const boardParentUpdateRequestSchema = z.object({
  parentBoardId: z.string().uuid().optional(),
  clearParent: z.boolean().optional(),
});

export const boardQueryRequestSchema = z.object({
  search: z.string().optional(),
  includeChildren: z.boolean().optional(),
  page: z.number().optional(),
  amount: z.number().optional(),
  sortBy: z.string().optional(),
  sortDirection: z.string().optional(),
});

export const boardMemberUpdateRequestSchema = z.object({
  role: BoardMemberRoleSchema,
});

export const boardMemberSpecialtyUpdateRequestSchema = z.object({
  specialties: z.array(BoardMemberSpecialtyTypeSchema),
});

// ── Form Schemas ────────────────────────────────────────────────────

export const createBoardFormSchema = z.object({
  name: z.string().min(1, "board.nameRequired"),
  prefix: z
    .string()
    .min(1, "board.prefixRequired")
    .max(5, "board.prefixTooLong")
    .regex(/^[A-Z0-9_]+$/, "board.prefixUppercase"),
  description: z.string().optional(),
  parentBoardId: z.string().optional(),
  color: z.string(),
});

export type CreateBoardFormValues = z.infer<typeof createBoardFormSchema>;

export const boardDeleteFormSchema = z
  .object({
    ticketAction: TicketRelocationActionSchema,
    targetBoardId: z.string().optional(),
    targetSprintId: z.string().optional(),
    childBoardAction: BoardChildBoardActionSchema.optional(),
    newParentBoardId: z.string().optional(),
  })
  .superRefine((data, ctx) => {
    if (
      (data.ticketAction === "MOVE_TO_BACKLOG" ||
        data.ticketAction === "MOVE_TO_SPRINT") &&
      !data.targetBoardId
    ) {
      ctx.addIssue({
        code: "custom",
        path: ["targetBoardId"],
        message: "required",
      });
    }
    if (data.ticketAction === "MOVE_TO_SPRINT" && !data.targetSprintId) {
      ctx.addIssue({
        code: "custom",
        path: ["targetSprintId"],
        message: "required",
      });
    }
    if (data.childBoardAction === "MOVE" && !data.newParentBoardId) {
      ctx.addIssue({
        code: "custom",
        path: ["newParentBoardId"],
        message: "required",
      });
    }
  });

export type BoardDeleteFormValues = z.infer<typeof boardDeleteFormSchema>;

// ── Response Schemas ────────────────────────────────────────────────

export const boardDetailResponseSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  name: z.string(),
  description: z.string().optional(),
  prefix: z.string(),
  /** URL segment of the board, unique among live boards; follows the name on a rename. */
  slug: z.string(),
  color: z.string().optional(),
  icon: z.string().optional(),
  parentBoardId: z.string().optional(),
});

export const boardListResponseSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  updatedAt: z.string().optional(),
  name: z.string(),
  description: z.string().optional(),
  prefix: z.string(),
  /** URL segment of the board, unique among live boards; follows the name on a rename. */
  slug: z.string(),
  color: z.string().optional(),
  icon: z.string().optional(),
  currentUserRole: BoardMemberRoleSchema.optional(),
});

export const boardListWithChildrenResponseSchema = boardListResponseSchema.extend({
  childBoards: z.array(boardListResponseSchema).nullable().optional(),
});

export const boardMemberResponseSchema = z.object({
  id: z.string(),
  userId: z.string(),
  email: z.string(),
  firstName: z.string(),
  lastName: z.string(),
  role: BoardMemberRoleSchema,
  createdAt: z.string(),
  /** True when the member can open Kazan in the board's organization; a missing flag means no access. */
  kazanAccess: z.boolean().optional(),
  /** A board member may be an agent, which works the tickets assigned to it. */
  ...userKindFieldsSchema.shape,
});

/**
 * An active member of the board's organization, offered by the "Add Member"
 * picker. Current board members are listed too; `role` is the organization
 * role, not a board role.
 */
export const boardMemberCandidateResponseSchema = z.object({
  userId: z.string(),
  email: z.string(),
  firstName: z.string().nullable(),
  lastName: z.string().nullable(),
  role: organizationRoleSchema,
  /** True when the member can open Kazan in the organization; a missing flag means no access. */
  kazanAccess: z.boolean().optional(),
  /** Agents are offered too; a board admin adds one with the board role MEMBER only. */
  ...userKindFieldsSchema.shape,
});

// ── Inferred Types ──────────────────────────────────────────────────

export type BoardCreateRequest = z.infer<typeof boardCreateRequestSchema>;
export type BoardUpdateRequest = z.infer<typeof boardUpdateRequestSchema>;
export type BoardParentUpdateRequest = z.infer<typeof boardParentUpdateRequestSchema>;
export type BoardQueryRequest = z.infer<typeof boardQueryRequestSchema>;
export type BoardMemberAddRequest = z.infer<typeof boardMemberAddRequestSchema>;
export type BoardMemberUpdateRequest = z.infer<
  typeof boardMemberUpdateRequestSchema
>;
export type BoardMemberSpecialtyUpdateRequest = z.infer<
  typeof boardMemberSpecialtyUpdateRequestSchema
>;
export type BoardDetailResponse = z.infer<typeof boardDetailResponseSchema>;
export type BoardListResponse = z.infer<typeof boardListResponseSchema>;
export type BoardListWithChildrenResponse = z.infer<typeof boardListWithChildrenResponseSchema>;
export type BoardMemberResponse = z.infer<typeof boardMemberResponseSchema>;
export type BoardMemberCandidateResponse = z.infer<typeof boardMemberCandidateResponseSchema>;

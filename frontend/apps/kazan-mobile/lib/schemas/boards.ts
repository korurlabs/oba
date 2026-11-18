import { z } from "zod";

import {
  ticketBoardSchema,
  ticketPersonSchema,
  ticketReporterSchema,
  ticketSystemReporterSchema,
} from "@/lib/schemas/tickets";

/*
 * The board responses the app reads. Enum fields stay strings, for the reason
 * given in lib/schemas/home.ts. Instants (`startDate`, `endDate`, `dueDate`)
 * stay ISO strings until display; milestone dates are date-only `YYYY-MM-DD`.
 */

/** `BoardListWithChildrenResponse` of `GET /kazan/boards`, without the child boards. */
export const boardListItemSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable().optional(),
  prefix: z.string().nullable().optional(),
  slug: z.string(),
  color: z.string().nullable().optional(),
  icon: z.string().nullable().optional(),
});

export type BoardListItem = z.infer<typeof boardListItemSchema>;

/** A board of `GET /kazan/boards?includeChildren=true`: top-level boards with their child boards. */
export type BoardTreeItem = BoardListItem & { childBoards?: BoardTreeItem[] | null };

export const boardTreeItemSchema: z.ZodType<BoardTreeItem> = z.lazy(() =>
  boardListItemSchema.extend({
    childBoards: z.array(boardTreeItemSchema).nullable().optional(),
  })
);

/** `BoardDetailResponse` of create and update; `parentBoardId` is null for a top-level board. */
export const boardDetailSchema = boardListItemSchema.extend({
  parentBoardId: z.string().nullable().optional(),
});

export type BoardDetail = z.infer<typeof boardDetailSchema>;

/**
 * `SprintListResponse`. `startDate` and `endDate` are instants. `endsSeriesName`
 * names the repeating series that stops when this active sprint is closed,
 * because it is the series' last running sprint.
 */
export const sprintSchema = z.object({
  id: z.string(),
  name: z.string(),
  slug: z.string().nullable().optional(),
  status: z.string(),
  startDate: z.string().nullable(),
  endDate: z.string().nullable(),
  endsSeriesName: z.string().nullable().optional(),
});

export const sprintListSchema = z.array(sprintSchema);

export type Sprint = z.infer<typeof sprintSchema>;

/** `BacklogDetailResponse`; only its id is read, to scope the backlog ticket list. */
export const backlogSchema = z.object({
  id: z.string(),
  boardId: z.string(),
});

export type Backlog = z.infer<typeof backlogSchema>;

/**
 * `BoardMemberResponse`. `userId` is the user; `role` is OWNER, ADMIN, MEMBER
 * or VIEWER. `kazanAccess` is false for a member who may no longer open
 * Kazan in the board's organization: the row stays, but the server refuses to
 * assign, mention or ask a review of them. Only `true` counts as access.
 */
export const boardMemberSchema = z.object({
  id: z.string(),
  userId: z.string(),
  email: z.string().nullable().optional(),
  firstName: z.string().nullable().optional(),
  lastName: z.string().nullable().optional(),
  role: z.string(),
  kazanAccess: z.boolean().optional(),
});

export const boardMemberListSchema = z.array(boardMemberSchema);

export type BoardMember = z.infer<typeof boardMemberSchema>;

/** A named, coloured option of a board, such as a label on a ticket. */
export const boardOptionSchema = z.object({
  id: z.string(),
  name: z.string(),
  color: z.string().nullable().optional(),
});

export type BoardOption = z.infer<typeof boardOptionSchema>;

/**
 * `LabelResponse` and `ProjectListResponse`. A board's list holds its related
 * boards' options too; `boardId` names the board that owns one, and its writes
 * go to that board.
 */
export const boardOwnedOptionSchema = boardOptionSchema.extend({
  boardId: z.string(),
  boardName: z.string(),
});

export const boardOptionListSchema = z.array(boardOwnedOptionSchema);

export type BoardOwnedOption = z.infer<typeof boardOwnedOptionSchema>;

/** `MilestoneListResponse`. `startDate` and `targetDate` are date-only. */
export const milestoneSchema = z.object({
  id: z.string(),
  name: z.string(),
  status: z.string().nullable().optional(),
  startDate: z.string().nullable().optional(),
  targetDate: z.string().nullable().optional(),
  boardId: z.string().nullable().optional(),
  doneCount: z.number().nullable().optional(),
  totalCount: z.number().nullable().optional(),
  health: z.string().nullable().optional(),
  color: z.string().nullable().optional(),
});

export const milestoneListSchema = z.array(milestoneSchema);

export type Milestone = z.infer<typeof milestoneSchema>;

/** `MilestoneDetailResponse` of `GET /milestones/{id}`: the list fields and the description. */
export const milestoneDetailSchema = milestoneSchema.extend({
  boardName: z.string().nullable().optional(),
  boardPrefix: z.string().nullable().optional(),
  description: z.string().nullable().optional(),
  createdAt: z.string().nullable().optional(),
  updatedAt: z.string().nullable().optional(),
});

export type MilestoneDetail = z.infer<typeof milestoneDetailSchema>;

/** `RoadmapResponse`: one lane per board, the board first, then its child boards. */
export const roadmapSchema = z.object({
  from: z.string().nullable().optional(),
  to: z.string().nullable().optional(),
  boards: z.array(
    z.object({
      id: z.string(),
      name: z.string(),
      prefix: z.string().nullable().optional(),
      color: z.string().nullable().optional(),
      milestones: milestoneListSchema,
    })
  ),
});

export type Roadmap = z.infer<typeof roadmapSchema>;
export type RoadmapLane = Roadmap["boards"][number];

/** A label on a ticket. */
const ticketLabelSchema = z.object({
  id: z.string(),
  name: z.string(),
  color: z.string().nullable().optional(),
});

/**
 * `TicketListResponse` of `GET /kazan/boards/{boardId}/tickets`: root tickets
 * of one sprint or backlog, with their sub-tickets' statuses for the progress.
 */
export const boardTicketSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  title: z.string(),
  key: z.string(),
  displayOrder: z.number().nullable().optional(),
  type: z.string(),
  status: z.string(),
  priority: z.string().nullable(),
  missionRank: z.string().nullable(),
  dueDate: z.string().nullable(),
  createdBy: ticketReporterSchema.nullable().optional(),
  createdBySystemActor: ticketSystemReporterSchema.nullable().optional(),
  assignees: z.array(ticketPersonSchema).nullable(),
  board: ticketBoardSchema.nullable(),
  parentTicketId: z.string().nullable().optional(),
  parentTicket: z
    .object({ id: z.string(), key: z.string(), title: z.string() })
    .nullable()
    .optional(),
  subTickets: z
    .array(z.object({ id: z.string(), status: z.string() }))
    .nullable()
    .optional(),
  labels: z.array(ticketLabelSchema).nullable().optional(),
  sprint: z.object({ id: z.string(), name: z.string() }).nullable().optional(),
});

export const boardTicketListSchema = z.array(boardTicketSchema);

export type BoardTicket = z.infer<typeof boardTicketSchema>;

/**
 * The New board form, with the rules of the web app's create-board form: a
 * name, and a key of one to five upper-case letters, digits or underscores
 * that prefixes every ticket key.
 */
export const BOARD_NAME_MAX = 255;
export const BOARD_PREFIX_MAX = 5;
export const BOARD_PREFIX_PATTERN = /^[A-Z0-9_]+$/;

/** Backend code of a board key another board already uses (HTTP 409). */
export const BOARD_PREFIX_TAKEN_CODE = "KAZ_STA_007";

/** A colour of a palette, with the name a screen reader says. */
export interface PaletteColor {
  name: string;
  value: string;
}

/** The format the server accepts for a board, label, project or milestone colour. */
export const HEX_COLOR_PATTERN = /^#[0-9A-Fa-f]{6}$/;

/** The palette of labels and projects, the web app's: brighter than the board palette. */
export const OPTION_COLORS: readonly PaletteColor[] = [
  { name: "Red", value: "#EF4444" },
  { name: "Orange", value: "#F97316" },
  { name: "Yellow", value: "#EAB308" },
  { name: "Green", value: "#22C55E" },
  { name: "Cyan", value: "#06B6D4" },
  { name: "Blue", value: "#3B82F6" },
  { name: "Violet", value: "#8B5CF6" },
  { name: "Pink", value: "#EC4899" },
  { name: "Gray", value: "#6B7280" },
  { name: "Stone", value: "#78716C" },
];

/** The colour a new label or project starts with, as on the web: blue. */
export const DEFAULT_OPTION_COLOR = "#3B82F6";

/** The web app's palette of boards, which milestones share. */
export const MILESTONE_COLORS: readonly PaletteColor[] = [
  { name: "Korur", value: "#4AA3FF" },
  { name: "Sakura", value: "#FFB7C5" },
  { name: "Purple", value: "#C084FC" },
  { name: "Blue", value: "#60A5FA" },
  { name: "Green", value: "#4ADE80" },
  { name: "Yellow", value: "#FBBF24" },
  { name: "Gray", value: "#9CA3AF" },
];

/**
 * The colour a new board gets: the first colour of the web app's board
 * palette. The sheet has no colour field; the web board settings change it.
 */
export const DEFAULT_BOARD_COLOR = "#4AA3FF";

/** The web's milestone limits, which the server checks too. */
export const MILESTONE_NAME_MAX = 255;
export const MILESTONE_DESCRIPTION_MAX = 2000;

/**
 * `BoardMemberCandidateResponse` of `GET /kazan/boards/{boardId}/members/candidates`:
 * an active member of the board's organization, current board members
 * included, in the server's order (first name, last name, email). `role` is
 * the organization role. A candidate whose `kazanAccess` is not `true` is
 * listed so Add member can say why the server refuses to add them.
 */
export const boardMemberCandidateSchema = z.object({
  userId: z.string(),
  email: z.string(),
  firstName: z.string().nullable(),
  lastName: z.string().nullable(),
  role: z.string(),
  kazanAccess: z.boolean().optional(),
});

export const boardMemberCandidateListSchema = z.array(boardMemberCandidateSchema);

export type BoardMemberCandidate = z.infer<typeof boardMemberCandidateSchema>;

/** The roles a board member can be given; OWNER is the creator's and is never offered. */
export const ASSIGNABLE_BOARD_ROLES = ["ADMIN", "MEMBER", "VIEWER"] as const;
export type AssignableBoardRole = (typeof ASSIGNABLE_BOARD_ROLES)[number];

/** The role Add member preselects, as on the web. */
export const DEFAULT_MEMBER_ROLE: AssignableBoardRole = "MEMBER";

// ── Write bodies ─────────────────────────────────────────────────

/** The body of a label or project create and update: the trimmed name and a colour. */
export interface BoardOptionBody {
  name: string;
  color: string;
}

/** The body of `POST /milestones`. Dates are date-only; an omitted colour takes the board's. */
export interface MilestoneCreateBody {
  name: string;
  description?: string;
  startDate?: string;
  targetDate: string;
  color?: string;
}

/** The body of `PUT /milestones/{id}`: an omitted field stays, a clear flag empties it. */
export interface MilestoneUpdateBody {
  name?: string;
  description?: string;
  startDate?: string;
  targetDate?: string;
  clearStartDate?: boolean;
  clearDescription?: boolean;
  color?: string;
}

import { z } from "zod";

/** The longest sprint name the API takes. */
export const SPRINT_NAME_MAX = 255;

/** The longest series name the API takes; the round number follows it in each sprint name. */
export const SPRINT_SERIES_NAME_MAX = 240;

/** The longest sprint goal the API takes. */
export const SPRINT_GOAL_MAX = 1000;

/** The most child boards one create request may link. */
export const SPRINT_MAX_CHILD_BOARDS = 50;

/** The shortest and longest sprint an automated series runs, in days. */
export const SPRINT_LENGTH_MIN_DAYS = 1;
export const SPRINT_LENGTH_MAX_DAYS = 365;

/** What a series does with the unfinished tickets of a sprint it closes. */
export type SprintUnfinishedAction = "MOVE_TO_NEXT_SPRINT" | "MOVE_TO_BACKLOG";

/** When a series creates its next sprint: on the end date, or some days before it. */
export type SprintCreationMode = "AT_END" | "DAYS_BEFORE";

/** `SprintSeriesResponse`: the automation of a sprint's series. Instants carry `Z`. */
export const sprintSeriesSchema = z.object({
  id: z.string(),
  name: z.string(),
  nextRound: z.number().int(),
  nextSprintName: z.string().nullable(),
  enabled: z.boolean(),
  lengthDays: z.number().int(),
  unfinishedTicketAction: z.string(),
  creationMode: z.string(),
  createDaysBefore: z.number().int().nullable(),
  nextRolloverAt: z.string().nullable(),
  nextCreationAt: z.string().nullable(),
  lastRunAt: z.string().nullable(),
  lastRunStatus: z.string().nullable(),
  lastRunMessage: z.string().nullable(),
  timeZone: z.string().nullable().optional(),
});

export type SprintSeries = z.infer<typeof sprintSeriesSchema>;

/** `SprintDetailResponse`. `round` is the sprint's number in its series, null outside one. */
export const sprintDetailSchema = z.object({
  id: z.string(),
  name: z.string(),
  goal: z.string().nullable(),
  startDate: z.string().nullable(),
  endDate: z.string().nullable(),
  status: z.string(),
  round: z.number().int().nullable(),
  boards: z
    .array(z.object({ id: z.string(), name: z.string(), color: z.string().nullable().optional() }))
    .nullable()
    .optional(),
  series: sprintSeriesSchema.nullable(),
});

export type SprintDetail = z.infer<typeof sprintDetailSchema>;

/**
 * `SprintChildBoardResponse`: a descendant board a new sprint can be linked to.
 * `canLink` is false where the user is not a board admin; `activeSprintId`
 * names the sprint a takeover would close there, and
 * `activeSprintEndsSeriesName` the repeating series that closing it stops.
 */
export const sprintChildBoardSchema = z.object({
  id: z.string(),
  name: z.string(),
  prefix: z.string().nullable(),
  color: z.string().nullable().optional(),
  parentBoardId: z.string().nullable(),
  depth: z.number().int(),
  canLink: z.boolean(),
  activeSprintId: z.string().nullable(),
  activeSprintName: z.string().nullable(),
  activeSprintEndsSeriesName: z.string().nullable().optional(),
});

export const sprintChildBoardListSchema = z.array(sprintChildBoardSchema);

export type SprintChildBoard = z.infer<typeof sprintChildBoardSchema>;

/**
 * `SprintStartConflictResponse`: an active sprint a start would close.
 * `endsSeriesName` names the repeating series that stops with it.
 */
export const sprintStartConflictSchema = z.object({
  boardId: z.string(),
  boardName: z.string(),
  boardPrefix: z.string().nullable().optional(),
  sprintId: z.string(),
  sprintName: z.string(),
  unfinishedTicketCount: z.number().int(),
  movesToBacklog: z.boolean(),
  canManage: z.boolean(),
  endsSeriesName: z.string().nullable().optional(),
});

export type SprintStartConflict = z.infer<typeof sprintStartConflictSchema>;

/** `SprintStartPreviewResponse`. */
export const sprintStartPreviewSchema = z.object({
  canTakeOver: z.boolean(),
  conflicts: z.array(sprintStartConflictSchema),
});

export type SprintStartPreview = z.infer<typeof sprintStartPreviewSchema>;

/** `SprintSeriesRequest`: a full replace of a series' automation. */
export interface SprintSeriesBody {
  enabled: boolean;
  lengthDays: number;
  unfinishedTicketAction: SprintUnfinishedAction;
  creationMode: SprintCreationMode;
  /** Only with `DAYS_BEFORE`: 1 to `lengthDays - 1`. */
  createDaysBefore: number | null;
}

/** `SprintCreateRequest`. Dates are instants with `Z`. */
export interface SprintCreateBody {
  name: string;
  goal?: string;
  startDate: string;
  endDate: string;
  /** With repeat on: `name` is then the series name and the sprint is "{name} 1". */
  series?: SprintSeriesBody;
  childBoardIds?: string[];
  /** Sent only while a selected board runs an active sprint: true takes it over. */
  takeOver?: boolean;
}

/** `SprintUpdateRequest`; an omitted field keeps its value. */
export interface SprintUpdateBody {
  name?: string;
  goal?: string;
  startDate?: string;
  endDate?: string;
  /** Renames the series of a sprint in an enabled series, in place of `name`. */
  seriesName?: string;
}

/** `SprintCompleteRequest`. */
export interface SprintCompleteBody {
  ticketAction: "MOVE_TO_BACKLOG" | "MOVE_TO_SPRINT" | "DELETE";
  targetSprintId?: string;
  /** With `MOVE_TO_SPRINT` and no target: the next round of the sprint's series. */
  moveToNextSprint?: boolean;
}

/** The sprint a ticket or sub-ticket is planned in; null in the backlog. */
const movePlacementSprintSchema = z.object({ id: z.string(), name: z.string() });

/** A sub-ticket as Move to sprint lists it: with its status and where it is planned. */
export const moveSubTicketSchema = z.object({
  id: z.string(),
  key: z.string(),
  title: z.string(),
  status: z.string(),
  sprint: movePlacementSprintSchema.nullable().optional(),
});

export type MoveSubTicket = z.infer<typeof moveSubTicketSchema>;

/**
 * The fields of `TicketDetailResponse` that Move to sprint reads: the ticket's
 * board and sprint, and each sub-ticket with its own sprint.
 */
export const ticketMoveSourceSchema = z.object({
  id: z.string(),
  key: z.string(),
  title: z.string(),
  status: z.string(),
  board: z.object({ id: z.string(), name: z.string() }),
  sprint: movePlacementSprintSchema.nullable().optional(),
  subTickets: z.array(moveSubTicketSchema).nullable().optional(),
});

export type TicketMoveSource = z.infer<typeof ticketMoveSourceSchema>;

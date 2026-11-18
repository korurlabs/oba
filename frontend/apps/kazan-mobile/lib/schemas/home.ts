import { z } from "zod";

/*
 * Wire shapes of the Home dashboard reads (`/kazan/home/*`). Every read covers
 * only the boards the user is a member of; a user without boards gets the empty
 * shape, never an error. Instants are ISO strings with `Z`; `LocalDate` fields
 * are `YYYY-MM-DD`. Enum fields stay strings: the kit resolves an unknown value
 * to a neutral look, so a value this build does not know cannot fail a section.
 */

/** The window the leaderboard counts. */
export const HomeLeaderboardPeriodSchema = z.enum(["SPRINT", "LAST_30_DAYS"]);
export type HomeLeaderboardPeriod = z.infer<typeof HomeLeaderboardPeriodSchema>;
// eslint-disable-next-line @typescript-eslint/no-redeclare -- reason: the enum's type and its value map share one name, so callers write `HomeLeaderboardPeriod.SPRINT` and annotate with the same identifier.
export const HomeLeaderboardPeriod = HomeLeaderboardPeriodSchema.enum;

/** `HomeSummaryResponse`: open incidents and open active-sprint tickets, sub-tickets left out. */
export const homeSummarySchema = z.object({
  openIncidents: z.number().int().nonnegative(),
  openSprintTickets: z.number().int().nonnegative(),
});

/**
 * `HomeStaleTicketResponse`: a backlog ticket nobody touched for 30 days.
 * `canDelete` is false on a board where the user is only a viewer.
 */
export const homeStaleTicketSchema = z.object({
  id: z.string(),
  key: z.string(),
  title: z.string(),
  status: z.string(),
  priority: z.string().nullable(),
  missionRank: z.string().nullable(),
  boardId: z.string(),
  boardName: z.string(),
  boardSlug: z.string(),
  lastActivityAt: z.string(),
  canDelete: z.boolean(),
});

/** `HomeStaleBacklogResponse`: at most 50 tickets, oldest activity first; `total` counts them all. */
export const homeStaleBacklogSchema = z.object({
  total: z.number().int().nonnegative(),
  tickets: z.array(homeStaleTicketSchema),
});

/** `HomeLeaderboardEntryResponse`: `points` are size points split between assignees, one decimal. */
export const homeLeaderboardEntrySchema = z.object({
  userId: z.string(),
  firstName: z.string().nullable(),
  lastName: z.string().nullable(),
  email: z.string(),
  points: z.number(),
  tickets: z.number().int().nonnegative(),
});

/**
 * `HomeLeaderboardResponse`. `period` is the window actually counted:
 * `LAST_30_DAYS` when `SPRINT` was asked and no member board has an active
 * sprint. Every user is ranked; the client cuts the list.
 */
export const homeLeaderboardSchema = z.object({
  period: HomeLeaderboardPeriodSchema,
  from: z.string().nullable(),
  to: z.string().nullable(),
  entries: z.array(homeLeaderboardEntrySchema),
});

/**
 * One burndown day, a date on the Istanbul business calendar. `remaining` is
 * null for days after today; `ideal` falls from the total to 0.
 */
export const homeBurndownPointSchema = z.object({
  date: z.string(),
  remaining: z.number().int().nonnegative().nullable(),
  ideal: z.number(),
});

/** `BoardListResponse` as the sprint read lists the boards with an active sprint. */
export const homeSprintBoardSchema = z.object({
  id: z.string(),
  name: z.string(),
  slug: z.string(),
  prefix: z.string().nullable().optional(),
  color: z.string().nullable().optional(),
  icon: z.string().nullable().optional(),
});

/**
 * `HomeSprintResponse`: the active sprint of the focus board. Without one every
 * board and sprint field is null, both counts are 0 and both lists are empty.
 * `burndown` is empty when the sprint lacks a start or end date.
 */
export const homeSprintSchema = z.object({
  boardId: z.string().nullable(),
  boardName: z.string().nullable(),
  boardSlug: z.string().nullable(),
  sprintId: z.string().nullable(),
  sprintName: z.string().nullable(),
  startDate: z.string().nullable(),
  endDate: z.string().nullable(),
  totalTickets: z.number().int().nonnegative(),
  openTickets: z.number().int().nonnegative(),
  burndown: z.array(homeBurndownPointSchema),
  boards: z.array(homeSprintBoardSchema),
});

/** `HomeDeadlineResponse`: an open milestone due within 60 days or overdue; `targetDate` is a UTC day. */
export const homeDeadlineSchema = z.object({
  id: z.string(),
  name: z.string(),
  targetDate: z.string(),
  health: z.string(),
  doneCount: z.number().int().nonnegative(),
  totalCount: z.number().int().nonnegative(),
  color: z.string().nullable(),
  boardId: z.string(),
  boardName: z.string(),
  boardSlug: z.string(),
});

/** At most 8 deadlines, by target date then name. */
export const homeDeadlinesSchema = z.array(homeDeadlineSchema);

export type HomeSummary = z.infer<typeof homeSummarySchema>;
export type HomeStaleTicket = z.infer<typeof homeStaleTicketSchema>;
export type HomeStaleBacklog = z.infer<typeof homeStaleBacklogSchema>;
export type HomeLeaderboardEntry = z.infer<typeof homeLeaderboardEntrySchema>;
export type HomeLeaderboard = z.infer<typeof homeLeaderboardSchema>;
export type HomeBurndownPoint = z.infer<typeof homeBurndownPointSchema>;
export type HomeSprintBoard = z.infer<typeof homeSprintBoardSchema>;
export type HomeSprint = z.infer<typeof homeSprintSchema>;
export type HomeDeadline = z.infer<typeof homeDeadlineSchema>;

import { z } from "zod";
import type { BoardListResponse } from "@/features/boards/schemas";
import { MilestoneHealthSchema } from "@/features/milestones/schemas";
import {
  MissionRankSchema,
  TicketPrioritySchema,
  TicketStatusSchema,
} from "@/features/tickets/schemas";

// ── Enum Schemas ───────────────────────────────────────────────────

/**
 * The window the leaderboard counts. `SPRINT` is every ACTIVE sprint of the
 * member boards, the same tickets as "Finished this sprint" on the update
 * cards; `LAST_30_DAYS` is tickets that reached Done in the last 30 days.
 */
export const HomeLeaderboardPeriodSchema = z.enum(["SPRINT", "LAST_30_DAYS"]);
export type HomeLeaderboardPeriod = z.infer<typeof HomeLeaderboardPeriodSchema>;
export const HomeLeaderboardPeriod = HomeLeaderboardPeriodSchema.enum;

// ── Response Schemas ────────────────────────────────────────────────

/**
 * Body of `GET /kaizen/home/summary`. Both counts cover only the boards the
 * user is a member of and leave sub-tickets out, so they match the incidents
 * and tickets lists the stat cards link to. A user without boards gets 0.
 */
export const homeSummaryResponseSchema = z.object({
  openIncidents: z.number().int().nonnegative(),
  openSprintTickets: z.number().int().nonnegative(),
});

// Every home dashboard read below covers only the boards the user is a
// member of; a user without boards gets the empty shape, never an error.
// Instants are ISO timestamps; `LocalDate` fields are `YYYY-MM-DD` strings.

/**
 * A backlog ticket nobody touched for 30 days. `lastActivityAt` is the later
 * of its last edit and its latest live comment. `canDelete` is false on a
 * board where the user is only a viewer, so the delete controls stay hidden.
 */
export const homeStaleTicketResponseSchema = z.object({
  id: z.string(),
  key: z.string(),
  title: z.string(),
  status: TicketStatusSchema,
  priority: TicketPrioritySchema,
  missionRank: MissionRankSchema,
  boardId: z.string(),
  boardName: z.string(),
  boardSlug: z.string(),
  lastActivityAt: z.string(),
  canDelete: z.boolean(),
});

/** Body of `GET /kaizen/home/stale-backlog`: at most 50 tickets, oldest activity first; `total` counts them all. */
export const homeStaleBacklogResponseSchema = z.object({
  total: z.number().int().nonnegative(),
  tickets: z.array(homeStaleTicketResponseSchema),
});

/** One ranked user. `points` are size points split between assignees, one decimal. */
export const homeLeaderboardEntryResponseSchema = z.object({
  userId: z.string(),
  firstName: z.string().nullable(),
  lastName: z.string().nullable(),
  email: z.string(),
  points: z.number(),
  tickets: z.number().int().nonnegative(),
});

/**
 * Body of `GET /kaizen/home/leaderboard`. `period` is the window actually
 * counted: `LAST_30_DAYS` when `SPRINT` was asked and no member board has an
 * ACTIVE sprint. `from` and `to` are null for `SPRINT`. Every user is ranked;
 * the client cuts the list and finds the current user's row itself.
 */
export const homeLeaderboardResponseSchema = z.object({
  period: HomeLeaderboardPeriodSchema,
  from: z.string().nullable(),
  to: z.string().nullable(),
  entries: z.array(homeLeaderboardEntryResponseSchema),
});

/**
 * One burndown day, a date in the updates channel's zone (`Europe/Istanbul`).
 * `remaining` is null for days after today; `ideal` falls from the total to 0.
 */
export const homeBurndownPointResponseSchema = z.object({
  date: z.string(),
  remaining: z.number().int().nonnegative().nullable(),
  ideal: z.number(),
});

/**
 * Body of `GET /kaizen/home/sprint`: the ACTIVE sprint of the focus board.
 * Without a candidate board every board and sprint field is null, both counts
 * are 0 and both lists are empty. `burndown` is empty when the sprint lacks a
 * start or end date. `boards` are the member boards with an ACTIVE sprint, by
 * name, for the focus select.
 */
export const homeSprintResponseSchema = z.object({
  boardId: z.string().nullable(),
  boardName: z.string().nullable(),
  boardSlug: z.string().nullable(),
  sprintId: z.string().nullable(),
  sprintName: z.string().nullable(),
  startDate: z.string().nullable(),
  endDate: z.string().nullable(),
  totalTickets: z.number().int().nonnegative(),
  openTickets: z.number().int().nonnegative(),
  burndown: z.array(homeBurndownPointResponseSchema),
  boards: z.array(z.custom<BoardListResponse>()),
});

/**
 * One OPEN milestone due within 60 days or overdue. `targetDate` is a UTC
 * calendar date, the calendar its `health` is computed on.
 */
export const homeDeadlineResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  targetDate: z.string(),
  health: MilestoneHealthSchema,
  doneCount: z.number().int().nonnegative(),
  totalCount: z.number().int().nonnegative(),
  color: z.string().nullable(),
  boardId: z.string(),
  boardName: z.string(),
  boardSlug: z.string(),
});

/** Body of `GET /kaizen/home/deadlines`: at most 8, by target date then name. */
export const homeDeadlinesResponseSchema = z.array(homeDeadlineResponseSchema);

// ── Inferred Types ──────────────────────────────────────────────────

export type HomeSummaryResponse = z.infer<typeof homeSummaryResponseSchema>;
export type HomeStaleTicketResponse = z.infer<typeof homeStaleTicketResponseSchema>;
export type HomeStaleBacklogResponse = z.infer<typeof homeStaleBacklogResponseSchema>;
export type HomeLeaderboardEntryResponse = z.infer<typeof homeLeaderboardEntryResponseSchema>;
export type HomeLeaderboardResponse = z.infer<typeof homeLeaderboardResponseSchema>;
export type HomeBurndownPointResponse = z.infer<typeof homeBurndownPointResponseSchema>;
export type HomeSprintResponse = z.infer<typeof homeSprintResponseSchema>;
export type HomeDeadlineResponse = z.infer<typeof homeDeadlineResponseSchema>;

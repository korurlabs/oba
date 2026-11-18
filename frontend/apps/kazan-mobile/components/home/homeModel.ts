import {
  BUSINESS_TIME_ZONE,
  businessToday,
  dayInZone,
  daysBetween,
  todayDateOnly,
  utcTodayDateOnly,
} from "@/lib/dates";
import type { HomeBurndownPoint, HomeLeaderboardEntry } from "@/lib/schemas/home";
import type { TicketListItem } from "@/lib/schemas/tickets";

/*
 * The pure half of the Home dashboard: sprint progress, burndown geometry,
 * leaderboard rows, deadline counters, stale selection and the My Tickets
 * groups. No React or native import, so `bun test` covers it.
 */

// ── Sprint ───────────────────────────────────────────────────────

/** Where a sprint stands on the business calendar. */
export type SprintProgress =
  | { kind: "no-end"; startDay: string | null }
  | {
      kind: "running" | "ended";
      startDay: string | null;
      endDay: string;
      /** Days until the end day; negative once the sprint ran past it. */
      daysLeft: number;
      /** Share of the sprint days that have started, 0 to 100; null without a start day. */
      percent: number | null;
    };

/**
 * The sprint's days left and days passed. Sprint days follow the Istanbul
 * business calendar, the same calendar as the burndown, so the counter and
 * the chart's today marker agree for every viewer. Today counts as passed:
 * on the first day one day of the sprint has started.
 */
export function sprintProgress(
  startDate: string | null,
  endDate: string | null,
  now: Date = new Date()
): SprintProgress {
  const startDay: string | null = dayInZone(startDate, BUSINESS_TIME_ZONE);
  const endDay: string | null = dayInZone(endDate, BUSINESS_TIME_ZONE);
  if (!endDay) return { kind: "no-end", startDay };
  const today: string = businessToday(now);
  const daysLeft: number = daysBetween(today, endDay) ?? 0;
  let percent: number | null = null;
  if (startDay) {
    const length: number = (daysBetween(startDay, endDay) ?? 0) + 1;
    const passed: number = (daysBetween(startDay, today) ?? 0) + 1;
    percent = length > 0 ? Math.min(100, Math.max(0, Math.round((passed / length) * 100))) : 100;
  }
  return { kind: daysLeft < 0 ? "ended" : "running", startDay, endDay, daysLeft, percent };
}

// ── Burndown ─────────────────────────────────────────────────────

export interface BurndownGeometry {
  /** The SVG path of the open-ticket line, up to today. Empty without a day. */
  actualPath: string;
  /** The SVG path of the even-pace line over every day. */
  idealPath: string;
  /** The index of the last day with a count, the today marker; -1 when none. */
  todayIndex: number;
  /** The x of day `index`. */
  x: (index: number) => number;
  /** The y of a ticket count. */
  y: (value: number) => number;
  /** The day nearest to an x, for the scrub. */
  indexAt: (x: number) => number;
}

/** Pixel geometry of the burndown in a `width` by `height` box, `pad` inside every edge. */
export function burndownGeometry(
  points: readonly HomeBurndownPoint[],
  width: number,
  height: number,
  pad: number
): BurndownGeometry {
  const count: number = points.length;
  const maxValue: number = Math.max(
    1,
    ...points.map((point) => Math.max(point.remaining ?? 0, point.ideal))
  );
  const innerWidth: number = Math.max(0, width - pad * 2);
  const innerHeight: number = Math.max(0, height - pad * 2);
  const step: number = count > 1 ? innerWidth / (count - 1) : 0;
  const x = (index: number): number => pad + index * step;
  const y = (value: number): number => pad + innerHeight - (value / maxValue) * innerHeight;
  const indexAt = (atX: number): number => {
    if (count <= 1 || step === 0) return 0;
    return Math.min(count - 1, Math.max(0, Math.round((atX - pad) / step)));
  };

  let todayIndex = -1;
  points.forEach((point, index) => {
    if (point.remaining !== null) todayIndex = index;
  });

  const line = (values: { index: number; value: number }[]): string =>
    values
      .map(
        ({ index, value }, i) =>
          `${i === 0 ? "M" : "L"}${x(index).toFixed(1)},${y(value).toFixed(1)}`
      )
      .join(" ");

  const actual: { index: number; value: number }[] = [];
  points.forEach((point, index) => {
    if (point.remaining !== null && index <= todayIndex)
      actual.push({ index, value: point.remaining });
  });

  return {
    actualPath: line(actual),
    idealPath: line(points.map((point, index) => ({ index, value: point.ideal }))),
    todayIndex,
    x,
    y,
    indexAt,
  };
}

/** The even-pace value as the chart labels it: whole tickets, or one decimal. */
export function formatIdeal(value: number, language: string): string {
  return new Intl.NumberFormat(language, { maximumFractionDigits: 1 }).format(value);
}

// ── Leaderboard ──────────────────────────────────────────────────

/** How many ranked rows Home shows before the current user's own row. */
export const LEADERBOARD_ROWS = 5;

export interface LeaderboardRowModel {
  rank: number;
  entry: HomeLeaderboardEntry;
  name: string;
  isMe: boolean;
  /** The current user's row below the top rows, set apart by a divider. */
  detached: boolean;
}

/** The name of a ranked user: first and last name, or the email without them. */
export function displayName(
  entry: Pick<HomeLeaderboardEntry, "firstName" | "lastName" | "email">
): string {
  const name: string = [entry.firstName, entry.lastName]
    .filter((part): part is string => !!part && part.trim().length > 0)
    .join(" ")
    .trim();
  return name || entry.email;
}

/**
 * The top rows of the leaderboard, plus the current user's row with its real
 * rank when it falls below them, so everyone sees where they stand. Users
 * with the same points share a rank.
 */
export function leaderboardRows(
  entries: readonly HomeLeaderboardEntry[],
  meId: string | undefined,
  limit: number = LEADERBOARD_ROWS
): LeaderboardRowModel[] {
  const ranked: LeaderboardRowModel[] = [];
  entries.forEach((entry, index) => {
    const previous: LeaderboardRowModel | undefined = ranked[index - 1];
    const rank: number =
      previous && previous.entry.points === entry.points ? previous.rank : index + 1;
    ranked.push({
      rank,
      entry,
      name: displayName(entry),
      isMe: entry.userId === meId,
      detached: false,
    });
  });
  const top: LeaderboardRowModel[] = ranked.slice(0, limit);
  const mine: LeaderboardRowModel | undefined = ranked.slice(limit).find((row) => row.isMe);
  return mine ? [...top, { ...mine, detached: true }] : top;
}

/**
 * A theme colour such as "hsl(20.5 90.2% 48.2%)" at the given opacity, for a
 * tint the tokens do not carry. React Native reads the alpha only in the
 * `hsla(h s l / a)` form.
 */
export function hslWithAlpha(color: string, alpha: number): string {
  const match: RegExpMatchArray | null = color.match(/^hsl\((.+)\)$/);
  if (!match) return color;
  return `hsla(${match[1]} / ${alpha})`;
}

/** Points with at most one decimal in the app language: "24.5", "24,5". */
export function formatPoints(points: number, language: string): string {
  return new Intl.NumberFormat(language, { maximumFractionDigits: 1 }).format(points);
}

// ── Deadlines ────────────────────────────────────────────────────

/**
 * Days from today to a milestone's target day. Milestone health is computed on
 * the UTC calendar, so the counter uses the UTC day too and agrees with the
 * health badge next to it.
 */
export function daysUntilDeadline(targetDate: string, now: Date = new Date()): number | null {
  return daysBetween(utcTodayDateOnly(now), targetDate);
}

// ── Stale backlog ────────────────────────────────────────────────

/**
 * Whether an organization role may create, change or delete tickets. The
 * ticket write endpoints require `MEMBER` or above, so a `GUEST` gets no
 * delete controls even on a board where it is a contributor; an unknown role
 * (the profile is still loading) gets none either.
 */
export function canWriteTickets(role: string | null | undefined): boolean {
  return role === "OWNER" || role === "ADMIN" || role === "MEMBER";
}

/** Adds or removes `id` from a selection, returning a new set. */
export function toggleSelection(selection: ReadonlySet<string>, id: string): Set<string> {
  const next: Set<string> = new Set(selection);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  return next;
}

/** Keeps only the selected ids that are still listed, after a refetch or delete. */
export function pruneSelection(
  selection: ReadonlySet<string>,
  listedIds: readonly string[]
): Set<string> {
  const listed: Set<string> = new Set(listedIds);
  return new Set([...selection].filter((id) => listed.has(id)));
}

// ── My Tickets ───────────────────────────────────────────────────

/** The order of the My Tickets groups: work in flight first, then waiting, review and to do. */
export const MY_TICKETS_STATUS_ORDER = [
  "IN_PROGRESS",
  "WAITING_FOR_THIRD_PARTY",
  "IN_REVIEW",
  "TODO",
] as const;

export interface TicketGroup {
  status: string;
  tickets: TicketListItem[];
}

/**
 * Tickets grouped by status in the My Tickets order. A status this build does
 * not know gets its own group at the end, so no ticket goes missing.
 */
export function groupTicketsByStatus(tickets: readonly TicketListItem[]): TicketGroup[] {
  const groups: Map<string, TicketListItem[]> = new Map();
  for (const ticket of tickets) {
    const list: TicketListItem[] = groups.get(ticket.status) ?? [];
    list.push(ticket);
    groups.set(ticket.status, list);
  }
  const known: string[] = MY_TICKETS_STATUS_ORDER.filter((status) => groups.has(status));
  const unknown: string[] = [...groups.keys()].filter(
    (status) => !(MY_TICKETS_STATUS_ORDER as readonly string[]).includes(status)
  );
  return [...known, ...unknown].map((status) => ({ status, tickets: groups.get(status) ?? [] }));
}

/** Where a ticket's due day stands for the viewer. */
export interface DueState {
  /** The due day in the device zone, `YYYY-MM-DD`. */
  day: string;
  /** Due today. */
  today: boolean;
  /** The due day has passed. */
  overdue: boolean;
}

/**
 * The due day of a ticket in the viewer's zone, compared with the viewer's
 * today; null without a due date. A due date is an instant, so the day it
 * falls on depends on where the viewer is.
 */
export function dueState(dueDate: string | null, now: Date = new Date()): DueState | null {
  const day: string | null = dayInZone(dueDate, Intl.DateTimeFormat().resolvedOptions().timeZone);
  if (!day) return null;
  const diff: number = daysBetween(todayDateOnly(now), day) ?? 0;
  return { day, today: diff === 0, overdue: diff < 0 };
}

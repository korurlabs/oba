import {
  addDays,
  addMonths,
  addQuarters,
  addYears,
  differenceInCalendarDays,
  endOfMonth,
  endOfQuarter,
  endOfYear,
  max,
  min,
  startOfMonth,
  startOfQuarter,
  startOfYear,
} from "date-fns";
import type {
  MilestoneHealth,
  MilestoneListResponse,
  MilestoneStatus,
  RoadmapBoardResponse,
} from "@/features/milestones/schemas";
import { parseDateOnly } from "@oba/shared/lib/date-only";
import type { RoadmapZoom } from "../hooks/use-roadmap-params";

/**
 * Width of one header column (a month, a quarter or a year). Every column is
 * equally wide so the grid reads evenly; a day's width varies a little with
 * the length of its period.
 */
export const COLUMN_WIDTH_PX: Record<RoadmapZoom, number> = {
  month: 160,
  quarter: 220,
  year: 280,
};

/** Side of the diamond drawn for a milestone without a start date. */
export const DIAMOND_PX = 14;

/** Empty periods kept before the earliest and after the latest date. */
const BUFFER_PERIODS: Record<RoadmapZoom, number> = { month: 2, quarter: 1, year: 1 };

const PERIOD: Record<
  RoadmapZoom,
  { startOf: (d: Date) => Date; endOf: (d: Date) => Date; add: (d: Date, n: number) => Date }
> = {
  month: { startOf: startOfMonth, endOf: endOfMonth, add: addMonths },
  quarter: { startOf: startOfQuarter, endOf: endOfQuarter, add: addQuarters },
  year: { startOf: startOfYear, endOf: endOfYear, add: addYears },
};

export type RoadmapPeriod = { start: Date; days: number };

export type RoadmapWindow = {
  zoom: RoadmapZoom;
  start: Date;
  periods: RoadmapPeriod[];
  columnWidth: number;
  width: number;
};

/** Where a milestone's marker sits on the grid, in px from the window start. */
export type MarkerPlacement = {
  /** Left edge of the bar, or the centre of the diamond. */
  startX: number;
  /** Right edge of the bar, or the centre of the diamond. */
  endX: number;
  hasSpan: boolean;
};

/** The dates a milestone is drawn with; a drag preview replaces them. */
export type MilestoneDates = { startDate: Date | null; targetDate: Date };

/**
 * The visible window: every milestone date and today, padded by a few empty
 * periods on each side, then extended to the right until it fills
 * `minWidth` so the grid never stops short of the viewport.
 */
export function computeWindow(
  boards: RoadmapBoardResponse[],
  zoom: RoadmapZoom,
  today: Date | null,
  minWidth: number,
): RoadmapWindow {
  const period = PERIOD[zoom];
  const columnWidth = COLUMN_WIDTH_PX[zoom];

  const dates: Date[] = today ? [today] : [];
  for (const board of boards) {
    for (const m of board.milestones) {
      // A day that does not parse does not widen the window; `milestoneDates`
      // warns about it and the timeline leaves that milestone out.
      for (const day of [m.targetDate, m.startDate]) {
        const date = parseDateOnly(day);
        if (date) dates.push(date);
      }
    }
  }
  if (dates.length === 0) dates.push(new Date());

  const start = period.startOf(period.add(min(dates), -BUFFER_PERIODS[zoom]));
  const lastStart = period.startOf(period.add(max(dates), BUFFER_PERIODS[zoom]));

  const periods: RoadmapPeriod[] = [];
  let cursor = start;
  while (cursor <= lastStart || periods.length * columnWidth < minWidth) {
    periods.push({
      start: cursor,
      days: differenceInCalendarDays(period.endOf(cursor), cursor) + 1,
    });
    cursor = period.add(cursor, 1);
  }

  return { zoom, start, periods, columnWidth, width: periods.length * columnWidth };
}

/**
 * The x position of a calendar day: its period's column plus the day's share
 * of that column. The anchor picks the day's left edge, middle or right edge.
 */
export function dateToX(
  range: RoadmapWindow,
  date: Date,
  anchor: "start" | "centre" | "end" = "start",
): number {
  const period = PERIOD[range.zoom];
  const periodStart = period.startOf(date);
  const index = differenceInPeriods(range, periodStart);
  const days = differenceInCalendarDays(period.endOf(periodStart), periodStart) + 1;
  const dayInPeriod = differenceInCalendarDays(date, periodStart);
  const fraction = anchor === "start" ? 0 : anchor === "centre" ? 0.5 : 1;
  return (index + (dayInPeriod + fraction) / days) * range.columnWidth;
}

/** The calendar day under an x position; the inverse of `dateToX`, clamped to the window. */
export function xToDate(range: RoadmapWindow, x: number): Date {
  const lastIndex = range.periods.length - 1;
  const index = Math.min(lastIndex, Math.max(0, Math.floor(x / range.columnWidth)));
  const period = range.periods[index]!;
  const fraction = (x - index * range.columnWidth) / range.columnWidth;
  const day = Math.min(period.days - 1, Math.max(0, Math.floor(fraction * period.days)));
  return addDays(period.start, day);
}

function differenceInPeriods(range: RoadmapWindow, periodStart: Date): number {
  const months =
    (periodStart.getFullYear() - range.start.getFullYear()) * 12 +
    (periodStart.getMonth() - range.start.getMonth());
  if (range.zoom === "month") return months;
  if (range.zoom === "quarter") return months / 3;
  return months / 12;
}

/** Index of the period that contains `date`, or null when it is outside the window. */
export function periodIndexOf(range: RoadmapWindow, date: Date): number | null {
  const index = differenceInPeriods(range, PERIOD[range.zoom].startOf(date));
  return index >= 0 && index < range.periods.length ? index : null;
}

/**
 * The days a milestone is drawn on, or null when its target is not a
 * `YYYY-MM-DD` day: the marker has no place on the grid then, so the caller
 * leaves the milestone out and the warning records the cause. A start that
 * does not parse is dropped with a warning and the marker becomes a diamond on
 * the target.
 */
export function milestoneDates(milestone: MilestoneListResponse): MilestoneDates | null {
  const targetDate = parseDateOnly(milestone.targetDate);
  if (!targetDate) {
    console.warn(
      "[milestoneDates] target is not a YYYY-MM-DD day; the milestone is not drawn",
      milestone.id,
      milestone.targetDate,
    );
    return null;
  }
  const startDate = milestone.startDate ? parseDateOnly(milestone.startDate) : null;
  if (milestone.startDate && !startDate) {
    console.warn(
      "[milestoneDates] start is not a YYYY-MM-DD day; the milestone is drawn on its target",
      milestone.id,
      milestone.startDate,
    );
  }
  return { startDate, targetDate };
}

/**
 * A bar from the start day's left edge to the target day's right edge, or a
 * diamond centred on the target day when there is no start before the target.
 */
export function placeMarker(range: RoadmapWindow, dates: MilestoneDates): MarkerPlacement {
  const { startDate, targetDate } = dates;
  const hasSpan = startDate !== null && startDate < targetDate;
  const startX = hasSpan ? dateToX(range, startDate) : dateToX(range, targetDate, "centre");
  const endX = hasSpan ? dateToX(range, targetDate, "end") : startX;
  return { startX, endX, hasSpan };
}

/** Where an incoming dependency line ends: the bar's left edge or the diamond's left tip. */
export function markerInX(placement: MarkerPlacement): number {
  return placement.hasSpan ? placement.startX : placement.startX - DIAMOND_PX / 2;
}

/** Where an outgoing dependency line starts: the bar's right edge or the diamond's right tip. */
export function markerOutX(placement: MarkerPlacement): number {
  return placement.hasSpan ? placement.endX : placement.endX + DIAMOND_PX / 2;
}

/**
 * The server's conflict rule, for dates that changed on the client before the
 * server answered: the dependent starts (or, without a start, is due) before
 * the dependency's target date. `YYYY-MM-DD` strings compare as dates.
 */
export function isDependencyConflict(
  dependentStartDate: string | null | undefined,
  dependentTargetDate: string,
  dependencyTargetDate: string,
): boolean {
  return (dependentStartDate ?? dependentTargetDate) < dependencyTargetDate;
}

/**
 * Milestones by start date (the target date when there is no start), then
 * target date, then name, then id, so the timeline reads left to right and the
 * order is stable. The CLI's roadmap sorts its rows the same way.
 */
export function sortMilestones(milestones: MilestoneListResponse[]): MilestoneListResponse[] {
  return [...milestones].sort(
    (a, b) =>
      (a.startDate ?? a.targetDate).localeCompare(b.startDate ?? b.targetDate) ||
      a.targetDate.localeCompare(b.targetDate) ||
      a.name.localeCompare(b.name) ||
      a.id.localeCompare(b.id),
  );
}

export type RoadmapFilters = {
  status: MilestoneStatus | null;
  health: MilestoneHealth[] | null;
};

/** Keeps the milestones that match every set filter; an unset filter matches all. */
export function filterRoadmapBoards(
  boards: RoadmapBoardResponse[],
  filters: RoadmapFilters,
): RoadmapBoardResponse[] {
  const health = filters.health && filters.health.length > 0 ? new Set(filters.health) : null;
  if (!filters.status && !health) return boards;
  return boards.map((board) => ({
    ...board,
    milestones: board.milestones.filter(
      (m) => (!filters.status || m.status === filters.status) && (!health || health.has(m.health)),
    ),
  }));
}

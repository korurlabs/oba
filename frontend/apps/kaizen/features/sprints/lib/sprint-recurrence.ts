/**
 * Date helpers for automated sprints.
 *
 * An automated sprint rolls over at 00:00 UTC on its end date, and the server
 * rejects dates at any other time. The date picker works in the viewer's own
 * zone, so while automation is on the picked day is read as a calendar day and
 * sent as that day at 00:00 UTC. Without this, "Oct 27" picked in Turkey would
 * be stored as Oct 26 21:00 UTC and the sprint would close a day early.
 *
 * This module imports only types from the sprint schemas, because the schemas
 * import it at runtime.
 */

import type {
  SprintRecurrenceFieldsValues,
  SprintSeriesRequest,
} from "../schemas";

const MS_PER_DAY = 1000 * 60 * 60 * 24;

/** The picked calendar day (in the viewer's zone) at 00:00 UTC. */
export function toUtcCalendarDate(date: Date): Date {
  return new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
}

/**
 * A stored instant shown as its UTC calendar day: a local Date on that day, so
 * the date picker shows "Oct 27" for 2026-10-27T00:00:00Z in every zone.
 */
export function fromUtcCalendarDate(value: Date | string): Date {
  const date = new Date(value);
  return new Date(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate());
}

/** True when the instant is exactly 00:00:00.000 UTC. */
export function isUtcMidnight(value: Date | string): boolean {
  return new Date(value).getTime() % MS_PER_DAY === 0;
}

/**
 * Whole calendar days from `start` to `end`, both read as calendar days in
 * the viewer's zone. This is the sprint length the server derives from the
 * two dates once they are sent at 00:00 UTC. Null when a date is missing.
 */
export function calendarDaysBetween(
  start: Date | null | undefined,
  end: Date | null | undefined,
): number | null {
  if (!start || !end) return null;
  const diff = toUtcCalendarDate(end).getTime() - toUtcCalendarDate(start).getTime();
  return Math.round(diff / MS_PER_DAY);
}

/** A UTC calendar day in the active locale, e.g. "Oct 27, 2026". */
export function formatUtcDate(locale: string, value: Date | string): string {
  return new Intl.DateTimeFormat(locale, {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(value));
}

/**
 * The request for the form values. "Days before" is sent only for the mode
 * that uses it, so a value left in the hidden field is not stored. The
 * length is checked by the form only while repeat is on; a request that
 * switches repeat off still needs a value, so `fallbackLengthDays` fills it.
 */
export function toSprintSeriesRequest(
  values: SprintRecurrenceFieldsValues,
  fallbackLengthDays: number,
): SprintSeriesRequest {
  const length = values.lengthDays;
  const lengthInRange = length != null && length >= 1 && length <= 365;
  return {
    enabled: values.enabled,
    lengthDays: lengthInRange ? length : fallbackLengthDays,
    unfinishedTicketAction: values.unfinishedTicketAction,
    creationMode: values.creationMode,
    createDaysBefore:
      values.creationMode === "DAYS_BEFORE" ? (values.createDaysBefore ?? null) : null,
  };
}

/**
 * Trailing " <digits>" of a sprint name, as the server splits it when repeat
 * is switched on for a sprint outside any series.
 */
const SERIES_NAME_PATTERN = /^(.*\S)\s+(\d{1,6})$/;
const SERIES_MAX_ROUND = 999999;

/**
 * The series name and round the server derives from a sprint name: a
 * trailing number from 1 to 999999 is the round ("Sprint 7" gives "Sprint",
 * 7), any other name is the series name with round 1. Used to preview the
 * next sprint's name before the series exists.
 */
export function splitSeriesName(name: string): { seriesName: string; round: number } {
  const trimmed = name.trim();
  const match = SERIES_NAME_PATTERN.exec(trimmed);
  if (match) {
    const round = Number(match[2]);
    if (round >= 1 && round <= SERIES_MAX_ROUND) {
      return { seriesName: match[1] ?? trimmed, round };
    }
  }
  return { seriesName: trimmed, round: 1 };
}

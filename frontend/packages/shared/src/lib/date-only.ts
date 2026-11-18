// Calendar days (`YYYY-MM-DD`) on the viewer's own calendar, shared by the Oba
// web apps.
//
// No date library and no React: the only import is the sibling
// `format-instant`, which has none, so a plain function, a server module and a
// client component can all call these functions.
//
// A day the user picks is a calendar day, not an instant. Every function here
// reads and builds it from the local date fields, so a viewer east or west of
// UTC never sees it shift. `new Date("2026-11-04")` reads UTC midnight and
// shows 3 November west of UTC, and a zone-aware formatter renders in UTC until
// hydration, which shows local midnight as the day before east of UTC.

import { formatInstant } from "./format-instant";

const DATE_ONLY_PATTERN: RegExp = /^(\d{4})-(\d{2})-(\d{2})$/;

interface DayFields {
  year: number;
  /** 1 to 12. */
  month: number;
  day: number;
}

function pad(value: number, length: number): string {
  return String(value).padStart(length, "0");
}

function formatDayFields(fields: DayFields): string {
  return `${pad(fields.year, 4)}-${pad(fields.month, 2)}-${pad(fields.day, 2)}`;
}

/**
 * Reads the fields of a `YYYY-MM-DD` value, or null when it is not a real day
 * (`2026-02-30`, `2026-13-01`, free text).
 *
 * The check runs in UTC because UTC has no daylight saving gap, so only the
 * calendar decides; `setUTCFullYear` keeps years below 100, which `Date.UTC`
 * would map to 19xx.
 */
function parseDayFields(value: string | null | undefined): DayFields | null {
  const match: RegExpExecArray | null = DATE_ONLY_PATTERN.exec(value ?? "");
  if (!match) return null;
  const fields: DayFields = {
    year: Number(match[1]),
    month: Number(match[2]),
    day: Number(match[3]),
  };
  const probe: Date = new Date(0);
  probe.setUTCFullYear(fields.year, fields.month - 1, fields.day);
  // An impossible day rolls over into the next month, so a round trip that
  // changes any field means the value was not a day.
  if (
    probe.getUTCFullYear() !== fields.year ||
    probe.getUTCMonth() !== fields.month - 1 ||
    probe.getUTCDate() !== fields.day
  ) {
    return null;
  }
  return fields;
}

/** Pure calendar arithmetic, in UTC so that no daylight saving change skews it. */
function shiftDayFields(fields: DayFields, days: number): DayFields {
  const shifted: Date = new Date(0);
  shifted.setUTCFullYear(fields.year, fields.month - 1, fields.day + days);
  return {
    year: shifted.getUTCFullYear(),
    month: shifted.getUTCMonth() + 1,
    day: shifted.getUTCDate(),
  };
}

/**
 * The local moment of a time of day on a calendar day. On a day whose local
 * midnight does not exist (a daylight saving change at midnight) the runtime
 * moves 00:00 forward to the first moment the day has.
 */
function localMoment(
  fields: DayFields,
  hours: number,
  minutes: number,
  seconds: number,
  ms: number,
): Date {
  const date: Date = new Date(2000, 0, 1);
  date.setFullYear(fields.year, fields.month - 1, fields.day);
  date.setHours(hours, minutes, seconds, ms);
  return date;
}

/** UTC midnight of a calendar day: the one moment that is this day in UTC everywhere. */
function utcMidnight(fields: DayFields): Date {
  const date: Date = new Date(0);
  date.setUTCFullYear(fields.year, fields.month - 1, fields.day);
  return date;
}

/**
 * The `YYYY-MM-DD` of a date on the viewer's own calendar (its local fields).
 * Returns an empty string for an invalid `Date`.
 */
export function toDateOnly(date: Date): string {
  if (Number.isNaN(date.getTime())) return "";
  return formatDayFields({
    year: date.getFullYear(),
    month: date.getMonth() + 1,
    day: date.getDate(),
  });
}

/**
 * Local midnight of a `YYYY-MM-DD` value, or null for an empty value or a day
 * that does not exist. Never `new Date("2026-11-04")`, which reads UTC midnight
 * and is the previous day for every viewer west of UTC.
 */
export function parseDateOnly(value: string | null | undefined): Date | null {
  const fields: DayFields | null = parseDayFields(value);
  return fields ? localMoment(fields, 0, 0, 0, 0) : null;
}

/** Today on the viewer's own calendar, which differs from the UTC day for part of every day. */
export function todayDateOnly(now: Date = new Date()): string {
  return toDateOnly(now);
}

/**
 * The calendar day `days` after (or before, when negative) a `YYYY-MM-DD`
 * value. Returns an empty string for a value that is not a day.
 */
export function addDaysToDateOnly(value: string, days: number): string {
  const fields: DayFields | null = parseDayFields(value);
  if (!fields) return "";
  return formatDayFields(shiftDayFields(fields, days));
}

/**
 * The UTC calendar day of `now`. The backend judges milestone health and the
 * home deadline window on the UTC day, so text shown next to that health uses
 * this day to agree with it for every viewer.
 */
export function utcTodayDateOnly(now: Date = new Date()): string {
  return formatDayFields({
    year: now.getUTCFullYear(),
    month: now.getUTCMonth() + 1,
    day: now.getUTCDate(),
  });
}

/**
 * Shows a calendar day in the locale's medium date ("Nov 4, 2026",
 * "4 nov 2026"), the same day for every viewer and on the server: it formats
 * the UTC midnight of the day in UTC, so no zone can move it.
 *
 * A value that is not `YYYY-MM-DD` is returned unchanged with a warning, so a
 * malformed field stays visible and the cause is recorded.
 */
export function formatDateOnly(value: string, locale: string): string {
  const fields: DayFields | null = parseDayFields(value);
  if (!fields) {
    console.warn("[formatDateOnly] value is not a YYYY-MM-DD day", value);
    return value;
  }
  return formatInstant(utcMidnight(fields), "date", { locale, timeZone: "UTC" }) ?? value;
}

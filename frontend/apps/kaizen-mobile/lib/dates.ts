// Every date and time the app draws, in one place.
//
// Times are always on the 24-hour clock (`HH:mm`), whatever the device's 12-hour
// setting: every pattern below is explicit, so no locale default time and no
// Hermes `hourCycle` support is involved. Month and weekday names follow the app
// language through the date-fns locale.
//
// An instant (an ISO string with `Z` or an offset) is shown in the device zone,
// except for the two business rules named below. A date-only value
// (`YYYY-MM-DD`) is read as a calendar day and never shifts.

import { differenceInCalendarDays, format, startOfWeek, type Locale } from "date-fns";
import { enUS, nl, tr } from "date-fns/locale";
import { parseInstant } from "@oba/shared/lib/format-instant";
import {
  addDaysToDateOnly,
  parseDateOnly,
  toDateOnly,
  todayDateOnly,
  utcTodayDateOnly,
} from "@oba/shared/lib/date-only";

import type { SupportedLanguage, TranslationKey } from "@/i18n";

export { addDaysToDateOnly, parseDateOnly, toDateOnly, todayDateOnly, utcTodayDateOnly };

/**
 * The team's business zone. Daily-update days, their 10:30 deadline, the sprint
 * burndown and the Home sprint day counts are Istanbul days, because the team
 * works in Istanbul and the server builds the burndown on that calendar; every
 * reader must agree on what "today" is for them.
 */
export const BUSINESS_TIME_ZONE = "Europe/Istanbul";

/** The translator `createDates` needs: the typed `t` of `useTranslation` or `translate`. */
export type DateTranslate = (key: TranslationKey, options?: Record<string, unknown>) => string;

const DATE_FNS_LOCALES: Record<SupportedLanguage, Locale> = { en: enUS, nl, tr };

/**
 * Date patterns per app language. English writes the month first ("Nov 4"),
 * Dutch and Turkish the day first ("4 nov.", "4 Kas"), as each language reads
 * a date.
 */
const PATTERNS: Record<
  SupportedLanguage,
  {
    shortDate: string;
    date: string;
    longDate: string;
    shortDateTime: string;
    dateTime: string;
  }
> = {
  en: {
    shortDate: "MMM d",
    date: "MMM d, yyyy",
    longDate: "MMMM d, yyyy",
    shortDateTime: "MMM d, HH:mm",
    dateTime: "MMM d, yyyy, HH:mm",
  },
  nl: {
    shortDate: "d MMM",
    date: "d MMM yyyy",
    longDate: "d MMMM yyyy",
    shortDateTime: "d MMM, HH:mm",
    dateTime: "d MMM yyyy, HH:mm",
  },
  tr: {
    shortDate: "d MMM",
    date: "d MMM yyyy",
    longDate: "d MMMM yyyy",
    shortDateTime: "d MMM, HH:mm",
    dateTime: "d MMM yyyy, HH:mm",
  },
};

const TIME_PATTERN = "HH:mm";
const WEEKDAY_TIME_PATTERN = "EEEE HH:mm";
const MINUTE_MS = 60_000;
const HOUR_MINUTES = 60;
const DAY_MINUTES = 24 * HOUR_MINUTES;
/** "Yesterday" and the weekday name cover the last six days; older times show the date. */
const WEEKDAY_WINDOW_DAYS = 6;

// Building an Intl formatter is expensive and the zone set is tiny.
const dayFormatters: Map<string, Intl.DateTimeFormat> = new Map();

function dayFormatter(timeZone: string): Intl.DateTimeFormat {
  const cached: Intl.DateTimeFormat | undefined = dayFormatters.get(timeZone);
  if (cached) return cached;
  // `en-CA` writes a date as YYYY-MM-DD, the server's date format.
  const formatter: Intl.DateTimeFormat = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  dayFormatters.set(timeZone, formatter);
  return formatter;
}

/**
 * The calendar day (`YYYY-MM-DD`) of an instant in a named zone, or null for a
 * missing or unparseable value.
 */
export function dayInZone(
  value: string | Date | null | undefined,
  timeZone: string
): string | null {
  const date: Date | null = parseInstant(value);
  return date ? dayFormatter(timeZone).format(date) : null;
}

/** Today in the business zone (see {@link BUSINESS_TIME_ZONE}). */
export function businessToday(now: Date = new Date()): string {
  return dayFormatter(BUSINESS_TIME_ZONE).format(now);
}

/**
 * Whole calendar days from `from` to `to`, both `YYYY-MM-DD`; null when either
 * is not a day. Counted on the calendar, so no zone or summer time shifts it.
 */
export function daysBetween(from: string, to: string): number | null {
  const start: Date | null = parseDateOnly(from);
  const end: Date | null = parseDateOnly(to);
  if (!start || !end) return null;
  return differenceInCalendarDays(end, start);
}

/** Monday to Sunday of the viewer's current week, as `YYYY-MM-DD` days. */
export function currentWeekRange(now: Date = new Date()): { start: string; end: string } {
  const start: string = toDateOnly(startOfWeek(now, { weekStartsOn: 1 }));
  return { start, end: addDaysToDateOnly(start, 6) };
}

export interface DateFormatter {
  /** `HH:mm` in the device zone. */
  time(value: string | Date): string | null;
  /**
   * A message or activity time: `HH:mm` today, "Yesterday 10:12", the weekday
   * within six days, then "Nov 4, 09:52" (with the year in another year).
   */
  timestamp(value: string | Date): string | null;
  /** A date-only value in lists and cards: "Oct 28" (with the year in another year). */
  shortDate(value: string): string | null;
  /** A date-only value in pickers and filters: "Nov 6, 2026". */
  date(value: string): string | null;
  /** A date-only value spelled out: "November 4, 2026". */
  longDate(value: string): string | null;
  /** Two date-only values joined with an en dash: "Oct 20 – Oct 31". */
  range(start: string, end: string): string | null;
  /** The day of an instant in a zone, in pickers and filters: "Nov 6, 2026". */
  instantDate(value: string | Date, timeZone?: string): string | null;
  /** The day of an instant in a zone, in lists: "Oct 28". */
  instantShortDate(value: string | Date, timeZone?: string): string | null;
  /** How long ago: "Just now", "2 min ago", "2 h ago", "3 d ago". */
  relativePast(value: string | Date): string | null;
  /** A compact age: "2 min", "2 h", "3 d". */
  age(value: string | Date): string | null;
  /** An open duration: "42 min", "2 h 5 min", "3 d". */
  openDuration(value: string | Date): string | null;
  /** A sprint end counter: "4 days left", "Ended 2 days ago". */
  daysLeft(days: number): string;
  /** A due counter: "Due today", "in 12 days", "2 days overdue". */
  dueIn(days: number): string;
  /** A UTC rollover in local time with UTC in parentheses: "Nov 10, 03:00 (00:00 UTC)". */
  utcRollover(value: string | Date): string | null;
}

export interface DateFormatterOptions {
  language: SupportedLanguage;
  t: DateTranslate;
  /** The moment relative values count from; defaults to the time of each call. */
  now?: () => Date;
}

function warnUnparseable(kind: string, value: unknown): null {
  console.warn(`[dates] ${kind} is not parseable`, value);
  return null;
}

/**
 * Builds the date formatter for one app language. Screens use it through
 * `useDates()`; tests build it with a fixed `now`.
 */
export function createDates({
  language,
  t,
  now = () => new Date(),
}: DateFormatterOptions): DateFormatter {
  const locale: Locale = DATE_FNS_LOCALES[language] ?? enUS;
  const patterns = PATTERNS[language] ?? PATTERNS.en;
  const fmt = (date: Date, pattern: string): string => format(date, pattern, { locale });

  const instant = (value: string | Date): Date | null =>
    parseInstant(value) ?? warnUnparseable("instant", value);
  const day = (value: string): Date | null => parseDateOnly(value) ?? warnUnparseable("day", value);

  const shortDateOf = (date: Date, reference: Date): string =>
    fmt(date, date.getFullYear() === reference.getFullYear() ? patterns.shortDate : patterns.date);

  const minutesSince = (date: Date): number =>
    Math.max(0, Math.floor((now().getTime() - date.getTime()) / MINUTE_MS));

  const compact = (minutes: number): string => {
    if (minutes < HOUR_MINUTES) return t("dateMinutes", { count: minutes });
    if (minutes < DAY_MINUTES) return t("dateHours", { count: Math.floor(minutes / HOUR_MINUTES) });
    return t("dateDays", { count: Math.floor(minutes / DAY_MINUTES) });
  };

  return {
    time(value) {
      const date: Date | null = instant(value);
      return date ? fmt(date, TIME_PATTERN) : null;
    },

    timestamp(value) {
      const date: Date | null = instant(value);
      if (!date) return null;
      const reference: Date = now();
      const daysAgo: number = differenceInCalendarDays(reference, date);
      if (daysAgo <= 0) return fmt(date, TIME_PATTERN);
      if (daysAgo === 1) return t("dateYesterdayAt", { time: fmt(date, TIME_PATTERN) });
      if (daysAgo <= WEEKDAY_WINDOW_DAYS) return fmt(date, WEEKDAY_TIME_PATTERN);
      return fmt(
        date,
        date.getFullYear() === reference.getFullYear() ? patterns.shortDateTime : patterns.dateTime
      );
    },

    shortDate(value) {
      const date: Date | null = day(value);
      return date ? shortDateOf(date, now()) : null;
    },

    date(value) {
      const date: Date | null = day(value);
      return date ? fmt(date, patterns.date) : null;
    },

    longDate(value) {
      const date: Date | null = day(value);
      return date ? fmt(date, patterns.longDate) : null;
    },

    range(start, end) {
      const first: Date | null = day(start);
      const last: Date | null = day(end);
      if (!first || !last) return null;
      const reference: Date = now();
      return t("dateRange", {
        start: shortDateOf(first, reference),
        end: shortDateOf(last, reference),
      });
    },

    instantDate(value, timeZone) {
      const dayValue: string | null = instant(value)
        ? dayInZone(value, zoneOrDevice(timeZone))
        : null;
      return dayValue ? fmt(parseDateOnly(dayValue) as Date, patterns.date) : null;
    },

    instantShortDate(value, timeZone) {
      const dayValue: string | null = instant(value)
        ? dayInZone(value, zoneOrDevice(timeZone))
        : null;
      return dayValue ? shortDateOf(parseDateOnly(dayValue) as Date, now()) : null;
    },

    relativePast(value) {
      const date: Date | null = instant(value);
      if (!date) return null;
      const minutes: number = minutesSince(date);
      if (minutes < 1) return t("dateJustNow");
      if (minutes < HOUR_MINUTES) return t("dateMinutesAgo", { count: minutes });
      if (minutes < DAY_MINUTES)
        return t("dateHoursAgo", { count: Math.floor(minutes / HOUR_MINUTES) });
      return t("dateDaysAgo", { count: Math.floor(minutes / DAY_MINUTES) });
    },

    age(value) {
      const date: Date | null = instant(value);
      return date ? compact(minutesSince(date)) : null;
    },

    openDuration(value) {
      const date: Date | null = instant(value);
      if (!date) return null;
      const minutes: number = minutesSince(date);
      if (minutes < HOUR_MINUTES || minutes >= DAY_MINUTES) return compact(minutes);
      const hours: number = Math.floor(minutes / HOUR_MINUTES);
      const rest: number = minutes % HOUR_MINUTES;
      return rest === 0
        ? t("dateHours", { count: hours })
        : t("dateHoursMinutes", { hours, minutes: rest });
    },

    daysLeft(days) {
      return days >= 0
        ? t("dateDaysLeft", { count: days })
        : t("dateEndedDaysAgo", { count: -days });
    },

    dueIn(days) {
      if (days === 0) return t("dateDueToday");
      return days > 0 ? t("dateInDays", { count: days }) : t("dateDaysOverdue", { count: -days });
    },

    utcRollover(value) {
      const date: Date | null = instant(value);
      if (!date) return null;
      const utc: string = `${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
      return t("dateUtcRollover", { local: fmt(date, patterns.shortDateTime), utc });
    },
  };
}

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

/** The named zone, or the device zone that `Intl` resolves when none is given. */
function zoneOrDevice(timeZone: string | undefined): string {
  return timeZone ?? Intl.DateTimeFormat().resolvedOptions().timeZone;
}

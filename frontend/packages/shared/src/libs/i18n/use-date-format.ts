"use client";

import { useLocale, useTimeZone } from "next-intl";
import { useCallback, useMemo, useSyncExternalStore } from "react";

import { addDaysToDateOnly } from "../../lib/date-only";
import { formatInstant, resolveFormatLocale, type InstantStyle } from "../../lib/format-instant";

export type DateOnlyStyle = Extract<InstantStyle, "date" | "dateLong">;
export type DateTimeStyle = Extract<InstantStyle, "dateTime" | "dateTimeLong">;

export interface UseDateFormat {
  /** An instant's day without time of day; `style` defaults to `"date"` ("Nov 3, 2026"). */
  formatDate: (value: string | Date, style?: DateOnlyStyle) => string;
  /** An instant with time of day on the 24 hour clock; `style` defaults to `"dateTime"` ("Nov 3, 2026, 13:46"). */
  formatDateTime: (value: string | Date, style?: DateTimeStyle) => string;
  /** The viewer's calendar day of an instant, `YYYY-MM-DD`, for grouping a feed by day. */
  dayKey: (value: string | Date) => string;
  /** Whether a {@link UseDateFormat.dayKey} value is the viewer's today, yesterday or an older day (null). */
  relativeDay: (key: string) => "today" | "yesterday" | null;
  /** An instant's day with the weekday spelled out ("Tuesday, November 3, 2026"), for a day separator. */
  formatDay: (value: string | Date) => string;
  /** An instant's time of day on the 24 hour clock ("09:05", "21:05"), for a message in a feed. */
  formatTime: (value: string | Date) => string;
}

/**
 * The clock of every time of day the Oba apps show. The product shows a 24 hour
 * clock in every locale, so an English viewer reads "13:05", never "1:05 PM";
 * only the date order and the month names follow the locale.
 */
export const HOUR_CYCLE: "h23" = "h23";

// The day key is assembled from the year, month and day parts of this locale,
// which uses Latin digits; the formatted string itself is not used, because
// its order and separators differ between browsers and versions.
const DAY_KEY_LOCALE: string = "en-CA";

// A feed lists many messages per render, so the formats are built once per
// locale and zone instead of once per message.
function buildFeedFormats(
  locale: string,
  timeZone: string | undefined,
): { dayKey: Intl.DateTimeFormat; day: Intl.DateTimeFormat; time: Intl.DateTimeFormat } {
  return {
    dayKey: new Intl.DateTimeFormat(DAY_KEY_LOCALE, {
      timeZone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }),
    day: new Intl.DateTimeFormat(locale, {
      timeZone,
      weekday: "long",
      day: "numeric",
      month: "long",
      year: "numeric",
    }),
    time: new Intl.DateTimeFormat(locale, {
      timeZone,
      hour: "2-digit",
      minute: "2-digit",
    }),
  };
}

// A value that is not an instant is shown raw with a warning, as `formatInstant`
// does, so a malformed field stays visible and its cause is recorded.
function parseFeedValue(value: string | Date): Date | null {
  const date: Date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    console.warn("[useDateFormat] value is not a parseable instant", value);
    return null;
  }
  return date;
}

function formatFeedValue(formatter: Intl.DateTimeFormat, value: string | Date): string {
  const date: Date | null = parseFeedValue(value);
  return date === null ? String(value) : formatter.format(date);
}

// `YYYY-MM-DD` of the instant's day in the formatter's zone, sortable and
// comparable with the calendar-day helpers.
function formatDayKey(formatter: Intl.DateTimeFormat, value: string | Date): string {
  const date: Date | null = parseFeedValue(value);
  if (date === null) return String(value);
  const parts: Intl.DateTimeFormatPart[] = formatter.formatToParts(date);
  const part = (type: Intl.DateTimeFormatPartTypes, length: number): string =>
    (parts.find((candidate) => candidate.type === type)?.value ?? "").padStart(length, "0");
  return `${part("year", 4)}-${part("month", 2)}-${part("day", 2)}`;
}

// The day before today, or null when today's key is not a calendar day. The
// hook must not throw there, because every component that shows a date uses
// it; the feed then labels yesterday's messages with their full day instead of
// "yesterday", and the warning records the cause.
function previousDayKey(todayKey: string): string | null {
  const previous: string = addDaysToDateOnly(todayKey, -1);
  if (previous === "") {
    console.warn("[useDateFormat] today's day key is not a YYYY-MM-DD date", todayKey);
    return null;
  }
  return previous;
}

// The hour cycle travels in the locale tag (`en-GB-u-hc-h23`), so
// `formatInstant` and the feed formats apply it without an option of their own.
// A tag that `Intl.Locale` rejects keeps the locale's own clock, because a
// date must stay on screen; the warning records the cause.
function withHourCycle(locale: string): string {
  try {
    return new Intl.Locale(locale, { hourCycle: HOUR_CYCLE }).toString();
  } catch (error) {
    console.warn("[useDateFormat] cannot set the 24 hour clock on the locale", locale, error);
    return locale;
  }
}

function subscribeToLanguages(onChange: () => void): () => void {
  window.addEventListener("languagechange", onChange);
  return () => window.removeEventListener("languagechange", onChange);
}

// Joined into one string because `useSyncExternalStore` compares snapshots by
// identity, and `navigator.languages` is not guaranteed to be the same array on
// every read.
function getLanguagesSnapshot(): string {
  const languages: readonly string[] =
    navigator.languages && navigator.languages.length > 0
      ? navigator.languages
      : [navigator.language];
  return languages.filter(Boolean).join(",");
}

// The server has no browser languages; an empty list makes the server and the
// hydrating client both format in the bare app locale.
function getServerLanguagesSnapshot(): string {
  return "";
}

/**
 * The web entry point for showing an API instant: formats in the viewer's
 * browser zone and in the app language with the browser's region for that
 * language (app `en` with browser `en-GB` gives "3 Nov 2026, 11:46"), with
 * every time of day on the {@link HOUR_CYCLE} clock.
 *
 * The zone comes from the surrounding next-intl provider, which the shared
 * `NextIntlClientProvider` sets to UTC on the server and to the browser zone
 * after hydration, so a feature needs no setup of its own. An unparseable value
 * is returned as the raw string, so a malformed field stays visible instead of
 * disappearing.
 *
 * Not for a calendar day (`YYYY-MM-DD`): use `formatDateOnly` from
 * `lib/date-only`, which never shifts the day.
 */
export function useDateFormat(): UseDateFormat {
  const appLocale: string = useLocale();
  const timeZone: string | undefined = useTimeZone();
  const languages: string = useSyncExternalStore(
    subscribeToLanguages,
    getLanguagesSnapshot,
    getServerLanguagesSnapshot,
  );

  const locale: string = useMemo(
    () => withHourCycle(resolveFormatLocale(appLocale, languages ? languages.split(",") : [])),
    [appLocale, languages],
  );

  const format = useCallback(
    (value: string | Date, style: InstantStyle): string =>
      formatInstant(value, style, { locale, timeZone }) ?? String(value),
    [locale, timeZone],
  );

  return useMemo(() => {
    const feedFormats = buildFeedFormats(locale, timeZone);
    const dayKey = (value: string | Date): string => formatDayKey(feedFormats.dayKey, value);
    // Today and yesterday are fixed when the zone or locale changes, as the
    // feed's day separators are; yesterday is counted on the calendar day, so a
    // daylight saving change cannot skip or repeat a day.
    const todayKey: string = dayKey(new Date());
    const yesterdayKey: string | null = previousDayKey(todayKey);

    return {
      formatDate: (value: string | Date, style: DateOnlyStyle = "date"): string =>
        format(value, style),
      formatDateTime: (value: string | Date, style: DateTimeStyle = "dateTime"): string =>
        format(value, style),
      dayKey,
      relativeDay: (key: string): "today" | "yesterday" | null =>
        key === todayKey ? "today" : key === yesterdayKey ? "yesterday" : null,
      formatDay: (value: string | Date): string => formatFeedValue(feedFormats.day, value),
      formatTime: (value: string | Date): string => formatFeedValue(feedFormats.time, value),
    };
  }, [format, locale, timeZone]);
}

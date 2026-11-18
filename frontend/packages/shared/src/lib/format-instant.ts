// Display formatting of API instants, shared by the Oba web apps.
//
// ZERO IMPORTS BY DESIGN: no `next`, `next-intl`, React, date library or DOM
// global. The hook in `libs/i18n/use-date-format.ts` feeds the locale and the
// zone, and this file only turns an instant into text, so a plain function (a
// column factory, a label builder) and a server module can call it too.

/**
 * The display shapes an instant can take. `date` is a medium date
 * ("Nov 3, 2026"), `dateLong` spells the month out ("November 3, 2026"), and the
 * `dateTime` variants add the time of day in the locale's 12 or 24 hour clock.
 */
export type InstantStyle = "date" | "dateLong" | "dateTime" | "dateTimeLong";

export interface InstantFormatOptions {
  /** A BCP 47 tag, normally the result of {@link resolveFormatLocale}. */
  locale: string;
  /**
   * An IANA zone such as `Europe/Istanbul`. Undefined means the runtime's own
   * zone, which in a browser is the viewer's zone.
   */
  timeZone?: string;
}

const STYLE_OPTIONS: Record<InstantStyle, Intl.DateTimeFormatOptions> = {
  date: { year: "numeric", month: "short", day: "numeric" },
  dateLong: { year: "numeric", month: "long", day: "numeric" },
  dateTime: { year: "numeric", month: "short", day: "numeric", hour: "numeric", minute: "2-digit" },
  dateTimeLong: {
    year: "numeric",
    month: "long",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  },
};

// Building an `Intl.DateTimeFormat` is far more expensive than calling it, and a
// table renders the same style hundreds of times. The key space is small (a few
// locales, one zone per viewer, four styles), so the cache needs no eviction.
const formatterCache: Map<string, Intl.DateTimeFormat> = new Map();

function getFormatter(style: InstantStyle, options: InstantFormatOptions): Intl.DateTimeFormat {
  const key: string = `${options.locale}|${options.timeZone ?? ""}|${style}`;
  const cached: Intl.DateTimeFormat | undefined = formatterCache.get(key);
  if (cached) return cached;

  const formatter: Intl.DateTimeFormat = new Intl.DateTimeFormat(options.locale, {
    ...STYLE_OPTIONS[style],
    timeZone: options.timeZone,
  });
  formatterCache.set(key, formatter);
  return formatter;
}

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

// Built from the local getters on purpose: when Intl cannot format, the viewer
// still sees their own wall-clock time, never the UTC slice of the ISO string,
// which reads as a correct local time while being hours off.
function formatLocalFallback(date: Date, style: InstantStyle): string {
  const day: string = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  if (style === "date" || style === "dateLong") return day;
  return `${day} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/**
 * Reads an API instant (an ISO string with `Z` or an offset) or a `Date` as a
 * `Date`, or null for a null, undefined, empty or unparseable value.
 *
 * It does not log: a caller that treats an unparseable value as a defect logs
 * it, as `formatInstant` does, while one that probes a value stays quiet.
 */
export function parseInstant(value: string | Date | null | undefined): Date | null {
  if (value === null || value === undefined || value === "") return null;
  const date: Date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

/**
 * Formats an instant for display in the given locale and zone.
 *
 * Returns null for a null, undefined or unparseable value, so each caller keeps
 * its own placeholder; an unparseable value also logs a warning, because it
 * means the wire carried something that is not an instant.
 */
export function formatInstant(
  value: string | Date | null | undefined,
  style: InstantStyle,
  options: InstantFormatOptions,
): string | null {
  if (value === null || value === undefined) return null;

  const date: Date | null = parseInstant(value);
  if (!date) {
    console.warn("[formatInstant] value is not a parseable instant", value);
    return null;
  }

  try {
    return getFormatter(style, options).format(date);
  } catch (error) {
    // An unknown zone or a locale the runtime's Intl data lacks must not blank a
    // date out of the screen: the warning records the cause and the viewer gets
    // the same instant as a plain local timestamp.
    console.warn("[formatInstant] Intl formatting failed; using the local fallback", error);
    return formatLocalFallback(date, style);
  }
}

function languageOf(tag: string): string {
  return (tag.split("-")[0] ?? "").toLowerCase();
}

/**
 * Picks the locale that dates are formatted in: the app language, refined by
 * the first browser tag of that same language.
 *
 * Month names must match the UI language, so a tag of another language never
 * wins; the region of a matching tag decides the rest (day order, 12 or 24 hour
 * clock). `resolveFormatLocale("en", ["en-GB", "nl-NL"])` returns `en-GB`; with
 * no matching or supported tag the app locale is returned unchanged.
 */
export function resolveFormatLocale(appLocale: string, preferredTags: readonly string[]): string {
  const appLanguage: string = languageOf(appLocale);

  for (const tag of preferredTags) {
    if (languageOf(tag) !== appLanguage) continue;
    try {
      if (Intl.DateTimeFormat.supportedLocalesOf([tag]).length > 0) return tag;
    } catch {
      // eslint-disable-next-line no-empty -- reason: a malformed tag in the browser language list is not something the viewer can act on; skipping it falls through to the next tag or the app locale, which is the intended result.
    }
  }

  return appLocale;
}

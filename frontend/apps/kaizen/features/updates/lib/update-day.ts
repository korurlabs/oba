/**
 * The days of the daily updates channel. A day is a `YYYY-MM-DD` date in
 * `UPDATE_DEADLINE_TIME_ZONE`, the zone of the posting deadline, so every
 * reader sees the same messages under the same day wherever they are.
 *
 * Not marked "use client": the daily updates page resolves the day on the
 * server to prefetch the channel under the key the client builds, and the
 * chat query keys read it there too. Inside a "use client" module these
 * functions would become client-reference proxies on the server.
 */
import { addDaysToDateOnly, parseDateOnly } from "@oba/shared/lib/date-only";
import { UPDATE_DEADLINE_TIME_ZONE } from "../schemas";

/** The URL keys of the updates channel's filters; a redirect to another channel drops them. */
export const UPDATE_FILTER_KEYS = ["day", "person"] as const;

// `en-CA` formats a date as YYYY-MM-DD, the server's date format.
const updateDayFormat = new Intl.DateTimeFormat("en-CA", {
  timeZone: UPDATE_DEADLINE_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

/**
 * Today in the deadline's zone. The server refuses a later day for the
 * missing report, so pickers stop here even for a reader whose own calendar
 * is a day ahead.
 */
export function updateToday(): string {
  return updateDayFormat.format(new Date());
}

/**
 * The day before `day`, counted on the calendar day, so no zone shift or summer
 * time can skip a day. An empty string, with a warning, when `day` is not a
 * `YYYY-MM-DD` day: no preset then matches it, and the URL value falls back to
 * today through `resolveUpdateDay`.
 */
export function updatePreviousDay(day: string): string {
  const previous = addDaysToDateOnly(day, -1);
  if (previous === "") console.warn("[updatePreviousDay] day is not a YYYY-MM-DD day", day);
  return previous;
}

/** The day of an instant (an ISO timestamp from the server) in the deadline's zone. */
export function updateDayOf(instant: string): string {
  return updateDayFormat.format(new Date(instant));
}

/**
 * The day a URL value names: a real `YYYY-MM-DD` date not after today, else
 * today. A repeated parameter, a malformed or impossible date and a future
 * day all fall back to today, so a bad link shows the default channel.
 */
export function resolveUpdateDay(raw: string | string[] | null | undefined): string {
  const today = updateToday();
  if (typeof raw !== "string" || parseDateOnly(raw) === null) return today;
  // Both are YYYY-MM-DD, so string order is date order.
  return raw > today ? today : raw;
}

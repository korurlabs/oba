/**
 * Sprint date helpers.
 *
 * Shared by the Sprint tab (via `useSprintPage`) and the All tab toolbar so
 * both render the same date string and the same "n days left" number for the
 * same sprint.
 */

const MS_PER_DAY = 1000 * 60 * 60 * 24;

/**
 * Formats a sprint start/end date in the active locale.
 *
 * Module-private: the only caller is formatSprintDateRange below, which is
 * what every consumer renders. Exporting it again would invite a second,
 * subtly different date string for the same sprint.
 */
function formatSprintDate(
  locale: string,
  date: Date | string | null | undefined,
): string {
  if (!date) return "";
  return new Intl.DateTimeFormat(locale, {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date(date));
}

/**
 * Whole days left until `endDate`, never negative.
 *
 * `now` is passed in rather than read from the clock so the caller can hold a
 * single value for the lifetime of a render (reading `Date.now()` in a render
 * body is impure and can straddle a day boundary between the server render
 * and hydration).
 */
export function getDaysRemaining(
  endDate: Date | string | null | undefined,
  now: number,
): number {
  if (!endDate) return 0;
  return Math.max(0, Math.ceil((new Date(endDate).getTime() - now) / MS_PER_DAY));
}

/**
 * Formats a sprint's start-to-end range as one string in the active locale.
 *
 * Uses Intl.DateTimeFormat.formatRange, which collapses the parts the two
 * endpoints share -- "Sep 11 - 25, 2026" rather than "Sep 11, 2026 - Sep 25,
 * 2026" -- so the range fits on the secondary line of a sprint dropdown row
 * without the caller doing string surgery on two formatted dates.
 *
 * A range with only one endpoint falls back to that single date; a range with
 * neither returns the empty string, and the caller renders nothing.
 */
export function formatSprintDateRange(
  locale: string,
  startDate: Date | string | null | undefined,
  endDate: Date | string | null | undefined,
): string {
  if (!startDate) return formatSprintDate(locale, endDate);
  if (!endDate) return formatSprintDate(locale, startDate);
  return new Intl.DateTimeFormat(locale, {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).formatRange(new Date(startDate), new Date(endDate));
}

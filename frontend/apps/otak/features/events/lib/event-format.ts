import { formatDateOnly } from "@oba/shared/lib/date-only";

/**
 * An event's days as the viewer reads them: the start alone, or "start – end"
 * when it lasts more than one day. Calendar days, so no time zone moves them.
 */
export function formatEventDates(startDate: string, endDate: string | null, locale: string) {
  const start = formatDateOnly(startDate, locale);
  if (!endDate || endDate === startDate) return start;
  return `${start} – ${formatDateOnly(endDate, locale)}`;
}

/**
 * Where an event takes place, "city, country", with only the parts that are
 * set; null when neither is. `countryName` turns an ISO code into its name.
 */
export function formatEventPlace(
  city: string | null,
  country: string | null,
  countryName: (code: string) => string
): string | null {
  const parts = [city, country ? countryName(country) : null].filter(
    (part): part is string => !!part
  );
  return parts.length > 0 ? parts.join(", ") : null;
}

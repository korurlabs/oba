import { formatDateOnly } from "@oba/shared/lib/date-only";

/**
 * The campaign's days as one text: "start – end", "from start", "until end",
 * or null when it has neither. Calendar days, so every viewer sees the same.
 */
export function formatCampaignDates(
  startDate: string | null,
  endDate: string | null,
  locale: string,
  t: (key: string, values: Record<string, string>) => string
): string | null {
  if (startDate && endDate) {
    if (startDate === endDate) return formatDateOnly(startDate, locale);
    return `${formatDateOnly(startDate, locale)} – ${formatDateOnly(endDate, locale)}`;
  }
  if (startDate) return t("datesFrom", { date: formatDateOnly(startDate, locale) });
  if (endDate) return t("datesUntil", { date: formatDateOnly(endDate, locale) });
  return null;
}

/** The largest count a badge spells out; above it the badge reads "99+". */
export const MAX_BADGE_COUNT = 99;

/**
 * The text of a count badge, or null when there is nothing to show. A badge
 * never shows 0 or a negative count.
 */
export function formatBadgeCount(count: number | null | undefined): string | null {
  if (count == null || !Number.isFinite(count) || count <= 0) return null;
  if (count > MAX_BADGE_COUNT) return `${MAX_BADGE_COUNT}+`;
  return String(Math.floor(count));
}

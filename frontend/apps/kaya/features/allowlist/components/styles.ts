export const CARD_GRID = "grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3";

/** An id for a heading, from its text. */
export function headingId(prefix: string, text: string): string {
  return `${prefix}-${text.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`;
}

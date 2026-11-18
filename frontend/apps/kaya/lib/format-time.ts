/** "just now", "5 min ago", "3 h ago" or "2 d ago"; "Never" when there is no time. */
export function ageText(iso: string | null | undefined, now: number): string {
  if (iso === null || iso === undefined) return "Never";
  const then = Date.parse(iso);
  if (Number.isNaN(then)) return iso;
  const seconds = Math.max(0, Math.round((now - then) / 1000));
  if (seconds < 60) return "just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  return `${Math.floor(hours / 24)} d ago`;
}

/** A date and time in the viewer's locale and time zone, as "23 Oct 2026, 14:30". */
export function dateTimeText(iso: string | null | undefined, timeZone?: string): string {
  if (iso === null || iso === undefined) return "Never";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone,
  }).format(date);
}

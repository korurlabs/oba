import { currentWeekRange } from "@/lib/dates";

/** The statuses of a ticket that is still open. */
export const OPEN_TICKET_STATUSES = [
  "TODO",
  "IN_PROGRESS",
  "WAITING_FOR_THIRD_PARTY",
  "IN_REVIEW",
] as const;

/** Whose tickets My Tickets lists: assigned to the user, or created by the user. */
export type MyTicketsMode = "assigned" | "reported";

/** The chips of My Tickets. `boardId` null means every board. */
export interface MyTicketsFilters {
  boardId: string | null;
  dueThisWeek: boolean;
  incidentsOnly: boolean;
}

/**
 * The query string of My Tickets: open tickets only, since the list groups by
 * the open statuses. Lists go comma-joined, as Spring binds them. "Due this
 * week" is Monday to Sunday of the viewer's week; the due-date filter compares
 * calendar days.
 */
export function myTicketsParams(
  userId: string,
  mode: MyTicketsMode,
  filters: MyTicketsFilters,
  now: Date = new Date()
): Record<string, string> {
  const params: Record<string, string> = {
    statuses: OPEN_TICKET_STATUSES.join(","),
    sortBy: "createdAt",
    sortDirection: "desc",
  };
  if (mode === "assigned") params.assigneeIds = userId;
  else params.createdByIds = userId;
  if (filters.boardId) params.boardIds = filters.boardId;
  if (filters.incidentsOnly) params.types = "INCIDENT";
  if (filters.dueThisWeek) {
    const week = currentWeekRange(now);
    params.dueDateStart = week.start;
    params.dueDateEnd = week.end;
  }
  return params;
}

/** The query string of the Incidents tab badge: open P1 and P2 incidents. */
export const URGENT_INCIDENT_PARAMS = {
  types: "INCIDENT",
  priorities: "P1,P2",
  statuses: OPEN_TICKET_STATUSES.join(","),
} as const;

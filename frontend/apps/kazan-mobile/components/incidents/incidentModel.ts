import type { TicketCardTiming } from "@/components/kazan/ticketCardModel";
import { OPEN_TICKET_STATUSES } from "@/hooks/queries/ticketParams";
import type { DateFormatter, DateTranslate } from "@/lib/dates";
import type { TicketListItem } from "@/lib/schemas/tickets";

/*
 * The rules of the Incidents tab, without React, so they are testable: which
 * incidents each segment lists, how they group, and which one the banner names.
 */

/** The segments of the Incidents tab, in their order on screen. */
export const INCIDENT_SEGMENTS = ["open", "mine", "resolved"] as const;
export type IncidentSegment = (typeof INCIDENT_SEGMENTS)[number];

/**
 * What the API is asked for. Open and Mine share one read, because Mine is
 * the open incidents assigned to the user and both must agree.
 */
export type IncidentScope = "open" | "resolved";

export function scopeOfSegment(segment: IncidentSegment): IncidentScope {
  return segment === "resolved" ? "resolved" : "open";
}

/**
 * The query string of `GET /kazan/tickets` for a scope. Lists go
 * comma-joined, as Spring binds them. Open incidents come newest first;
 * resolved ones by their last change, the closest the list has to the time
 * they were resolved.
 */
export function incidentListParams(
  scope: IncidentScope,
  boardId: string | null = null
): Record<string, string> {
  const params: Record<string, string> =
    scope === "open"
      ? {
          types: "INCIDENT",
          statuses: OPEN_TICKET_STATUSES.join(","),
          sortBy: "createdAt",
          sortDirection: "desc",
        }
      : { types: "INCIDENT", statuses: "DONE", sortBy: "updatedAt", sortDirection: "desc" };
  if (boardId) params.boardIds = boardId;
  return params;
}

/** The open incidents with `userId` among their assignees. */
export function assignedTo(
  incidents: readonly TicketListItem[],
  userId: string | null | undefined
): TicketListItem[] {
  if (!userId) return [];
  return incidents.filter((incident) =>
    (incident.assignees ?? []).some((person) => person.userId === userId)
  );
}

/** The group key of an incident without a priority. */
export const NO_PRIORITY = "NONE";

/** The incident priorities, most severe first. */
const PRIORITY_ORDER: readonly string[] = ["P1", "P2", "P3"];

export interface IncidentGroup {
  /** `P1` to `P3`, another priority the ticket carries, or {@link NO_PRIORITY}. */
  priority: string;
  incidents: TicketListItem[];
}

/**
 * Groups incidents by priority: P1, P2 and P3 first, then any other priority
 * an incident was given before its type changed, then those without one.
 * Each group keeps the order of the list it came from.
 */
export function groupIncidentsByPriority(incidents: readonly TicketListItem[]): IncidentGroup[] {
  const groups: Map<string, TicketListItem[]> = new Map();
  for (const incident of incidents) {
    const key: string = incident.priority || NO_PRIORITY;
    const group: TicketListItem[] | undefined = groups.get(key);
    if (group) group.push(incident);
    else groups.set(key, [incident]);
  }
  const rank = (priority: string): number => {
    const index: number = PRIORITY_ORDER.indexOf(priority);
    if (index >= 0) return index;
    return priority === NO_PRIORITY ? PRIORITY_ORDER.length + 1 : PRIORITY_ORDER.length;
  };
  return [...groups.entries()]
    .map(([priority, list]) => ({ priority, incidents: list }))
    .sort((a, b) => rank(a.priority) - rank(b.priority));
}

/** The open P1 incidents, which the banner announces. */
export function openP1Incidents(openIncidents: readonly TicketListItem[]): TicketListItem[] {
  return openIncidents.filter((incident) => incident.priority === "P1");
}

/**
 * The incident the banner names: the P1 that has been open longest, since it
 * has waited the most. Null when no P1 is open.
 */
export function bannerIncident(openIncidents: readonly TicketListItem[]): TicketListItem | null {
  let oldest: TicketListItem | null = null;
  for (const incident of openP1Incidents(openIncidents)) {
    if (!oldest || Date.parse(incident.createdAt) < Date.parse(oldest.createdAt)) {
      oldest = incident;
    }
  }
  return oldest;
}

/**
 * The time slot of an incident card. An open incident shows how long it has
 * been open ("open 42 min"), in the late colour only for P1, so red stays
 * rare and means every minute counts; a resolved one shows the day it was
 * opened. A clock, not a calendar, marks the open time, because a calendar
 * beside an elapsed time reads as a due date.
 */
export function incidentTiming(
  incident: Pick<TicketListItem, "createdAt" | "priority">,
  resolved: boolean,
  dates: Pick<DateFormatter, "openDuration" | "instantShortDate">,
  t: DateTranslate
): TicketCardTiming | null {
  if (resolved) {
    const openedOn: string | null = dates.instantShortDate(incident.createdAt);
    return openedOn
      ? { icon: "calendar", label: t("incidentsOpenedOn", { date: openedOn }), late: false }
      : null;
  }
  const openFor: string | null = dates.openDuration(incident.createdAt);
  return openFor
    ? {
        icon: "clock",
        label: t("incidentsOpenFor", { duration: openFor }),
        late: incident.priority === "P1",
      }
    : null;
}

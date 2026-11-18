/**
 * Rows of the sprint and backlog tables.
 *
 * The list API returns the list roots: every ticket placed in the list whose
 * parent is not placed in the same list. A sub-ticket can be planned apart
 * from its parent, so a root may be a sub-ticket whose parent is elsewhere.
 * The table shows such sub-tickets under a header row for their parent, so
 * the structure stays visible. A header row is not work in the list: it is
 * never selected, dragged or counted. The table, the caption strip and every
 * count go through this module so that they agree.
 */

import type { TicketListResponse, TicketParentResponse } from "@/features/tickets/schemas";
import { TicketStatus } from "@/features/tickets/schemas";

/**
 * A ticket of a sprint list. `subTickets` keeps every sub-ticket wherever it
 * is placed, for the progress bar and the move dialogs; `sprintSubTickets`
 * holds only those planned in the sprint shown, and is what the table nests.
 */
export type SprintListTicket = TicketListResponse & {
  sprintSubTickets?: SprintListTicket[];
};

/** A header row for a parent that is not in the list, above its sub-tickets that are. */
export type TicketGroupHeaderRow = SprintListTicket & {
  isGroupHeader: true;
  /** The parent as the sub-tickets carry it, with every sub-ticket's placement. */
  groupParent: TicketParentResponse;
};

/** A row of a ticket table: a ticket, or a header row for an absent parent. */
export type TicketListRow = TicketListResponse | TicketGroupHeaderRow;

export function isGroupHeader(row: TicketListResponse): row is TicketGroupHeaderRow {
  return (row as Partial<TicketGroupHeaderRow>).isGroupHeader === true;
}

/**
 * Builds the header row of `parent` over `children`. The fields a column or a
 * sort reads but a parent reference does not carry (priority, Mission Rank,
 * order, board, creation date) come from the first child, so the group sorts and opens
 * where its sub-tickets are; the header renders only key, title and progress.
 */
function buildGroupHeader(
  parent: TicketParentResponse,
  children: SprintListTicket[],
): TicketGroupHeaderRow {
  const anchor = children[0]!;
  return {
    id: parent.id,
    key: parent.key,
    title: parent.title,
    status: parent.status,
    type: parent.type,
    createdAt: anchor.createdAt,
    displayOrder: anchor.displayOrder,
    priority: anchor.priority,
    missionRank: anchor.missionRank,
    percentage: 0,
    board: anchor.board,
    parentTicketId: null,
    parentTicket: null,
    sprint: null,
    subTickets: children,
    sprintSubTickets: children,
    isGroupHeader: true,
    groupParent: parent,
  };
}

/**
 * Table rows for a list: each top-level ticket as it is, and for each parent
 * that is not in the list one header row, at the place of its first
 * sub-ticket, with its sub-tickets of this list nested under it.
 */
export function groupListRows(tickets: TicketListResponse[]): TicketListRow[] {
  const listedIds = new Set(tickets.map((ticket) => ticket.id));
  const childrenByParent = new Map<string, SprintListTicket[]>();
  const rows: TicketListRow[] = [];

  for (const ticket of tickets) {
    const parent = ticket.parentTicket;
    if (!parent || listedIds.has(parent.id)) {
      rows.push(ticket);
      continue;
    }
    const children = childrenByParent.get(parent.id);
    if (children) {
      children.push(ticket);
      continue;
    }
    const group = [ticket];
    childrenByParent.set(parent.id, group);
    rows.push(buildGroupHeader(parent, group));
  }

  return rows;
}

function scopeToSprint(ticket: TicketListResponse, sprintId: string): SprintListTicket {
  return {
    ...ticket,
    sprintSubTickets: (ticket.subTickets ?? [])
      .filter((subTicket) => subTicket.sprint?.id === sprintId)
      .map((subTicket) => scopeToSprint(subTicket, sprintId)),
  };
}

/**
 * The tickets of a sprint list with, at every level, `sprintSubTickets` set
 * to the sub-tickets planned in `sprintId`. The API nests every sub-ticket
 * wherever it is placed; in a sprint a sub-ticket left in another sprint or
 * the backlog is not work of this sprint and would otherwise also show in
 * that other list. `subTickets` stays complete at every level.
 */
export function keepSprintSubTickets(
  tickets: TicketListResponse[],
  sprintId: string,
): SprintListTicket[] {
  return tickets.map((ticket) => scopeToSprint(ticket, sprintId));
}

/** The tickets of `rows` that are work in the list: every row except header rows, whose sub-tickets count instead. */
export function listWorkItems(rows: TicketListRow[]): TicketListResponse[] {
  return rows.flatMap((row) => (isGroupHeader(row) ? (row.subTickets ?? []) : [row]));
}

/** Where the sub-tickets of a parent stand, over all of them. */
export interface SubTicketProgress {
  done: number;
  /** Unfinished and planned in the sprint shown. */
  inThisSprint: number;
  /** Unfinished and planned in any other sprint. */
  inOtherSprints: number;
  /** Unfinished and in the backlog. */
  inBacklog: number;
  total: number;
}

/**
 * Splits `subTickets` into done, and the unfinished ones by placement relative
 * to `currentSprintId` (none on the backlog page, where every planned
 * sub-ticket is in another sprint).
 */
export function subTicketProgress(
  subTickets: ReadonlyArray<{ status: string; sprint?: { id: string } | null }>,
  currentSprintId?: string | null,
): SubTicketProgress {
  const progress: SubTicketProgress = {
    done: 0,
    inThisSprint: 0,
    inOtherSprints: 0,
    inBacklog: 0,
    total: subTickets.length,
  };
  for (const subTicket of subTickets) {
    if (subTicket.status === TicketStatus.DONE) progress.done += 1;
    else if (!subTicket.sprint) progress.inBacklog += 1;
    else if (subTicket.sprint.id === currentSprintId) progress.inThisSprint += 1;
    else progress.inOtherSprints += 1;
  }
  return progress;
}

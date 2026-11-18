import type { TicketScope } from "@/components/boards/boardModel";
import {
  isStatusNoop,
  nextIdOf,
  ticketBoardId,
  type StatusTarget,
} from "@/components/boards/ticketMoveModel";
import type { TicketPlace } from "@/components/boards/useBoardMoves";
import type { TicketStatus } from "@/components/kaizen/ticketMetaModel";
import type { BoardTicket } from "@/lib/schemas/boards";

/*
 * Pure rules of dragging a row in a board list: which group the finger is
 * over, what a drop there does, and how far the list scrolls while a row is
 * held at its top or bottom edge.
 */

/** The height of the zone at the top and bottom of a list that scrolls it while a row is held there. */
export const LIST_EDGE_ZONE = 56;

/** How far the list scrolls on each step while a row is held in an edge zone. */
export const LIST_SCROLL_STEP = 10;

/** The pause between two scroll steps, about one frame. */
export const LIST_SCROLL_MS = 16;

/** A group of a board list that rows are dragged out of and dropped into. */
export interface ListDropGroup {
  key: string;
  /** The sprint or backlog of the group, with that scope's rows in board order. */
  place: TicketPlace;
  /** The group's rows as the list shows them, the order a drop lands in. */
  rows: readonly BoardTicket[];
  /** Set when the group holds one status of a sprint, as the Sprint list does. */
  status?: TicketStatus;
  /** False keeps the group a source only, such as a backlog sorted by priority. */
  accepts: boolean;
}

/** What a drop does: a new status, a new position in the same group, or a new sprint or backlog. */
export type ListDropAction =
  | { kind: "status"; target: StatusTarget }
  | { kind: "reorder"; beforeId: string | null }
  | { kind: "place"; scope: TicketScope; beforeId: string | null };

/** True when two scopes are the same sprint or the same backlog. */
export function sameScope(a: TicketScope, b: TicketScope): boolean {
  if ("sprintId" in a) return "sprintId" in b && a.sprintId === b.sprintId;
  return "backlogId" in b && a.backlogId === b.backlogId;
}

/**
 * What dropping `ticket` from group `from` before `beforeId` of group `to`
 * does, or null when the drop changes nothing or is not allowed. Within one
 * scope a status group takes a status move and any other group a reorder;
 * across scopes the ticket is planned into the other sprint or the backlog,
 * which a ticket of a child board cannot be, because this board's view does
 * not know that board's sprints and backlog.
 */
export function listDropAction(
  ticket: BoardTicket,
  from: ListDropGroup,
  to: ListDropGroup,
  beforeId: string | null,
  viewBoardId: string
): ListDropAction | null {
  if (!to.accepts) return null;
  if (sameScope(from.place.scope, to.place.scope)) {
    if (to.status) {
      const target: StatusTarget = { status: to.status, beforeId };
      return isStatusNoop(to.place.rows, ticket, target) ? null : { kind: "status", target };
    }
    if (beforeId === ticket.id || nextIdOf(to.rows, ticket.id) === beforeId) return null;
    return { kind: "reorder", beforeId };
  }
  if (ticketBoardId(ticket, viewBoardId) !== viewBoardId) return null;
  return { kind: "place", scope: to.place.scope, beforeId };
}

/** The top of a group in the list's content. */
export interface GroupTop {
  key: string;
  top: number;
}

/**
 * The group under a content y: the last group that starts at or above it,
 * or the first group when the finger is above them all; null without groups.
 */
export function groupAt(tops: readonly GroupTop[], y: number): string | null {
  const sorted: GroupTop[] = [...tops].sort((a, b) => a.top - b.top);
  let found: string | null = sorted[0]?.key ?? null;
  for (const group of sorted) {
    if (group.top <= y) found = group.key;
  }
  return found;
}

/** How far a list can scroll: its content, its visible height and the inset under the content. */
export interface ListScrollExtent {
  contentHeight: number;
  viewportHeight: number;
  /**
   * The space the scroll view keeps under its content, such as the inset iOS
   * adds so the last rows can scroll out from under the floating tab bar.
   */
  bottomInset: number;
}

/**
 * The scroll offset after one edge step, kept between the top and the end of
 * the content, the bottom inset included, so a held row can reach the rows a
 * user scrolls to by hand.
 */
export function edgeScrollOffset(
  offset: number,
  direction: -1 | 1,
  extent: ListScrollExtent,
  step: number = LIST_SCROLL_STEP
): number {
  const max: number = Math.max(
    extent.contentHeight + extent.bottomInset - extent.viewportHeight,
    0
  );
  return Math.min(Math.max(offset + direction * step, 0), max);
}

/**
 * The bottom inset iOS adds to a list whose box reaches into the unsafe area
 * at the screen bottom (the tab bar and the home indicator): the part of the
 * box below `safeBottom`, the window y where that area starts.
 */
export function listBottomInset(list: { y: number; height: number }, safeBottom: number): number {
  return Math.max(list.y + list.height - safeBottom, 0);
}

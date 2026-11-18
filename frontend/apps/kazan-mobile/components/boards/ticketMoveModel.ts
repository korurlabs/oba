import type { TicketScope } from "@/components/boards/boardModel";
import {
  resolveStatus,
  TICKET_STATUSES,
  type TicketStatus,
} from "@/components/kazan/ticketMetaModel";
import { requiresIncidentClose } from "@/components/ticket/incidentCloseModel";
import type { TicketMove } from "@/hooks/queries/ticketMoves";
import type { TranslationKey } from "@/i18n";
import type { BoardTicket, Sprint } from "@/lib/schemas/boards";

/*
 * Pure rules of moving tickets on a board: where a drop lands, which display
 * order the API needs to keep it there, how the cached lists look at once,
 * and what Undo sends back. The screens and the move hook share them.
 */

/** How long a card is held before it lifts. */
export const LIFT_DELAY_MS = 300;

/** How far a lifted card moves before it is a drag rather than a hold that opens the menu. */
export const DRAG_SLOP = 8;

/** The width of the zone at each side of the Kanban board that pages to the next column. */
export const EDGE_ZONE = 36;

/** The pause between two pages while a card is held in an edge zone. */
export const EDGE_PAGE_MS = 700;

/**
 * The status a swipe right moves a ticket to: Todo, In Progress, In Review,
 * Done. Waiting for 3rd party is reached by drag or menu only, and moves on
 * to In Review. Done has no next status.
 */
export function nextSwipeStatus(status: string): TicketStatus | null {
  switch (resolveStatus(status).status) {
    case "TODO":
      return "IN_PROGRESS";
    case "IN_PROGRESS":
    case "WAITING_FOR_THIRD_PARTY":
      return "IN_REVIEW";
    case "IN_REVIEW":
      return "DONE";
    case "DONE":
      return null;
  }
}

/** The label of the swipe-right action, which names the step rather than the status. */
export function swipeActionLabelKey(next: TicketStatus): TranslationKey {
  switch (next) {
    case "IN_PROGRESS":
      return "boardSwipeStart";
    case "IN_REVIEW":
      return "boardSwipeReview";
    case "DONE":
      return "boardSwipeDone";
    default:
      return resolveStatus(next).labelKey;
  }
}

/** The board a ticket lives on; a ticket of a child board is moved through that board. */
export function ticketBoardId(ticket: Pick<BoardTicket, "board">, viewBoardId: string): string {
  return ticket.board?.id ?? viewBoardId;
}

/** The tickets of one status, in board order. An unknown status counts as Todo. */
export function columnOf(tickets: readonly BoardTicket[], status: TicketStatus): BoardTicket[] {
  return tickets.filter((ticket) => resolveStatus(ticket.status).status === status);
}

/** The id of the ticket after `ticketId` in `rows`, or null when it is last or absent. */
export function nextIdOf(rows: readonly BoardTicket[], ticketId: string): string | null {
  const index: number = rows.findIndex((row) => row.id === ticketId);
  return index >= 0 ? (rows[index + 1]?.id ?? null) : null;
}

/** `rows` without `ticket`, then with it before `beforeId` (at the end when null or absent). */
export function placeBefore(
  rows: readonly BoardTicket[],
  ticket: BoardTicket,
  beforeId: string | null
): BoardTicket[] {
  const next: BoardTicket[] = rows.filter((row) => row.id !== ticket.id);
  const index: number = beforeId ? next.findIndex((row) => row.id === beforeId) : -1;
  next.splice(index >= 0 ? index : next.length, 0, ticket);
  return next;
}

/**
 * The `order` a move sends so that the API stores the ticket where it landed
 * in `landing`, the target list with the ticket in place; undefined when the
 * ticket's order already keeps it there.
 *
 * Display orders are board-wide: the API gives the ticket that order and
 * shifts the board's tickets between its old and new order by one. With the
 * ticket's order above the next row's, taking the next row's order puts it
 * just before that row; with its order below the previous row's, taking the
 * previous row's order puts it just after that row. Only rows of the ticket's
 * own board count, because a child board's orders are another sequence.
 */
export function landingOrder(
  landing: readonly BoardTicket[],
  ticketId: string,
  viewBoardId: string
): number | undefined {
  const ticket: BoardTicket | undefined = landing.find((row) => row.id === ticketId);
  if (!ticket || ticket.displayOrder == null) return undefined;
  const boardId: string = ticketBoardId(ticket, viewBoardId);
  const rows: BoardTicket[] = landing.filter(
    (row) => ticketBoardId(row, viewBoardId) === boardId && row.displayOrder != null
  );
  const index: number = rows.findIndex((row) => row.id === ticketId);
  const own: number = ticket.displayOrder;
  const next: number | null | undefined = rows[index + 1]?.displayOrder;
  if (next != null && own > next) return next;
  const previous: number | null | undefined = rows[index - 1]?.displayOrder;
  if (previous != null && own < previous) return previous;
  return undefined;
}

/** Where a status move puts a ticket: its new status and the ticket it lands before. */
export interface StatusTarget {
  status: TicketStatus;
  /** The ticket of that column it lands before; null is the end of the column. */
  beforeId: string | null;
}

/**
 * A status move as the cached list shows it at once: the ticket takes the
 * status (and the order, when one is sent) and sits before `beforeId`, or at
 * the end of the list. The Kanban columns group the list by status in list
 * order, so the end of the list is the end of the column.
 */
export function applyStatusMove(
  rows: readonly BoardTicket[],
  ticketId: string,
  target: StatusTarget,
  order: number | undefined
): BoardTicket[] {
  const ticket: BoardTicket | undefined = rows.find((row) => row.id === ticketId);
  if (!ticket) return [...rows];
  const moved: BoardTicket = {
    ...ticket,
    status: target.status,
    displayOrder: order ?? ticket.displayOrder,
  };
  return placeBefore(rows, moved, target.beforeId);
}

/**
 * A field change of one ticket as a cached list shows it at once: the row of
 * `ticketId` becomes what `change` makes of it. A list without the ticket is
 * returned as it is, so the caller can skip writing it back.
 */
export function applyTicketChange(
  rows: BoardTicket[],
  ticketId: string,
  change: (ticket: BoardTicket) => BoardTicket
): BoardTicket[] {
  if (!rows.some((row) => row.id === ticketId)) return rows;
  return rows.map((row) => (row.id === ticketId ? change(row) : row));
}

/**
 * The column a status drop lands in, with the ticket in place, for the order
 * to send: the target column without the ticket, then the ticket before
 * `beforeId` with its new status.
 */
export function statusLanding(
  rows: readonly BoardTicket[],
  ticket: BoardTicket,
  target: StatusTarget
): BoardTicket[] {
  const column: BoardTicket[] = columnOf(rows, target.status).filter((row) => row.id !== ticket.id);
  return placeBefore(column, { ...ticket, status: target.status }, target.beforeId);
}

/** True when a status drop leaves the ticket where it was. */
export function isStatusNoop(
  rows: readonly BoardTicket[],
  ticket: BoardTicket,
  target: StatusTarget
): boolean {
  if (resolveStatus(ticket.status).status !== target.status) return false;
  if (target.beforeId === ticket.id) return true;
  const column: BoardTicket[] = columnOf(rows, target.status);
  return nextIdOf(column, ticket.id) === target.beforeId;
}

/** True when a cached ticket list holds the tickets of `scope`. */
export function listHoldsScope(params: Record<string, string>, scope: TicketScope): boolean {
  return "sprintId" in scope
    ? params.sprintId === scope.sprintId
    : params.backlogId === scope.backlogId;
}

/**
 * A placement move (sprint to backlog, backlog to sprint) as one cached list
 * shows it at once: the list of the target scope gains the ticket before
 * `beforeId`, every other list loses it.
 */
export function applyPlacementMove(
  rows: readonly BoardTicket[],
  params: Record<string, string>,
  ticket: BoardTicket,
  target: TicketScope,
  beforeId: string | null,
  order: number | undefined
): BoardTicket[] {
  if (!listHoldsScope(params, target)) return rows.filter((row) => row.id !== ticket.id);
  const moved: BoardTicket = { ...ticket, displayOrder: order ?? ticket.displayOrder };
  return placeBefore(rows, moved, beforeId);
}

/**
 * The ticket as it reads in the backlog: a top-level ticket there is always
 * Todo, because the API resets it when it enters; a sub-ticket keeps its
 * status.
 */
export function inBacklog(ticket: BoardTicket): BoardTicket {
  return ticket.parentTicketId ? ticket : { ...ticket, status: "TODO" };
}

/** A point and a box in window coordinates. */
export interface Rect {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** True when the point lies in the box. */
export function contains(rect: Rect, x: number, y: number): boolean {
  return x >= rect.x && x <= rect.x + rect.width && y >= rect.y && y <= rect.y + rect.height;
}

/** A card of a drop target as it stood when the drag began: its id and vertical middle. */
export interface Slot {
  id: string;
  middle: number;
}

/**
 * The card a dragged one lands before, from the card middles of the target:
 * the first card other than the dragged one whose middle is below the finger;
 * null is the end.
 */
export function slotBefore(slots: readonly Slot[], y: number, draggedId: string): string | null {
  const sorted: Slot[] = slots
    .filter((slot) => slot.id !== draggedId)
    .sort((a, b) => a.middle - b.middle);
  return sorted.find((slot) => slot.middle > y)?.id ?? null;
}

/** -1 near the left edge, 1 near the right edge, else 0. */
export function edgeDirection(x: number, width: number, zone: number = EDGE_ZONE): -1 | 0 | 1 {
  if (x <= zone) return -1;
  if (x >= width - zone) return 1;
  return 0;
}

/** A move a screen reader offers instead of a drag. */
export type MoveChoice =
  | { kind: "status"; status: TicketStatus }
  | { kind: "place"; scope: TicketScope; label: string };

/** The accessibility action name of a move choice. */
export function moveChoiceName(choice: MoveChoice): string {
  if (choice.kind === "status") return `status:${choice.status}`;
  return "sprintId" in choice.scope ? `sprint:${choice.scope.sprintId}` : "backlog";
}

/** Every status other than the ticket's own, in board order. */
export function statusChoices(status: string): MoveChoice[] {
  const current: TicketStatus = resolveStatus(status).status;
  return TICKET_STATUSES.filter((next) => next !== current).map((next) => ({
    kind: "status",
    status: next,
  }));
}

/** The open sprints a ticket can be planned into: active first, then planned by start. */
export function openSprintTargets(
  sprints: readonly Sprint[],
  currentSprintId: string | null
): Sprint[] {
  const rank = (status: string): number => (status === "ACTIVE" ? 0 : 1);
  return sprints
    .filter((sprint) => sprint.status === "ACTIVE" || sprint.status === "PLANNED")
    .filter((sprint) => sprint.id !== currentSprintId)
    .sort(
      (a, b) =>
        rank(a.status) - rank(b.status) || (a.startDate ?? "").localeCompare(b.startDate ?? "")
    );
}

/**
 * The placements a ticket can move to: the backlog when it sits in a sprint,
 * and the other open sprints. A ticket of a child board stays on its own
 * board's sprints and backlog, which this board's view does not know.
 */
export function placementChoices(
  ticket: BoardTicket,
  viewBoardId: string,
  currentSprintId: string | null,
  backlogId: string | null,
  sprints: readonly Sprint[],
  backlogLabel: string
): MoveChoice[] {
  if (ticketBoardId(ticket, viewBoardId) !== viewBoardId) return [];
  const choices: MoveChoice[] = [];
  if (currentSprintId && backlogId) {
    choices.push({ kind: "place", scope: { backlogId }, label: backlogLabel });
  }
  for (const sprint of openSprintTargets(sprints, currentSprintId)) {
    choices.push({ kind: "place", scope: { sprintId: sprint.id }, label: sprint.name });
  }
  return choices;
}

/**
 * True when a board move sets an incident to Done, so its root cause
 * analysis and lessons learned must be asked for before it is sent. A status
 * move closes it when it enters Done. A placement move closes it when it
 * gives the ticket back Done after the move, as the Undo of a move into the
 * backlog does: its ticket is Todo there, because the backlog made it so.
 */
export function closesIncident(move: TicketMove): boolean {
  if (move.kind === "status") {
    return requiresIncidentClose(move.ticket.type, move.ticket.status, move.target.status);
  }
  return (
    move.status !== undefined &&
    requiresIncidentClose(move.ticket.type, move.ticket.status, move.status)
  );
}

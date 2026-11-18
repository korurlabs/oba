/**
 * Cross-container drag-and-drop primitives for the All tab.
 *
 * The All tab stacks two independent SenseyTable sections (the selected
 * sprint and the backlog). A single DndContext in `all-page-view` spans both
 * so a row can be dragged across the section boundary; these constants and
 * helpers are the shared vocabulary that context and its droppable sections
 * agree on.
 */

import {
  closestCenter,
  pointerWithin,
  type ClientRect,
  type CollisionDetection,
  type UniqueIdentifier,
} from "@dnd-kit/core";
import { arrayMove } from "@dnd-kit/sortable";
import type { TicketListResponse } from "@/features/tickets/schemas";
import { groupListRows, listWorkItems } from "@/lib/utils/ticket-groups";

// ── Container identity ───────────────────────────────────────────────

/** Droppable id for the sprint section wrapper. */
export const ALL_SPRINT_CONTAINER_ID = "all-section-sprint";

/** Droppable id for the backlog section wrapper. */
export const ALL_BACKLOG_CONTAINER_ID = "all-section-backlog";

export type AllSectionContainerId =
  | typeof ALL_SPRINT_CONTAINER_ID
  | typeof ALL_BACKLOG_CONTAINER_ID;

const CONTAINER_IDS = new Set<string>([ALL_SPRINT_CONTAINER_ID, ALL_BACKLOG_CONTAINER_ID]);

/** True when the given droppable id is a section wrapper rather than a row. */
export const isAllSectionContainerId = (id: string): id is AllSectionContainerId =>
  CONTAINER_IDS.has(id);

// ── Collision detection ──────────────────────────────────────────────

/**
 * Row-first collision detection, pointer-only when there is a pointer.
 *
 * For pointer drags this is `pointerWithin` with deliberately no rect-based
 * fallback. The two sections are separated by a visible gap that belongs to
 * neither, and a fallback such as `rectIntersection` resolves that gap against
 * whichever section the dragged row's rect overlaps most, turning a release
 * into no-man's-land into a move. Pointer-only keeps "released outside every
 * drop target" a genuine no-op.
 *
 * Keyboard drags have no pointer at all: `pointerWithin` reads the coordinates
 * derived from the activator event, and a KeyboardSensor's space-key activator
 * is a KeyboardEvent with no clientX/clientY, so it returns `[]` on every
 * frame. With no fallback that makes `over` permanently null -- no section ever
 * highlights, nothing is announced while arrowing, and every drop is a silent
 * no-op reported as a cancellation. `closestCenter` is the rect-based
 * equivalent for that case; it cannot reintroduce the gap problem because the
 * gap is only reachable with a pointer.
 *
 * Rows are then preferred over the section wrappers explicitly. The wrappers
 * are large enough that dnd-kit's centre-distance ordering is not dependable
 * when both contain the pointer, so a wrapper only wins when nothing more
 * specific was hit -- which is exactly the "dropped on section background"
 * case.
 */
export const allSectionCollisionDetection: CollisionDetection = (args) => {
  const collisions = args.pointerCoordinates ? pointerWithin(args) : closestCenter(args);

  const rowCollisions = collisions.filter(
    (collision) => !isAllSectionContainerId(String(collision.id))
  );

  return rowCollisions.length > 0 ? rowCollisions : collisions;
};

// ── Row lookup ───────────────────────────────────────────────────────

/**
 * Builds the set of draggable row ids for a section.
 *
 * Every ticket of a section's array is a list root and a draggable row: a
 * top-level ticket, or a sub-ticket planned without its parent, which the
 * table nests under a static header row and still lets drag. The header row
 * and the sub-tickets nested under a listed parent are not in the array, so
 * they never count as members of a container.
 */
export const collectTopLevelTicketIds = (tickets: TicketListResponse[]): Set<string> =>
  new Set(tickets.map((ticket) => ticket.id));

/** Finds a top-level ticket by id within a section's data. */
export const findTopLevelTicket = (
  tickets: TicketListResponse[],
  ticketId: string
): TicketListResponse | undefined =>
  tickets.find((ticket) => ticket.id === ticketId);

/**
 * Number of top-level rows in `tickets` other than `ticketId`: the rows a
 * section's table keeps while that row is shown in the other section.
 */
export const countOtherTopLevelTickets = (
  tickets: readonly TicketListResponse[],
  ticketId: string
): number => tickets.filter((ticket) => ticket.id !== ticketId).length;

/**
 * A section's tickets in the order its table shows them. The table nests the
 * sub-tickets of a parent that is not in the section under one header row,
 * at the place of the first of them, so siblings that are not adjacent in
 * `displayOrder` render together. Every index the drag computes (the preview
 * slot, the landing slot, the neighbours that set the move's order) must be
 * an index in that rendered order, or the previewed row shows off by the
 * siblings that were pulled up. Returns `tickets` itself when the order is
 * already the rendered one, so the table's data keeps its identity.
 */
export const toRenderedOrder = (tickets: TicketListResponse[]): TicketListResponse[] => {
  const rendered = listWorkItems(groupListRows(tickets));
  return rendered.every((ticket, index) => ticket === tickets[index]) ? tickets : rendered;
};

// ── Cross-section placement ──────────────────────────────────────────

/** The droppable a drag is over, as dnd-kit reports it. */
export interface PreviewOverTarget {
  id: UniqueIdentifier;
  rect: ClientRect;
}

const verticalCentre = (rect: ClientRect): number => rect.top + rect.height / 2;

/**
 * Index at which a row dragged in from the other section is shown in
 * `targetRows`, the target's rows without the dragged one; `targetSlot` is
 * the section it is shown in, and `sourceRowCount` the number of rows its own
 * section keeps without it.
 *
 * Over a row it is the index the target's sortable list keeps once the row is
 * shown there, so the placeholder never hops by a row after it appears.
 * dnd-kit hit-tests the rows' layout positions, measured again after that
 * render, and the vertical list strategy shows the dragged row at the index
 * of the row under the pointer, or leaves it where it is when the pointer is
 * on the dragged row itself. The row therefore goes into the slot the pointer
 * lies in after the render. The sprint sits above the backlog: going into the
 * sprint nothing above it moves, so that slot is the hovered row's and the
 * row goes before it. Going into the backlog the sprint above loses the row:
 * when the sprint keeps other rows it gets shorter by one row and the backlog
 * moves up by one row, so the pointer lies in the slot after the hovered row
 * and the row goes after it; when it was the sprint's only row the sprint
 * table holds the row's space instead of showing its taller empty state, the
 * backlog does not move, and the row goes before the hovered row.
 *
 * Over the section itself (its header, selection bar or bottom padding) it
 * goes first in the section's upper half and last in the lower half, so a row
 * dropped on the header of a long list does not travel to its bottom. The
 * sortable list leaves both where they are, because the pointer is then on
 * the section or on the dragged row. Without a measured rect for the dragged
 * row it goes last on the section.
 */
export const resolvePreviewIndex = (
  targetRows: readonly TicketListResponse[],
  targetSlot: AllSectionContainerId,
  sourceRowCount: number,
  over: PreviewOverTarget,
  activeRect: ClientRect | null
): number => {
  const overId = String(over.id);
  const overIndex = isAllSectionContainerId(overId)
    ? -1
    : targetRows.findIndex((row) => row.id === overId);
  if (overIndex !== -1) {
    const targetMovesUp = targetSlot === ALL_BACKLOG_CONTAINER_ID && sourceRowCount > 0;
    return targetMovesUp ? overIndex + 1 : overIndex;
  }

  const isAboveOverCentre =
    activeRect !== null && verticalCentre(activeRect) <= verticalCentre(over.rect);
  return isAboveOverCentre ? 0 : targetRows.length;
};

/**
 * The `order` a move sends so that the server stores the ticket at `index` of
 * `rows`, the target list with the ticket in place.
 *
 * The server gives the ticket that order and shifts the board's tickets
 * between its old and new order by one, as an order update does. With the
 * ticket's order above the next row's, taking the next row's order puts it
 * just before that row (the row and the ones after it move down one); with
 * its order below the previous row's, taking the previous row's order puts it
 * just after that row. When it already sits between its neighbours the move
 * alone keeps it there, so no order is sent.
 */
export const resolveMoveOrder = (
  rows: readonly TicketListResponse[],
  index: number
): number | undefined => {
  const ticket = rows[index];
  if (!ticket) return undefined;

  const next = rows[index + 1];
  if (next && ticket.displayOrder > next.displayOrder) return next.displayOrder;

  const previous = rows[index - 1];
  if (previous && ticket.displayOrder < previous.displayOrder) return previous.displayOrder;

  return undefined;
};

/** `rows` without `ticket`, then with it at `index` (clamped to the list). */
export const placeTicketAt = (
  rows: readonly TicketListResponse[],
  ticket: TicketListResponse,
  index: number
): TicketListResponse[] => {
  const next = rows.filter((row) => row.id !== ticket.id);
  next.splice(Math.min(Math.max(index, 0), next.length), 0, ticket);
  return next;
};

/**
 * The target list a cross-section drop lands in, with the dragged ticket in
 * place: the order the target table shows in the frame the drag ends.
 *
 * While a preview runs the ticket is already in `shownTarget`, and the
 * target's sortable strategy shows it at the hovered row's index, so the drop
 * applies that same move. Without a preview (the drop came before any
 * drag-over) it is placed where a preview would have put it.
 */
export const resolveLandingRows = (
  shownTarget: readonly TicketListResponse[],
  targetSlot: AllSectionContainerId,
  sourceRowCount: number,
  ticket: TicketListResponse,
  over: PreviewOverTarget,
  activeRect: ClientRect | null
): TicketListResponse[] => {
  const activeIndex = shownTarget.findIndex((row) => row.id === ticket.id);
  if (activeIndex === -1) {
    return placeTicketAt(
      shownTarget,
      ticket,
      resolvePreviewIndex(shownTarget, targetSlot, sourceRowCount, over, activeRect)
    );
  }

  const overIndex = shownTarget.findIndex((row) => row.id === String(over.id));
  return overIndex === -1 || overIndex === activeIndex
    ? [...shownTarget]
    : arrayMove([...shownTarget], activeIndex, overIndex);
};

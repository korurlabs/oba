"use client";

/**
 * Shared Kanban Board Configuration
 *
 * Centralizes column definitions, collision detection, and constants
 * used across sprint-kanban, backlog-kanban, and all-tickets-kanban.
 */

import { useCallback, useMemo, useState } from "react";
import { TicketStatus } from "@/types/enums";
import {
  closestCorners,
  defaultDropAnimationSideEffects,
  pointerWithin,
  useDroppable,
  type CollisionDetection,
  type DropAnimation,
} from "@dnd-kit/core";
import { cn } from "@oba/ui/lib/utils";
import type { TicketListResponse, TicketPriority } from "@/features/tickets/schemas";
import { sortByPriority } from "@/features/tickets/lib/ticket-priority-order";

// ---------------------------------------------------------------------------
// Column definitions
// ---------------------------------------------------------------------------

/**
 * The status columns in board order. In the modern layout a column is told
 * apart by its label and its position, not by a colour; the classic layout
 * keeps the coloured edge on the column header it always had.
 */
export interface KanbanColumn {
  id: TicketStatus;
  /** The coloured left edge of the column header, in the classic layout only. */
  classicEdgeClass: string;
}

export const KANBAN_COLUMNS: KanbanColumn[] = [
  { id: TicketStatus.TODO, classicEdgeClass: "classic:border-l-gray-300" },
  { id: TicketStatus.IN_PROGRESS, classicEdgeClass: "classic:border-l-blue-400" },
  { id: TicketStatus.WAITING_FOR_THIRD_PARTY, classicEdgeClass: "classic:border-l-amber-400" },
  { id: TicketStatus.IN_REVIEW, classicEdgeClass: "classic:border-l-purple-400" },
  { id: TicketStatus.DONE, classicEdgeClass: "classic:border-l-green-400" },
];

/**
 * Set of valid kanban column statuses for fast lookups during drag resolution.
 */
export const VALID_KANBAN_STATUSES = new Set(KANBAN_COLUMNS.map((c) => c.id));

// ---------------------------------------------------------------------------
// DnD activation constraint
// ---------------------------------------------------------------------------

/** Minimum drag distance (px) before a drag operation starts. */
export const KANBAN_DRAG_ACTIVATION_DISTANCE = 8;

// ---------------------------------------------------------------------------
// Drop animation
// ---------------------------------------------------------------------------

/**
 * Drop animation for the kanban DragOverlay.
 *
 * dnd-kit's default uses a 250ms cubic-bezier that overshoots (its final
 * control point is 1.22), which reads as a bounce/scale jump when the card is
 * already in its new column thanks to the optimistic cache update. This is a
 * short, purely decelerating curve instead: the overlay glides into the slot
 * the card now occupies and hands over.
 *
 * The side effect fades the real card to 0 for the duration so the overlay and
 * the freshly rendered card are never both visible.
 */
export const KANBAN_DROP_ANIMATION: DropAnimation = {
  duration: 180,
  easing: "cubic-bezier(0.2, 0, 0, 1)",
  sideEffects: defaultDropAnimationSideEffects({
    styles: {
      active: { opacity: "0" },
    },
  }),
};

// ---------------------------------------------------------------------------
// Drop hand-off
// ---------------------------------------------------------------------------

interface KanbanDragCard {
  id: string;
  status: TicketStatus;
}

/** A dropped card that the view renders in `slot` until its real data agrees. */
export interface PendingDrop<S extends string = string> {
  id: string;
  slot: S;
}

/**
 * Reports which slot a card currently occupies in the real data, or null when
 * the card is not in this view at all.
 *
 * A "slot" is whatever the surface treats as a drop container: the status
 * column on a kanban board, the section container id on the All tab. It only
 * has to be a stable string that changes when -- and only when -- the drop has
 * visibly taken effect.
 *
 * Must be referentially stable per data snapshot (wrap it in `useCallback`
 * keyed on the underlying rows).
 */
export type DropSlotResolver<S extends string = string> = (cardId: string) => S | null;

/**
 * Owns a drag overlay and places a dropped card in its target slot from the
 * very commit that ends the drag.
 *
 * Why the placement exists: dnd-kit renders the DragOverlay only while a drag
 * is active, and it ends the drag in the same commit that calls `onDragEnd`.
 * In that commit's layout effect it animates the overlay to the node
 * registered for the card's id. The optimistic cache patch from the ticket
 * mutations reaches React later (the mutation first cancels the running
 * ticket queries, then TanStack notifies asynchronously), so without help the
 * registered node is still the card in its old slot, and the overlay glides
 * back there while the card appears in the new slot mid-glide.
 *
 * `handOffTo` therefore records a pending placement, and the view renders the
 * card in `slot` while it lasts. The card is in its target in the drop commit,
 * its new node registers before the overlay measures, and the overlay glides
 * into the new slot. The placement ends when the real data puts the card in
 * `slot` (the patch landed) or drops it (null), when `settled` settles (the
 * request finished, and a failure has already rolled the cache back), or when
 * a newer `handOffTo` replaces it. There is no timer: the card already shows
 * in its target, so ending early could only make it flash back.
 *
 * `releaseDrag` ends a drag that changes nothing; it leaves the placement of
 * an earlier drop alone.
 */
export function useDropHandoff<T extends { id: string }, S extends string = string>(
  resolveSlot: DropSlotResolver<S>
) {
  const [activeCard, setActiveCard] = useState<T | null>(null);
  const [pendingDrop, setPendingDrop] = useState<PendingDrop<S> | null>(null);

  // A placement ends as soon as the real data agrees with it or no longer
  // holds the card. It is cleared during render rather than in an effect so
  // that the commit which shows the patched data never renders a stale
  // placement, and a later rollback of that patch cannot revive it.
  let livePendingDrop = pendingDrop;
  if (pendingDrop) {
    const currentSlot = resolveSlot(pendingDrop.id);
    if (currentSlot === null || currentSlot === pendingDrop.slot) {
      livePendingDrop = null;
      setPendingDrop(null);
    }
  }

  // Re-dragging the card of a running placement starts from its real slot, so
  // the drag-end handler, which reads the real data, compares against the
  // slot the card is shown in.
  const beginDrag = useCallback((card: T | null) => {
    if (card) {
      setPendingDrop((current) => (current?.id === card.id ? null : current));
    }
    setActiveCard(card);
  }, []);

  /** Ends a drag that changes nothing. */
  const releaseDrag = useCallback(() => {
    setActiveCard(null);
  }, []);

  /**
   * Renders `cardId` in `slot` until the real data agrees or `settled`
   * settles. Call it inside `onDragEnd`, so that the placement and the end of
   * the drag share one commit. `settled` is the request's promise, or a
   * resolved one when no request was sent, so the card returns to its slot.
   */
  const handOffTo = useCallback((cardId: string, slot: S, settled: Promise<unknown>) => {
    const drop: PendingDrop<S> = { id: cardId, slot };
    setActiveCard(null);
    setPendingDrop(drop);

    // Only the placement this call created may end here; a newer drop has
    // replaced it otherwise.
    const endPlacement = () => setPendingDrop((current) => (current === drop ? null : current));
    settled.then(endPlacement, () => {
      // rule-10-no-op -- reason: callers pass a mutation promise whose onError
      // already rolls the cache back and surfaces the failure as a toast, and
      // they catch any other failure before passing it; this handler only ends
      // the placement and keeps the rejection from going unhandled.
      endPlacement();
    });
  }, []);

  return { activeCard, pendingDrop: livePendingDrop, beginDrag, releaseDrag, handOffTo };
}

/**
 * Kanban binding of `useDropHandoff`: the slot of a card is its status column.
 * `displayTickets` is `tickets` with a pending drop's card moved to the column
 * it was dropped on (same list position); the columns must be built from it.
 * It is `tickets` itself while nothing is pending.
 */
export function useKanbanDropHandoff<T extends KanbanDragCard>(tickets: T[]) {
  const resolveSlot = useCallback<DropSlotResolver<TicketStatus>>(
    (ticketId) => tickets.find((ticket) => ticket.id === ticketId)?.status ?? null,
    [tickets]
  );

  const { activeCard, pendingDrop, beginDrag, releaseDrag, handOffTo } = useDropHandoff<
    T,
    TicketStatus
  >(resolveSlot);

  const pendingId = pendingDrop?.id;
  const pendingStatus = pendingDrop?.slot;
  const displayTickets = useMemo(() => {
    if (!pendingId || !pendingStatus) return tickets;
    return tickets.map((ticket) =>
      ticket.id === pendingId ? { ...ticket, status: pendingStatus } : ticket
    );
  }, [tickets, pendingId, pendingStatus]);

  return { activeTicket: activeCard, displayTickets, beginDrag, releaseDrag, handOffTo };
}

// ---------------------------------------------------------------------------
// Collision detection
// ---------------------------------------------------------------------------

/**
 * Custom collision detection strategy for kanban boards.
 * Uses `pointerWithin` first (catches empty columns), then falls back to
 * `closestCorners` for populated columns with sortable items.
 */
export const kanbanCollisionDetection: CollisionDetection = (args) => {
  const pointerCollisions = pointerWithin(args);
  if (pointerCollisions.length > 0) {
    return pointerCollisions;
  }
  return closestCorners(args);
};

// ---------------------------------------------------------------------------
// Column contents
// ---------------------------------------------------------------------------

/**
 * The cards of one status column, most urgent first. Tickets of equal
 * priority keep the order of `tickets`, which is the board order or the page's
 * sort, so the order set on the All page still decides between them. A card
 * dropped into the column, or whose priority changed, therefore shows at its
 * priority place at once: a kanban has no manual order within a column.
 */
export function getTicketsByStatus<
  T extends { status: TicketStatus; priority?: TicketPriority | null },
>(tickets: T[], status: TicketStatus): T[] {
  return sortByPriority(tickets.filter((ticket) => ticket.status === status));
}

// ---------------------------------------------------------------------------
// Droppable column component
// ---------------------------------------------------------------------------

interface DroppableColumnProps {
  id: string;
  children: React.ReactNode;
  isOver: boolean;
}

/**
 * Wrapper that registers a droppable zone for a kanban column.
 * Shows a visual highlight when a card is dragged over.
 */
export function DroppableColumn({ id, children, isOver }: DroppableColumnProps) {
  const { setNodeRef } = useDroppable({ id });

  return (
    <div
      ref={setNodeRef}
      className={cn(
        "classic:rounded-lg classic:border-2 flex flex-1 flex-col gap-3 rounded-none border border-dashed border-transparent p-0 transition-colors",
        isOver &&
          "border-primary bg-primary/5 dark:border-border dark:bg-muted classic:bg-primary/10"
      )}
    >
      {children}
    </div>
  );
}

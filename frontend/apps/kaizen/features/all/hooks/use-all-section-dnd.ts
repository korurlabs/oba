"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import {
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragOverEvent,
  type DragStartEvent,
} from "@dnd-kit/core";
import { sortableKeyboardCoordinates } from "@dnd-kit/sortable";
import type { TicketListResponse } from "@/features/tickets/schemas";
import { useMoveTicket, useUpdateTicketOrder } from "@/features/tickets/hooks/use-tickets";
import { insertTicket } from "@/features/tickets/hooks/ticket-cache-updates";
import {
  KANBAN_DRAG_ACTIVATION_DISTANCE,
  useDropHandoff,
  type DropSlotResolver,
} from "@/features/tickets/lib/kanban-config";
import { getBacklog } from "@/features/backlog/api";
import {
  ALL_BACKLOG_CONTAINER_ID,
  ALL_SPRINT_CONTAINER_ID,
  allSectionCollisionDetection,
  collectTopLevelTicketIds,
  countOtherTopLevelTickets,
  findTopLevelTicket,
  isAllSectionContainerId,
  placeTicketAt,
  resolveLandingRows,
  resolveMoveOrder,
  resolvePreviewIndex,
  toRenderedOrder,
  type AllSectionContainerId,
} from "@/features/all/lib/all-section-dnd";

/** How long a row that arrived from the other section stays tinted. */
const RECENTLY_MOVED_TINT_MS = 1200;

type MoveTicketVariables = Parameters<ReturnType<typeof useMoveTicket>["mutateAsync"]>[0];

/** The rows of both sections, in the order the tables show them. */
interface SectionLists {
  sprint: TicketListResponse[];
  backlog: TicketListResponse[];
}

/** A row shown in the other section while it is dragged over it. */
interface CrossSectionPreview {
  id: string;
  slot: AllSectionContainerId;
  index: number;
}

/** Where a dropped row is shown in its target until the move lands. */
interface CrossSectionLanding {
  id: string;
  index: number;
}

/**
 * Shows `ticketId` in section `slot` instead of the other one: at `index`, or
 * at its `displayOrder` slot without one. Returns `lists` itself when the row
 * is not in the other section.
 */
function showInSection(
  lists: SectionLists,
  ticketId: string,
  slot: AllSectionContainerId,
  index: number | undefined
): SectionLists {
  const toSprint = slot === ALL_SPRINT_CONTAINER_ID;
  const source = toSprint ? lists.backlog : lists.sprint;
  const target = toSprint ? lists.sprint : lists.backlog;
  const ticket = findTopLevelTicket(source, ticketId);
  if (!ticket) return lists;

  const shownSource = source.filter((row) => row.id !== ticketId);
  const shownTarget =
    index === undefined
      ? (insertTicket(target, ticket) ?? target)
      : placeTicketAt(target, ticket, index);
  return toSprint
    ? { sprint: shownTarget, backlog: shownSource }
    : { sprint: shownSource, backlog: shownTarget };
}

// ── Options ──────────────────────────────────────────────────────────

interface UseAllSectionDndOptions {
  /** Board the All tab is scoped to (fallback home board for a ticket). */
  boardId: string;
  sprintTickets: TicketListResponse[];
  backlogTickets: TicketListResponse[];
  /** Sprint the sprint section is currently showing, if any. */
  currentSprintId?: string;
  /** Backlog of `boardId`, as resolved by the page. */
  backlogId: string | null;
  /**
   * Whether the user may move tickets between sections. Reordering inside a
   * section is gated by the tables themselves (`canReorder`), as on the
   * standalone Sprint and Backlog pages.
   */
  canMove: boolean;
}

// ── Hook ─────────────────────────────────────────────────────────────

/**
 * Drag resolution for the All tab's two stacked sections.
 *
 * Both SenseyTables opt out of their own DndContext (`externalDndContext`)
 * and this hook supplies the single context that spans them. Four drops are
 * possible:
 *
 * - sprint row onto the backlog section  -> move mutation, `{ backlogId, order }`
 * - backlog row onto the sprint section  -> move mutation, `{ sprintId, order }`
 * - sprint row onto the sprint section   -> order mutation
 * - backlog row onto the backlog section -> order mutation
 *
 * While a row hovers the other section it is shown there, at the hovered
 * position, and the target table's rows make room for it as in an in-table
 * drag; the drop lands it at that position. Anything else (no droppable under
 * the pointer, an unknown id, a drop on the section the row already lives in
 * without a row underneath) is a no-op.
 */
export function useAllSectionDnd({
  boardId,
  sprintTickets: sprintTicketsInput,
  backlogTickets: backlogTicketsInput,
  currentSprintId,
  backlogId,
  canMove,
}: UseAllSectionDndOptions) {
  const t = useTranslations();

  // Positions are computed in the order the tables render the rows.
  const sprintTickets = useMemo(() => toRenderedOrder(sprintTicketsInput), [sprintTicketsInput]);
  const backlogTickets = useMemo(
    () => toRenderedOrder(backlogTicketsInput),
    [backlogTicketsInput]
  );

  const moveTicketMutation = useMoveTicket();
  const updateOrderMutation = useUpdateTicketOrder();

  const [activeContainerId, setActiveContainerId] = useState<AllSectionContainerId | null>(null);
  const [overContainerId, setOverContainerId] = useState<AllSectionContainerId | null>(null);

  // Match the kanban's drag feel; keyboard sensor keeps the rows reachable
  // without a pointer.
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: KANBAN_DRAG_ACTIVATION_DISTANCE },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  const sprintTicketIds = useMemo(() => collectTopLevelTicketIds(sprintTickets), [sprintTickets]);
  const backlogTicketIds = useMemo(
    () => collectTopLevelTicketIds(backlogTickets),
    [backlogTickets]
  );

  /**
   * Maps a droppable id (a section wrapper or a row) to its container.
   * Returns null for ids that belong to neither section — a sub-ticket row,
   * or stale data mid-refetch.
   */
  const resolveContainerId = useCallback(
    (id: string | null): AllSectionContainerId | null => {
      if (!id) return null;
      if (isAllSectionContainerId(id)) return id;
      if (sprintTicketIds.has(id)) return ALL_SPRINT_CONTAINER_ID;
      if (backlogTicketIds.has(id)) return ALL_BACKLOG_CONTAINER_ID;
      return null;
    },
    [sprintTicketIds, backlogTicketIds]
  );

  /**
   * The slot of a row is the section it sits in according to the real data,
   * so a cross-section drop stays placed in the target section until the
   * optimistic move renders it there. `resolveContainerId` doubles as the
   * resolver: a ticket id is never a container id, so the container-id
   * short-circuit inside it can never fire for one.
   */
  const resolveDropSlot: DropSlotResolver<AllSectionContainerId> = resolveContainerId;

  const {
    activeCard: activeTicket,
    pendingDrop,
    beginDrag,
    releaseDrag,
    handOffTo,
  } = useDropHandoff<TicketListResponse, AllSectionContainerId>(resolveDropSlot);

  // The dragged row while it hovers the other section, and the position a
  // dropped row keeps in its target until the move lands in the cache. Both
  // only change which table shows the row and where; the data is untouched.
  const [preview, setPreview] = useState<CrossSectionPreview | null>(null);
  const [landing, setLanding] = useState<CrossSectionLanding | null>(null);

  /**
   * The section a row is shown in: the previewed section while it hovers the
   * other one, its pending drop's target while that runs, else its real
   * section. Drop targets resolve through this, because the user aims at the
   * row where it is shown.
   */
  const resolveShownContainerId = useCallback(
    (id: string | null): AllSectionContainerId | null => {
      if (preview && id === preview.id) return preview.slot;
      if (pendingDrop && id === pendingDrop.id) return pendingDrop.slot;
      return resolveContainerId(id);
    },
    [preview, pendingDrop, resolveContainerId]
  );

  // The lists with a pending drop applied: the row is taken out of its
  // section and shown in the target at the position it was dropped at, which
  // is where the optimistic move and the refetch put it too, because the move
  // sends the matching order. With nothing pending they are the input arrays
  // themselves, because the tables show a same-section drop in its new order
  // only while their `data` keeps its identity.
  const pendingId = pendingDrop?.id;
  const pendingSlot = pendingDrop?.slot;
  const landingIndex = landing && landing.id === pendingId ? landing.index : undefined;
  const placedLists = useMemo((): SectionLists => {
    const real = { sprint: sprintTickets, backlog: backlogTickets };
    if (!pendingId || !pendingSlot) return real;
    return showInSection(real, pendingId, pendingSlot, landingIndex);
  }, [sprintTickets, backlogTickets, pendingId, pendingSlot, landingIndex]);

  // The lists the tables render: the placed lists with the previewed row in
  // the section it hovers. Each table builds its sortable items from these
  // rows, so the row joins the target's sortable list and shows as its
  // placeholder there, and dnd-kit slides the rows of both tables around it.
  const shownLists = useMemo(
    (): SectionLists =>
      preview ? showInSection(placedLists, preview.id, preview.slot, preview.index) : placedLists,
    [placedLists, preview]
  );

  /**
   * Finds a top-level row by id. A pending drop only moves a row between the
   * two lists, so searching the real lists finds every row that is shown.
   */
  const findTicket = useCallback(
    (ticketId: string): TicketListResponse | undefined =>
      findTopLevelTicket(sprintTickets, ticketId) ?? findTopLevelTicket(backlogTickets, ticketId),
    [sprintTickets, backlogTickets]
  );

  // ── Arrived-row tint ───────────────────────────────────────────────

  const [recentlyMovedTicketId, setRecentlyMovedTicketId] = useState<string | null>(null);
  const tintTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearRecentlyMoved = useCallback(() => {
    if (tintTimerRef.current !== null) clearTimeout(tintTimerRef.current);
    tintTimerRef.current = null;
    setRecentlyMovedTicketId(null);
  }, []);

  const markRecentlyMoved = useCallback((ticketId: string) => {
    if (tintTimerRef.current !== null) clearTimeout(tintTimerRef.current);
    setRecentlyMovedTicketId(ticketId);
    tintTimerRef.current = setTimeout(() => {
      tintTimerRef.current = null;
      setRecentlyMovedTicketId(null);
    }, RECENTLY_MOVED_TINT_MS);
  }, []);

  useEffect(
    () => () => {
      if (tintTimerRef.current !== null) clearTimeout(tintTimerRef.current);
    },
    []
  );

  /**
   * Sends a cross-section move and tints the row while it arrives. The
   * returned promise settles with the request and never rejects, so it can
   * end the pending drop either way.
   */
  const dispatchMove = useCallback(
    (variables: MoveTicketVariables): Promise<void> => {
      markRecentlyMoved(variables.ticketId);
      return moveTicketMutation.mutateAsync(variables).then(
        () => undefined,
        () => {
          // rule-10-no-op -- reason: useMoveTicket's onError already rolls the
          // cache back and toasts the server's message; this handler only
          // keeps the rejection from going unhandled.
        }
      );
    },
    [markRecentlyMoved, moveTicketMutation]
  );

  /**
   * Drops the section highlight. Deliberately does not touch the overlay --
   * that is `releaseDrag` / `handOffTo`, which outlive the drop on a real
   * move.
   */
  const clearDragHighlight = useCallback(() => {
    setActiveContainerId(null);
    setOverContainerId(null);
  }, []);

  // ── Move: sprint -> backlog ────────────────────────────────────────

  /**
   * Resolves the backlog the ticket should land in. Tickets from child
   * boards can appear in these lists (the "include all boards" toggle), and
   * a ticket always moves within its own board, so the page-level backlog id
   * only applies to tickets that live on this board.
   */
  const resolveTargetBacklogId = useCallback(
    async (ticketBoardId: string): Promise<string | null> => {
      if (ticketBoardId === boardId && backlogId) return backlogId;
      const response = await getBacklog(ticketBoardId);
      return response?.data?.id ?? null;
    },
    [boardId, backlogId]
  );

  /**
   * Settles when the move request settles, or at once when no move was sent
   * (no backlog to move into, already toasted), so the row returns to its
   * section. Never rejects for a failed request.
   */
  const moveTicketToBacklog = useCallback(
    async (ticket: TicketListResponse, order: number | undefined): Promise<void> => {
      const ticketBoardId = ticket.board?.id ?? boardId;
      let targetBacklogId: string | null = null;

      try {
        targetBacklogId = await resolveTargetBacklogId(ticketBoardId);
      } catch (error) {
        console.warn("[useAllSectionDnd] backlog lookup failed", error);
        toast.error(t("all.dnd.backlogUnavailable"));
        return;
      }

      if (!targetBacklogId) {
        toast.error(t("all.dnd.backlogUnavailable"));
        return;
      }

      await dispatchMove({
        boardId: ticketBoardId,
        ticketId: ticket.id,
        data: { backlogId: targetBacklogId, order },
      });
    },
    [boardId, resolveTargetBacklogId, dispatchMove, t]
  );

  // ── Move: backlog -> sprint ────────────────────────────────────────

  /** Same contract as `moveTicketToBacklog`. */
  const moveTicketToSprint = useCallback(
    (ticket: TicketListResponse, order: number | undefined): Promise<void> => {
      if (!currentSprintId) {
        toast.error(t("all.dnd.sprintUnavailable"));
        return Promise.resolve();
      }

      return dispatchMove({
        boardId: ticket.board?.id ?? boardId,
        ticketId: ticket.id,
        data: { sprintId: currentSprintId, order },
      });
    },
    [boardId, currentSprintId, dispatchMove, t]
  );

  // ── Reorder within a section (pre-existing behaviour) ──────────────

  const reorderTicket = useCallback(
    (activeRow: TicketListResponse, overRow: TicketListResponse) => {
      updateOrderMutation.mutate({
        boardId: activeRow.board?.id ?? boardId,
        ticketId: activeRow.id,
        targetTicketId: overRow.id,
        data: { order: overRow.displayOrder },
      });
    },
    [boardId, updateOrderMutation]
  );

  // ── dnd-kit handlers ───────────────────────────────────────────────

  const handleDragStart = useCallback(
    (event: DragStartEvent) => {
      const activeId = String(event.active.id);
      // The real section, not the shown one: `beginDrag` ends a pending drop
      // of the dragged row, so the row goes back to its real section.
      const containerId = resolveContainerId(activeId);
      if (!containerId) return;

      clearRecentlyMoved();
      setActiveContainerId(containerId);
      beginDrag(findTicket(activeId) ?? null);
    },
    [resolveContainerId, findTicket, beginDrag, clearRecentlyMoved]
  );

  const handleDragOver = useCallback(
    (event: DragOverEvent) => {
      const { active, over } = event;
      const overContainer = resolveShownContainerId(over ? String(over.id) : null);
      setOverContainerId(overContainer);

      // Over the gap between the sections the preview stays, so the row does
      // not flicker back to its own section on the way across.
      if (!over || !overContainer || !canMove) return;

      const activeId = String(active.id);
      const realContainer = resolveContainerId(activeId);
      if (!realContainer) return;

      // Back over its own section: the row returns to its slot there, and that
      // table's sortable list takes over.
      if (overContainer === realContainer) {
        setPreview(null);
        return;
      }

      // Already shown in the hovered section: the target's sortable list moves
      // the row between its rows, as in an in-table drag.
      if (preview?.id === activeId && preview.slot === overContainer) return;

      const targetRows = (
        overContainer === ALL_SPRINT_CONTAINER_ID ? placedLists.sprint : placedLists.backlog
      ).filter((row) => row.id !== activeId);
      const sourceRowCount = countOtherTopLevelTickets(
        realContainer === ALL_SPRINT_CONTAINER_ID ? placedLists.sprint : placedLists.backlog,
        activeId
      );
      setPreview({
        id: activeId,
        slot: overContainer,
        index: resolvePreviewIndex(
          targetRows,
          overContainer,
          sourceRowCount,
          over,
          active.rect.current.translated
        ),
      });
    },
    [resolveShownContainerId, resolveContainerId, canMove, preview, placedLists]
  );

  const handleDragCancel = useCallback(() => {
    clearDragHighlight();
    setPreview(null);
    releaseDrag();
  }, [clearDragHighlight, releaseDrag]);

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const { active, over } = event;
      // The highlight and the preview go as soon as the pointer is up; the
      // overlay is a separate decision per branch below.
      clearDragHighlight();
      setPreview(null);

      // Dropped outside every droppable: the row returns to its own slot.
      if (!over) {
        releaseDrag();
        return;
      }

      const activeId = String(active.id);
      const overId = String(over.id);

      // The row's real section, not the previewed one: `beginDrag` ended any
      // pending drop of this row when the drag started.
      const fromContainerId = resolveContainerId(activeId);
      const toContainerId = resolveShownContainerId(overId);
      if (!fromContainerId || !toContainerId) {
        releaseDrag();
        return;
      }

      const draggedTicket = findTicket(activeId);
      if (!draggedTicket) {
        releaseDrag();
        return;
      }

      // Same section: reorder. The table already shows a drop of one of its
      // rows onto another of its rows in the new order, so this reorders on
      // exactly that condition; a drop on the section background (no row
      // underneath) has no target order and changes nothing. The row stays
      // in its section, so there is no placement to hold.
      if (fromContainerId === toContainerId) {
        releaseDrag();
        if (overId === activeId || isAllSectionContainerId(overId)) return;
        const overTicket = findTicket(overId);
        if (!overTicket) return;
        reorderTicket(draggedTicket, overTicket);
        return;
      }

      // Cross-section: move the ticket to the other bucket.
      if (!canMove) {
        releaseDrag();
        return;
      }

      // The row lands where the target table shows it in this commit, so the
      // drop animation glides there instead of back to the section it was
      // dragged out of, and the move sends the order that makes the server
      // and the optimistic patch keep it there. The placement ends when the
      // move lands in the cache or the request settles; a move that is never
      // sent (no backlog, no sprint, both already toasted) settles at once
      // and the row returns.
      const shownTarget =
        toContainerId === ALL_SPRINT_CONTAINER_ID ? shownLists.sprint : shownLists.backlog;
      const sourceRowCount = countOtherTopLevelTickets(
        fromContainerId === ALL_SPRINT_CONTAINER_ID ? placedLists.sprint : placedLists.backlog,
        activeId
      );
      const landingRows = resolveLandingRows(
        shownTarget,
        toContainerId,
        sourceRowCount,
        draggedTicket,
        over,
        active.rect.current.translated
      );
      const index = landingRows.findIndex((row) => row.id === activeId);
      const order = resolveMoveOrder(landingRows, index);
      setLanding({ id: activeId, index });

      const settled =
        toContainerId === ALL_BACKLOG_CONTAINER_ID
          ? moveTicketToBacklog(draggedTicket, order).catch((error: unknown) => {
              // moveTicketToBacklog handles its own failures, so this only
              // fires on an unforeseen throw. Never swallow it: report it.
              console.warn("[useAllSectionDnd] move to backlog threw", error);
              toast.error(t("all.dnd.backlogUnavailable"));
            })
          : moveTicketToSprint(draggedTicket, order);
      handOffTo(activeId, toContainerId, settled);
    },
    [
      clearDragHighlight,
      releaseDrag,
      handOffTo,
      resolveContainerId,
      resolveShownContainerId,
      placedLists,
      shownLists,
      findTicket,
      reorderTicket,
      canMove,
      moveTicketToBacklog,
      moveTicketToSprint,
      t,
    ]
  );

  // ── Screen reader support ──────────────────────────────────────────

  /** Human-facing ticket name ("SEN-42"), not the uuid dnd-kit knows it by. */
  const ticketKeyForId = useCallback(
    (id: string): string => {
      return findTicket(id)?.key ?? id;
    },
    [findTicket]
  );

  const sectionLabel = useCallback(
    (containerId: AllSectionContainerId): string =>
      containerId === ALL_SPRINT_CONTAINER_ID
        ? t("all.dnd.sprintSection")
        : t("all.dnd.backlogSection"),
    [t]
  );

  /**
   * dnd-kit's defaults announce the raw droppable id ("droppable area
   * all-section-backlog") and are English-only. These name the ticket and the
   * section in the user's locale instead.
   */
  const announcements = useMemo(
    () => ({
      onDragStart({ active }: { active: { id: string | number } }) {
        return t("all.dnd.announceLift", { ticket: ticketKeyForId(String(active.id)) });
      },
      onDragOver({
        active,
        over,
      }: {
        active: { id: string | number };
        over: { id: string | number } | null;
      }) {
        const containerId = resolveShownContainerId(over ? String(over.id) : null);
        if (!containerId) return undefined;
        return t("all.dnd.announceOver", {
          ticket: ticketKeyForId(String(active.id)),
          section: sectionLabel(containerId),
        });
      },
      onDragEnd({
        active,
        over,
      }: {
        active: { id: string | number };
        over: { id: string | number } | null;
      }) {
        const ticket = ticketKeyForId(String(active.id));
        const containerId = resolveShownContainerId(over ? String(over.id) : null);
        if (!containerId) return t("all.dnd.announceCancelled", { ticket });
        return t("all.dnd.announceDropped", {
          ticket,
          section: sectionLabel(containerId),
        });
      },
      onDragCancel({ active }: { active: { id: string | number } }) {
        return t("all.dnd.announceCancelled", {
          ticket: ticketKeyForId(String(active.id)),
        });
      },
    }),
    [t, ticketKeyForId, resolveShownContainerId, sectionLabel]
  );

  /**
   * The section currently highlighted as a drop target. Only foreign
   * sections light up — hovering the section a row came from means "reorder",
   * which the sortable list already communicates by shifting rows.
   */
  const highlightedContainerId =
    canMove && activeContainerId && overContainerId && overContainerId !== activeContainerId
      ? overContainerId
      : null;

  return {
    sensors,
    collisionDetection: allSectionCollisionDetection,
    handleDragStart,
    handleDragOver,
    handleDragEnd,
    handleDragCancel,
    activeTicket,
    sprintTickets: shownLists.sprint,
    backlogTickets: shownLists.backlog,
    recentlyMovedTicketId,
    highlightedContainerId,
    announcements,
    sectionLabel,
  };
}

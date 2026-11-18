import * as React from "react";
import type { View } from "react-native";
import type {
  GestureStateChangeEvent,
  GestureUpdateEvent,
  PanGestureHandlerEventPayload,
} from "react-native-gesture-handler";
import {
  useReducedMotion,
  useSharedValue,
  withTiming,
  type SharedValue,
} from "react-native-reanimated";

import type { StatusGroup } from "@/components/boards/boardModel";
import { hapticImpact, hapticSelection, measure } from "@/components/boards/dragFeedback";
import {
  contains,
  DRAG_SLOP,
  EDGE_PAGE_MS,
  edgeDirection,
  isStatusNoop,
  slotBefore,
  type Rect,
  type Slot,
  type StatusTarget,
} from "@/components/boards/ticketMoveModel";
import type { BoardMoves, TicketPlace } from "@/components/boards/useBoardMoves";
import type { TicketStatus } from "@/components/kazan/ticketMetaModel";
import type { BoardTicket } from "@/lib/schemas/boards";

/** How far a card shrinks over the drop strip, so the chips stay readable under it. */
const STRIP_SCALE = 0.62;

/** How long a dropped card keeps its highlight. */
const ARRIVED_MS = 1200;

/** The height of a card that has not been laid out yet. */
const FALLBACK_CARD_HEIGHT = 108;

type PanStart = GestureStateChangeEvent<PanGestureHandlerEventPayload>;
type PanUpdate = GestureUpdateEvent<PanGestureHandlerEventPayload>;

/** Where a dragged card would land: a chip of the drop strip or a slot in a column. */
export type DropTarget =
  | { kind: "strip"; status: TicketStatus }
  | { kind: "column"; status: TicketStatus; beforeId: string | null };

/** A lifted card: shown above the board while the finger holds it. */
export interface KanbanDrag {
  ticket: BoardTicket;
  from: TicketStatus;
  width: number;
  height: number;
  /** The card's top-left corner at lift, relative to the board. */
  left: number;
  top: number;
  /** The card has moved past the slop: a drag, not a hold that opens the menu. */
  dragging: boolean;
  target: DropTarget | null;
  /** The edge zone the finger is in: -1 left, 1 right, 0 none. */
  edge: -1 | 0 | 1;
}

/** The gesture callbacks every card shares; stable for the board's life. */
export interface DragHandlers {
  lift: (ticket: BoardTicket, status: TicketStatus, event: PanStart) => void;
  move: (event: PanUpdate) => void;
  drop: (event: PanStart, success: boolean) => void;
  finish: (ticketId: string) => void;
}

type KanbanDragInput = {
  groups: readonly StatusGroup[];
  place: TicketPlace;
  moves: BoardMoves | undefined;
  columnWidth: number;
  /** The width of one page of the strip: a column and the gap after it. */
  pageWidth: number;
  /** The space before the first column. */
  side: number;
  /** Pages the strip to a column. */
  scrollToPage: (page: number) => void;
};

/** The board's layout, read when a card lifts and while it is dragged. */
interface Geometry {
  /** The board's box in window coordinates. */
  root: Rect | null;
  /** The box of the paged column strip in window coordinates. */
  pager: Rect | null;
  pagerX: number;
  /** The window y of the top of each column's ticket list. */
  columnTop: Map<TicketStatus, number>;
  columnScrollY: Map<TicketStatus, number>;
  /** Each card's y and height within its column's ticket list. */
  cards: Map<string, { y: number; height: number }>;
  /** The cards as they stood at lift; the drop slots are read from these. */
  liftCards: Map<string, { y: number; height: number }>;
  strip: Map<TicketStatus, Rect>;
  /** The last finger position, to hit-test again after the strip pages. */
  finger: { x: number; y: number } | null;
}

function sameTarget(a: DropTarget | null, b: DropTarget | null): boolean {
  if (!a || !b) return a === b;
  if (a.kind !== b.kind || a.status !== b.status) return false;
  return a.kind === "strip" || a.beforeId === (b as { beforeId: string | null }).beforeId;
}

/**
 * The drag of a Kanban card: hold to lift it, then drag it onto a column slot
 * or a chip of the drop strip; a hold released without a drag opens the
 * ticket menu. The lifted card follows the finger above the board, the target
 * column makes room for it, and holding it at a side of the board pages the
 * strip to the next column.
 */
export function useKanbanDrag({
  groups,
  place,
  moves,
  columnWidth,
  pageWidth,
  side,
  scrollToPage,
}: KanbanDragInput) {
  const reduceMotion: boolean = useReducedMotion();
  const [drag, setDrag] = React.useState<KanbanDrag | null>(null);
  const [arrivedId, setArrivedId] = React.useState<string | null>(null);
  const translateX: SharedValue<number> = useSharedValue(0);
  const translateY: SharedValue<number> = useSharedValue(0);
  const scale: SharedValue<number> = useSharedValue(1);

  const rootRef = React.useRef<View>(null);
  const pagerRef = React.useRef<View>(null);
  const columnRefs = React.useRef<Map<TicketStatus, View>>(new Map());
  const geometry = React.useRef<Geometry>({
    root: null,
    pager: null,
    pagerX: 0,
    columnTop: new Map(),
    columnScrollY: new Map(),
    cards: new Map(),
    liftCards: new Map(),
    strip: new Map(),
    finger: null,
  });
  const dragRef = React.useRef<KanbanDrag | null>(null);
  const edgeTimer = React.useRef<ReturnType<typeof setInterval> | null>(null);
  const arrivedTimer = React.useRef<ReturnType<typeof setTimeout> | null>(null);

  // The handlers are created once and read the latest board through this ref.
  const latest = React.useRef({ groups, place, moves, columnWidth, pageWidth, side, scrollToPage });
  latest.current = { groups, place, moves, columnWidth, pageWidth, side, scrollToPage };

  const update = React.useCallback((next: KanbanDrag | null) => {
    dragRef.current = next;
    setDrag(next);
  }, []);

  const stopEdge = React.useCallback(() => {
    if (edgeTimer.current) clearInterval(edgeTimer.current);
    edgeTimer.current = null;
  }, []);

  React.useEffect(
    () => () => {
      stopEdge();
      if (arrivedTimer.current) clearTimeout(arrivedTimer.current);
    },
    [stopEdge]
  );

  const hitTest = React.useCallback(
    (x: number, y: number, ticket: BoardTicket): DropTarget | null => {
      const g: Geometry = geometry.current;
      for (const [status, rect] of g.strip) {
        if (contains(rect, x, y)) return { kind: "strip", status };
      }
      if (!g.pager || !contains(g.pager, x, y)) return null;
      const { groups: columns, columnWidth: width, pageWidth: page, side: start } = latest.current;
      const local: number = x - g.pager.x + g.pagerX - start;
      const index: number = Math.min(
        Math.max(Math.round((local - width / 2) / page), 0),
        columns.length - 1
      );
      const column: StatusGroup | undefined = columns[index];
      if (!column) return null;
      const top: number | undefined = g.columnTop.get(column.status);
      if (top === undefined) return { kind: "column", status: column.status, beforeId: null };
      const contentY: number = y - top + (g.columnScrollY.get(column.status) ?? 0);
      const slots: Slot[] = column.tickets.flatMap((row) => {
        const box = g.liftCards.get(row.id) ?? g.cards.get(row.id);
        return box ? [{ id: row.id, middle: box.y + box.height / 2 }] : [];
      });
      return {
        kind: "column",
        status: column.status,
        beforeId: slotBefore(slots, contentY, ticket.id),
      };
    },
    []
  );

  const retarget = React.useCallback(
    (x: number, y: number) => {
      const current: KanbanDrag | null = dragRef.current;
      if (!current?.dragging) return;
      const target: DropTarget | null = hitTest(x, y, current.ticket);
      const root: Rect | null = geometry.current.root;
      const pager: Rect | null = geometry.current.pager;
      const edge: -1 | 0 | 1 =
        root && target?.kind !== "strip" && pager && contains(pager, x, y)
          ? edgeDirection(x - root.x, root.width)
          : 0;
      if (target?.kind === "strip" && current.target?.kind !== "strip") hapticSelection();
      scale.value = withTiming(target?.kind === "strip" ? STRIP_SCALE : 1, {
        duration: reduceMotion ? 0 : 150,
      });
      if (sameTarget(target, current.target) && edge === current.edge) return;
      update({ ...current, target, edge });
    },
    [hitTest, reduceMotion, scale, update]
  );

  const pageBy = React.useCallback((direction: -1 | 1) => {
    const g: Geometry = geometry.current;
    const { groups: columns, pageWidth: page, scrollToPage: scrollTo } = latest.current;
    const current: number = Math.round(g.pagerX / page);
    const next: number = Math.min(Math.max(current + direction, 0), columns.length - 1);
    if (next !== current) scrollTo(next);
  }, []);

  const followEdge = React.useCallback(
    (edge: -1 | 0 | 1) => {
      if (edge === 0) {
        stopEdge();
        return;
      }
      if (edgeTimer.current) return;
      pageBy(edge);
      edgeTimer.current = setInterval(() => {
        const current: KanbanDrag | null = dragRef.current;
        if (!current || current.edge === 0) {
          stopEdge();
          return;
        }
        pageBy(current.edge);
      }, EDGE_PAGE_MS);
    },
    [pageBy, stopEdge]
  );

  const handlers = React.useMemo<DragHandlers>(
    () => ({
      lift: (ticket, status, event) => {
        if (dragRef.current) return;
        const g: Geometry = geometry.current;
        hapticImpact();
        g.liftCards = new Map(g.cards);
        g.strip = new Map();
        g.finger = { x: event.absoluteX, y: event.absoluteY };
        translateX.value = 0;
        translateY.value = 0;
        scale.value = 1;
        const root: Rect | null = g.root;
        update({
          ticket,
          from: status,
          width: latest.current.columnWidth - 16,
          height: g.cards.get(ticket.id)?.height ?? FALLBACK_CARD_HEIGHT,
          left: event.absoluteX - event.x - (root?.x ?? 0),
          top: event.absoluteY - event.y - (root?.y ?? 0),
          dragging: false,
          target: null,
          edge: 0,
        });
        // The pager and the column lists may have moved since they were laid
        // out (the board scrolls under the header), so read them again.
        void measure(pagerRef.current).then((rect) => {
          g.pager = rect;
        });
        for (const [key, view] of columnRefs.current) {
          void measure(view).then((rect) => {
            if (rect) g.columnTop.set(key, rect.y);
          });
        }
      },
      move: (event) => {
        const current: KanbanDrag | null = dragRef.current;
        if (!current) return;
        translateX.value = event.translationX;
        translateY.value = event.translationY;
        geometry.current.finger = { x: event.absoluteX, y: event.absoluteY };
        if (!current.dragging) {
          if (Math.hypot(event.translationX, event.translationY) < DRAG_SLOP) return;
          update({ ...current, dragging: true });
        }
        retarget(event.absoluteX, event.absoluteY);
        followEdge(dragRef.current?.edge ?? 0);
      },
      drop: (event, success) => {
        const current: KanbanDrag | null = dragRef.current;
        const { moves: boardMoves, place: where } = latest.current;
        if (!current || !success || !boardMoves) return;
        if (!current.dragging) {
          boardMoves.openMenu(current.ticket, where);
          return;
        }
        const target: DropTarget | null = hitTest(event.absoluteX, event.absoluteY, current.ticket);
        if (!target) return;
        const statusTarget: StatusTarget = {
          status: target.status,
          beforeId: target.kind === "column" ? target.beforeId : null,
        };
        if (isStatusNoop(where.rows, current.ticket, statusTarget)) return;
        boardMoves.moveStatus(current.ticket, where, statusTarget);
        setArrivedId(current.ticket.id);
        if (arrivedTimer.current) clearTimeout(arrivedTimer.current);
        arrivedTimer.current = setTimeout(() => setArrivedId(null), ARRIVED_MS);
      },
      finish: (ticketId) => {
        if (dragRef.current?.ticket.id !== ticketId) return;
        stopEdge();
        geometry.current.finger = null;
        scale.value = 1;
        update(null);
      },
    }),
    [followEdge, hitTest, retarget, scale, stopEdge, translateX, translateY, update]
  );

  /** Keeps the board's own box current; the lifted card is placed against it. */
  const onRootLayout = React.useCallback(() => {
    void measure(rootRef.current).then((rect) => {
      geometry.current.root = rect;
    });
  }, []);

  const onPagerScroll = React.useCallback(
    (x: number) => {
      geometry.current.pagerX = x;
      const finger = geometry.current.finger;
      if (finger) retarget(finger.x, finger.y);
    },
    [retarget]
  );

  const onColumnScroll = React.useCallback((status: TicketStatus, y: number) => {
    geometry.current.columnScrollY.set(status, y);
  }, []);

  const onCardLayout = React.useCallback((ticketId: string, y: number, height: number) => {
    geometry.current.cards.set(ticketId, { y, height });
  }, []);

  const onStripMeasure = React.useCallback((status: TicketStatus, rect: Rect) => {
    geometry.current.strip.set(status, rect);
  }, []);

  const setColumnRef = React.useCallback((status: TicketStatus, view: View | null) => {
    if (view) columnRefs.current.set(status, view);
    else columnRefs.current.delete(status);
  }, []);

  /** True when the drop would put the card back where it was; no slot is drawn then. */
  const targetIsNoop: boolean =
    !!drag?.target &&
    drag.target.kind === "column" &&
    isStatusNoop(place.rows, drag.ticket, {
      status: drag.target.status,
      beforeId: drag.target.beforeId,
    });

  return {
    drag,
    targetIsNoop,
    arrivedId,
    handlers,
    translateX,
    translateY,
    scale,
    rootRef,
    pagerRef,
    setColumnRef,
    onRootLayout,
    onPagerScroll,
    onColumnScroll,
    onCardLayout,
    onStripMeasure,
  };
}

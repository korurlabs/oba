import * as React from "react";
import {
  Platform,
  useWindowDimensions,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
  type SectionList,
  type View,
} from "react-native";
import type {
  GestureStateChangeEvent,
  GestureUpdateEvent,
  PanGestureHandlerEventPayload,
} from "react-native-gesture-handler";
import { useSharedValue, type SharedValue } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { hapticImpact, hapticSelection, measure } from "@/components/boards/dragFeedback";
import {
  edgeScrollOffset,
  groupAt,
  LIST_EDGE_ZONE,
  LIST_SCROLL_MS,
  listBottomInset,
  listDropAction,
  type GroupTop,
  type ListDropAction,
  type ListDropGroup,
} from "@/components/boards/listDragModel";
import {
  contains,
  DRAG_SLOP,
  edgeDirection,
  slotBefore,
  type Rect,
  type Slot,
} from "@/components/boards/ticketMoveModel";
import type { BoardMoves } from "@/components/boards/useBoardMoves";
import type { BoardTicket } from "@/lib/schemas/boards";

/** How long a dropped row keeps its highlight. */
const ARRIVED_MS = 1200;

/** The height of a row that has not been laid out yet. */
const FALLBACK_ROW_HEIGHT = 96;

/** The gap between two rows of a board list. */
const ROW_GAP = 8;

type PanStart = GestureStateChangeEvent<PanGestureHandlerEventPayload>;
type PanUpdate = GestureUpdateEvent<PanGestureHandlerEventPayload>;

/** Where a dragged row would land, and what a drop there does; a null action changes nothing. */
export interface ListDropTarget {
  groupKey: string;
  beforeId: string | null;
  action: ListDropAction | null;
}

/** A lifted row: shown above the list while the finger holds it. */
export interface ListDrag {
  ticket: BoardTicket;
  fromKey: string;
  width: number;
  height: number;
  /** The row's top-left corner at lift, relative to the list. */
  left: number;
  top: number;
  /** The row has moved past the slop: a drag, not a hold that opens the menu. */
  dragging: boolean;
  target: ListDropTarget | null;
  /** The outline of the group a drop would plan the ticket into. */
  outline: GroupOutline | null;
}

/** The outline of a group that a drop would move the ticket into, in list content coordinates. */
export interface GroupOutline {
  top: number;
  height: number;
}

/** The gesture callbacks every row shares; stable for the list's life. */
export interface ListDragHandlers {
  lift: (ticket: BoardTicket, groupKey: string, event: PanStart) => void;
  move: (event: PanUpdate) => void;
  drop: (event: PanStart, success: boolean) => void;
  finish: (ticketId: string) => void;
  setRowRef: (ticketId: string, view: View | null) => void;
  onRowLayout: (ticketId: string, width: number, height: number) => void;
}

type ListDragInput = {
  groups: readonly ListDropGroup[];
  moves: BoardMoves;
  boardId: string;
};

/** The list's layout, read when a row lifts and while it is dragged. */
interface Geometry {
  /** The list's box in window coordinates. */
  list: Rect | null;
  scrollY: number;
  contentHeight: number;
  /** Each row's box in the list content, as it stood at lift. */
  rows: Map<string, { top: number; height: number }>;
  /** Each group header's box in the list content, as it stood at lift. */
  headers: Map<string, { top: number; height: number }>;
  /** The last finger position, to hit-test again after the list scrolls. */
  finger: { x: number; y: number } | null;
}

function sameTarget(a: ListDropTarget | null, b: ListDropTarget | null): boolean {
  if (!a || !b) return a === b;
  return (
    a.groupKey === b.groupKey && a.beforeId === b.beforeId && a.action?.kind === b.action?.kind
  );
}

/**
 * The drag of a row in a board list grouped into sections: hold to lift it,
 * then drag it to another position in its group, to another status group of
 * the sprint, or into the other sprint or backlog group; a hold released
 * without a drag opens the ticket menu. The lifted row follows the finger
 * above the list, the target group makes room for it, and holding it at the
 * top or bottom of the list scrolls the list.
 *
 * The rows and headers are measured once at lift and the drop slots are read
 * from that layout, so the room a target makes does not move the target.
 */
export function useListDrag<SectionT>({ groups, moves, boardId }: ListDragInput) {
  const [drag, setDrag] = React.useState<ListDrag | null>(null);
  const [arrivedId, setArrivedId] = React.useState<string | null>(null);
  const translateX: SharedValue<number> = useSharedValue(0);
  const translateY: SharedValue<number> = useSharedValue(0);
  const scrollOffset: SharedValue<number> = useSharedValue(0);
  const windowHeight: number = useWindowDimensions().height;
  const safeAreaBottom: number = useSafeAreaInsets().bottom;

  const listRef = React.useRef<SectionList<BoardTicket, SectionT>>(null);
  const rootRef = React.useRef<View>(null);
  const rowRefs = React.useRef<Map<string, View>>(new Map());
  const headerRefs = React.useRef<Map<string, View>>(new Map());
  const rowSizes = React.useRef<Map<string, { width: number; height: number }>>(new Map());
  const geometry = React.useRef<Geometry>({
    list: null,
    scrollY: 0,
    contentHeight: 0,
    rows: new Map(),
    headers: new Map(),
    finger: null,
  });
  const dragRef = React.useRef<ListDrag | null>(null);
  const edge = React.useRef<-1 | 0 | 1>(0);
  const edgeTimer = React.useRef<ReturnType<typeof setInterval> | null>(null);
  const arrivedTimer = React.useRef<ReturnType<typeof setTimeout> | null>(null);

  // The handlers are created once and read the latest list through this ref.
  const latest = React.useRef({ groups, moves, boardId });
  latest.current = { groups, moves, boardId };

  const update = React.useCallback((next: ListDrag | null) => {
    dragRef.current = next;
    setDrag(next);
  }, []);

  const stopEdge = React.useCallback(() => {
    if (edgeTimer.current) clearInterval(edgeTimer.current);
    edgeTimer.current = null;
    edge.current = 0;
  }, []);

  React.useEffect(
    () => () => {
      stopEdge();
      if (arrivedTimer.current) clearTimeout(arrivedTimer.current);
    },
    [stopEdge]
  );

  /** Reads the list, its rows and its headers into list content coordinates. */
  const measureLayout = React.useCallback(async () => {
    const g: Geometry = geometry.current;
    const scrollY: number = g.scrollY;
    const list: Rect | null = await measure(rootRef.current);
    if (!list) return;
    g.list = list;
    const read = async (
      views: Map<string, View>
    ): Promise<Map<string, { top: number; height: number }>> => {
      const entries = await Promise.all(
        [...views].map(async ([key, view]) => [key, await measure(view)] as const)
      );
      const boxes = new Map<string, { top: number; height: number }>();
      for (const [key, rect] of entries) {
        if (rect) boxes.set(key, { top: rect.y - list.y + scrollY, height: rect.height });
      }
      return boxes;
    };
    const [rows, headers] = await Promise.all([read(rowRefs.current), read(headerRefs.current)]);
    g.rows = rows;
    g.headers = headers;
  }, []);

  const hitTest = React.useCallback(
    (x: number, y: number, current: ListDrag): ListDropTarget | null => {
      const g: Geometry = geometry.current;
      if (!g.list || !contains(g.list, x, y)) return null;
      const { groups: all, boardId: viewBoardId } = latest.current;
      const contentY: number = y - g.list.y + g.scrollY;
      const tops: GroupTop[] = all.flatMap((group) => {
        const top: number | undefined =
          g.headers.get(group.key)?.top ??
          group.rows.map((row) => g.rows.get(row.id)?.top).find((value) => value !== undefined);
        return top === undefined ? [] : [{ key: group.key, top }];
      });
      const key: string | null = groupAt(tops, contentY);
      const to: ListDropGroup | undefined = all.find((group) => group.key === key);
      const from: ListDropGroup | undefined = all.find((group) => group.key === current.fromKey);
      if (!to || !from) return null;
      const slots: Slot[] = to.rows.flatMap((row) => {
        const box = g.rows.get(row.id);
        return box ? [{ id: row.id, middle: box.top + box.height / 2 }] : [];
      });
      const beforeId: string | null = slotBefore(slots, contentY, current.ticket.id);
      return {
        groupKey: to.key,
        beforeId,
        action: listDropAction(current.ticket, from, to, beforeId, viewBoardId),
      };
    },
    []
  );

  /**
   * The box of a group from its header to its last row, with room for the
   * dropped row; null while the header has not been measured.
   */
  const outlineOf = React.useCallback((groupKey: string, rowHeight: number) => {
    const g: Geometry = geometry.current;
    const header = g.headers.get(groupKey);
    if (!header) return null;
    const group: ListDropGroup | undefined = latest.current.groups.find(
      (item) => item.key === groupKey
    );
    const bottoms: number[] = (group?.rows ?? []).flatMap((row) => {
      const box = g.rows.get(row.id);
      return box ? [box.top + box.height] : [];
    });
    const end: number = Math.max(header.top + header.height, ...bottoms);
    return { top: header.top, height: end - header.top + rowHeight + ROW_GAP };
  }, []);

  const retarget = React.useCallback(
    (x: number, y: number) => {
      const current: ListDrag | null = dragRef.current;
      if (!current?.dragging) return;
      const target: ListDropTarget | null = hitTest(x, y, current);
      if (sameTarget(target, current.target)) return;
      const placing: boolean = target?.action?.kind === "place";
      if (placing && target?.groupKey !== current.target?.groupKey) hapticSelection();
      update({
        ...current,
        target,
        outline: placing && target ? outlineOf(target.groupKey, current.height) : null,
      });
    },
    [hitTest, outlineOf, update]
  );

  const scrollStep = React.useCallback(() => {
    const g: Geometry = geometry.current;
    if (!g.list || edge.current === 0) {
      stopEdge();
      return;
    }
    // iOS insets the list by the part of it under the tab bar and the home
    // indicator; Android has no such inset, its lists pad their content instead.
    const bottomInset: number =
      Platform.OS === "ios" ? listBottomInset(g.list, windowHeight - safeAreaBottom) : 0;
    const next: number = edgeScrollOffset(g.scrollY, edge.current, {
      contentHeight: g.contentHeight,
      viewportHeight: g.list.height,
      bottomInset,
    });
    if (next === g.scrollY) return;
    g.scrollY = next;
    scrollOffset.value = next;
    listRef.current?.getScrollResponder()?.scrollTo({ y: next, animated: false });
    if (g.finger) retarget(g.finger.x, g.finger.y);
  }, [retarget, safeAreaBottom, scrollOffset, stopEdge, windowHeight]);

  const followEdge = React.useCallback(
    (y: number) => {
      const list: Rect | null = geometry.current.list;
      const direction: -1 | 0 | 1 = list
        ? edgeDirection(y - list.y, list.height, LIST_EDGE_ZONE)
        : 0;
      if (direction === 0) {
        stopEdge();
        return;
      }
      edge.current = direction;
      if (!edgeTimer.current) edgeTimer.current = setInterval(scrollStep, LIST_SCROLL_MS);
    },
    [scrollStep, stopEdge]
  );

  const handlers = React.useMemo<ListDragHandlers>(
    () => ({
      lift: (ticket, groupKey, event) => {
        if (dragRef.current) return;
        const g: Geometry = geometry.current;
        hapticImpact();
        g.finger = { x: event.absoluteX, y: event.absoluteY };
        translateX.value = 0;
        translateY.value = 0;
        const size = rowSizes.current.get(ticket.id);
        update({
          ticket,
          fromKey: groupKey,
          width: size?.width ?? (g.list?.width ?? 0) - 32,
          height: size?.height ?? FALLBACK_ROW_HEIGHT,
          left: event.absoluteX - event.x - (g.list?.x ?? 0),
          top: event.absoluteY - event.y - (g.list?.y ?? 0),
          dragging: false,
          target: null,
          outline: null,
        });
        measureLayout().catch((error: unknown) => {
          console.warn(
            "[boards] list layout read failed",
            error instanceof Error ? error.message : error
          );
        });
      },
      move: (event) => {
        const current: ListDrag | null = dragRef.current;
        if (!current) return;
        translateX.value = event.translationX;
        translateY.value = event.translationY;
        geometry.current.finger = { x: event.absoluteX, y: event.absoluteY };
        if (!current.dragging) {
          if (Math.hypot(event.translationX, event.translationY) < DRAG_SLOP) return;
          update({ ...current, dragging: true });
        }
        retarget(event.absoluteX, event.absoluteY);
        followEdge(event.absoluteY);
      },
      drop: (event, success) => {
        const current: ListDrag | null = dragRef.current;
        const { groups: all, moves: boardMoves } = latest.current;
        const from: ListDropGroup | undefined = all.find((group) => group.key === current?.fromKey);
        if (!current || !success || !from) return;
        if (!current.dragging) {
          boardMoves.openMenu(current.ticket, from.place);
          return;
        }
        const target: ListDropTarget | null = hitTest(event.absoluteX, event.absoluteY, current);
        const to: ListDropGroup | undefined = all.find((group) => group.key === target?.groupKey);
        const action: ListDropAction | null | undefined = target?.action;
        if (!to || !action) return;
        if (action.kind === "status") {
          boardMoves.moveStatus(current.ticket, from.place, action.target);
        } else if (action.kind === "reorder") {
          boardMoves.reorder(current.ticket, to.place, to.rows, action.beforeId);
        } else {
          boardMoves.place(current.ticket, from.place, action.scope, to.rows, action.beforeId);
        }
        setArrivedId(current.ticket.id);
        if (arrivedTimer.current) clearTimeout(arrivedTimer.current);
        arrivedTimer.current = setTimeout(() => setArrivedId(null), ARRIVED_MS);
      },
      finish: (ticketId) => {
        if (dragRef.current?.ticket.id !== ticketId) return;
        stopEdge();
        geometry.current.finger = null;
        update(null);
      },
      setRowRef: (ticketId, view) => {
        if (view) rowRefs.current.set(ticketId, view);
        else rowRefs.current.delete(ticketId);
      },
      onRowLayout: (ticketId, width, height) => {
        rowSizes.current.set(ticketId, { width, height });
      },
    }),
    [followEdge, hitTest, measureLayout, retarget, stopEdge, translateX, translateY, update]
  );

  const setHeaderRef = React.useCallback((key: string, view: View | null) => {
    if (view) headerRefs.current.set(key, view);
    else headerRefs.current.delete(key);
  }, []);

  /** Keeps the list's own box current; the lifted row is placed against it. */
  const onRootLayout = React.useCallback(() => {
    measure(rootRef.current)
      .then((rect) => {
        geometry.current.list = rect;
      })
      .catch((error: unknown) => {
        console.warn(
          "[boards] list layout read failed",
          error instanceof Error ? error.message : error
        );
      });
  }, []);

  const onScroll = React.useCallback(
    (event: NativeSyntheticEvent<NativeScrollEvent>) => {
      const y: number = event.nativeEvent.contentOffset.y;
      geometry.current.scrollY = y;
      scrollOffset.value = y;
      const finger = geometry.current.finger;
      if (finger) retarget(finger.x, finger.y);
    },
    [retarget, scrollOffset]
  );

  const onContentSizeChange = React.useCallback((_width: number, height: number) => {
    geometry.current.contentHeight = height;
  }, []);

  const target: ListDropTarget | null = drag?.dragging && drag.target?.action ? drag.target : null;

  /** True when the drop slot sits before this row of this group. */
  const slotBeforeRow = (groupKey: string, ticketId: string): boolean =>
    !!target && target.groupKey === groupKey && target.beforeId === ticketId;

  /** True when the drop slot sits at the end of this group. */
  const slotAtEnd = (groupKey: string): boolean =>
    !!target && target.groupKey === groupKey && target.beforeId === null;

  /** The group a drop would plan the ticket into, which reads "drop to move here". */
  const placeKey: string | null = target?.action?.kind === "place" ? target.groupKey : null;

  return {
    drag,
    arrivedId,
    handlers,
    translateX,
    translateY,
    scrollOffset,
    listRef,
    rootRef,
    onRootLayout,
    setHeaderRef,
    onScroll,
    onContentSizeChange,
    slotBeforeRow,
    slotAtEnd,
    placeKey,
  };
}

"use client";

import { useState, useCallback, useEffect, useRef, useMemo, useSyncExternalStore, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { useParams } from "next/navigation";
import { useTranslations } from "next-intl";
import {
  DndContext,
  DragOverlay,
  MouseSensor,
  TouchSensor,
  closestCenter,
  useDroppable,
  useSensor,
  useSensors,
  type ClientRect,
  type Collision,
  type CollisionDetection,
  type DragEndEvent,
  type DragMoveEvent,
  type DragStartEvent,
  type DroppableContainer,
  type UniqueIdentifier,
} from "@dnd-kit/core";
import { hasSortableData } from "@dnd-kit/sortable";
import { Skeleton } from "@/components/ui/skeleton";
import { useBoards, useMoveBoard } from "@/features/boards/hooks/use-boards";
import { useManageableBoardIds } from "@/features/boards/hooks/use-board-role";
import { CreateBoardDialog } from "@/features/boards/components/create-board-dialog";
import {
  collectSubtreeIds,
  findAncestorIds,
  findBoardInTree,
  indexBoardTree,
  resolveTreeDrop,
  type TreeDropIntent,
  type TreeDropRow,
} from "@/features/boards/lib/board-tree";
import { SidebarBoardTreeItem, TREE_ROW_ATTRIBUTE } from "./sidebar-board-tree-item";
import { SidebarBoardBranch, type BranchDropPreview } from "./sidebar-board-branch";
import { SidebarBoardDropGhost, TREE_GHOST_ATTRIBUTE } from "./sidebar-board-drop-ghost";
import type { SortableBoardData } from "./sidebar-sortable-board-item";
import { useSidebarContext } from "./sidebar-context";
import { useSidebarBoardPreferences, type PinnedBoardItem } from "@/hooks/use-sidebar-board-preferences";
import { useSidebarExpandedBoards } from "@/hooks/use-sidebar-expanded-boards";
import { cn } from "@/lib/utils";
import { Pin, Plus } from "lucide-react";
import type { BoardListWithChildrenResponse, BoardListResponse } from "@/features/boards/schemas";
import { Tooltip, TooltipContent, TooltipTrigger } from "@oba/ui/components/tooltip";

// ── Types ────────────────────────────────────────────────────────────

/** The row being dragged, drawn again in the drag overlay. */
interface ActiveDrag extends SortableBoardData {
  board: BoardListWithChildrenResponse;
}

/** The board being dragged in the tree section and the list it comes from. */
interface ActiveTreeDrag {
  board: BoardListWithChildrenResponse;
  /** Parent of the list the board shows in; `null` for the top level. */
  parentId: string | null;
  /** Position of the board in that displayed list. */
  index: number;
  /** The board and its descendants, where it can never be dropped. */
  subtreeIds: Set<string>;
}

/** What the pointer is over in the tree section while a board is dragged. */
type TreeHit =
  | {
      kind: "row";
      id: string;
      parentId: string | null;
      index: number;
      top: number;
      height: number;
      /** The row has children in the tree, open or not. */
      hasChildren: boolean;
      pointerY: number;
    }
  | { kind: "ghost" }
  | { kind: "root" };

/** Sortable list ids of the two top-level lists; a nested list uses its parent board id. */
const PINNED_CONTAINER_ID = "pinned";
const ROOT_CONTAINER_ID = "root";

/** Mouse movement that turns a press on a row into a drag; anything shorter is a click. */
const SIDEBAR_DRAG_ACTIVATION_DISTANCE_PX = 8;
/** Touch hold that turns a press on a row into a drag, so a quick swipe still scrolls the list. */
const SIDEBAR_TOUCH_DRAG_DELAY_MS = 250;
/** Finger movement allowed during the touch hold before it counts as a scroll instead. */
const SIDEBAR_TOUCH_DRAG_TOLERANCE_PX = 5;
/** Rest on a closed row with children before it opens under a dragged board. */
const SIDEBAR_DRAG_EXPAND_DELAY_MS = 600;
/**
 * Vertical probes around the pointer, so the 2px gap between two rows still
 * finds the nearer row instead of dropping the indicator for a frame.
 */
const TREE_HIT_PROBE_OFFSETS_PX = [0, -3, 3];

/** Droppable id of the "Move to top level" strip. */
const TREE_ROOT_DROP_ID = "tree-root-drop";
const TREE_ROOT_STRIP_ATTRIBUTE = "data-tree-root-strip";
const TREE_HIT_SELECTOR = `[${TREE_ROW_ATTRIBUTE}],[${TREE_GHOST_ATTRIBUTE}],[${TREE_ROOT_STRIP_ATTRIBUTE}]`;

// ── Helpers ──────────────────────────────────────────────────────────

/** Convert PinnedBoardItem to BoardListWithChildrenResponse shape for SidebarBoardTreeItem */
function pinnedItemToBoardShape(
  item: PinnedBoardItem,
  allBoards: BoardListWithChildrenResponse[],
): BoardListWithChildrenResponse {
  const realBoard = findBoardInTree(allBoards, item.id);
  return {
    id: item.id,
    createdAt: "",
    name: item.name,
    slug: item.slug,
    prefix: item.prefix,
    color: item.color ?? undefined,
    icon: item.icon ?? undefined,
    childBoards: realBoard?.childBoards ?? null,
  };
}

interface VisibleRow {
  key: string;
  board: BoardListWithChildrenResponse;
}

/**
 * The rows a section shows in its current open state, in display order. A
 * board can show twice in one section (a pinned child under its pinned
 * parent), so each key is the path of ids from the section's top.
 */
function flattenVisibleRows(
  boards: BoardListWithChildrenResponse[],
  getChildren: (board: BoardListWithChildrenResponse) => BoardListWithChildrenResponse[],
  isExpanded: (id: string) => boolean,
  keyPrefix: string,
): VisibleRow[] {
  const rows: VisibleRow[] = [];
  for (const board of boards) {
    const key = `${keyPrefix}/${board.id}`;
    rows.push({ key, board });
    if (isExpanded(board.id)) {
      rows.push(...flattenVisibleRows(getChildren(board), getChildren, isExpanded, key));
    }
  }
  return rows;
}

/**
 * The sortable list under a vertical position: of the lists whose rows span
 * that position, the shortest one. Each row's rect holds its open children,
 * so a nested list always lies inside its parent's list and is shorter.
 * Returns `null` when the position is above or below every list.
 */
function innermostListAt(
  y: number,
  containers: DroppableContainer[],
  rects: Map<UniqueIdentifier, ClientRect>,
): UniqueIdentifier | null {
  const bounds = new Map<UniqueIdentifier, { top: number; bottom: number }>();
  for (const container of containers) {
    const rect = rects.get(container.id);
    if (!rect || !hasSortableData(container)) continue;
    const listId = container.data.current.sortable.containerId;
    const current = bounds.get(listId);
    bounds.set(listId, {
      top: Math.min(current?.top ?? rect.top, rect.top),
      bottom: Math.max(current?.bottom ?? rect.bottom, rect.bottom),
    });
  }

  let innermost: UniqueIdentifier | null = null;
  let innermostHeight = Number.POSITIVE_INFINITY;
  for (const [listId, { top, bottom }] of bounds) {
    if (y < top || y > bottom || bottom - top >= innermostHeight) continue;
    innermost = listId;
    innermostHeight = bottom - top;
  }
  return innermost;
}

/**
 * Collision detection of the pinned section: it only sees rows of the dragged
 * row's own list, and only while the pointer is over that list, so a drag
 * there never shifts or targets rows of another level. The pinned list shows
 * boards pinned from anywhere in the hierarchy, so a drop there only reorders.
 */
const sameListCollision: CollisionDetection = (args) => {
  const { active, collisionRect, droppableContainers, droppableRects, pointerCoordinates } = args;
  if (!hasSortableData(active)) return closestCenter(args);
  const containerId = active.data.current.sortable.containerId;
  const y = pointerCoordinates?.y ?? collisionRect.top + collisionRect.height / 2;
  if (innermostListAt(y, droppableContainers, droppableRects) !== containerId) return [];
  return closestCenter({
    ...args,
    droppableContainers: droppableContainers.filter(
      (container) =>
        hasSortableData(container) && container.data.current.sortable.containerId === containerId,
    ),
  });
};

/** The list both rows of a drop belong to, or `null` when the drop crosses lists. */
function sharedContainerId(event: DragEndEvent): string | null {
  const { active, over } = event;
  if (!over || active.id === over.id) return null;
  if (!hasSortableData(active) || !hasSortableData(over)) return null;
  const containerId = active.data.current.sortable.containerId;
  if (containerId !== over.data.current.sortable.containerId) return null;
  return String(containerId);
}

/** The drop target element of the tree section at a viewport position, if any. */
function treeTargetAt(container: HTMLElement, x: number, y: number): Element | null {
  for (const element of document.elementsFromPoint(x, y)) {
    if (!container.contains(element)) continue;
    const target = element.closest(TREE_HIT_SELECTOR);
    if (target && container.contains(target)) return target;
  }
  return null;
}

/**
 * Collision detection of the tree section: the row, landing preview or
 * top-level strip under the pointer, measured live. Rects cached at drag
 * start go stale here, because the landing preview and a row that opens
 * under the pointer push the rows below it down during the drag.
 *
 * The hit is probed at the horizontal middle of the tree, so the indent left
 * of a nested row still counts as that row. A pointer outside the tree
 * section hits nothing, and a drop there changes nothing.
 */
function createTreeRowCollision(getContainer: () => HTMLElement | null): CollisionDetection {
  return ({ pointerCoordinates, droppableContainers }) => {
    const container = getContainer();
    if (!pointerCoordinates || !container) return [];
    const bounds = container.getBoundingClientRect();
    if (pointerCoordinates.x < bounds.left || pointerCoordinates.x > bounds.right) return [];
    const probeX = bounds.left + bounds.width / 2;

    for (const offset of TREE_HIT_PROBE_OFFSETS_PX) {
      const target = treeTargetAt(container, probeX, pointerCoordinates.y + offset);
      if (!target) continue;
      const collision = toTreeCollision(target, pointerCoordinates.y, droppableContainers);
      if (collision) return [collision];
    }
    return [];
  };
}

function toTreeCollision(
  target: Element,
  pointerY: number,
  droppableContainers: DroppableContainer[],
): Collision | null {
  if (target.hasAttribute(TREE_ROOT_STRIP_ATTRIBUTE)) {
    const hit: TreeHit = { kind: "root" };
    return { id: TREE_ROOT_DROP_ID, data: { hit } };
  }
  const ghostTargetId = target.getAttribute(TREE_GHOST_ATTRIBUTE);
  if (ghostTargetId) {
    const hit: TreeHit = { kind: "ghost" };
    return { id: ghostTargetId, data: { hit } };
  }

  const id = target.getAttribute(TREE_ROW_ATTRIBUTE);
  const droppable = droppableContainers.find((container) => container.id === id);
  if (!id || !droppable || !hasSortableData(droppable)) return null;
  const { containerId, index } = droppable.data.current.sortable;
  const custom = droppable.data.current as Partial<SortableBoardData>;
  const rect = target.getBoundingClientRect();
  const hit: TreeHit = {
    kind: "row",
    id,
    parentId: containerId === ROOT_CONTAINER_ID ? null : String(containerId),
    index,
    top: rect.top,
    height: rect.height,
    hasChildren: custom.hasVisibleChildren ?? false,
    pointerY,
  };
  return { id, data: { hit } };
}

function readTreeHit(event: DragMoveEvent): TreeHit | null {
  return (event.collisions?.[0]?.data?.hit as TreeHit | undefined) ?? null;
}

function sameIntent(a: TreeDropIntent | null, b: TreeDropIntent | null): boolean {
  if (a === null || b === null) return a === b;
  return (
    a.kind === b.kind &&
    a.parentId === b.parentId &&
    a.index === b.index &&
    a.overId === b.overId &&
    a.zone === b.zone
  );
}

/**
 * Drop target below the tree that makes the dragged board a top-level board.
 * It only exists during a drag, and dragging needs a pointer, so it is hidden
 * from assistive tech; the Child Boards tab of board settings offers the same
 * change without dragging.
 */
function TreeRootDropStrip({ isTarget }: { isTarget: boolean }) {
  const t = useTranslations("sidebar");
  const { setNodeRef } = useDroppable({ id: TREE_ROOT_DROP_ID });

  return (
    <div
      ref={setNodeRef}
      aria-hidden="true"
      {...{ [TREE_ROOT_STRIP_ATTRIBUTE]: "" }}
      className={cn(
        "text-muted-foreground mx-1 mt-1 rounded-md border border-dashed px-3 py-2 text-center text-xs transition-colors",
        isTarget && "border-primary text-primary bg-primary/5",
      )}
    >
      {t("moveToTopLevel")}
    </div>
  );
}

function readDragData(event: DragStartEvent): SortableBoardData {
  const data = event.active.data.current as Partial<SortableBoardData> | undefined;
  return { depth: data?.depth ?? 0, hasVisibleChildren: data?.hasVisibleChildren ?? false };
}

// ── Main Component ──────────────────────────────────────────────────

export function SidebarBoardTree() {
  const t = useTranslations("sidebar");
  const { state } = useSidebarContext();
  const collapsed = state === "collapsed";

  // `isPending`, not `isLoading`: the query waits, disabled, until Kazan is
  // known to be open, and the tree shows its skeleton during that wait
  // instead of "no boards".
  const { data: boardsResponse, isPending } = useBoards({
    includeChildren: true,
    amount: 100,
  });
  const boards = useMemo(() => boardsResponse?.data ?? [], [boardsResponse?.data]);

  // Preferences hook for ordering and pinning
  const {
    allPinnedBoards,
    unpinnedRootBoards,
    reorderPinnedBoards,
    placeBoard,
    getUnpinnedChildren,
    togglePin,
    toggleChildPin,
    isChildPinned,
  } = useSidebarBoardPreferences(boards);

  const expanded = useSidebarExpandedBoards(boards);
  const { expandMany, isExpanded } = expanded;
  const boardIndex = useMemo(() => indexBoardTree(boards), [boards]);

  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const params = useParams();
  const activeBoardSlug = params.boardSlug as string | undefined;
  // The route names the open board by slug; the tree state is keyed by id.
  const activeBoardId = useMemo(() => {
    if (!activeBoardSlug) return undefined;
    for (const board of boardIndex.values()) {
      if (board.slug === activeBoardSlug) return board.id;
    }
    return undefined;
  }, [activeBoardSlug, boardIndex]);

  const [activePinnedDrag, setActivePinnedDrag] = useState<ActiveDrag | null>(null);
  const [activeTreeDrag, setActiveTreeDrag] = useState<ActiveTreeDrag | null>(null);
  const [dropIntent, setDropIntent] = useState<TreeDropIntent | null>(null);
  // Read by the drop handler, which may run before the last move's state has rendered.
  const activeTreeDragRef = useRef<ActiveTreeDrag | null>(null);
  const dropIntentRef = useRef<TreeDropIntent | null>(null);

  const moveBoard = useMoveBoard();
  const movePending = moveBoard.isPending;

  const treeContainerRef = useRef<HTMLDivElement>(null);
  const treeRowCollision = useMemo(
    () => createTreeRowCollision(() => treeContainerRef.current),
    [],
  );

  const sensors = useSensors(
    useSensor(MouseSensor, {
      activationConstraint: { distance: SIDEBAR_DRAG_ACTIVATION_DISTANCE_PX },
    }),
    useSensor(TouchSensor, {
      activationConstraint: {
        delay: SIDEBAR_TOUCH_DRAG_DELAY_MS,
        tolerance: SIDEBAR_TOUCH_DRAG_TOLERANCE_PX,
      },
    }),
  );

  // Open the ancestors of the active board once per change of active board.
  // Running on every board refetch would reopen a branch the user just closed.
  const expandedForBoardRef = useRef<string | null>(null);
  useEffect(() => {
    if (!activeBoardId) {
      expandedForBoardRef.current = null;
      return;
    }
    if (expandedForBoardRef.current === activeBoardId || !boardIndex.has(activeBoardId)) return;
    expandedForBoardRef.current = activeBoardId;
    expandMany(findAncestorIds(boards, activeBoardId));
  }, [activeBoardId, boards, boardIndex, expandMany]);

  // ── Section rows ─────────────────────────────────────────────────
  const pinnedBoards = useMemo(
    () => allPinnedBoards.map((item) => pinnedItemToBoardShape(item, boards)),
    [allPinnedBoards, boards],
  );

  // The tree leaves out pinned children, because they already show in the pinned list.
  const getTreeChildren = useCallback(
    (board: BoardListWithChildrenResponse): BoardListWithChildrenResponse[] =>
      getUnpinnedChildren(
        board.id,
        (board.childBoards ?? []) as BoardListResponse[],
      ) as BoardListWithChildrenResponse[],
    [getUnpinnedChildren],
  );

  // Rights are read only for the rows the tree shows: those are the only drop
  // targets, and each row already loads its board's members for its menu.
  const visibleTreeIds = useMemo(
    () =>
      flattenVisibleRows(unpinnedRootBoards, getTreeChildren, isExpanded, ROOT_CONTAINER_ID).map(
        (row) => row.board.id,
      ),
    [unpinnedRootBoards, getTreeChildren, isExpanded],
  );
  const manageableIds = useManageableBoardIds(visibleTreeIds);

  // Under a pinned board every child shows, pinned or not, so the pinned board keeps its whole subtree.
  const getPinnedChildren = useCallback(
    (board: BoardListWithChildrenResponse): BoardListWithChildrenResponse[] =>
      (board.childBoards ?? []) as BoardListWithChildrenResponse[],
    [],
  );

  // ── Pin handlers ─────────────────────────────────────────────────
  const handleTogglePinForPinnedItem = useCallback(
    (boardId: string) => {
      const item = allPinnedBoards.find((b) => b.id === boardId);
      if (!item) return;
      if (item.parentBoardId) {
        toggleChildPin(item.parentBoardId, boardId);
      } else {
        togglePin(boardId);
      }
    },
    [allPinnedBoards, togglePin, toggleChildPin],
  );

  const handlePinnedSectionTogglePin = useCallback(
    (board: BoardListWithChildrenResponse, parentId: string | null) => {
      if (parentId === null) {
        handleTogglePinForPinnedItem(board.id);
      } else {
        toggleChildPin(parentId, board.id);
      }
    },
    [handleTogglePinForPinnedItem, toggleChildPin],
  );

  const isPinnedInPinnedSection = useCallback(
    (board: BoardListWithChildrenResponse, parentId: string | null) =>
      parentId === null || isChildPinned(parentId, board.id),
    [isChildPinned],
  );

  const handleTreeTogglePin = useCallback(
    (board: BoardListWithChildrenResponse, parentId: string | null) => {
      if (parentId === null) {
        togglePin(board.id);
      } else {
        toggleChildPin(parentId, board.id);
      }
    },
    [togglePin, toggleChildPin],
  );

  // ── Pinned section DnD handlers ──────────────────────────────────
  const handlePinnedDragStart = useCallback(
    (event: DragStartEvent) => {
      const board = pinnedBoards.find((b) => b.id === event.active.id);
      if (board) setActivePinnedDrag({ board, ...readDragData(event) });
    },
    [pinnedBoards],
  );

  const handlePinnedDragEnd = useCallback(
    (event: DragEndEvent) => {
      setActivePinnedDrag(null);
      if (sharedContainerId(event) !== PINNED_CONTAINER_ID) return;
      reorderPinnedBoards(String(event.active.id), String(event.over?.id));
    },
    [reorderPinnedBoards],
  );

  // ── Tree section DnD handlers ────────────────────────────────────
  const updateDropIntent = useCallback((intent: TreeDropIntent | null) => {
    if (sameIntent(dropIntentRef.current, intent)) return;
    dropIntentRef.current = intent;
    setDropIntent(intent);
  }, []);

  const endTreeDrag = useCallback(() => {
    activeTreeDragRef.current = null;
    setActiveTreeDrag(null);
    updateDropIntent(null);
  }, [updateDropIntent]);

  const handleTreeDragStart = useCallback(
    (event: DragStartEvent) => {
      const board = boardIndex.get(String(event.active.id));
      if (!board || !hasSortableData(event.active)) return;
      const { containerId, index } = event.active.data.current.sortable;
      const drag: ActiveTreeDrag = {
        board,
        parentId: containerId === ROOT_CONTAINER_ID ? null : String(containerId),
        index,
        subtreeIds: collectSubtreeIds(board),
      };
      activeTreeDragRef.current = drag;
      setActiveTreeDrag(drag);
    },
    [boardIndex],
  );

  const handleTreeDragMove = useCallback(
    (event: DragMoveEvent) => {
      const drag = activeTreeDragRef.current;
      if (!drag) return;
      const hit = readTreeHit(event);
      // The landing preview only shows for a drop inside the row above it,
      // so a pointer over it keeps that drop.
      if (hit?.kind === "ghost") return;

      let over: TreeDropRow | "root" | null = null;
      let pointerY = 0;
      if (hit?.kind === "root") {
        over = "root";
      } else if (hit?.kind === "row") {
        over = {
          id: hit.id,
          parentId: hit.parentId,
          index: hit.index,
          top: hit.top,
          height: hit.height,
          hasVisibleChildren: hit.hasChildren && isExpanded(hit.id),
        };
        pointerY = hit.pointerY;
      }

      updateDropIntent(
        resolveTreeDrop({
          activeId: drag.board.id,
          activeParentId: drag.parentId,
          activeIndex: drag.index,
          over,
          pointerY,
          subtreeIds: drag.subtreeIds,
          manageableIds,
          rootListLength: unpinnedRootBoards.length,
          movePending,
        }),
      );
    },
    [isExpanded, manageableIds, movePending, unpinnedRootBoards.length, updateDropIntent],
  );

  const handleTreeDragEnd = useCallback(() => {
    const drag = activeTreeDragRef.current;
    const intent = dropIntentRef.current;
    endTreeDrag();
    if (!drag || !intent) return;

    const boardId = drag.board.id;
    const applyOrder = () => placeBoard(boardId, drag.parentId, intent.parentId, intent.index);

    // A drop in the board's own list is a personal order, saved without a request.
    if (intent.kind === "reorder") {
      applyOrder();
      return;
    }

    // The intent may predate a move that started since; one move runs at a time.
    if (movePending) return;
    const newParent = intent.parentId === null ? null : boardIndex.get(intent.parentId);
    if (newParent === undefined) return;
    moveBoard.mutate({
      board: { id: boardId, name: drag.board.name },
      newParent: newParent ? { id: newParent.id, name: newParent.name } : null,
      index: intent.index,
      applyOrder,
    });
    if (newParent) expandMany([newParent.id]);
  }, [boardIndex, endTreeDrag, expandMany, moveBoard, movePending, placeBoard]);

  const handlePinnedDragCancel = useCallback(() => setActivePinnedDrag(null), []);

  // A closed row with children opens when a dragged board rests inside it,
  // so the board can be dropped among those children.
  const expandTargetId = dropIntent?.zone === "inside" ? dropIntent.overId : null;
  useEffect(() => {
    if (!expandTargetId || isExpanded(expandTargetId)) return;
    const target = boardIndex.get(expandTargetId);
    if (!target || getTreeChildren(target).length === 0) return;
    const timer = setTimeout(() => expandMany([expandTargetId]), SIDEBAR_DRAG_EXPAND_DELAY_MS);
    return () => clearTimeout(timer);
  }, [expandTargetId, boardIndex, getTreeChildren, isExpanded, expandMany]);

  const treeDropPreview = useMemo<BranchDropPreview | null>(
    () => (activeTreeDrag ? { intent: dropIntent, board: activeTreeDrag.board } : null),
    [activeTreeDrag, dropIntent],
  );
  const showRootDropStrip =
    activeTreeDrag !== null &&
    activeTreeDrag.parentId !== null &&
    manageableIds.has(activeTreeDrag.board.id) &&
    !movePending;

  if (isPending) {
    return (
      <div className={cn("space-y-2 px-3 py-2", collapsed && "px-1")}>
        <Skeleton className="h-7 w-full rounded-md" />
        <Skeleton className="h-7 w-full rounded-md" />
        <Skeleton className="h-7 w-full rounded-md" />
      </div>
    );
  }

  if (boards.length === 0) {
    return (
      <div className="px-4 py-6 text-center">
        {!collapsed && <p className="text-muted-foreground text-sm">{t("noBoards")}</p>}
        <button
          type="button"
          onClick={() => setCreateDialogOpen(true)}
          className="text-primary hover:text-primary/80 mt-2 cursor-pointer text-sm font-medium transition-colors"
        >
          {!collapsed ? (
            t("createFirstBoard")
          ) : (
            <Tooltip>
              <TooltipTrigger asChild>
                <div className="hover:bg-muted rounded-md p-1">
                  <Plus className="h-4 w-4" />
                </div>
              </TooltipTrigger>
              <TooltipContent>{t("createFirstBoard")}</TooltipContent>
            </Tooltip>
          )}
        </button>
        <CreateBoardDialog open={createDialogOpen} onOpenChange={setCreateDialogOpen} />
      </div>
    );
  }

  // Collapsed icon rail: the rows the open branches show, flat, as dots
  if (collapsed) {
    const rows = [
      ...flattenVisibleRows(pinnedBoards, getPinnedChildren, isExpanded, PINNED_CONTAINER_ID),
      ...flattenVisibleRows(unpinnedRootBoards, getTreeChildren, isExpanded, ROOT_CONTAINER_ID),
    ];
    return (
      <div className="space-y-0.5 px-1" role="tree" aria-label={t("boards")}>
        {rows.map((row) => (
          <SidebarBoardTreeItem key={row.key} board={row.board} />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-0.5" role="tree" aria-label={t("boards")}>
      {/* ── Pinned Section ── */}
      {pinnedBoards.length > 0 && (
        <>
          <div className="text-muted-foreground px-3 py-1 text-xs font-medium">
            {t("pinned")}
          </div>

          <DndContext
            sensors={sensors}
            collisionDetection={sameListCollision}
            onDragStart={handlePinnedDragStart}
            onDragEnd={handlePinnedDragEnd}
            onDragCancel={handlePinnedDragCancel}
          >
            <div className="px-1">
              <SidebarBoardBranch
                containerId={PINNED_CONTAINER_ID}
                parentId={null}
                boards={pinnedBoards}
                depth={0}
                sortable
                section="pinned"
                expanded={expanded}
                getChildren={getPinnedChildren}
                isPinned={isPinnedInPinnedSection}
                onTogglePin={handlePinnedSectionTogglePin}
              />
            </div>
            <SidebarDragOverlay>
              {activePinnedDrag ? (
                <div className="bg-background rounded-md border shadow-md">
                  <SidebarBoardTreeItem
                    board={activePinnedDrag.board}
                    depth={activePinnedDrag.depth}
                    hasVisibleChildren={activePinnedDrag.hasVisibleChildren}
                    isExpanded={isExpanded(activePinnedDrag.board.id)}
                    isPinned
                  />
                </div>
              ) : null}
            </SidebarDragOverlay>
          </DndContext>

          {/* Pin separator */}
          <div className="flex items-center gap-2 px-3 py-1">
            <div className="bg-border h-px flex-1" />
            <Pin className="text-muted-foreground/50 h-2.5 w-2.5" />
            <div className="bg-border h-px flex-1" />
          </div>
        </>
      )}

      {/* ── Tree Section ── */}
      <DndContext
        sensors={sensors}
        collisionDetection={treeRowCollision}
        onDragStart={handleTreeDragStart}
        onDragMove={handleTreeDragMove}
        onDragEnd={handleTreeDragEnd}
        onDragCancel={endTreeDrag}
      >
        <div ref={treeContainerRef} className="px-1">
          <SidebarBoardBranch
            containerId={ROOT_CONTAINER_ID}
            parentId={null}
            boards={unpinnedRootBoards}
            depth={0}
            sortable
            section="tree"
            expanded={expanded}
            getChildren={getTreeChildren}
            onTogglePin={handleTreeTogglePin}
            dropPreview={treeDropPreview}
          />

          {unpinnedRootBoards.length === 0 && (
            <p className="text-muted-foreground px-3 py-4 text-center text-xs">{t("noBoards")}</p>
          )}

          {showRootDropStrip && <TreeRootDropStrip isTarget={dropIntent?.zone === "root"} />}
        </div>

        <SidebarDragOverlay>
          {activeTreeDrag ? (
            <SidebarBoardDropGhost board={activeTreeDrag.board} variant="overlay" />
          ) : null}
        </SidebarDragOverlay>
      </DndContext>
    </div>
  );
}

const subscribeToNothing = () => () => {};

/**
 * The row that follows the pointer while a board is dragged. The sky panel's
 * backdrop filter makes the panel the containing block of fixed elements, so
 * an overlay drawn inside it would sit offset from the pointer by the panel's
 * own position; it is drawn on the document body instead. The wrapper takes
 * the sidebar's slot so the row keeps the panel's colour tokens. The portal
 * waits for the client, since the server has no document body.
 */
function SidebarDragOverlay({ children }: { children: ReactNode }) {
  const isClient = useSyncExternalStore(
    subscribeToNothing,
    () => true,
    () => false,
  );
  if (!isClient) return null;
  return createPortal(
    <DragOverlay>
      {children ? (
        <div data-slot="sidebar" className="text-sidebar-foreground">
          {children}
        </div>
      ) : null}
    </DragOverlay>,
    document.body,
  );
}

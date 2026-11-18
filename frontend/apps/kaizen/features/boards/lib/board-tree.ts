import type { BoardListWithChildrenResponse } from "@/features/boards/schemas";

/**
 * Helpers for the board hierarchy returned by the board list with
 * `includeChildren=true`. The server nests `childBoards` to any depth, while
 * the Zod type describes only one level, so each helper reads the children
 * as full tree nodes.
 */

function childrenOf(board: BoardListWithChildrenResponse): BoardListWithChildrenResponse[] {
  return (board.childBoards ?? []) as BoardListWithChildrenResponse[];
}

/** Finds a board anywhere in the tree by id, or `null` when the tree does not hold it. */
export function findBoardInTree(
  items: BoardListWithChildrenResponse[],
  id: string,
): BoardListWithChildrenResponse | null {
  for (const board of items) {
    if (board.id === id) return board;
    const match = findBoardInTree(childrenOf(board), id);
    if (match) return match;
  }
  return null;
}

/** Maps the id of every board in the tree, at any depth, to its node. */
export function indexBoardTree(
  items: BoardListWithChildrenResponse[],
): Map<string, BoardListWithChildrenResponse> {
  const index = new Map<string, BoardListWithChildrenResponse>();
  const visit = (boards: BoardListWithChildrenResponse[]) => {
    for (const board of boards) {
      index.set(board.id, board);
      visit(childrenOf(board));
    }
  };
  visit(items);
  return index;
}

/**
 * Ids of the ancestors of a board, root first, excluding the board itself.
 * Empty for a root board and for an id the tree does not hold.
 */
export function findAncestorIds(items: BoardListWithChildrenResponse[], id: string): string[] {
  return findPath(items, id) ?? [];
}

/** Ancestor ids of the board, root first, or `null` when the subtree does not hold it. */
function findPath(items: BoardListWithChildrenResponse[], id: string): string[] | null {
  for (const board of items) {
    if (board.id === id) return [];
    const below = findPath(childrenOf(board), id);
    if (below) return [board.id, ...below];
  }
  return null;
}

/**
 * Id of the board's parent, or `null` when the board sits at the top level.
 * Also `null` for an id the tree does not hold; check with `findBoardInTree`
 * where the difference matters.
 */
export function findParentId(items: BoardListWithChildrenResponse[], id: string): string | null {
  return findPath(items, id)?.at(-1) ?? null;
}

/** Ids of the board and of every board below it, at any depth. */
export function collectSubtreeIds(board: BoardListWithChildrenResponse): Set<string> {
  const ids = new Set<string>();
  const visit = (node: BoardListWithChildrenResponse) => {
    ids.add(node.id);
    for (const child of childrenOf(node)) visit(child);
  };
  visit(board);
  return ids;
}

/**
 * The tree with board `id` and its subtree moved under `newParentId`, or to
 * the top level when that is `null`, at `index` of the new list (clamped to
 * its length). `index` orders the cached list only; the sidebar shows each
 * list in the user's saved order.
 *
 * Returns `items` unchanged when the board or the new parent is not in the
 * tree, or when the new parent lies inside the moved subtree, so a refused
 * move can never drop a board from the cache.
 */
export function moveBoardInTree(
  items: BoardListWithChildrenResponse[],
  id: string,
  newParentId: string | null,
  index: number,
): BoardListWithChildrenResponse[] {
  const board = findBoardInTree(items, id);
  if (!board) return items;
  if (newParentId !== null) {
    if (collectSubtreeIds(board).has(newParentId) || !findBoardInTree(items, newParentId)) {
      return items;
    }
  }

  const without = removeFromTree(items, id);
  if (newParentId === null) return insertAt(without, board, index);
  return insertUnder(without, newParentId, board, index);
}

function removeFromTree(
  items: BoardListWithChildrenResponse[],
  id: string,
): BoardListWithChildrenResponse[] {
  return items
    .filter((node) => node.id !== id)
    .map((node) => {
      const children = childrenOf(node);
      if (children.length === 0) return node;
      return { ...node, childBoards: removeFromTree(children, id) };
    });
}

function insertUnder(
  items: BoardListWithChildrenResponse[],
  parentId: string,
  board: BoardListWithChildrenResponse,
  index: number,
): BoardListWithChildrenResponse[] {
  return items.map((node) => {
    if (node.id === parentId) {
      return { ...node, childBoards: insertAt(childrenOf(node), board, index) };
    }
    const children = childrenOf(node);
    if (children.length === 0) return node;
    return { ...node, childBoards: insertUnder(children, parentId, board, index) };
  });
}

function insertAt<T>(list: T[], item: T, index: number): T[] {
  const at = Math.max(0, Math.min(index, list.length));
  return [...list.slice(0, at), item, ...list.slice(at)];
}

// ── Drop rules for dragging boards in the sidebar tree ──────────────

/**
 * Where a drop lands relative to the row under the pointer: above it or below
 * it as a sibling, inside it as its first child, or on the top-level strip.
 */
export type TreeDropZone = "before" | "inside" | "after" | "root";

/** A visible row of the sidebar tree that the pointer is over. */
export interface TreeDropRow {
  id: string;
  /** Parent of the list the row belongs to; `null` for the top-level list. */
  parentId: string | null;
  /** Position of the row in its displayed list. */
  index: number;
  /** Top edge of the row in viewport pixels. */
  top: number;
  height: number;
  /** The row is open and shows its children below it. */
  hasVisibleChildren: boolean;
}

export interface TreeDropInput {
  activeId: string;
  /** Parent of the dragged board's list; `null` for the top-level list. */
  activeParentId: string | null;
  /** Position of the dragged board in its displayed list. */
  activeIndex: number;
  /** The row under the pointer, `"root"` for the top-level strip, `null` for neither. */
  over: TreeDropRow | "root" | null;
  /** Pointer position in viewport pixels. */
  pointerY: number;
  /** The dragged board and its descendants (`collectSubtreeIds`). */
  subtreeIds: ReadonlySet<string>;
  /** Boards the user is OWNER or ADMIN of. */
  manageableIds: ReadonlySet<string>;
  /** Number of rows in the displayed top-level list, where a strip drop appends. */
  rootListLength: number;
  /** A move request is still running; no drop is offered until it settles. */
  movePending: boolean;
}

/**
 * The outcome of a drop. `reorder` keeps the parent and only changes the
 * user's saved order; `move` changes the parent on the server.
 *
 * `index` is the position the board takes in the list of `parentId` once it
 * has left its old list, so it can be passed to `placeBoard` as it is.
 * `overId` is the row under the pointer, `null` on the top-level strip.
 */
export type TreeDropIntent = {
  kind: "reorder" | "move";
  parentId: string | null;
  index: number;
  overId: string | null;
  zone: TreeDropZone;
};

/** Share of the row height, from each edge, that places a drop beside the row. */
const SIBLING_EDGE_RATIO = 0.25;

/**
 * Resolves where a dragged board would land, or `null` when the drop is
 * refused or would change nothing.
 *
 * A drop on the dragged board or inside its own subtree is refused, since
 * the board cannot become its own descendant. A change of parent needs
 * OWNER or ADMIN on the dragged board and on the new parent (the top level
 * needs none), the same rights the server checks; a reorder within the
 * board's own list is a personal preference open to every member.
 */
export function resolveTreeDrop(input: TreeDropInput): TreeDropIntent | null {
  const { activeId, activeParentId, activeIndex, over, manageableIds } = input;
  if (input.movePending || over === null) return null;
  const canMoveActive = manageableIds.has(activeId);

  if (over === "root") {
    if (activeParentId === null || !canMoveActive) return null;
    return { kind: "move", parentId: null, index: input.rootListLength, overId: null, zone: "root" };
  }

  if (input.subtreeIds.has(over.id)) return null;

  const zone = zoneAt(over, input.pointerY);
  const parentId = zone === "inside" ? over.id : over.parentId;
  const position = zone === "inside" ? 0 : zone === "before" ? over.index : over.index + 1;

  if (parentId === activeParentId) {
    // The board leaves its slot before it is inserted again, so a slot
    // below its old one moves up by one.
    const index = position > activeIndex ? position - 1 : position;
    if (index === activeIndex) return null;
    return { kind: "reorder", parentId, index, overId: over.id, zone };
  }

  if (!canMoveActive) return null;
  if (parentId !== null && !manageableIds.has(parentId)) return null;
  return { kind: "move", parentId, index: position, overId: over.id, zone };
}

/**
 * The top and bottom quarter of a row place the drop beside it, the middle
 * half inside it. On an open row with visible children the bottom quarter
 * also means inside, because a line there would sit above its first child.
 */
function zoneAt(row: TreeDropRow, pointerY: number): Exclude<TreeDropZone, "root"> {
  const offset = row.height > 0 ? (pointerY - row.top) / row.height : 0.5;
  if (offset < SIBLING_EDGE_RATIO) return "before";
  if (offset > 1 - SIBLING_EDGE_RATIO) return row.hasVisibleChildren ? "inside" : "after";
  return "inside";
}

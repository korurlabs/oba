"use client";

import type { ReactNode } from "react";
import {
  SortableContext,
  verticalListSortingStrategy,
  type SortingStrategy,
} from "@dnd-kit/sortable";
import { cn } from "@/lib/utils";
import { SidebarBoardTreeItem, type SidebarDropIndicator } from "./sidebar-board-tree-item";
import { SortableBoardItem } from "./sidebar-sortable-board-item";
import { SidebarBoardDropGhost } from "./sidebar-board-drop-ghost";
import type { useSidebarExpandedBoards } from "@/hooks/use-sidebar-expanded-boards";
import type { BoardListWithChildrenResponse } from "@/features/boards/schemas";
import type { TreeDropIntent } from "@/features/boards/lib/board-tree";

/** Deeper levels keep this indent, so a deep branch never pushes names out of a narrow sidebar. */
const MAX_INDENT_DEPTH = 6;

/**
 * Tree rows stay where they are while a board is dragged: a drop can land
 * beside a row or inside it, so shifting rows like a flat list would show a
 * slot that is not where the board lands. The drop line and the landing
 * preview are the only feedback.
 */
const keepRowsInPlace: SortingStrategy = () => null;

/** The drag in progress in the tree section, for the drop line and the landing preview. */
export interface BranchDropPreview {
  /** Where the dragged board would land; `null` while no drop is offered. */
  intent: TreeDropIntent | null;
  /** The dragged board, drawn as the landing preview. */
  board: BoardListWithChildrenResponse;
}

function indentClass(depth: number) {
  return depth > 0 && depth <= MAX_INDENT_DEPTH ? "ml-3" : undefined;
}

interface SidebarBoardBranchProps {
  /** Sortable list id: the parent board id, `"root"` or `"pinned"`. */
  containerId: string;
  /** The board that owns this list, or `null` for a top-level list. */
  parentId: string | null;
  boards: BoardListWithChildrenResponse[];
  depth: number;
  sortable: boolean;
  section: "pinned" | "tree";
  expanded: ReturnType<typeof useSidebarExpandedBoards>;
  getChildren: (board: BoardListWithChildrenResponse) => BoardListWithChildrenResponse[];
  isPinned?: (board: BoardListWithChildrenResponse, parentId: string | null) => boolean;
  onTogglePin?: (board: BoardListWithChildrenResponse, parentId: string | null) => void;
  /** Tree section only: the drag in progress, or `null` when none. */
  dropPreview?: BranchDropPreview | null;
  /** Rendered before the first row, for the landing preview of a drop inside this list's parent. */
  leadingItem?: ReactNode;
}

/**
 * One list of sibling boards, with the open children of each row nested
 * inside that row. Each sibling list is its own sortable list. In the pinned
 * section a drag only reorders boards that share a parent; in the tree a row
 * can also be dropped beside or inside a row of another list, which the tree
 * resolves and this list only draws. Below the top of the pinned section
 * rows are not sortable: a pinned child can show there and in the pinned list
 * at once, and a board id may be registered only once for dragging.
 */
export function SidebarBoardBranch({
  containerId,
  parentId,
  boards,
  depth,
  sortable,
  section,
  expanded,
  getChildren,
  isPinned,
  onTogglePin,
  dropPreview,
  leadingItem,
}: SidebarBoardBranchProps) {
  const isTreeSortable = sortable && section === "tree";
  const intent = dropPreview?.intent ?? null;

  const rows = boards.map((board) => {
    const children = getChildren(board);
    const hasVisibleChildren = children.length > 0;
    const isExpanded = expanded.isExpanded(board.id);
    const dropIndicator: SidebarDropIndicator | undefined =
      isTreeSortable && intent?.overId === board.id && intent.zone !== "root"
        ? intent.zone
        : undefined;
    // The preview sits directly under the row it would become the first child of.
    const ghost =
      dropIndicator === "inside" && dropPreview ? (
        <SidebarBoardDropGhost board={dropPreview.board} variant="preview" targetId={board.id} />
      ) : null;
    const nested =
      hasVisibleChildren && isExpanded ? (
        <SidebarBoardBranch
          containerId={board.id}
          parentId={board.id}
          boards={children}
          depth={depth + 1}
          sortable={section === "tree"}
          section={section}
          expanded={expanded}
          getChildren={getChildren}
          isPinned={isPinned}
          onTogglePin={onTogglePin}
          dropPreview={dropPreview}
          leadingItem={ghost}
        />
      ) : ghost ? (
        <div className={cn("mt-0.5", indentClass(depth + 1))}>{ghost}</div>
      ) : null;
    const rowProps = {
      board,
      depth,
      isExpanded,
      hasVisibleChildren,
      onToggleExpand: expanded.toggle,
      onExpand: (id: string) => expanded.expandMany([id]),
      isPinned: isPinned?.(board, parentId) ?? false,
      onTogglePin: onTogglePin ? () => onTogglePin(board, parentId) : undefined,
    };

    return sortable ? (
      <SortableBoardItem
        key={board.id}
        {...rowProps}
        isDropTarget={isTreeSortable}
        dropIndicator={dropIndicator}
      >
        {nested}
      </SortableBoardItem>
    ) : (
      <SidebarBoardTreeItem key={board.id} {...rowProps}>
        {nested}
      </SidebarBoardTreeItem>
    );
  });

  // A nested list is inset from its parent's row as a whole, so each child's
  // box (hover and active background, drag grip, drag rect) starts at its own
  // level instead of spanning the full sidebar width like a top-level row.
  const list = (
    <div
      role={depth > 0 ? "group" : undefined}
      className={cn(
        "space-y-0.5",
        depth > 0 && "mt-0.5",
        indentClass(depth),
      )}
    >
      {leadingItem}
      {rows}
    </div>
  );

  if (!sortable) return list;

  return (
    <SortableContext
      id={containerId}
      items={boards.map((board) => board.id)}
      strategy={section === "tree" ? keepRowsInPlace : verticalListSortingStrategy}
    >
      {list}
    </SortableContext>
  );
}

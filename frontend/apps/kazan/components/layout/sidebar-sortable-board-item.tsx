"use client";

import type { ReactNode } from "react";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { cn } from "@/lib/utils";
import { SidebarBoardTreeItem, type SidebarDropIndicator } from "./sidebar-board-tree-item";
import type { BoardListWithChildrenResponse } from "@/features/boards/schemas";

/** Extra data on each sortable row, read back by the drag overlay. */
export interface SortableBoardData {
  depth: number;
  hasVisibleChildren: boolean;
}

interface SortableBoardItemProps {
  board: BoardListWithChildrenResponse;
  depth?: number;
  isExpanded?: boolean;
  hasVisibleChildren?: boolean;
  onToggleExpand?: (id: string) => void;
  onExpand?: (id: string) => void;
  isPinned?: boolean;
  onTogglePin?: (boardId: string) => void;
  /** Tree rows only: lets a dragged board be dropped on this row. */
  isDropTarget?: boolean;
  dropIndicator?: SidebarDropIndicator;
  children?: ReactNode;
}

export function SortableBoardItem({
  board,
  depth = 0,
  isExpanded,
  hasVisibleChildren = false,
  onToggleExpand,
  onExpand,
  isPinned,
  onTogglePin,
  isDropTarget,
  dropIndicator,
  children,
}: SortableBoardItemProps) {
  const data: SortableBoardData = { depth, hasVisibleChildren };
  // The sortable attributes are not spread: they would make the row a role="button" tab stop
  // inside a treeitem, while there is no keyboard sensor to act on it.
  const {
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: board.id, data });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  // The node holds the row and its open children, so a dragged parent moves with its subtree.
  // The drag starts only from the row itself, so a press on a child row never drags this one.
  return (
    <div
      ref={setNodeRef}
      style={style}
      className={cn("relative", isDragging && "z-50 opacity-50")}
    >
      <SidebarBoardTreeItem
        board={board}
        depth={depth}
        isExpanded={isExpanded}
        hasVisibleChildren={hasVisibleChildren}
        onToggleExpand={onToggleExpand}
        onExpand={onExpand}
        isPinned={isPinned}
        onTogglePin={onTogglePin}
        dragListeners={listeners}
        dragActivatorRef={setActivatorNodeRef}
        isDropTarget={isDropTarget}
        dropIndicator={dropIndicator}
      >
        {children}
      </SidebarBoardTreeItem>
    </div>
  );
}

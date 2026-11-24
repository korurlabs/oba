"use client";

import { SquareKanban } from "lucide-react";
import { cn } from "@/lib/utils";
import type { BoardListWithChildrenResponse } from "@/features/boards/schemas";

/** Marks the landing preview, so a pointer over it still counts as a drop inside its target row. */
export const TREE_GHOST_ATTRIBUTE = "data-tree-ghost";

interface SidebarBoardDropGhostProps {
  board: BoardListWithChildrenResponse;
  /**
   * `preview` is the muted row shown where the board lands, under the row it
   * would become a child of; `overlay` is the row that follows the pointer.
   */
  variant: "preview" | "overlay";
  /** Preview only: the row the board lands in. */
  targetId?: string;
}

/**
 * A board row without controls, drawn while a board is dragged in the
 * sidebar tree. It is hidden from assistive tech, since it only mirrors the
 * dragged row and takes no input.
 */
export function SidebarBoardDropGhost({ board, variant, targetId }: SidebarBoardDropGhostProps) {
  const isPreview = variant === "preview";

  return (
    <div
      aria-hidden="true"
      {...(isPreview && targetId ? { [TREE_GHOST_ATTRIBUTE]: targetId } : {})}
      className={cn(
        // The left padding is one pixel short of a row's pl-2, giving that pixel to the 1px border
        "mx-1 flex min-h-9 items-center gap-1 rounded-md pr-1 pl-[7px] text-sm select-none",
        isPreview
          ? "text-muted-foreground border border-dashed opacity-60"
          : "bg-background text-foreground border shadow-(--kazan-grid-shadow)",
      )}
    >
      {/* Same slot and gap as a row's icon, so the icon and name line up with real rows */}
      <span className="flex w-4 shrink-0 items-center justify-center">
        <SquareKanban className="text-muted-foreground h-3.5 w-3.5 shrink-0" />
      </span>
      <span className="min-w-0 flex-1 truncate">{board.name}</span>
    </div>
  );
}

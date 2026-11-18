"use client";

import { useRef, useState, type ReactNode, type SyntheticEvent } from "react";
import type { DraggableSyntheticListeners } from "@dnd-kit/core";
import { useParams } from "next/navigation";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { useTranslations } from "next-intl";
import { ChevronRight, FolderPlus, MoreHorizontal, Pin, PinOff, Settings } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { CreateBoardDialog } from "@/features/boards/components/create-board-dialog";
import { BoardSettingsDialog } from "@/features/boards/components/board-settings-dialog";
import { DEFAULT_BOARD_COLOR } from "@/features/boards/lib/board-colors";
import { useSidebarContext } from "./sidebar-context";
import { cn } from "@/lib/utils";
import type { BoardListWithChildrenResponse } from "@/features/boards/schemas";
import { BoardDot, boardDotAppearance } from "../ui/board-dot";
import { useBoardHref } from "@/hooks/use-board-preferences";
import { useBoardRole } from "@/features/boards/hooks/use-board-role";

/** Marks a tree row as a drop target; the value is the board id. */
export const TREE_ROW_ATTRIBUTE = "data-tree-row";

/** Where a dragged board would land relative to this row. */
export type SidebarDropIndicator = "before" | "after" | "inside";

interface SidebarBoardTreeItemProps {
  board: BoardListWithChildrenResponse;
  /** Nesting level, 0 for a top-level row; the indent itself comes from the enclosing branch. */
  depth?: number;
  isExpanded?: boolean;
  /** Whether the row has children to show in this section; only then is its dot an expand control. */
  hasVisibleChildren?: boolean;
  /** Dot click on a board with children: opens or closes them without navigating. */
  onToggleExpand?: (id: string) => void;
  /** Row click on a board with children: opens them, never closes them. */
  onExpand?: (id: string) => void;
  /** Sortable rows only: the sensor listeners that start a drag on a press on the row. */
  dragListeners?: DraggableSyntheticListeners;
  /** Sortable rows only: marks the row as the element the drag starts from. */
  dragActivatorRef?: (element: HTMLElement | null) => void;
  /** Tree rows only: lets a dragged board be dropped on this row. */
  isDropTarget?: boolean;
  /** `before` and `after` draw a line on that edge, `inside` highlights the row as the new parent. */
  dropIndicator?: SidebarDropIndicator;
  isPinned?: boolean;
  onTogglePin?: (boardId: string) => void;
  /** The nested children, rendered inside this tree item so assistive tech reads the hierarchy. */
  children?: ReactNode;
}

// The row is the drag activator, so a press on a control inside it (or in the menu it opens,
// whose events bubble back through the React tree) must not reach the drag sensors.
const stopDragStart = (e: SyntheticEvent) => e.stopPropagation();

export function SidebarBoardTreeItem({
  board,
  depth = 0,
  isExpanded = false,
  hasVisibleChildren = false,
  onToggleExpand,
  onExpand,
  dragListeners,
  dragActivatorRef,
  isDropTarget = false,
  dropIndicator,
  isPinned,
  onTogglePin,
  children,
}: SidebarBoardTreeItemProps) {
  const t = useTranslations("sidebar");
  const params = useParams();
  const { closeMobile, state } = useSidebarContext();
  const collapsed = state === "collapsed";
  const { canManage } = useBoardRole(board.id);

  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const settingsTriggerRef = useRef<HTMLButtonElement>(null);

  const getBoardHref = useBoardHref();
  const isActive = params.boardSlug === board.slug;
  const showChevron = hasVisibleChildren;
  const boardHref = getBoardHref(board);

  const handleBoardClick = () => {
    closeMobile();
    if (hasVisibleChildren) onExpand?.(board.id);
  };

  const dotAppearance = boardDotAppearance(board.color);

  // The dot, or the pin of a pinned board, sits in a slot of the same width on
  // every row, so names line up whether or not the row can expand.
  const marker = isPinned ? (
    <Pin className="text-muted-foreground h-3.5 w-3.5 shrink-0" />
  ) : (
    <span
      className={cn("h-2.5 w-2.5 shrink-0 rounded-full", dotAppearance.className)}
      style={dotAppearance.style}
    />
  );

  // Collapsed rail mode: only show color dot with tooltip
  if (collapsed) {
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <Link
            href={boardHref}
            onClick={closeMobile}
            className={cn(
              "flex items-center justify-center rounded py-2",
              isActive && "bg-primary/10"
            )}
          >
            <BoardDot color={board.color} />
          </Link>
        </TooltipTrigger>
        <TooltipContent side="right">
          ({board.prefix}) {board.name}
        </TooltipContent>
      </Tooltip>
    );
  }

  return (
    <div
      role="treeitem"
      aria-selected={isActive}
      aria-level={depth + 1}
      aria-expanded={showChevron ? isExpanded : undefined}
    >
      <div
        ref={dragActivatorRef}
        {...dragListeners}
        {...(isDropTarget ? { [TREE_ROW_ATTRIBUTE]: board.id } : {})}
        className={cn(
          "group/row relative mx-1 flex min-h-9 items-center gap-1 rounded-md pr-1 pl-2 text-sm transition-colors select-none [-webkit-touch-callout:none]",
          !isActive && "hover:bg-muted",
          isActive && "bg-primary/10 text-primary font-medium",
          !isActive && "text-foreground",
          dropIndicator === "inside" && "bg-primary/10 ring-primary/40 ring-1"
        )}
      >
        {/* The line sits in the gap between this row and its neighbour, at this row's indent */}
        {(dropIndicator === "before" || dropIndicator === "after") && (
          <span
            aria-hidden="true"
            className={cn(
              "bg-primary pointer-events-none absolute inset-x-0 h-0.5 rounded-full",
              dropIndicator === "before" ? "-top-0.5" : "-bottom-0.5"
            )}
          />
        )}
        {/*
          On a board with children the marker is the expand control, outside the
          link so a click toggles the children instead of opening the board. It
          shows the chevron in the marker's place while the row is hovered or the
          control has keyboard focus.
        */}
        {showChevron && (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onToggleExpand?.(board.id);
            }}
            onMouseDown={stopDragStart}
            onTouchStart={stopDragStart}
            aria-expanded={isExpanded}
            aria-label={t(isExpanded ? "collapseBoard" : "expandBoard", { name: board.name })}
            className="group/toggle text-muted-foreground hover:text-foreground flex h-7 w-4 shrink-0 items-center justify-center rounded-sm"
          >
            <span
              aria-hidden="true"
              className="flex items-center justify-center group-hover/row:hidden group-focus-visible/toggle:hidden"
            >
              {marker}
            </span>
            <ChevronRight
              aria-hidden="true"
              className={cn(
                "hidden h-4 w-4 shrink-0 group-hover/row:block group-focus-visible/toggle:block",
                isExpanded && "rotate-90"
              )}
            />
          </button>
        )}

        <Link
          href={boardHref}
          onClick={handleBoardClick}
          className="flex min-w-0 flex-1 items-center gap-1 self-stretch"
        >
          {!showChevron && (
            <span className="flex w-4 shrink-0 items-center justify-center">{marker}</span>
          )}

          <span className="min-w-0 flex-1 truncate">{board.name}</span>

          <span className="text-muted-foreground shrink-0 text-xs font-medium uppercase">
            {board.prefix}
          </span>
        </Link>

        {/* Context menu */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              aria-label={t("boardActions", { name: board.name })}
              className="text-muted-foreground hover:text-foreground hover:bg-muted flex h-7 w-7 shrink-0 items-center justify-center rounded"
              onClick={(e) => e.stopPropagation()}
              onMouseDown={stopDragStart}
              onTouchStart={stopDragStart}
            >
              <MoreHorizontal className="h-4 w-4" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            align="end"
            className="w-48"
            onMouseDown={stopDragStart}
            onTouchStart={stopDragStart}
          >
            {onTogglePin && (
              <DropdownMenuItem onClick={() => onTogglePin(board.id)}>
                {isPinned ? (
                  <>
                    <PinOff className="h-3.5 w-3.5" />
                    {t("contextMenu.unpin")}
                  </>
                ) : (
                  <>
                    <Pin className="h-3.5 w-3.5" />
                    {t("contextMenu.pin")}
                  </>
                )}
              </DropdownMenuItem>
            )}
            {canManage && (
              <DropdownMenuItem onClick={() => setCreateDialogOpen(true)}>
                <FolderPlus className="h-3.5 w-3.5" />
                {t("contextMenu.createChildBoard")}
              </DropdownMenuItem>
            )}
            {canManage && (
              <DropdownMenuItem onClick={() => settingsTriggerRef.current?.click()}>
                <Settings className="h-3.5 w-3.5" />
                {t("contextMenu.settings")}
              </DropdownMenuItem>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {children}

      {/* Create child board dialog */}
      <CreateBoardDialog
        open={createDialogOpen}
        onOpenChange={setCreateDialogOpen}
        parentBoardId={board.id}
      />

      {/*
        Hidden settings trigger -- BoardSettingsDialog manages its own open
        state. The trigger is programmatic only and carries no label, so it is
        hidden from assistive tech (aria-hidden) rather than announced as a
        bare nameless button.
      */}
      <BoardSettingsDialog
        boardId={board.id}
        board={{
          id: board.id,
          slug: board.slug,
          name: board.name,
          prefix: board.prefix,
          description: board.description,
          color: board.color ?? DEFAULT_BOARD_COLOR,
        }}
        canManage={canManage}
      >
        <button
          type="button"
          ref={settingsTriggerRef}
          className="sr-only"
          tabIndex={-1}
          aria-hidden="true"
        />
      </BoardSettingsDialog>
    </div>
  );
}

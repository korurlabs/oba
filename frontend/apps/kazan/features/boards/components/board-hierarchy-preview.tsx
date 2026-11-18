"use client";

import { Badge } from "@/components/ui/badge";
import { BoardDot } from "@/components/ui/board-dot";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { useTranslations } from "next-intl";
import { Info, X } from "lucide-react";
import type { BoardListWithChildrenResponse } from "../schemas";

export interface PreviewItem {
  board: BoardListWithChildrenResponse;
  /** true = directly selected, false = descendant tagging along */
  isSelected: boolean;
  /** depth relative to the parent board (0 = direct child) */
  previewDepth: number;
}

interface BoardHierarchyPreviewProps {
  parentBoard: BoardListWithChildrenResponse;
  items: PreviewItem[];
  /** When provided, shows X button on directly selected items */
  onRemove?: (boardId: string) => void;
  /** Narrows which directly selected items get the X button; all of them when left out */
  canRemove?: (boardId: string) => boolean;
  /** Disables remove buttons */
  disabled?: boolean;
  /** Show info tooltip on parent row (defaults to true) */
  showParentInfo?: boolean;
  /** Show the info message below the preview (defaults to false) */
  showInfoMessage?: boolean;
  /** Read-only mode: no opacity dimming or "included" labels (defaults to false) */
  readOnly?: boolean;
}

export function BoardHierarchyPreview({
  parentBoard,
  items,
  onRemove,
  canRemove,
  disabled = false,
  showParentInfo = true,
  showInfoMessage = false,
  readOnly = false,
}: BoardHierarchyPreviewProps) {
  const t = useTranslations("board.addExisting");
  const tBoard = useTranslations("board");

  return (
    <div className="space-y-2 w-full">
      <div className="rounded-lg border">
        {/* Parent row */}
        <div className="bg-primary/5 flex items-center gap-2 rounded-t-md border-b px-3 py-2.5">
          <BoardDot color={parentBoard.color} />
          <span className="flex-1 truncate text-sm font-medium">{parentBoard.name}</span>
          <Badge variant="secondary" className="text-xs">
            {parentBoard.prefix}
          </Badge>
          {showParentInfo && (
            <Tooltip>
              <TooltipTrigger asChild>
                <Info className="text-muted-foreground h-4 w-4 shrink-0" />
              </TooltipTrigger>
              <TooltipContent>{t("parentLabel")}</TooltipContent>
            </Tooltip>
          )}
        </div>

        {/* Child rows */}
        {items.map(({ board, isSelected, previewDepth }) => (
          <div
            key={board.id}
            className={cn(
              "flex items-center gap-2 px-3 py-2",
              !readOnly && !isSelected && "opacity-60"
            )}
            style={{ paddingLeft: `${(previewDepth + 1) * 16 + 12}px` }}
          >
            <span className="text-muted-foreground text-xs">{"\u21B3"}</span>
            <BoardDot color={board.color} />
            <span className="flex-1 truncate text-sm">{board.name}</span>
            <Badge variant="secondary" className="text-xs">
              {board.prefix}
            </Badge>
            {isSelected && onRemove && (canRemove?.(board.id) ?? true) ? (
              <button
                type="button"
                onClick={() => onRemove(board.id)}
                disabled={disabled}
                aria-label={tBoard("removeChild", { name: board.name, parent: parentBoard.name })}
                className="text-muted-foreground hover:text-foreground hover:bg-muted flex h-7 w-7 shrink-0 items-center justify-center rounded transition-colors disabled:pointer-events-none disabled:opacity-50"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            ) : !readOnly && !isSelected ? (
              <span className="text-muted-foreground text-[10px]">{t("includedAsSub")}</span>
            ) : null}
          </div>
        ))}
      </div>

      {showInfoMessage && (
        <div className="flex items-start gap-2 rounded-md bg-blue-50 p-3 dark:bg-blue-950/30">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-blue-500" />
          <p className="text-xs text-blue-700 dark:text-blue-400">
            {t("infoMessage", { boardName: parentBoard.name })}
          </p>
        </div>
      )}
    </div>
  );
}

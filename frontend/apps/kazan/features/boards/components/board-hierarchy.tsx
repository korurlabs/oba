/**
 * Board Hierarchy Component
 *
 * Displays board hierarchy navigation (child boards only).
 * Uses the cached board list with includeChildren=true
 * to show child boards without additional API calls.
 *
 * Note: ancestors/hierarchy endpoints do not exist on the backend,
 * so breadcrumb navigation for parent boards is not available.
 */

"use client";

import { Button } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Plus, FolderTree, Loader2 } from "lucide-react";
import { useState, useMemo } from "react";
import { CreateBoardDialog } from "./create-board-dialog";
import { AddChildChoiceDialog } from "./add-child-choice-dialog";
import { AddExistingBoardDialog } from "./add-existing-board-dialog";
import { BoardHierarchyPreview } from "./board-hierarchy-preview";
import { useTranslations } from "next-intl";
import { useCachedBoard, useCachedChildBoards, useMoveBoard } from "../hooks/use-boards";
import { useManageableBoardIds } from "../hooks/use-board-role";

import { EmptyState } from "@/components/ui/empty-state";
import type { BoardListWithChildrenResponse } from "../schemas";
import type { PreviewItem } from "./board-hierarchy-preview";

interface BoardHierarchyProps {
  boardId: string;
  showAlways?: boolean;
  canManage: boolean;
}

type DialogMode = "choice" | "new" | "existing" | null;

export function BoardHierarchy({ boardId, showAlways = false, canManage }: BoardHierarchyProps) {
  const t = useTranslations();
  const [dialogMode, setDialogMode] = useState<DialogMode>(null);

  const [removeTarget, setRemoveTarget] = useState<BoardListWithChildrenResponse | null>(null);

  const { data: children, isLoading } = useCachedChildBoards(boardId);
  const { data: currentBoard } = useCachedBoard(boardId);
  const moveBoard = useMoveBoard();

  // Removing a child changes where the child sits, so the button also needs
  // OWNER or ADMIN on the child, the one role the server checks for it.
  const childIds = useMemo(() => children.map((child) => child.id), [children]);
  const manageableChildIds = useManageableBoardIds(childIds);

  const previewItems = useMemo(() => {
    const items: PreviewItem[] = [];

    function flatten(boards: BoardListWithChildrenResponse[], depth: number) {
      for (const board of boards) {
        items.push({ board, isSelected: depth === 0, previewDepth: depth });
        if (board.childBoards?.length) {
          flatten(board.childBoards as BoardListWithChildrenResponse[], depth + 1);
        }
      }
    }

    flatten(children, 0);
    return items;
  }, [children]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center border bg-card p-4">
        <Loader2 className="text-muted-foreground h-5 w-5 animate-spin" />
      </div>
    );
  }

  const hasChildren = children.length > 0;

  const handleRemove = () => {
    if (!removeTarget || !currentBoard) return;
    moveBoard.mutate(
      {
        board: { id: removeTarget.id, name: removeTarget.name },
        newParent: null,
        previousParent: { id: currentBoard.id, name: currentBoard.name },
        successMessage: "removedFromParent",
      },
      { onSuccess: () => setRemoveTarget(null) },
    );
  };

  // If not showAlways and no children, don't render
  if (!hasChildren && !showAlways) return null;

  return (
    <div className="bg-card space-y-4 rounded-lg border p-4">
      {/* Child Boards */}
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <span className="text-foreground text-sm font-medium">{t("board.childBoards")}</span>
          {canManage && (
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setDialogMode("choice")}
              className="h-7 text-xs"
            >
              <Plus className="h-3 w-3" />
              {t("board.addChild")}
            </Button>
          )}
        </div>
        {hasChildren && currentBoard ? (
          <BoardHierarchyPreview
            parentBoard={currentBoard}
            items={previewItems}
            showParentInfo={false}
            readOnly
            onRemove={
              canManage
                ? (id) => setRemoveTarget(children.find((child) => child.id === id) ?? null)
                : undefined
            }
            canRemove={(id) => manageableChildIds.has(id)}
            disabled={moveBoard.isPending}
          />
        ) : (
          <div className="text-foreground py-6 text-center">
            <EmptyState
              Icon={FolderTree}
              title={t("board.noBoards")}
              action={canManage ? {
                label: t("board.addChild"),
                onClick: () => setDialogMode("choice"),
              } : undefined}
            />
          </div>
        )}
      </div>

      {/* Choice dialog */}
      <AddChildChoiceDialog
        open={dialogMode === "choice"}
        onOpenChange={(open) => !open && setDialogMode(null)}
        onChoose={(mode) => setDialogMode(mode)}
      />

      {/* New board dialog */}
      <CreateBoardDialog
        open={dialogMode === "new"}
        onOpenChange={(open) => !open && setDialogMode(null)}
        parentBoardId={boardId}
      />

      {/* Existing board dialog */}
      {currentBoard && (
        <AddExistingBoardDialog
          open={dialogMode === "existing"}
          onOpenChange={(open) => !open && setDialogMode(null)}
          parentBoardId={boardId}
          parentBoard={currentBoard}
        />
      )}

      {/* Remove child confirmation */}
      <AlertDialog
        open={removeTarget !== null}
        onOpenChange={(open) => {
          if (!open && !moveBoard.isPending) setRemoveTarget(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("board.removeChildDialog.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("board.removeChildDialog.description", {
                child: removeTarget?.name ?? "",
                parent: currentBoard?.name ?? "",
              })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={moveBoard.isPending}>
              {t("common.cancel")}
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={(event) => {
                // The dialog stays open until the server confirms, so a
                // refused removal leaves the user where they can retry.
                event.preventDefault();
                handleRemove();
              }}
              disabled={moveBoard.isPending}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {t("board.removeChildDialog.confirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

"use client";

import { useState, useMemo, useCallback } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { SelectFieldStandalone } from "@oba/shared/features/form/components";
import { BoardHierarchyPreview } from "./board-hierarchy-preview";
import { useTranslations } from "next-intl";
import { Loader2 } from "lucide-react";
import { useBoards, useCachedChildBoards, useAddChildBoards } from "../hooks/use-boards";
import { useNonManageableBoardRoles } from "../hooks/use-board-role";
import { useBoardSelect } from "../hooks/use-board-select";
import type { BoardListWithChildrenResponse } from "../schemas";

interface AddExistingBoardDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  parentBoardId: string;
  parentBoard: BoardListWithChildrenResponse;
}

/**
 * Flattens the full board tree into a flat list, excluding the board with
 * `excludeId` and all of its descendants (to prevent cycles).
 */
function flattenBoards(
  boards: BoardListWithChildrenResponse[],
  excludeId: string,
  depth = 0,
  parentPrefix = ""
): Array<BoardListWithChildrenResponse & { depth: number; hierarchyLabel: string }> {
  const result: Array<BoardListWithChildrenResponse & { depth: number; hierarchyLabel: string }> = [];

  for (const board of boards) {
    if (board.id === excludeId) continue;

    const hierarchyLabel = parentPrefix
      ? `${parentPrefix} > ${board.name}`
      : board.name;

    result.push({ ...board, depth, hierarchyLabel });

    if (board.childBoards?.length) {
      const children = flattenBoards(
        board.childBoards as BoardListWithChildrenResponse[],
        excludeId,
        depth + 1,
        hierarchyLabel
      );
      result.push(...children);
    }
  }

  return result;
}

export function AddExistingBoardDialog({
  open,
  onOpenChange,
  parentBoardId,
  parentBoard,
}: AddExistingBoardDialogProps) {
  const t = useTranslations("board.addExisting");
  const tCommon = useTranslations("common");

  const [selectedBoardIds, setSelectedBoardIdsRaw] = useState<string[]>([]);

  const { data: boardsResponse } = useBoards({
    includeChildren: true,
    amount: 100,
  });
  const { data: existingChildren } = useCachedChildBoards(parentBoardId);
  const addChildBoardsMutation = useAddChildBoards();

  const allBoards = useMemo(
    () => boardsResponse?.data ?? [],
    [boardsResponse?.data]
  );

  // Flatten tree excluding current board and its descendants
  const flatBoards = useMemo(
    () => flattenBoards(allBoards, parentBoardId),
    [allBoards, parentBoardId]
  );

  const existingChildIds = useMemo(
    () => new Set(existingChildren.map((c) => c.id)),
    [existingChildren]
  );

  const flatBoardIds = useMemo(
    () => flatBoards.map((b) => b.id),
    [flatBoards]
  );
  const nonManageableRoles = useNonManageableBoardRoles(flatBoardIds);

  // Collect all descendant IDs of currently selected boards so they are hidden
  const hiddenDescendantIds = useMemo(() => {
    const selectedSet = new Set(selectedBoardIds);
    const hidden = new Set<string>();

    for (let i = 0; i < flatBoards.length; i++) {
      const board = flatBoards[i]!;
      if (selectedSet.has(board.id)) {
        for (let j = i + 1; j < flatBoards.length; j++) {
          const descendant = flatBoards[j]!;
          if (descendant.depth > board.depth) {
            hidden.add(descendant.id);
          } else {
            break;
          }
        }
      }
    }
    return hidden;
  }, [flatBoards, selectedBoardIds]);

  // Wrap setter to auto-remove descendants when their ancestor is selected
  const setSelectedBoardIds = useCallback(
    (next: string[] | ((prev: string[]) => string[])) => {
      setSelectedBoardIdsRaw((prev) => {
        const nextIds = typeof next === "function" ? next(prev) : next;
        // Recompute hidden descendants for the new selection
        const hidden = new Set<string>();
        for (let i = 0; i < flatBoards.length; i++) {
          const board = flatBoards[i]!;
          if (nextIds.includes(board.id)) {
            for (let j = i + 1; j < flatBoards.length; j++) {
              const desc = flatBoards[j]!;
              if (desc.depth > board.depth) {
                hidden.add(desc.id);
              } else {
                break;
              }
            }
          }
        }
        return nextIds.filter((id) => !hidden.has(id));
      });
    },
    [flatBoards]
  );

  const selectOptions = useMemo(
    () =>
      flatBoards
        .filter((board) => !hiddenDescendantIds.has(board.id))
        .map((board) => {
          const isExistingChild = existingChildIds.has(board.id);
          const nonManageableRole = nonManageableRoles.get(board.id);
          const disabled = isExistingChild || !!nonManageableRole;
          return {
            value: board.id,
            label: board.name,
            disabled,
            disabledLabel: isExistingChild ? t("alreadyChild") : undefined,
            meta: {
              color: board.color,
              prefix: board.prefix,
              depth: board.depth,
              disabledRole: nonManageableRole,
            },
          };
        }),
    [flatBoards, existingChildIds, nonManageableRoles, hiddenDescendantIds, t]
  );

  // Get the board renderers from the shared hook (pass empty boards — we only need the renderers)
  const { renderBoardOption, renderBoardValue } = useBoardSelect([]);

  // Build preview items: each selected board + its descendants (which come along automatically)
  const previewItems = useMemo(() => {
    const selectedSet = new Set(selectedBoardIds);
    const added = new Set<string>();
    const items: Array<{
      board: BoardListWithChildrenResponse & { depth: number; hierarchyLabel: string };
      isSelected: boolean;
      previewDepth: number;
    }> = [];

    for (let i = 0; i < flatBoards.length; i++) {
      const board = flatBoards[i]!;
      if (selectedSet.has(board.id) && !added.has(board.id)) {
        items.push({ board, isSelected: true, previewDepth: 0 });
        added.add(board.id);
        for (let j = i + 1; j < flatBoards.length; j++) {
          const descendant = flatBoards[j]!;
          if (descendant.depth > board.depth) {
            if (!added.has(descendant.id)) {
              items.push({
                board: descendant,
                isSelected: false,
                previewDepth: descendant.depth - board.depth,
              });
              added.add(descendant.id);
            }
          } else {
            break;
          }
        }
      }
    }
    return items;
  }, [flatBoards, selectedBoardIds]);

  const handleSubmit = useCallback(async () => {
    try {
      await addChildBoardsMutation.mutateAsync({
        boardId: parentBoardId,
        childBoardIds: selectedBoardIds,
      });
      setSelectedBoardIds([]);
      onOpenChange(false);
    } catch {
      // Error is handled by the mutation's onError callback
    }
  }, [selectedBoardIds, parentBoardId, addChildBoardsMutation, onOpenChange, setSelectedBoardIds]);

  const handleClose = (isOpen: boolean) => {
    if (!addChildBoardsMutation.isPending) {
      if (!isOpen) {
        setSelectedBoardIds([]);
      }
      onOpenChange(isOpen);
    }
  };

  const handleRemove = (boardId: string) => {
    setSelectedBoardIds((prev) => prev.filter((id) => id !== boardId));
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("description")}</DialogDescription>
        </DialogHeader>

        <div className="max-h-[70vh] space-y-4 overflow-y-auto py-2">
          {/* Multi-select dropdown */}
          <SelectFieldStandalone
            isMulti
            value={selectedBoardIds}
            onValueChange={setSelectedBoardIds}
            options={selectOptions}
            renderOption={renderBoardOption}
            renderValue={renderBoardValue}
            placeholder={t("selectBoards")}
            searchPlaceholder={tCommon("search")}
            disabled={addChildBoardsMutation.isPending}
          />

          {/* Preview section */}
          {selectedBoardIds.length > 0 && (
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground text-xs font-medium uppercase tracking-wider">
                  {t("previewTitle")}
                </span>
                <div className="bg-border h-px flex-1" />
              </div>

              <BoardHierarchyPreview
                parentBoard={parentBoard}
                items={previewItems}
                onRemove={handleRemove}
                disabled={addChildBoardsMutation.isPending}
                showInfoMessage
              />
            </div>
          )}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={() => handleClose(false)}
            disabled={addChildBoardsMutation.isPending}
          >
            {tCommon("cancel")}
          </Button>
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={selectedBoardIds.length === 0 || addChildBoardsMutation.isPending}
          >
            {addChildBoardsMutation.isPending ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                {t("adding")}
              </>
            ) : (
              t("confirmButton")
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

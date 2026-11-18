"use client";

import { useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Link2, Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { SelectFieldStandalone } from "@oba/shared/features/form/components";
import type { SelectOption } from "@oba/shared/features/form/components";
import type { ParentSprintOption } from "../hooks/use-join-parent-sprint";
import { useJoinParentSprint } from "../hooks/use-join-parent-sprint";
import { ParentSprintDetails } from "./parent-sprint-details";

interface JoinParentSprintDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Name of the board that joins the sprint. */
  boardName: string;
  parentOptions: ParentSprintOption[];
}

/**
 * Lets a board join the active sprint of a board above it. The user first
 * picks the board; its active sprint then shows with the boards it is
 * shared with and whether it repeats, and only then can it be joined.
 */
export function JoinParentSprintDialog({
  open,
  onOpenChange,
  boardName,
  parentOptions,
}: JoinParentSprintDialogProps) {
  const t = useTranslations();
  const [selectedBoardId, setSelectedBoardId] = useState<string>("");
  const joinMutation = useJoinParentSprint();

  // Each opening starts with no board picked.
  useEffect(() => {
    if (open) setSelectedBoardId("");
  }, [open]);

  const selected = parentOptions.find((option) => option.board.id === selectedBoardId) ?? null;
  const activeSprint = selected?.activeSprint ?? null;

  const boardOptions = useMemo(
    (): SelectOption[] =>
      parentOptions.map((option) => {
        const relation =
          option.distance === 1
            ? t("sprint.joinParent.parent")
            : option.distance === 2
              ? t("sprint.joinParent.grandparent")
              : t("sprint.joinParent.ancestor");
        const disabledLabel = !option.activeSprint
          ? t("sprint.joinParent.noActiveSprint")
          : option.boardWithoutRole
            ? t("sprint.joinParent.noRole", { board: option.boardWithoutRole.name })
            : undefined;
        return {
          value: option.board.id,
          label: `${option.board.name} (${relation})`,
          disabled: !!disabledLabel,
          disabledLabel,
        };
      }),
    [parentOptions, t]
  );

  // A refused join keeps the dialog open; useJoinParentSprint shows the error.
  const handleJoin = () => {
    if (!selected || !activeSprint) return;
    joinMutation.mutate(
      { sprintId: activeSprint.id, linkPath: selected.linkPath },
      { onSuccess: () => onOpenChange(false) }
    );
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !joinMutation.isPending && onOpenChange(next)}>
      <DialogContent className="max-h-[80vh] max-w-lg overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t("sprint.joinParent.title")}</DialogTitle>
          <DialogDescription>
            {t("sprint.joinParent.description", { board: boardName })}
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label className="text-foreground text-sm font-semibold">
              {t("sprint.joinParent.boardLabel")}
            </Label>
            <SelectFieldStandalone
              value={selectedBoardId}
              onValueChange={setSelectedBoardId}
              options={boardOptions}
              placeholder={t("sprint.joinParent.selectBoard")}
              searchPlaceholder={t("common.search")}
            />
          </div>

          {selected && activeSprint && (
            <div className="flex flex-col gap-2">
              <Label className="text-foreground text-sm font-semibold">
                {t("sprint.joinParent.activeSprintOf", { board: selected.board.name })}
              </Label>
              <ParentSprintDetails
                parentBoardId={selected.board.id}
                sprintId={activeSprint.id}
                boardName={boardName}
                joiningBoardIds={selected.linkPath.map((board) => board.id)}
              />
            </div>
          )}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
            disabled={joinMutation.isPending}
          >
            {t("common.cancel")}
          </Button>
          <Button
            type="button"
            onClick={handleJoin}
            disabled={
              !selected || !activeSprint || !!selected.boardWithoutRole || joinMutation.isPending
            }
          >
            {joinMutation.isPending ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Link2 className="mr-2 h-4 w-4" />
            )}
            {t("sprint.joinParent.join")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

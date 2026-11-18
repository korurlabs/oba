"use client";

import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Label } from "@/components/ui/label";
import { InlineAlert } from "@/components/ui/inline-alert";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { SelectFieldStandalone } from "@oba/shared/features/form/components";
import { Loader2, Unlink, AlertCircle } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { useBoards } from "@/features/boards/hooks/use-boards";
import { useBoardSelect } from "@/features/boards/hooks/use-board-select";
import { isBoardManageRole } from "@/features/boards/hooks/use-board-role";
import { useSprints } from "@/features/sprints/hooks/use-sprints";
import type { BoardListWithChildrenResponse, BoardMemberRole } from "@/features/boards/schemas";
import {
  unlinkSprintFormSchema,
  type UnlinkSprintFormValues,
  type UnlinkTicketAction,
  type SprintUnlinkRequest,
} from "../schemas";

// ── Props ──────────────────────────────────────────────────────────

interface UnlinkSprintDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  boardId: string;
  sprintId: string;
  boardName: string;
  onUnlink: (data: SprintUnlinkRequest) => void;
}

// ── Component ──────────────────────────────────────────────────────

export function UnlinkSprintDialog({
  open,
  onOpenChange,
  boardId,
  sprintId,
  boardName,
  onUnlink,
}: UnlinkSprintDialogProps) {
  const t = useTranslations();
  const [boardTarget, setBoardTarget] = useState<"current" | "other">("current");

  const form = useForm<UnlinkSprintFormValues>({
    resolver: zodResolver(unlinkSprintFormSchema),
    mode: "onChange",
    defaultValues: {
      ticketAction: "MOVE_TO_BACKLOG",
      targetBoardId: boardId,
    },
  });

  const ticketAction = form.watch("ticketAction");
  const targetBoardId = form.watch("targetBoardId");

  // ── Data fetching ──────────────────────────────────────────────

  const { data: boardsResponse, isLoading: isBoardsLoading } = useBoards({
    includeChildren: true,
    amount: 100,
    enabled: open,
  });
  const allBoards = useMemo(() => boardsResponse?.data ?? [], [boardsResponse?.data]);

  const disabledIds = useMemo(() => new Set([boardId]), [boardId]);
  const disabledHintMap = useMemo(
    () => new Map([[boardId, t("sprint.unlinkDialog.currentBoardHint")]]),
    [boardId, t]
  );

  // Build a map of board IDs where the current user lacks manage permissions (OWNER/ADMIN).
  const insufficientRoleMap = useMemo(() => {
    const map = new Map<string, BoardMemberRole>();
    function walk(boards: BoardListWithChildrenResponse[]) {
      for (const b of boards) {
        if (b.currentUserRole !== undefined && !isBoardManageRole(b.currentUserRole)) {
          map.set(b.id, b.currentUserRole);
        }
        if (b.childBoards?.length) {
          walk(b.childBoards as BoardListWithChildrenResponse[]);
        }
      }
    }
    walk(allBoards);
    return map;
  }, [allBoards]);

  const {
    options: boardSelectOptions,
    renderBoardOption,
    renderBoardValue,
  } = useBoardSelect(allBoards, {
    disabledIds,
    disabledHintMap,
    disabledRoleMap: insufficientRoleMap,
  });

  const effectiveBoardId = boardTarget === "current" ? boardId : (targetBoardId ?? boardId);

  const { data: sprintsResponse } = useSprints(effectiveBoardId, {
    enabled: open,
  });
  const availableSprints = useMemo(() => {
    const sprints = sprintsResponse?.data ?? [];
    return sprints.filter((s) => s.status !== "COMPLETED" && s.id !== sprintId);
  }, [sprintsResponse?.data, sprintId]);

  const hasPlannedSprints = availableSprints.length > 0;

  // ── Effects ─────────────────────────────────────────────────────

  // Reset sprint ID when ticket action changes away from MOVE_TO_SPRINT
  useEffect(() => {
    if (ticketAction !== "MOVE_TO_SPRINT") {
      form.setValue("targetSprintId", undefined);
    }
  }, [ticketAction, form]);

  // Reset sprint selection when effective board changes
  useEffect(() => {
    form.setValue("targetSprintId", undefined);
  }, [effectiveBoardId, form]);

  // When switching to "current", reset targetBoardId to current board
  useEffect(() => {
    if (boardTarget === "current") {
      form.setValue("targetBoardId", boardId);
    }
  }, [boardTarget, boardId, form]);

  // Reset form when dialog opens
  useEffect(() => {
    if (open) {
      setBoardTarget("current");
      form.reset({
        ticketAction: "MOVE_TO_BACKLOG",
        targetBoardId: boardId,
      });
    }
  }, [open, form, boardId]);

  // ── Submit ─────────────────────────────────────────────────────

  const isSubmitting = form.formState.isSubmitting;

  const handleUnlink = form.handleSubmit((values) => {
    if (values.ticketAction === "DELETE") {
      onUnlink({ deleteTickets: true });
    } else if (values.ticketAction === "MOVE_TO_SPRINT") {
      onUnlink({
        transfer: {
          boardId: boardTarget === "other" ? values.targetBoardId! : boardId,
          sprintId: values.targetSprintId,
        },
      });
    } else {
      // MOVE_TO_BACKLOG
      onUnlink({
        transfer: {
          boardId: boardTarget === "other" ? values.targetBoardId! : boardId,
        },
      });
    }
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[80vh] max-w-[90vw] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            {t("sprint.unlinkDialog.title")}: {boardName}
          </DialogTitle>
          <DialogDescription>{t("sprint.unlinkDialog.description")}</DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-6 py-2">
          {/* Board target: Current / Other */}
          <div className="flex flex-col gap-2">
            <Label className="text-foreground text-sm font-semibold">
              {t("sprint.unlinkDialog.selectTargetBoard")}
            </Label>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setBoardTarget("current")}
                className={cn(
                  "rounded-lg border p-2 text-center text-sm font-medium transition-colors",
                  boardTarget === "current"
                    ? "border-primary bg-primary/10 text-primary"
                    : "border-border text-muted-foreground hover:bg-accent"
                )}
              >
                {t("sprint.unlinkDialog.currentBoard")}
              </button>
              <button
                type="button"
                onClick={() => setBoardTarget("other")}
                className={cn(
                  "rounded-lg border p-2 text-center text-sm font-medium transition-colors",
                  boardTarget === "other"
                    ? "border-primary bg-primary/10 text-primary"
                    : "border-border text-muted-foreground hover:bg-accent"
                )}
              >
                {t("sprint.unlinkDialog.otherBoard")}
              </button>
            </div>
          </div>

          {/* Board SearchSelect — only when "Other Board" */}
          {boardTarget === "other" && (
            <SelectFieldStandalone
              value={targetBoardId !== boardId ? (targetBoardId ?? "") : ""}
              onValueChange={(id) => form.setValue("targetBoardId", id, { shouldValidate: true })}
              options={boardSelectOptions}
              renderOption={renderBoardOption}
              renderValue={renderBoardValue}
              placeholder={t("sprint.unlinkDialog.selectTargetBoard")}
              searchPlaceholder={t("common.search")}
              isLoading={isBoardsLoading}
            />
          )}

          {/* Action selection */}
          <Label className="text-foreground text-sm font-semibold">
            {t("sprint.unlinkDialog.action")}
          </Label>

          <RadioGroup
            value={ticketAction}
            onValueChange={(v) =>
              form.setValue("ticketAction", v as UnlinkTicketAction, {
                shouldValidate: true,
              })
            }
          >
            {/* Move to Backlog */}
            <label
              htmlFor="unlink-backlog"
              className="border-border hover:bg-accent flex cursor-pointer items-start gap-2 rounded-lg border p-3 transition-colors"
            >
              <RadioGroupItem value="MOVE_TO_BACKLOG" id="unlink-backlog" className="mt-1" />
              <div className="flex-1">
                <span className="font-medium">{t("sprint.unlinkDialog.moveToBacklog")}</span>
                <p className="text-muted-foreground mt-1 text-xs">
                  {t("sprint.unlinkDialog.moveToBacklogDesc")}
                </p>
              </div>
            </label>

            {/* Move to Sprint */}
            <label
              htmlFor="unlink-sprint"
              className="border-border hover:bg-accent flex cursor-pointer items-start gap-2 rounded-lg border p-3 transition-colors"
            >
              <RadioGroupItem value="MOVE_TO_SPRINT" id="unlink-sprint" className="mt-1" />
              <div className="flex-1">
                <span className="font-medium">{t("sprint.unlinkDialog.moveToSprint")}</span>
                <p className="text-muted-foreground mt-1 text-xs">
                  {t("sprint.unlinkDialog.moveToSprintDesc")}
                </p>
              </div>
            </label>

            {/* Delete Tickets */}
            <label
              htmlFor="unlink-delete"
              className="border-border hover:bg-accent flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors"
            >
              <RadioGroupItem value="DELETE" id="unlink-delete" className="mt-1" />
              <div className="flex-1">
                <span className="font-medium">{t("sprint.unlinkDialog.deleteTickets")}</span>
                <p className="text-muted-foreground mt-1 text-xs">
                  {t("sprint.unlinkDialog.deleteTicketsDesc")}
                </p>
              </div>
            </label>
          </RadioGroup>

          {/* Sprint selector when MOVE_TO_SPRINT */}
          {ticketAction === "MOVE_TO_SPRINT" && (
            <div className="flex w-full flex-col items-start gap-4 self-stretch pl-6">
              {hasPlannedSprints ? (
                <div className="flex w-full flex-col gap-2 self-stretch">
                  <Label className="text-xs">{t("sprint.unlinkDialog.selectTargetSprint")}</Label>
                  <Select
                    value={form.watch("targetSprintId") ?? ""}
                    onValueChange={(value) =>
                      form.setValue("targetSprintId", value, {
                        shouldValidate: true,
                      })
                    }
                  >
                    <SelectTrigger>
                      <SelectValue placeholder={t("sprint.unlinkDialog.selectTargetSprint")} />
                    </SelectTrigger>
                    <SelectContent>
                      {availableSprints.map((s) => (
                        <SelectItem key={s.id} value={s.id}>
                          {s.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              ) : (
                <InlineAlert
                  variant="info"
                  icon={<AlertCircle />}
                  description={t("sprint.unlinkDialog.noPlannedSprints")}
                />
              )}
            </div>
          )}

          {/* Warning Alert */}
          <InlineAlert
            variant="warning"
            icon={<AlertCircle />}
            description={t("sprint.unlinkDialog.warning")}
          />
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
            disabled={isSubmitting}
          >
            {t("common.cancel")}
          </Button>
          <Button onClick={handleUnlink} disabled={isSubmitting || !form.formState.isValid}>
            {isSubmitting ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                {t("sprint.unlinkDialog.unlinking")}
              </>
            ) : (
              <>
                <Unlink className="mr-2 h-4 w-4" />
                {t("sprint.unlinkDialog.button")}
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

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
import { Loader2, CheckCircle2, AlertCircle, ListTodo } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { useBoards } from "@/features/boards/hooks/use-boards";
import { useBoardSelect } from "@/features/boards/hooks/use-board-select";
import { isBoardManageRole } from "@/features/boards/hooks/use-board-role";
import { useSprint, useSprints } from "@/features/sprints/hooks/use-sprints";
import type { BoardListWithChildrenResponse, BoardMemberRole } from "@/features/boards/schemas";
import {
  completeSprintFormSchema,
  type CompleteSprintFormValues,
  type CompleteSprintTicketAction,
} from "../schemas";
import type { SprintCompleteRequest } from "../schemas";

// ── Props ──────────────────────────────────────────────────────────

interface CompleteSprintDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  boardId: string;
  sprint: { id: string; name: string };
  currentSprintId: string;
  completedTicketsCount: number;
  incompleteTicketsCount: number;
  onCompleteSprint: (data: SprintCompleteRequest) => Promise<void>;
}

// ── Component ──────────────────────────────────────────────────────

export function CompleteSprintDialog({
  open,
  onOpenChange,
  boardId,
  sprint,
  currentSprintId,
  completedTicketsCount,
  incompleteTicketsCount,
  onCompleteSprint,
}: CompleteSprintDialogProps) {
  const t = useTranslations();
  const [boardTarget, setBoardTarget] = useState<"current" | "other">("current");

  const form = useForm<CompleteSprintFormValues>({
    resolver: zodResolver(completeSprintFormSchema),
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
    () => new Map([[boardId, t("sprint.completeDialog.currentBoardHint")]]),
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
    return sprints.filter(
      (s) => s.status !== "COMPLETED" && (boardTarget === "other" || s.id !== currentSprintId)
    );
  }, [sprintsResponse?.data, currentSprintId, boardTarget]);

  const hasPlannedSprints = availableSprints.length > 0;

  // A sprint of an enabled series starts its next round on complete, so the
  // dialog can move the unfinished tickets straight into it.
  const { data: sprintDetailResponse } = useSprint(boardId, sprint.id, { enabled: open });
  const series = sprintDetailResponse?.data?.series;
  const seriesEnabled = series?.enabled === true;
  const defaultTicketAction: CompleteSprintTicketAction =
    seriesEnabled && series.unfinishedTicketAction === "MOVE_TO_NEXT_SPRINT"
      ? "MOVE_TO_NEXT_SPRINT"
      : "MOVE_TO_BACKLOG";
  const isMoveToNextSprint = ticketAction === "MOVE_TO_NEXT_SPRINT";

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

  // Preselect the series' choice once its data arrives, unless the user has
  // picked an action; drop the next sprint choice when the series no longer
  // offers it.
  useEffect(() => {
    if (!open) return;
    const nextSprintGone =
      form.getValues("ticketAction") === "MOVE_TO_NEXT_SPRINT" && !seriesEnabled;
    if (nextSprintGone || !form.getFieldState("ticketAction").isDirty) {
      form.setValue("ticketAction", defaultTicketAction, { shouldValidate: true });
    }
  }, [open, seriesEnabled, defaultTicketAction, form]);

  // ── Derived ─────────────────────────────────────────────────────
  const totalTickets = completedTicketsCount + incompleteTicketsCount;
  const completionRate =
    totalTickets > 0 ? Math.round((completedTicketsCount / totalTickets) * 100) : 0;

  const isSubmitting = form.formState.isSubmitting;

  // ── Submit ─────────────────────────────────────────────────────

  const handleComplete = form.handleSubmit(async (values) => {
    if (values.ticketAction === "MOVE_TO_NEXT_SPRINT") {
      // The server picks the next round; tickets keep their own boards.
      await onCompleteSprint({ ticketAction: "MOVE_TO_SPRINT", moveToNextSprint: true });
      return;
    }
    await onCompleteSprint({
      ticketAction: values.ticketAction,
      targetSprintId: values.ticketAction === "MOVE_TO_SPRINT" ? values.targetSprintId : undefined,
      targetBoardId: boardTarget === "other" ? values.targetBoardId : undefined,
    });
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[80vh] max-w-[90vw] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            {t("sprint.completeDialog.title")}: {sprint.name}
          </DialogTitle>
          <DialogDescription>{t("sprint.completeDialog.description")}</DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-6 py-2">
          {/* Sprint Summary */}
          <div className="kazan-grid-frame bg-card p-4">
            <h4 className="kazan-grid-heading mb-3">
              {t("sprint.completeDialog.summary")}
            </h4>

            <div className="flex flex-col gap-2">
              {/* Completed Tickets */}
              <div className="flex items-center justify-between text-sm">
                <div className="text-korur-blue-strong flex items-center gap-2">
                  <CheckCircle2 className="h-4 w-4" />
                  <span>{t("sprint.completeDialog.completed")}</span>
                </div>
                <span className="font-mono font-semibold tabular-nums">{completedTicketsCount}</span>
              </div>

              {/* Incomplete Tickets */}
              <div className="flex items-center justify-between text-sm">
                <div className="text-foreground flex items-center gap-2">
                  <ListTodo className="h-4 w-4" />
                  <span>{t("sprint.completeDialog.incomplete")}</span>
                </div>
                <span className="font-mono font-semibold tabular-nums">{incompleteTicketsCount}</span>
              </div>

              {/* Completion Rate */}
              <div className="border-border mt-3 flex items-center justify-between border-t pt-3 text-sm font-medium">
                <span>{t("sprint.completeDialog.completionRate")}</span>
                <span
                  className={cn(
                    // The rate reads in the primary ink once most work is done and in red
                    // only when less than half of it is; between the two it is plain ink.
                    "font-mono text-lg tabular-nums",
                    completionRate >= 80 && "text-korur-blue-strong",
                    completionRate < 50 && "text-destructive"
                  )}
                >
                  {completionRate}%
                </span>
              </div>
            </div>
          </div>

          {/* Incomplete Tickets Action */}
          {incompleteTicketsCount > 0 && (
            <div className="flex flex-col gap-6">
              {/* Board target: Current / Other; the next sprint keeps each ticket's board */}
              {!isMoveToNextSprint && (
                <div className="flex flex-col gap-2">
                  <Label className="text-foreground text-sm font-semibold">
                    {t("sprint.completeDialog.selectTargetBoard")}
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
                      {t("sprint.completeDialog.currentBoard")}
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
                      {t("sprint.completeDialog.otherBoard")}
                    </button>
                  </div>
                </div>
              )}

              {/* Board SearchSelect — only when "Other Board" */}
              {boardTarget === "other" && !isMoveToNextSprint && (
                <SelectFieldStandalone
                  value={targetBoardId !== boardId ? (targetBoardId ?? "") : ""}
                  onValueChange={(id) =>
                    form.setValue("targetBoardId", id, { shouldValidate: true })
                  }
                  options={boardSelectOptions}
                  renderOption={renderBoardOption}
                  renderValue={renderBoardValue}
                  placeholder={t("sprint.completeDialog.selectTargetBoard")}
                  searchPlaceholder={t("common.search")}
                  isLoading={isBoardsLoading}
                />
              )}

              {/* Action selection */}
              <Label className="text-foreground text-sm font-semibold">
                {t("sprint.completeDialog.incompleteAction")}
              </Label>

              <RadioGroup
                value={ticketAction}
                onValueChange={(v) =>
                  form.setValue("ticketAction", v as CompleteSprintTicketAction, {
                    shouldValidate: true,
                    shouldDirty: true,
                  })
                }
              >
                {/* Move to the next sprint of the series */}
                {seriesEnabled && (
                  <label
                    htmlFor="complete-next-sprint"
                    className="border-border hover:bg-accent flex cursor-pointer items-start gap-2 rounded-lg border p-3 transition-colors"
                  >
                    <RadioGroupItem
                      value="MOVE_TO_NEXT_SPRINT"
                      id="complete-next-sprint"
                      className="mt-1"
                    />
                    <div className="flex-1">
                      <span className="font-medium">
                        {t("sprint.completeDialog.moveToNextSprint")}
                      </span>
                      <p className="text-muted-foreground mt-1 text-xs">
                        {t("sprint.completeDialog.moveToNextSprintDesc", { series: series.name })}
                      </p>
                    </div>
                  </label>
                )}

                {/* Move to Backlog */}
                <label
                  htmlFor="complete-backlog"
                  className="border-border hover:bg-accent flex cursor-pointer items-start gap-2 rounded-lg border p-3 transition-colors"
                >
                  <RadioGroupItem value="MOVE_TO_BACKLOG" id="complete-backlog" className="mt-1" />
                  <div className="flex-1">
                    <span className="font-medium">{t("sprint.completeDialog.moveToBacklog")}</span>
                    <p className="text-muted-foreground mt-1 text-xs">
                      {t("sprint.completeDialog.moveToBacklogDesc")}
                    </p>
                  </div>
                </label>

                {/* Move to Sprint */}
                <label
                  htmlFor="complete-sprint"
                  className="border-border hover:bg-accent flex cursor-pointer items-start gap-2 rounded-lg border p-3 transition-colors"
                >
                  <RadioGroupItem value="MOVE_TO_SPRINT" id="complete-sprint" className="mt-1" />
                  <div className="flex-1">
                    <span className="font-medium">{t("sprint.completeDialog.moveToSprint")}</span>
                    <p className="text-muted-foreground mt-1 text-xs">
                      {t("sprint.completeDialog.moveToSprintDesc")}
                    </p>
                  </div>
                </label>

                {/* Delete Tickets */}
                <label
                  htmlFor="complete-delete"
                  className="border-border hover:bg-accent flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors"
                >
                  <RadioGroupItem value="DELETE" id="complete-delete" className="mt-1" />
                  <div className="flex-1">
                    <span className="font-medium">{t("sprint.completeDialog.deleteTickets")}</span>
                    <p className="text-muted-foreground mt-1 text-xs">
                      {t("sprint.completeDialog.deleteTicketsDesc")}
                    </p>
                  </div>
                </label>
              </RadioGroup>

              {/* Sprint selector when MOVE_TO_SPRINT */}
              {ticketAction === "MOVE_TO_SPRINT" && (
                <div className="flex w-full flex-col items-start gap-4 self-stretch pl-6">
                  {hasPlannedSprints ? (
                    <div className="flex w-full flex-col gap-2 self-stretch">
                      <Label className="text-xs">
                        {t("sprint.completeDialog.selectTargetSprint")}
                      </Label>
                      <Select
                        value={form.watch("targetSprintId") ?? ""}
                        onValueChange={(value) =>
                          form.setValue("targetSprintId", value, {
                            shouldValidate: true,
                          })
                        }
                      >
                        <SelectTrigger>
                          <SelectValue
                            placeholder={t("sprint.completeDialog.selectTargetSprint")}
                          />
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
                      description={t("sprint.completeDialog.noPlannedSprints")}
                    />
                  )}
                </div>
              )}
            </div>
          )}

          {/* Warning Alert */}
          <InlineAlert
            variant="warning"
            icon={<AlertCircle />}
            description={
              seriesEnabled
                ? `${t("sprint.completeDialog.warning")} ${t("sprint.completeDialog.seriesStartsNext", { series: series.name })}`
                : t("sprint.completeDialog.warning")
            }
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
          <Button onClick={handleComplete} disabled={isSubmitting || !form.formState.isValid}>
            {isSubmitting ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                {t("sprint.completeDialog.completing")}
              </>
            ) : (
              <>
                <CheckCircle2 className="mr-2 h-4 w-4" />
                {t("sprint.completeDialog.button")}
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

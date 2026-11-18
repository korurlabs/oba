"use client";

import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { SelectFieldStandalone } from "@oba/shared/features/form/components";
import { InlineAlert } from "@/components/ui/inline-alert";
import { Loader2, AlertCircle } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { useBoards } from "@/features/boards/hooks/use-boards";
import { useBoardSelect } from "@/features/boards/hooks/use-board-select";
import { isBoardManageRole } from "@/features/boards/hooks/use-board-role";
import { useSprints } from "@/features/sprints/hooks/use-sprints";
import type { TicketBulkMoveRequest, TicketListResponse } from "../schemas";
import {
  DEFAULT_SUB_TICKET_CHOICE,
  SubTicketChoice,
  hasSubTickets,
  type SubTicketMoveChoice,
} from "./sub-ticket-choice";
import type { BoardListWithChildrenResponse, BoardMemberRole } from "@/features/boards/schemas";

// ── Local form schema ────────────────────────────────────────────

const bulkMoveFormSchema = z
  .object({
    action: z.enum(["MOVE_TO_BACKLOG", "MOVE_TO_SPRINT"]),
    targetSprintId: z.string().optional(),
    targetBoardId: z.string().optional(),
  })
  .superRefine((data, ctx) => {
    if (data.action === "MOVE_TO_SPRINT" && !data.targetSprintId) {
      ctx.addIssue({
        code: "custom",
        path: ["targetSprintId"],
        message: "required",
      });
    }
  });

type BulkMoveFormValues = z.infer<typeof bulkMoveFormSchema>;

/** Whether a selected ticket, at any depth of the loaded rows, has sub-tickets. */
function selectionHasSubTickets(
  tickets: readonly TicketListResponse[] | null | undefined,
  selectedIds: ReadonlySet<string>
): boolean {
  return (tickets ?? []).some(
    (ticket) =>
      (selectedIds.has(ticket.id) && hasSubTickets(ticket)) ||
      selectionHasSubTickets(ticket.subTickets, selectedIds)
  );
}

// ── Props ──────────────────────────────────────────────────────────

interface BulkMoveTicketsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  boardId: string;
  selectedTicketIds: string[];
  onBulkMove: (data: TicketBulkMoveRequest) => Promise<void>;
  isPending: boolean;
  /** Where the dialog is opened from */
  source: "sprint" | "backlog";
  /** The sprint currently being viewed (used to exclude it from target list) */
  currentSprintId?: string;
  /**
   * The loaded rows the selection comes from. The sub-ticket choice shows only
   * when a selected ticket among them has sub-tickets; without rows it always shows.
   */
  tickets?: readonly TicketListResponse[];
}

// ── Component ──────────────────────────────────────────────────────

export function BulkMoveTicketsDialog({
  open,
  onOpenChange,
  boardId,
  selectedTicketIds,
  onBulkMove,
  isPending,
  source,
  currentSprintId,
  tickets,
}: BulkMoveTicketsDialogProps) {
  const t = useTranslations();
  const [boardTarget, setBoardTarget] = useState<"current" | "other">("current");
  const [subTicketChoice, setSubTicketChoice] =
    useState<SubTicketMoveChoice>(DEFAULT_SUB_TICKET_CHOICE);
  const selectedHaveSubTickets = useMemo(
    () => !tickets || selectionHasSubTickets(tickets, new Set(selectedTicketIds)),
    [tickets, selectedTicketIds]
  );
  // A ticket that changes board always takes its whole subtree, so the
  // choice applies only within the current board.
  const showSubTicketChoice = boardTarget === "current" && selectedHaveSubTickets;

  // ── Data fetching ──────────────────────────────────────────────
  const { data: boardsResponse, isLoading: isBoardsLoading } = useBoards({
    includeChildren: true,
    amount: 100,
    enabled: open,
  });
  const allBoards = useMemo(() => boardsResponse?.data ?? [], [boardsResponse?.data]);

  const disabledIds = useMemo(() => new Set([boardId]), [boardId]);
  const disabledHintMap = useMemo(
    () => new Map([[boardId, t("ticket.bulkMove.currentBoardHint")]]),
    [boardId, t],
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

  // ── Form ──────────────────────────────────────────────────────
  const form = useForm<BulkMoveFormValues>({
    resolver: zodResolver(bulkMoveFormSchema),
    mode: "onChange",
    defaultValues: {
      action: "MOVE_TO_BACKLOG",
      targetBoardId: boardId,
    },
  });

  const action = form.watch("action");
  const targetBoardId = form.watch("targetBoardId");

  const effectiveBoardId = boardTarget === "current" ? boardId : (targetBoardId ?? boardId);

  const { data: sprintsResponse } = useSprints(effectiveBoardId, {
    enabled: open,
  });

  // Available sprints: exclude completed sprints and, when staying on the
  // current board, also exclude the sprint that is currently being viewed.
  // When moving to another board we must keep the sprint even if it is
  // linked to both boards — otherwise the list can end up empty despite
  // the API returning valid sprints.
  const availableSprints = useMemo(() => {
    const sprints = sprintsResponse?.data ?? [];
    return sprints.filter(
      (s) =>
        s.status !== "COMPLETED" &&
        (boardTarget === "other" || s.id !== currentSprintId)
    );
  }, [sprintsResponse?.data, currentSprintId, boardTarget]);

  // ── Derived ────────────────────────────────────────────────────
  const canMoveToBacklog = source !== "backlog";
  const hasPlannedSprints = availableSprints.length > 0;

  // ── Effects ─────────────────────────────────────────────────────

  useEffect(() => {
    if (action !== "MOVE_TO_SPRINT") {
      form.setValue("targetSprintId", undefined);
    }
  }, [action, form]);

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
      setSubTicketChoice(DEFAULT_SUB_TICKET_CHOICE);
      form.reset({
        action: "MOVE_TO_BACKLOG",
        targetBoardId: boardId,
      });
    }
  }, [open, form, boardId]);

  // ── Submit ─────────────────────────────────────────────────────

  const handleSubmit = form.handleSubmit(async (values) => {
    await onBulkMove({
      ticketIds: selectedTicketIds,
      action: values.action,
      targetSprintId:
        values.action === "MOVE_TO_SPRINT" ? values.targetSprintId : undefined,
      targetBoardId: boardTarget === "other" ? values.targetBoardId : undefined,
      ...(showSubTicketChoice ? subTicketChoice : {}),
    });
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[450px]">
        <DialogHeader>
          <DialogTitle>{t("ticket.bulkMove.title")}</DialogTitle>
          <DialogDescription>
            {t("ticket.bulkMove.selectedCount", {
              count: selectedTicketIds.length,
            })}
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-4">
          {/* Board target: Current / Other */}
          <div className="flex flex-col gap-2">
            <Label className="text-foreground text-sm font-semibold">
              {t("ticket.bulkMove.selectTargetBoard")}
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
                {t("ticket.bulkMove.currentBoard")}
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
                {t("ticket.bulkMove.otherBoard")}
              </button>
            </div>
          </div>

          {/* Board SearchSelect — only when "Other Board" */}
          {boardTarget === "other" && (
            <SelectFieldStandalone
              value={targetBoardId !== boardId ? targetBoardId ?? "" : ""}
              onValueChange={(id) =>
                form.setValue("targetBoardId", id, { shouldValidate: true })
              }
              options={boardSelectOptions}
              renderOption={renderBoardOption}
              renderValue={renderBoardValue}
              placeholder={t("ticket.bulkMove.selectTargetBoard")}
              searchPlaceholder={t("common.search")}
              isLoading={isBoardsLoading}
            />
          )}

          {/* Action selection */}
          <Label className="text-foreground text-sm font-semibold">
            {t("ticket.bulkMove.selectAction")}
          </Label>

          <RadioGroup
            value={action}
            onValueChange={(v) =>
              form.setValue("action", v as "MOVE_TO_BACKLOG" | "MOVE_TO_SPRINT", {
                shouldValidate: true,
              })
            }
          >
            {/* Move to Backlog */}
            {canMoveToBacklog && (
              <label
                htmlFor="bulk-backlog"
                className="border-border hover:bg-accent flex cursor-pointer items-start gap-2 rounded-lg border p-3 transition-colors"
              >
                <RadioGroupItem value="MOVE_TO_BACKLOG" id="bulk-backlog" className="mt-1" />
                <div className="flex-1">
                  <span className="font-medium">{t("ticket.bulkMove.moveToBacklog")}</span>
                </div>
              </label>
            )}

            {/* Move to Sprint */}
            <label
              htmlFor="bulk-sprint"
              className="border-border hover:bg-accent flex cursor-pointer items-start gap-2 rounded-lg border p-3 transition-colors"
            >
              <RadioGroupItem value="MOVE_TO_SPRINT" id="bulk-sprint" className="mt-1" />
              <div className="flex-1">
                <span className="font-medium">{t("ticket.bulkMove.moveToSprint")}</span>
              </div>
            </label>
          </RadioGroup>

          {/* Sprint selector when MOVE_TO_SPRINT */}
          {action === "MOVE_TO_SPRINT" && (
            <div className="flex flex-col gap-4 items-start w-full self-stretch pl-6">
              {hasPlannedSprints ? (
                <div className="flex flex-col gap-2 w-full self-stretch">
                  <Label className="text-xs">
                    {t("ticket.bulkMove.selectSprint")}
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
                        placeholder={t("ticket.bulkMove.selectSprint")}
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
                  description={t("ticket.bulkMove.noAvailableSprints")}
                />
              )}
            </div>
          )}

          {showSubTicketChoice && (
            <SubTicketChoice
              value={subTicketChoice}
              onChange={setSubTicketChoice}
              allowPick={false}
              idPrefix="bulk-move"
              disabled={isPending}
            />
          )}
        </div>

        <DialogFooter>
          <Button
            variant="ghost"
            onClick={() => onOpenChange(false)}
            disabled={isPending}
          >
            {t("common.cancel")}
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={isPending || !form.formState.isValid}
          >
            {isPending ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : null}
            {t("ticket.bulkMove.confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

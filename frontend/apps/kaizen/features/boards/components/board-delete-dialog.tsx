"use client";

import { useEffect, useMemo } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { AlertTriangle } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { BoardDot } from "@/components/ui/board-dot";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { SelectFieldStandalone } from "@oba/shared/features/form/components";
import { Separator } from "@/components/ui/separator";
import { BoardHierarchyPreview } from "./board-hierarchy-preview";
import { useBoards } from "../hooks/use-boards";
import { useBoardSelect } from "../hooks/use-board-select";
import { useSprints } from "@/features/sprints/hooks/use-sprints";
import {
  boardDeleteFormSchema,
  type BoardDeleteFormValues,
  type BoardDeleteRequest,
  type BoardListWithChildrenResponse,
  type TicketRelocationAction,
} from "../schemas";
import { isBoardManageRole } from "../hooks/use-board-role";
import type { PreviewItem } from "./board-hierarchy-preview";

// ── Props ──────────────────────────────────────────────────────────

interface BoardDeleteDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  boardId: string;
  boardName: string;
  boardPrefix: string;
  boardColor?: string;
  childBoards: BoardListWithChildrenResponse[];
  onConfirm: (request: BoardDeleteRequest) => void;
  isPending: boolean;
}

// ── Helpers ────────────────────────────────────────────────────────

/** Collect all descendant IDs (recursive) from a list of child boards */
function collectDescendantIds(boards: BoardListWithChildrenResponse[]): Set<string> {
  const ids = new Set<string>();
  function walk(list: BoardListWithChildrenResponse[]) {
    for (const b of list) {
      ids.add(b.id);
      if (b.childBoards?.length) {
        walk(b.childBoards as BoardListWithChildrenResponse[]);
      }
    }
  }
  walk(boards);
  return ids;
}

/** Build flat preview items from child boards for BoardHierarchyPreview */
function buildPreviewItems(boards: BoardListWithChildrenResponse[], depth = 0): PreviewItem[] {
  const items: PreviewItem[] = [];
  for (const board of boards) {
    items.push({ board, isSelected: true, previewDepth: depth });
    if (board.childBoards?.length) {
      items.push(
        ...buildPreviewItems(board.childBoards as BoardListWithChildrenResponse[], depth + 1)
      );
    }
  }
  return items;
}

// ── Component ──────────────────────────────────────────────────────

export function BoardDeleteDialog({
  open,
  onOpenChange,
  boardId,
  boardName,
  boardPrefix,
  boardColor,
  childBoards,
  onConfirm,
  isPending,
}: BoardDeleteDialogProps) {
  const t = useTranslations();
  const hasChildren = childBoards.length > 0;

  const form = useForm<BoardDeleteFormValues>({
    resolver: zodResolver(boardDeleteFormSchema),
    mode: "onChange",
    defaultValues: {
      ticketAction: "DELETE",
      childBoardAction: hasChildren ? "DELETE" : undefined,
    },
  });

  const ticketAction = form.watch("ticketAction");
  const targetBoardId = form.watch("targetBoardId");
  const childBoardAction = form.watch("childBoardAction");

  // ── Data fetching ──────────────────────────────────────────────

  const { data: boardsResponse } = useBoards({ includeChildren: true, amount: 100 });
  const allBoards = useMemo(() => boardsResponse?.data ?? [], [boardsResponse?.data]);

  // Build a map of board IDs where the current user lacks Owner/Admin role.
  const insufficientRoleHintMap = useMemo(() => {
    const map = new Map<string, string>();
    const hint = t("board.delete.insufficientRole");
    function walk(boards: BoardListWithChildrenResponse[]) {
      for (const b of boards) {
        if (b.currentUserRole !== undefined && !isBoardManageRole(b.currentUserRole)) {
          map.set(b.id, hint);
        }
        if (b.childBoards?.length) {
          walk(b.childBoards as BoardListWithChildrenResponse[]);
        }
      }
    }
    walk(allBoards);
    return map;
  }, [allBoards, t]);

  // Ticket move: excludes the board being deleted
  const ticketExcludeIds = useMemo(() => new Set([boardId]), [boardId]);
  const {
    options: ticketMoveBoardOptions,
    renderBoardOption,
    renderBoardValue,
  } = useBoardSelect(allBoards, {
    excludeIds: ticketExcludeIds,
    disabledHintMap: insufficientRoleHintMap,
  });

  // Parent move: excludes current board + all descendants
  const parentExcludeIds = useMemo(() => {
    const ids = collectDescendantIds(childBoards);
    ids.add(boardId);
    return ids;
  }, [childBoards, boardId]);
  const { options: parentMoveBoardOptions } = useBoardSelect(allBoards, {
    excludeIds: parentExcludeIds,
    disabledHintMap: insufficientRoleHintMap,
  });

  // Descendant IDs of the board being deleted (for detecting child selection)
  const descendantIds = useMemo(() => collectDescendantIds(childBoards), [childBoards]);

  // Whether the selected target board is one of the deleting board's children
  const isTargetBoardAChild = useMemo(
    () => !!targetBoardId && descendantIds.has(targetBoardId),
    [targetBoardId, descendantIds]
  );

  // Sprints for the selected target board (tickets move destination)
  const { data: sprintsResponse } = useSprints(targetBoardId ?? "", {
    enabled: !!targetBoardId && ticketAction === "MOVE_TO_SPRINT",
  });
  const availableSprints = useMemo(() => {
    const sprints = sprintsResponse?.data ?? [];
    return sprints.filter((s) => s.status !== "COMPLETED");
  }, [sprintsResponse?.data]);

  // Preview items for the hierarchy preview
  const previewItems = useMemo(() => buildPreviewItems(childBoards), [childBoards]);

  // Build the parent board object for BoardHierarchyPreview
  const currentBoardForPreview = useMemo(() => {
    function find(boards: BoardListWithChildrenResponse[]): BoardListWithChildrenResponse | null {
      for (const b of boards) {
        if (b.id === boardId) return b;
        if (b.childBoards?.length) {
          const found = find(b.childBoards as BoardListWithChildrenResponse[]);
          if (found) return found;
        }
      }
      return null;
    }
    return (
      find(allBoards) ?? {
        id: boardId,
        createdAt: "",
        name: boardName,
        slug: "",
        prefix: "",
      }
    );
  }, [allBoards, boardId, boardName]);

  // ── Effects ─────────────────────────────────────────────────────

  // Reset dependent fields when ticket action changes
  useEffect(() => {
    const isMove =
      ticketAction === "MOVE_TO_BACKLOG" || ticketAction === "MOVE_TO_SPRINT";
    if (!isMove) {
      form.setValue("targetBoardId", undefined);
      form.setValue("targetSprintId", undefined);
    }
    if (ticketAction !== "MOVE_TO_SPRINT") {
      form.setValue("targetSprintId", undefined);
    }
  }, [ticketAction, form]);

  // Reset sprint fields when target board changes
  useEffect(() => {
    form.setValue("targetSprintId", undefined);
  }, [targetBoardId, form]);

  // Reset newParentBoardId when child board action changes
  useEffect(() => {
    if (childBoardAction !== "MOVE") {
      form.setValue("newParentBoardId", undefined);
    }
  }, [childBoardAction, form]);

  // Auto-select PROMOTE when the target board is one of the deleting board's children
  useEffect(() => {
    if (isTargetBoardAChild && hasChildren) {
      form.setValue("childBoardAction", "PROMOTE", { shouldValidate: true });
    }
  }, [isTargetBoardAChild, hasChildren, form]);

  // Reset form when dialog opens
  useEffect(() => {
    if (open) {
      form.reset({
        ticketAction: "DELETE",
        childBoardAction: hasChildren ? "DELETE" : undefined,
      });
    }
  }, [open, hasChildren, form]);

  // ── Submit ─────────────────────────────────────────────────────

  const handleConfirm = form.handleSubmit((values) => {
    const isMove =
      values.ticketAction === "MOVE_TO_BACKLOG" ||
      values.ticketAction === "MOVE_TO_SPRINT";
    const request: BoardDeleteRequest = {
      ticketAction: values.ticketAction,
      targetBoardId: isMove ? values.targetBoardId : undefined,
      targetSprintId:
        values.ticketAction === "MOVE_TO_SPRINT" ? values.targetSprintId : undefined,
      childBoardAction: hasChildren ? values.childBoardAction : undefined,
      newParentBoardId:
        values.childBoardAction === "MOVE" ? values.newParentBoardId : undefined,
    };
    onConfirm(request);
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {/* The 512px minimum would push the dialog past both edges of a phone,
        so it and the 80vw cap start at `md`; below it the dialog keeps the
        phone gutter of `DialogContent`. */}
      <DialogContent className="max-h-[90vh] overflow-y-auto md:max-w-[80vw] md:min-w-lg">
        <DialogHeader>
          {/* The title wraps on a phone, and its right padding keeps the
            board badge clear of the close button. */}
          <DialogTitle className="flex flex-wrap items-center gap-2 pr-8">
            <AlertTriangle className="text-destructive h-5 w-5" />
            {t("board.delete.title")}
            <span className="text-muted-foreground">—</span>
            <BoardDot color={boardColor} />
            <span className="font-normal">{boardName}</span>
            <Badge variant="secondary" className="text-xs font-normal">
              {boardPrefix}
            </Badge>
          </DialogTitle>
          <DialogDescription>{t("board.delete.description")}</DialogDescription>
        </DialogHeader>

        {/* Without `min-w-0` the dialog's grid column grows to the full width
          of the longest sub-board name, pushing the buttons off a phone. */}
        <div className="flex min-w-0 flex-col gap-6 py-2">
          {/* ── Section 1: Ticket Action ────────────────────────── */}
          <div className="flex flex-col gap-3 items-start">
            <Label className="text-sm font-medium">
              {t("board.delete.ticketAction.sectionTitle")}
            </Label>
            <RadioGroup
              value={ticketAction}
              onValueChange={(value) =>
                form.setValue("ticketAction", value as TicketRelocationAction, {
                  shouldValidate: true,
                })
              }
            >
              <div className="flex items-center space-x-2">
                <RadioGroupItem value="DELETE" id="ticket-delete" />
                <Label htmlFor="ticket-delete" className="font-normal">
                  {t("board.delete.ticketAction.delete")}
                </Label>
              </div>
              <div className="flex items-center space-x-2">
                <RadioGroupItem value="MOVE_TO_BACKLOG" id="ticket-move-backlog" />
                <Label htmlFor="ticket-move-backlog" className="font-normal">
                  {t("board.delete.ticketAction.moveToBacklog")}
                </Label>
              </div>
              <div className="flex items-center space-x-2">
                <RadioGroupItem value="MOVE_TO_SPRINT" id="ticket-move-sprint" />
                <Label htmlFor="ticket-move-sprint" className="font-normal">
                  {t("board.delete.ticketAction.moveToSprint")}
                </Label>
              </div>
            </RadioGroup>

            {/* Move tickets sub-section */}
            {(ticketAction === "MOVE_TO_BACKLOG" || ticketAction === "MOVE_TO_SPRINT") && (
              <div className="flex flex-col gap-4 items-start w-full self-stretch pl-6">
                <div className="flex flex-col gap-2 items-start w-full">
                  <Label className="text-xs">{t("board.delete.destination.targetBoard")}</Label>
                  <SelectFieldStandalone
                    value={targetBoardId ?? ""}
                    onValueChange={(value) =>
                      form.setValue("targetBoardId", value, {
                        shouldValidate: true,
                      })
                    }
                    options={ticketMoveBoardOptions}
                    renderOption={renderBoardOption}
                    renderValue={renderBoardValue}
                    placeholder={t("board.delete.destination.targetBoardPlaceholder")}
                    searchPlaceholder={t("common.search")}
                  />
                </div>

                {/* Sprint selector — only when MOVE_TO_SPRINT */}
                {ticketAction === "MOVE_TO_SPRINT" && targetBoardId && (
                  <div className="flex flex-col gap-2 items-start w-full">
                    <Label className="text-xs">{t("board.delete.destination.sprint")}</Label>
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
                          placeholder={t("board.delete.destination.sprintPlaceholder")}
                        />
                      </SelectTrigger>
                      <SelectContent>
                        {availableSprints.map((sprint) => (
                          <SelectItem key={sprint.id} value={sprint.id}>
                            {sprint.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* ── Section 2: Child Board Action ────────────────────── */}
          {hasChildren && (
            <>
              <Separator />
              <div className="flex flex-col gap-3 items-start w-full self-stretch">
                <Label className="text-sm font-medium">
                  {t("board.delete.childBoardAction.title")}
                </Label>

                <BoardHierarchyPreview
                  parentBoard={currentBoardForPreview}
                  items={previewItems}
                  readOnly
                  showParentInfo={false}
                />

                <RadioGroup
                  value={childBoardAction ?? "DELETE"}
                  onValueChange={(value) =>
                    form.setValue("childBoardAction", value as "PROMOTE" | "MOVE" | "DELETE", {
                      shouldValidate: true,
                    })
                  }
                >
                  <div className="flex items-center space-x-2">
                    <RadioGroupItem value="DELETE" id="child-delete" disabled={isTargetBoardAChild} />
                    <Label htmlFor="child-delete" className={cn("font-normal", isTargetBoardAChild && "text-muted-foreground")}>
                      {t("board.delete.childBoardAction.delete")}
                    </Label>
                  </div>
                  <div className="flex items-center space-x-2">
                    <RadioGroupItem value="PROMOTE" id="child-promote" />
                    <Label htmlFor="child-promote" className="font-normal">
                      {t("board.delete.childBoardAction.promote")}
                    </Label>
                  </div>
                  <div className="flex items-center space-x-2">
                    <RadioGroupItem value="MOVE" id="child-move" disabled={isTargetBoardAChild} />
                    <Label htmlFor="child-move" className={cn("font-normal", isTargetBoardAChild && "text-muted-foreground")}>
                      {t("board.delete.childBoardAction.move")}
                    </Label>
                  </div>
                </RadioGroup>

                {childBoardAction === "MOVE" && (
                  <div className="flex flex-col gap-2 items-start pl-6 w-full">
                    <SelectFieldStandalone
                      value={form.watch("newParentBoardId") ?? ""}
                      onValueChange={(value) =>
                        form.setValue("newParentBoardId", value, {
                          shouldValidate: true,
                        })
                      }
                      options={parentMoveBoardOptions}
                      renderOption={renderBoardOption}
                      renderValue={renderBoardValue}
                      placeholder={t("board.delete.childBoardAction.newParentPlaceholder")}
                      searchPlaceholder={t("common.search")}
                    />
                  </div>
                )}
              </div>
            </>
          )}
        </div>

        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={isPending}>
            {t("common.cancel")}
          </Button>
          <Button
            variant="destructive"
            onClick={handleConfirm}
            disabled={isPending || !form.formState.isValid}
          >
            {isPending ? t("board.settings.deleting") : t("board.delete.confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

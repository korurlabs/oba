/**
 * EditSprintDialog Component
 *
 * Tabbed dialog for editing an existing sprint.
 * - General tab: edit sprint name, goal, and dates
 * - Boards tab: view linked boards, link new boards, unlink boards
 * - Automation tab: repeat the sprint automatically, and the last run's status
 *
 * While the sprint's automation is on, the General tab shows and sends its
 * dates as calendar days at 00:00 UTC, the only time the server accepts for
 * an automated sprint.
 *
 * A sprint of an enabled series is named "{series name} {round}". Its name
 * field edits the series name, with the round as a fixed suffix, and saving
 * renames every unfinished sprint of the series. A completed sprint keeps
 * its name, so there the field is read-only.
 *
 * Follows the same tabbed dialog pattern as board-settings-dialog.
 */

"use client";

import { useState, useMemo, useCallback } from "react";
import { useTranslations } from "next-intl";
import { useFormContext } from "react-hook-form";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { TextField, TextareaField, DateField } from "@/components/ui/form-fields";
import { SelectFieldStandalone } from "@oba/shared/features/form/components";
import { FormGrid, ObaForm } from "@oba/shared/features/form";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Loader2, Settings, Workflow, Plus, Link2, X, Repeat, Globe } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { BoardDot } from "@/components/ui/board-dot";
import {
  buildEditSprintFormSchema,
  SprintStatus,
  type EditSprintFormValues,
} from "@/features/sprints/schemas";
import type { SprintUpdateRequest } from "@/features/sprints/schemas";
import {
  useSprint,
  useBulkLinkSprint,
  useUnlinkSprint,
} from "@/features/sprints/hooks/use-sprints";
import { useBoards } from "@/features/boards/hooks/use-boards";
import { useBoardSelect } from "@/features/boards/hooks/use-board-select";
import type { BoardListResponse } from "@/features/boards/schemas";
import type { BoardListWithChildrenResponse } from "@/features/boards/schemas";
import type { SprintUnlinkRequest } from "@/features/sprints/schemas";
import { UnlinkSprintDialog } from "./unlink-sprint-dialog";
import { SprintRecurrenceTab } from "./sprint-recurrence-tab";
import { fromUtcCalendarDate, toUtcCalendarDate } from "@/features/sprints/lib/sprint-recurrence";

interface EditSprintDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  boardId: string;
  sprint: {
    id: string;
    name: string;
    goal?: string | null;
    startDate?: string;
    endDate?: string;
  };
  onUpdateSprint: (data: SprintUpdateRequest) => Promise<void>;
}

export function EditSprintDialog({
  open,
  onOpenChange,
  boardId,
  sprint,
  onUpdateSprint,
}: EditSprintDialogProps) {
  const t = useTranslations("sprint");
  const tCommon = useTranslations("common");
  const [isSubmitting, setIsSubmitting] = useState(false);

  // The list row has no automation settings; the detail (shared with the
  // Boards and Automation tabs through the query cache) says whether the
  // dates are UTC calendar days.
  const { data: sprintDetail, isLoading: isDetailLoading } = useSprint(boardId, sprint.id);
  const series = sprintDetail?.data?.series ?? null;
  const round = sprintDetail?.data?.round ?? null;
  const utcDates = series?.enabled ?? false;
  // The server refuses a plain `name` for any sprint of an enabled series.
  const namedBySeries = utcDates && round !== null;
  const isCompleted = sprintDetail?.data?.status === SprintStatus.COMPLETED;
  const editsSeriesName = namedBySeries && !isCompleted;
  const schema = useMemo(() => buildEditSprintFormSchema(editsSeriesName), [editsSeriesName]);

  const toFormDate = (value: string | undefined) => {
    if (!value) return new Date();
    return utcDates ? fromUtcCalendarDate(value) : new Date(value);
  };
  const toRequestDate = (value: Date) =>
    (utcDates ? toUtcCalendarDate(value) : value).toISOString();

  const handleSubmit = async (data: EditSprintFormValues) => {
    setIsSubmitting(true);
    try {
      const seriesName = data.name.trim();
      await onUpdateSprint({
        name: namedBySeries ? undefined : data.name,
        seriesName: editsSeriesName && seriesName !== series?.name ? seriesName : undefined,
        goal: data.goal,
        startDate: toRequestDate(data.startDate),
        endDate: toRequestDate(data.endDate),
      });
      onOpenChange(false);
    } catch {
      // Error is handled by the mutation's onError callback
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t("editTitle")}</DialogTitle>
          <DialogDescription>{t("editDescription")}</DialogDescription>
        </DialogHeader>

        <Tabs defaultValue="general" className="w-full">
          <TabsList className="grid w-full grid-cols-3">
            <TabsTrigger value="general" className="gap-2">
              <Settings className="h-4 w-4 shrink-0" />
              {t("tabs.general")}
            </TabsTrigger>
            <TabsTrigger value="boards" className="gap-2">
              <Workflow className="h-4 w-4 shrink-0" />
              {t("tabs.boards")}
            </TabsTrigger>
            <TabsTrigger value="automation" className="gap-2">
              <Repeat className="h-4 w-4 shrink-0" />
              {t("tabs.automation")}
            </TabsTrigger>
          </TabsList>

          <TabsContent value="general">
            {isDetailLoading ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="text-muted-foreground h-5 w-5 animate-spin" />
              </div>
            ) : (
              <ObaForm
                schema={schema}
                onSubmit={handleSubmit}
                defaultValues={{
                  name: editsSeriesName && series ? series.name : sprint.name,
                  goal: sprint.goal ?? "",
                  startDate: toFormDate(sprint.startDate),
                  endDate: toFormDate(sprint.endDate),
                }}
                className="flex flex-col gap-4"
              >
                <EditSprintFormFields
                  isSubmitting={isSubmitting}
                  utcDates={utcDates}
                  round={editsSeriesName ? round : null}
                  nameReadOnly={namedBySeries && isCompleted}
                />

                <DialogFooter>
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() => onOpenChange(false)}
                    disabled={isSubmitting}
                  >
                    {tCommon("cancel")}
                  </Button>
                  <Button type="submit" disabled={isSubmitting}>
                    {isSubmitting ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        {t("saving")}
                      </>
                    ) : (
                      t("save")
                    )}
                  </Button>
                </DialogFooter>
              </ObaForm>
            )}
          </TabsContent>

          <TabsContent value="boards" className="py-4">
            <SprintLinkedBoards boardId={boardId} sprintId={sprint.id} />
          </TabsContent>

          <TabsContent value="automation">
            <SprintRecurrenceTab
              boardId={boardId}
              sprintId={sprint.id}
              onCancel={() => onOpenChange(false)}
            />
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  );
}

function EditSprintFormFields({
  isSubmitting,
  utcDates,
  round,
  nameReadOnly,
}: {
  isSubmitting: boolean;
  utcDates: boolean;
  /** Set when the name field holds the series name; shown as a fixed suffix. */
  round: number | null;
  /** A completed sprint of an enabled series keeps its full name. */
  nameReadOnly: boolean;
}) {
  const t = useTranslations("sprint");
  const tCommon = useTranslations("common");
  const { control, watch } = useFormContext<EditSprintFormValues>();
  const startDate = watch("startDate");

  return (
    <FormGrid className="py-2 md:grid-cols-1">
      <TextField
        name="name"
        label={t("name")}
        placeholder={t("namePlaceholder")}
        control={control}
        required
        disabled={isSubmitting}
        readOnly={nameReadOnly}
        suffix={round !== null ? ` ${round}` : undefined}
        tooltipText={round !== null ? t("seriesNameEditTooltip", { round }) : undefined}
      />

      <TextareaField
        name="goal"
        label={t("goal")}
        placeholder={t("goalPlaceholder")}
        description={t("goalDescription")}
        control={control}
        rows={3}
        className="resize-none"
        disabled={isSubmitting}
      />

      <div className="grid grid-cols-2 gap-4">
        <DateField
          control={control}
          name="startDate"
          label={t("startDate")}
          required
          placeholder={tCommon("selectDate")}
          dateFormat="MMM d, yyyy"
        />

        <DateField
          control={control}
          name="endDate"
          label={t("endDate")}
          required
          placeholder={tCommon("selectDate")}
          dateFormat="MMM d, yyyy"
          fromDate={startDate ?? undefined}
        />
      </div>

      {utcDates && (
        <p className="text-muted-foreground -mt-2 flex items-start gap-2 text-xs">
          <Globe className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          <span>{t("recurrence.utcDatesNote")}</span>
        </p>
      )}
    </FormGrid>
  );
}

// ── Boards Tab ───────────────────────────────────────────────────────

interface SprintLinkedBoardsProps {
  boardId: string;
  sprintId: string;
}

function SprintLinkedBoards({ boardId, sprintId }: SprintLinkedBoardsProps) {
  const t = useTranslations("sprint");
  const [showLinkDialog, setShowLinkDialog] = useState(false);
  const [unlinkTarget, setUnlinkTarget] = useState<{ boardId: string; boardName: string } | null>(
    null
  );

  const { data: sprintDetail, isLoading } = useSprint(boardId, sprintId);
  const unlinkSprintMutation = useUnlinkSprint();

  // Fetch the full board tree so we can display linked boards in hierarchy
  const { data: boardsResponse } = useBoards({ includeChildren: true, amount: 100 });
  const allBoards = useMemo(() => boardsResponse?.data ?? [], [boardsResponse?.data]);

  const linkedBoards: BoardListResponse[] = useMemo(
    () => sprintDetail?.data?.boards ?? [],
    [sprintDetail?.data?.boards]
  );

  const linkedBoardIds = useMemo(() => new Set(linkedBoards.map((b) => b.id)), [linkedBoards]);

  // Build a flat, depth-aware list of linked boards from the full tree
  // so their parent-child relationships are visible
  const linkedBoardItems = useMemo(() => {
    const items: Array<{ board: BoardListResponse; depth: number }> = [];

    function walk(boards: BoardListWithChildrenResponse[], depth: number) {
      for (const board of boards) {
        if (linkedBoardIds.has(board.id)) {
          items.push({ board, depth });
        }
        if (board.childBoards?.length) {
          walk(board.childBoards as BoardListWithChildrenResponse[], depth + 1);
        }
      }
    }

    walk(allBoards, 0);
    return items;
  }, [allBoards, linkedBoardIds]);

  const handleUnlinkConfirm = useCallback(
    (data: SprintUnlinkRequest) => {
      if (!unlinkTarget) return;
      unlinkSprintMutation.mutate(
        {
          boardId: unlinkTarget.boardId,
          sprintId,
          data,
        },
        {
          onSuccess: () => {
            setUnlinkTarget(null);
          },
        }
      );
    },
    [sprintId, unlinkSprintMutation, unlinkTarget]
  );

  if (isLoading) {
    return (
      <div className="bg-card flex items-center justify-center rounded-lg border p-4">
        <Loader2 className="text-muted-foreground h-5 w-5 animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <span className="text-foreground text-sm font-medium">{t("boards.title")}</span>
        <Button
          size="sm"
          variant="ghost"
          onClick={() => setShowLinkDialog(true)}
          className="h-7 text-xs"
        >
          <Plus className="h-3 w-3" />
          {t("boards.linkBoard")}
        </Button>
      </div>

      {linkedBoardItems.length > 0 ? (
        <div className="rounded-lg border">
          {linkedBoardItems.map(({ board, depth }) => (
            <div
              key={board.id}
              className="flex items-center gap-2 border-b px-3 py-2.5 last:border-b-0"
              style={{ paddingLeft: `${depth * 16 + 12}px` }}
            >
              {depth > 0 && <span className="text-muted-foreground text-xs">{"\u21B3"}</span>}
              <BoardDot color={board.color} />
              <span className="flex-1 truncate text-sm font-medium">{board.name}</span>
              <Badge variant="secondary" className="text-xs">
                {board.prefix}
              </Badge>
              {board.id === boardId ? (
                <Badge variant="outline" className="text-muted-foreground text-[10px]">
                  {t("boards.currentBoard")}
                </Badge>
              ) : (
                <button
                  type="button"
                  onClick={() => setUnlinkTarget({ boardId: board.id, boardName: board.name })}
                  disabled={unlinkSprintMutation.isPending}
                  className="text-muted-foreground hover:text-foreground hover:bg-muted flex h-5 w-5 shrink-0 items-center justify-center transition-colors"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              )}
            </div>
          ))}
        </div>
      ) : (
        <div className="py-6 text-center">
          <EmptyState
            Icon={Link2}
            title={t("boards.empty")}
            description={t("boards.emptyDescription")}
            action={{
              label: t("boards.linkBoard"),
              onClick: () => setShowLinkDialog(true),
            }}
          />
        </div>
      )}

      <LinkBoardToSprintDialog
        open={showLinkDialog}
        onOpenChange={setShowLinkDialog}
        boardId={boardId}
        sprintId={sprintId}
        linkedBoardIds={linkedBoards.map((b) => b.id)}
      />

      {unlinkTarget && (
        <UnlinkSprintDialog
          open={!!unlinkTarget}
          onOpenChange={(isOpen) => {
            if (!isOpen) setUnlinkTarget(null);
          }}
          boardId={unlinkTarget.boardId}
          sprintId={sprintId}
          boardName={unlinkTarget.boardName}
          onUnlink={handleUnlinkConfirm}
        />
      )}
    </div>
  );
}

// ── Link Board Dialog ────────────────────────────────────────────────

interface LinkBoardToSprintDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  boardId: string;
  sprintId: string;
  linkedBoardIds: string[];
}

function LinkBoardToSprintDialog({
  open,
  onOpenChange,
  boardId,
  sprintId,
  linkedBoardIds,
}: LinkBoardToSprintDialogProps) {
  const t = useTranslations("sprint");
  const tCommon = useTranslations("common");

  const [selectedBoardIds, setSelectedBoardIds] = useState<string[]>([]);
  const bulkLinkSprintMutation = useBulkLinkSprint();

  const { data: boardsResponse } = useBoards({
    includeChildren: true,
    amount: 100,
  });

  const allBoards = useMemo(() => boardsResponse?.data ?? [], [boardsResponse?.data]);

  // Disable already-linked boards in the dropdown (show them, but make them unselectable
  // with a localized "already linked" hint).
  const disabledIds = useMemo(() => new Set(linkedBoardIds), [linkedBoardIds]);

  const disabledHintMap = useMemo(() => {
    const hint = t("boards.alreadyLinked");
    return new Map(linkedBoardIds.map((id) => [id, hint]));
  }, [linkedBoardIds, t]);

  const {
    options: selectOptions,
    renderBoardOption,
    renderBoardValue,
  } = useBoardSelect(allBoards, {
    disabledIds,
    disabledHintMap,
  });

  const handleSubmit = useCallback(async () => {
    if (selectedBoardIds.length === 0) return;
    try {
      await bulkLinkSprintMutation.mutateAsync({
        boardId,
        sprintId,
        data: { boardIds: selectedBoardIds },
      });
      setSelectedBoardIds([]);
      onOpenChange(false);
    } catch {
      // Error is handled by the mutation's onError callback
    }
  }, [selectedBoardIds, boardId, sprintId, bulkLinkSprintMutation, onOpenChange]);

  const handleClose = (isOpen: boolean) => {
    if (!bulkLinkSprintMutation.isPending) {
      if (!isOpen) {
        setSelectedBoardIds([]);
      }
      onOpenChange(isOpen);
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[480px]">
        <DialogHeader>
          <DialogTitle>{t("boards.linkBoards")}</DialogTitle>
          <DialogDescription>{t("boards.emptyDescription")}</DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <SelectFieldStandalone
            isMulti
            value={selectedBoardIds}
            onValueChange={setSelectedBoardIds}
            options={selectOptions}
            renderOption={renderBoardOption}
            renderValue={renderBoardValue}
            placeholder={t("boards.selectBoards")}
            searchPlaceholder={tCommon("search")}
            disabled={bulkLinkSprintMutation.isPending}
          />
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={() => handleClose(false)}
            disabled={bulkLinkSprintMutation.isPending}
          >
            {tCommon("cancel")}
          </Button>
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={selectedBoardIds.length === 0 || bulkLinkSprintMutation.isPending}
          >
            {bulkLinkSprintMutation.isPending ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                {t("boards.linking")}
              </>
            ) : (
              t("boards.linkBoards")
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

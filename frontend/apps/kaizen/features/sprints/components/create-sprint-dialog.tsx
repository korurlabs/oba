/**
 * CreateSprintDialog Component
 *
 * Dialog for creating a new sprint, with its automation settings below the
 * dates. While automation is on, the picked dates are sent as calendar days
 * at 00:00 UTC, the only time the server accepts for an automated sprint,
 * and the typed name is the series name: the sprint is named "{name} 1" and
 * the ones the series creates "{name} 2", "{name} 3".
 * When the board has descendant boards, the user also picks which of them
 * get the same sprint; the server links them in the same request.
 * When the board or a ticked child board already runs an active sprint, the
 * user chooses between taking those sprints over (close them, move their
 * unfinished tickets in and start the new sprint) and only planning it.
 *
 * The header and the footer stay fixed and only the fields scroll, so the
 * create and cancel buttons stay in view however long the form grows.
 */

"use client";

import { useEffect, useMemo, useState } from "react";
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
import { TextField, TextareaField, DateField } from "@/components/ui/form-fields";
import { FormGrid, ObaForm } from "@oba/shared/features/form";
import { Button } from "@/components/ui/button";
import { Loader2, AlertCircle, Globe } from "lucide-react";
import { addDays } from "date-fns";
import { InlineAlert } from "@/components/ui/inline-alert";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { useCachedBoard } from "@/features/boards/hooks/use-boards";
import {
  createSprintFormSchema,
  DEFAULT_SPRINT_RECURRENCE_FIELDS,
  type CreateSprintFormValues,
  SPRINT_MAX_CHILD_BOARDS,
  type SprintChildBoardResponse,
  type SprintSeriesRequest,
  SprintStatus,
} from "@/features/sprints/schemas";
import { useSprintChildBoards, useSprints } from "@/features/sprints/hooks/use-sprints";
import {
  calendarDaysBetween,
  toSprintSeriesRequest,
  toUtcCalendarDate,
} from "@/features/sprints/lib/sprint-recurrence";
import { SprintRecurrenceFields } from "./sprint-recurrence-fields";
import { SprintChildBoardsField, preselectedChildBoardIds } from "./sprint-child-boards-field";
import { SprintTakeoverNotice, type SprintTakeoverConflict } from "./sprint-takeover-notice";

export interface CreateSprintData {
  name: string;
  goal?: string;
  startDate: Date;
  endDate: Date;
  /** Present only when automation is switched on; `name` is then the series name. */
  series?: SprintSeriesRequest;
  /** Descendant boards that get the sprint too; empty for none. */
  childBoardIds: string[];
  /**
   * True to close the blocking active sprints and start the new one, false
   * to only plan it; absent when no selected board has an active sprint.
   */
  takeOver?: boolean;
}

interface CreateSprintDialogProps {
  open: boolean;
  /**
   * The board the sprint is created on; its descendants are offered as child
   * boards. Without it the dialog offers none.
   */
  boardId?: string;
  onOpenChange: (open: boolean) => void;
  onCreateSprint: (data: CreateSprintData) => Promise<void>;
}

export function CreateSprintDialog({
  open,
  boardId,
  onOpenChange,
  onCreateSprint,
}: CreateSprintDialogProps) {
  const childBoardsQuery = useSprintChildBoards(boardId ?? "", { enabled: open });
  // The board's own active sprints come from the sprint list the page already
  // holds, so the takeover choice needs no extra request.
  const sprintsQuery = useSprints(boardId ?? "", { enabled: open });
  const { data: board } = useCachedBoard(boardId ?? "");
  const sprintList = sprintsQuery.data?.data;
  const boardConflicts = useMemo((): SprintTakeoverConflict[] => {
    if (!boardId) return [];
    return (sprintList ?? [])
      .filter((sprint) => sprint.status === SprintStatus.ACTIVE)
      .map((sprint) => ({
        boardId,
        boardName: board?.name ?? "",
        sprintId: sprint.id,
        sprintName: sprint.name,
        movesToBacklog: false,
        // Only a board admin can open this dialog.
        canManage: true,
        endsSeriesName: sprint.endsSeriesName,
      }));
  }, [boardId, board?.name, sprintList]);
  // The preselected child boards are part of the form defaults, so the form
  // waits for the list; a form shown earlier would be reset when it arrives
  // and lose what the user typed. A failed load shows the form without them.
  const childBoardsLoading = !!boardId && childBoardsQuery.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[90vh] flex-col gap-0 overflow-hidden p-0 sm:max-w-[500px]">
        {childBoardsLoading ? (
          <>
            <CreateSprintDialogHeader />
            <div className="flex justify-center py-8">
              <Loader2 className="text-muted-foreground h-6 w-6 animate-spin" />
            </div>
          </>
        ) : (
          <CreateSprintForm
            onOpenChange={onOpenChange}
            onCreateSprint={onCreateSprint}
            boardConflicts={boardConflicts}
            childBoards={childBoardsQuery.data?.data}
            childBoardsFailed={childBoardsQuery.isError}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

/**
 * The active sprints the new sprint would take over: the board's own ones,
 * those of each ticked child board, and, as backlog rows, each unticked child
 * board linked to one of them, whose unfinished tickets go to its backlog.
 */
/** Days between the default start and end date of a new sprint. */
const DEFAULT_SPRINT_LENGTH_DAYS = 14;

/** The round the first sprint of a new series gets, shown after the name. */
const FIRST_ROUND_SUFFIX = " 1";

function takeoverConflicts(
  boardConflicts: SprintTakeoverConflict[],
  childBoards: SprintChildBoardResponse[] | undefined,
  childBoardIds: string[]
): SprintTakeoverConflict[] {
  const ticked = new Set(childBoardIds);
  const toConflict = (
    child: SprintChildBoardResponse,
    sprintId: string,
    movesToBacklog: boolean
  ): SprintTakeoverConflict => ({
    boardId: child.id,
    boardName: child.name,
    sprintId,
    sprintName: child.activeSprintName ?? "",
    movesToBacklog,
    canManage: child.canLink,
    endsSeriesName: child.activeSprintEndsSeriesName,
  });

  const conflicts = [...boardConflicts];
  for (const child of childBoards ?? []) {
    if (child.activeSprintId && ticked.has(child.id)) {
      conflicts.push(toConflict(child, child.activeSprintId, false));
    }
  }
  const blockingSprintIds = new Set(conflicts.map((conflict) => conflict.sprintId));
  for (const child of childBoards ?? []) {
    if (
      child.activeSprintId &&
      !ticked.has(child.id) &&
      blockingSprintIds.has(child.activeSprintId)
    ) {
      conflicts.push(toConflict(child, child.activeSprintId, true));
    }
  }
  return conflicts;
}

/** The server refuses a takeover unless the user is admin of every listed board. */
function canTakeOver(conflicts: SprintTakeoverConflict[]): boolean {
  return conflicts.every((conflict) => conflict.canManage);
}

/**
 * Mounted once per open, because the dialog content unmounts on close. The
 * defaults are fixed for that mount: ObaForm resets the form whenever its
 * defaults change, so a new date on re-render or a refetched child-board list
 * would discard the user's edits.
 */
function CreateSprintForm({
  onOpenChange,
  onCreateSprint,
  boardConflicts,
  childBoards,
  childBoardsFailed,
}: Omit<CreateSprintDialogProps, "open" | "boardId"> & {
  /** The board's own active sprints, as takeover rows. */
  boardConflicts: SprintTakeoverConflict[];
  /** Undefined when the board has no list to offer or the load failed. */
  childBoards: SprintChildBoardResponse[] | undefined;
  childBoardsFailed: boolean;
}) {
  const t = useTranslations("sprint");
  const tCommon = useTranslations("common");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [defaultValues] = useState<CreateSprintFormValues>(() => {
    const today = new Date();
    const endDate = addDays(today, DEFAULT_SPRINT_LENGTH_DAYS);
    return {
      name: "",
      goal: "",
      startDate: today,
      endDate,
      recurrence: {
        ...DEFAULT_SPRINT_RECURRENCE_FIELDS,
        lengthDays: calendarDaysBetween(today, endDate),
      },
      childBoardIds: childBoards
        ? preselectedChildBoardIds(childBoards).slice(0, SPRINT_MAX_CHILD_BOARDS)
        : [],
      takeOver: true,
    };
  });

  const handleSubmit = async (data: CreateSprintFormValues) => {
    setIsSubmitting(true);
    try {
      const automated = data.recurrence.enabled;
      const datesLength =
        calendarDaysBetween(data.startDate, data.endDate) ?? DEFAULT_SPRINT_LENGTH_DAYS;
      const conflicts = takeoverConflicts(boardConflicts, childBoards, data.childBoardIds);
      await onCreateSprint({
        name: data.name,
        goal: data.goal,
        startDate: automated ? toUtcCalendarDate(data.startDate) : data.startDate,
        endDate: automated ? toUtcCalendarDate(data.endDate) : data.endDate,
        series: automated ? toSprintSeriesRequest(data.recurrence, datesLength) : undefined,
        childBoardIds: data.childBoardIds,
        takeOver: conflicts.length > 0 ? canTakeOver(conflicts) && data.takeOver : undefined,
      });
      onOpenChange(false);
    } catch (error) {
      // rule-10-no-op -- reason: `onCreateSprint` resolves to useCreateSprint's
      // `mutateAsync`, whose onError already surfaces the failure as a toast.
      // This catch only keeps the dialog open (skipping `onOpenChange(false)`)
      // and stops the rejected promise from becoming an unhandled rejection;
      // the log is kept so the stack is still reachable in the console.
      console.error("Failed to create sprint:", error);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <ObaForm
      schema={createSprintFormSchema}
      onSubmit={handleSubmit}
      defaultValues={defaultValues}
      className="flex min-h-0 flex-1 flex-col"
    >
      <CreateSprintDialogHeader />

      <div className="min-h-0 flex-1 overflow-y-auto px-6">
        <CreateSprintFormFields
          boardConflicts={boardConflicts}
          isSubmitting={isSubmitting}
          childBoards={childBoards}
          childBoardsFailed={childBoardsFailed}
        />
      </div>

      <DialogFooter className="border-border shrink-0 border-t px-6 py-4">
        <Button
          type="button"
          variant="ghost"
          onClick={() => onOpenChange(false)}
          disabled={isSubmitting}
        >
          {tCommon("cancel")}
        </Button>
        <CreateSprintSubmitButton
          isSubmitting={isSubmitting}
          boardConflicts={boardConflicts}
          childBoards={childBoards}
        />
      </DialogFooter>
    </ObaForm>
  );
}

/** Reads "Create and start" while the takeover option is chosen. */
function CreateSprintSubmitButton({
  isSubmitting,
  boardConflicts,
  childBoards,
}: {
  isSubmitting: boolean;
  boardConflicts: SprintTakeoverConflict[];
  childBoards: SprintChildBoardResponse[] | undefined;
}) {
  const t = useTranslations("sprint");
  const { watch } = useFormContext<CreateSprintFormValues>();
  const conflicts = takeoverConflicts(boardConflicts, childBoards, watch("childBoardIds"));
  const startsSprint = conflicts.length > 0 && canTakeOver(conflicts) && watch("takeOver");

  return (
    <Button type="submit" disabled={isSubmitting}>
      {isSubmitting ? (
        <>
          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          {t("creating")}
        </>
      ) : startsSprint ? (
        t("takeover.createAndStart")
      ) : (
        t("create")
      )}
    </Button>
  );
}

function CreateSprintDialogHeader() {
  const t = useTranslations("sprint");
  return (
    <DialogHeader className="border-border shrink-0 border-b p-6 pb-4">
      <DialogTitle className="pr-8">{t("createNewSprint")}</DialogTitle>
      <DialogDescription>{t("planYourNextSprint")}</DialogDescription>
    </DialogHeader>
  );
}

function CreateSprintFormFields({
  boardConflicts,
  isSubmitting,
  childBoards,
  childBoardsFailed,
}: {
  boardConflicts: SprintTakeoverConflict[];
  isSubmitting: boolean;
  /** Undefined when there is no list or the load failed. */
  childBoards: SprintChildBoardResponse[] | undefined;
  childBoardsFailed: boolean;
}) {
  const t = useTranslations("sprint");
  const tCommon = useTranslations("common");
  const { control, watch, setValue, getFieldState } = useFormContext<CreateSprintFormValues>();

  const startDate = watch("startDate");
  const endDate = watch("endDate");
  const recurrenceEnabled = watch("recurrence.enabled");
  const typedName = watch("name");
  // With repeat on, the sprint is created as round 1 of the series.
  const sprintName = recurrenceEnabled ? `${typedName.trim()}${FIRST_ROUND_SUFFIX}` : typedName;
  const datesLength = calendarDaysBetween(startDate, endDate);

  // The series length follows the picked dates until the user edits it.
  useEffect(() => {
    if (datesLength === null || getFieldState("recurrence.lengthDays").isDirty) return;
    setValue("recurrence.lengthDays", datesLength);
  }, [datesLength, getFieldState, setValue]);
  const conflicts = takeoverConflicts(boardConflicts, childBoards, watch("childBoardIds"));
  const takeOverAllowed = canTakeOver(conflicts);
  const takeOver = takeOverAllowed && watch("takeOver");

  return (
    <FormGrid className="py-4 md:grid-cols-1">
      <TextField
        name="name"
        label={t("name")}
        placeholder={recurrenceEnabled ? t("seriesNamePlaceholder") : t("namePlaceholder")}
        control={control}
        required
        disabled={isSubmitting}
        suffix={recurrenceEnabled ? FIRST_ROUND_SUFFIX : undefined}
        tooltipText={
          recurrenceEnabled
            ? t("seriesNameCreateTooltip", {
                name: typedName.trim() || t("seriesNamePlaceholder"),
              })
            : undefined
        }
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

      {recurrenceEnabled && (
        <p className="text-muted-foreground -mt-2 flex items-start gap-2 text-xs">
          <Globe className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          <span>{t("recurrence.utcDatesNote")}</span>
        </p>
      )}

      <SprintRecurrenceFields
        lengthDays={datesLength}
        rolloverAt={endDate ? toUtcCalendarDate(endDate) : null}
        disabled={isSubmitting}
      />

      {childBoards && childBoards.length > 0 && (
        <SprintChildBoardsField boards={childBoards} disabled={isSubmitting} />
      )}

      {childBoardsFailed && (
        <InlineAlert
          variant="danger"
          icon={<AlertCircle />}
          description={t("childBoards.loadFailed")}
        />
      )}

      {conflicts.length > 0 && (
        <div className="flex flex-col gap-3">
          <SprintTakeoverNotice conflicts={conflicts} sprintName={sprintName} />
          <RadioGroup
            value={takeOver ? "takeOver" : "planOnly"}
            onValueChange={(value) => setValue("takeOver", value === "takeOver")}
            disabled={isSubmitting}
          >
            <label
              htmlFor="create-sprint-take-over"
              className="border-border hover:bg-accent flex cursor-pointer items-center gap-2 rounded-lg border p-3 text-sm transition-colors has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-50"
            >
              <RadioGroupItem
                value="takeOver"
                id="create-sprint-take-over"
                disabled={!takeOverAllowed}
              />
              <span>{t("takeover.optionTakeOver", { name: sprintName })}</span>
            </label>
            <label
              htmlFor="create-sprint-plan-only"
              className="border-border hover:bg-accent flex cursor-pointer items-center gap-2 rounded-lg border p-3 text-sm transition-colors"
            >
              <RadioGroupItem value="planOnly" id="create-sprint-plan-only" />
              <span>{t("takeover.optionPlanOnly", { name: sprintName })}</span>
            </label>
          </RadioGroup>
        </div>
      )}
    </FormGrid>
  );
}

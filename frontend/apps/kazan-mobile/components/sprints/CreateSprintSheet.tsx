import { onlineManager } from "@tanstack/react-query";
import { Play } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import { BoardPickList } from "@/components/boards/BoardPickList";
import { selectAllIds, type BoardPickItem } from "@/components/boards/boardPickModel";
import { BottomSheet } from "@/components/kazan/BottomSheet";
import { EmptyState, InlineError } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import { RecurrenceFields } from "@/components/sprints/RecurrenceFields";
import { SprintFormFields } from "@/components/sprints/SprintFormFields";
import {
  boardConflicts,
  canTakeOver,
  createSprintBody,
  createdSprintName,
  DEFAULT_SPRINT_LENGTH_DAYS,
  defaultRecurrence,
  FIRST_ROUND,
  preselectedChildBoardIds,
  recurrenceProblems,
  sprintFieldProblems,
  sprintLengthDays,
  takeoverConflicts,
  tooManyChildBoards,
  type RecurrenceForm,
  type RecurrenceProblems,
  type SprintFieldProblems,
  type SprintFields,
  type TakeoverConflict,
} from "@/components/sprints/sprintModel";
import { TakeoverNotice } from "@/components/sprints/TakeoverNotice";
import { useSprintSheetParams } from "@/components/sprints/useSprintSheetParams";
import { DismissWhen } from "@/components/ticket/DismissWhen";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useBoard, useBoardSprints } from "@/hooks/queries/boards";
import { useCreateSprint, useSprintChildBoards } from "@/hooks/queries/sprints";
import { useTranslation } from "@/i18n";
import { isValidationError } from "@/lib/api";
import { addDaysToDateOnly, todayDateOnly } from "@/lib/dates";
import {
  SPRINT_MAX_CHILD_BOARDS,
  type SprintChildBoard,
  type SprintDetail,
} from "@/lib/schemas/sprints";

/**
 * Create sprint: name, goal, dates and automation, the child boards that get
 * the same sprint, and, when the board or a ticked child board already runs
 * an active sprint, whether the new sprint takes those over (closes them,
 * moves their unfinished tickets in and starts) or is only planned.
 *
 * The form waits for the child boards, because the ones ticked at first are
 * part of its starting state; a failed load shows the form without them.
 */
export function CreateSprintSheet() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const { boardId } = useSprintSheetParams();
  const board = useBoard(boardId);
  const sprints = useBoardSprints(boardId);
  const childBoards = useSprintChildBoards(boardId);

  if (!boardId) {
    return (
      <BottomSheet title={t("sprintCreateTitle")}>
        <EmptyState variant="error" title={t("sprintSheetError")} className="mt-8" />
      </BottomSheet>
    );
  }
  if (childBoards.isPending || sprints.isPending || board.isPending) {
    return (
      <BottomSheet title={t("sprintCreateTitle")}>
        <View className="items-center py-10">
          <ActivityIndicator color={colors.mutedForeground} />
        </View>
      </BottomSheet>
    );
  }
  return (
    <CreateSprintForm
      boardId={boardId}
      own={boardConflicts(boardId, board.data?.name ?? "", sprints.data ?? [])}
      childBoards={childBoards.data ?? NO_CHILD_BOARDS}
      childBoardsFailed={childBoards.isError}
      onRetryChildBoards={() => void childBoards.refetch()}
    />
  );
}

const NO_CHILD_BOARDS: readonly SprintChildBoard[] = [];

type CreateSprintFormProps = {
  boardId: string;
  /** The board's own active sprints, as takeover rows. */
  own: TakeoverConflict[];
  childBoards: readonly SprintChildBoard[];
  childBoardsFailed: boolean;
  onRetryChildBoards: () => void;
};

/** The starting state is fixed for the sheet's life, so a refetch never discards what was typed. */
function CreateSprintForm({
  boardId,
  own,
  childBoards,
  childBoardsFailed,
  onRetryChildBoards,
}: CreateSprintFormProps) {
  const { t } = useTranslation();
  const create = useCreateSprint(boardId);
  const [fields, setFields] = React.useState<SprintFields>(() => {
    const today: string = todayDateOnly();
    return {
      name: "",
      goal: "",
      startDay: today,
      endDay: addDaysToDateOnly(today, DEFAULT_SPRINT_LENGTH_DAYS),
    };
  });
  const [recurrence, setRecurrence] = React.useState<RecurrenceForm>(() =>
    defaultRecurrence(DEFAULT_SPRINT_LENGTH_DAYS)
  );
  const [lengthEdited, setLengthEdited] = React.useState<boolean>(false);
  const [childIds, setChildIds] = React.useState<string[]>(() =>
    preselectedChildBoardIds(childBoards)
  );
  const [takeOver, setTakeOver] = React.useState<boolean>(true);
  const [picking, setPicking] = React.useState<boolean>(false);
  const [attempted, setAttempted] = React.useState<boolean>(false);
  const [serverErrors, setServerErrors] = React.useState<Record<string, string>>({});
  const [created, setCreated] = React.useState<boolean>(false);

  const datesLength: number | null = sprintLengthDays(fields.startDay, fields.endDay);
  const conflicts: TakeoverConflict[] = takeoverConflicts(own, childBoards, childIds);
  const takeOverAllowed: boolean = canTakeOver(conflicts);
  const startsSprint: boolean = conflicts.length > 0 && takeOverAllowed && takeOver;
  const sprintName: string =
    createdSprintName(fields.name, recurrence.enabled) ||
    t(recurrence.enabled ? "sprintSeriesNamePlaceholder" : "sprintNamePlaceholder");

  const fieldProblems: SprintFieldProblems = sprintFieldProblems(fields, recurrence.enabled);
  const repeatProblems: RecurrenceProblems = recurrenceProblems(recurrence, datesLength);
  const tooManyChildren: boolean = tooManyChildBoards(childIds);
  const valid: boolean =
    Object.keys(fieldProblems).length === 0 &&
    Object.keys(repeatProblems).length === 0 &&
    !tooManyChildren;
  const dirty: boolean = !created && (fields.name.trim() !== "" || fields.goal.trim() !== "");

  const changeFields = (patch: Partial<SprintFields>) => {
    const next: SprintFields = { ...fields, ...patch };
    setFields(next);
    setServerErrors({});
    // The series length follows the picked dates until the user edits it.
    const length: number | null = sprintLengthDays(next.startDay, next.endDay);
    if (!lengthEdited && length !== null && length > 0) {
      setRecurrence((current) => ({ ...current, lengthDays: String(length) }));
    }
  };

  const changeRecurrence = (patch: Partial<RecurrenceForm>) => {
    if (patch.lengthDays !== undefined) setLengthEdited(true);
    setRecurrence((current) => ({ ...current, ...patch }));
  };

  const toggleChild = (id: string, ticked: boolean) => {
    setChildIds((current) =>
      ticked
        ? current.includes(id)
          ? current
          : [...current, id]
        : current.filter((item) => item !== id)
    );
  };

  const submit = () => {
    setAttempted(true);
    if (!valid) return;
    if (!onlineManager.isOnline()) {
      toast.error(t("offlineWriteBlocked"));
      return;
    }
    const body = createSprintBody({
      fields,
      recurrence,
      childBoardIds: childIds,
      conflicts,
      takeOver,
    });
    if (!body) return;
    create.mutate(body, {
      onSuccess: (sprint: SprintDetail) => {
        toast.success(
          t(startsSprint ? "sprintCreatedAndStarted" : "sprintCreated", { name: sprint.name })
        );
        setCreated(true);
      },
      onError: (error: Error) => {
        // A refused field shows under it; other failures are toasted by the app's error layer.
        if (isValidationError(error)) setServerErrors(error.fieldErrors);
        else console.warn("[sprints] create failed", error.message);
      },
    });
  };

  if (picking) {
    return (
      <BottomSheet
        title={t("sprintChildBoardsTitle")}
        confirmLabel={t("sprintChildBoardsDone")}
        onConfirm={() => setPicking(false)}
        dirty={dirty}
        scroll
      >
        <ChildBoardPicker
          boards={childBoards}
          picked={childIds}
          sprintName={sprintName}
          onToggle={toggleChild}
          onSet={setChildIds}
        />
      </BottomSheet>
    );
  }

  const shownFieldProblems: SprintFieldProblems = attempted ? fieldProblems : {};
  const shownRepeatProblems: RecurrenceProblems = attempted ? repeatProblems : {};

  return (
    <BottomSheet
      title={t("sprintCreateTitle")}
      // Creating and starting takes over a running sprint, so it moves from the
      // header to a full-width button at the end, below the takeover choice.
      confirmLabel={startsSprint ? undefined : t("sprintCreateConfirm")}
      onConfirm={startsSprint ? undefined : submit}
      confirmLoading={create.isPending}
      dirty={dirty}
      scroll
    >
      <DismissWhen when={created} />
      <Text variant="footnote" className="px-6 pt-2 text-muted-foreground">
        {t("sprintCreateSubtitle")}
      </Text>
      <SprintFormFields
        fields={fields}
        onChange={changeFields}
        problems={shownFieldProblems}
        serverErrors={serverErrors}
        round={recurrence.enabled ? FIRST_ROUND : null}
        utcDays={recurrence.enabled}
        datesError={shownRepeatProblems.dates ? t("sprintRepeatDatesRange") : null}
        disabled={create.isPending}
      />
      <RecurrenceFields
        value={recurrence}
        onChange={changeRecurrence}
        datesLength={datesLength}
        endDay={fields.endDay || null}
        problems={shownRepeatProblems}
        disabled={create.isPending}
      />
      {childBoards.length > 0 ? (
        <ListSection
          header={t("sprintChildBoardsHeader")}
          error={
            attempted && tooManyChildren
              ? t("sprintChildBoardsTooMany", { max: SPRINT_MAX_CHILD_BOARDS })
              : null
          }
        >
          <ListRow
            title={t("sprintChildBoardsTitle")}
            invalid={attempted && tooManyChildren}
            value={
              childIds.length > 0
                ? t("sprintChildBoardsSelected", { count: childIds.length })
                : t("sprintChildBoardsNone")
            }
            onPress={() => setPicking(true)}
            disabled={create.isPending}
          />
        </ListSection>
      ) : null}
      {childBoardsFailed ? (
        <InlineError
          message={t("sprintChildBoardsError")}
          onRetry={onRetryChildBoards}
          className="mx-4 mt-6"
        />
      ) : null}
      {conflicts.length > 0 ? (
        <>
          <TakeoverNotice conflicts={conflicts} sprintName={sprintName} />
          <ListSection>
            <ListRow
              title={t("sprintTakeoverOption", { name: sprintName })}
              wrap
              accessory="radio"
              checked={takeOverAllowed && takeOver}
              onPress={() => setTakeOver(true)}
              disabled={!takeOverAllowed || create.isPending}
            />
            <ListRow
              title={t("sprintPlanOnlyOption", { name: sprintName })}
              wrap
              accessory="radio"
              checked={!takeOverAllowed || !takeOver}
              onPress={() => setTakeOver(false)}
              disabled={create.isPending}
            />
          </ListSection>
        </>
      ) : null}
      {startsSprint ? (
        <View className="px-4 pb-10 pt-4">
          <Button
            block
            icon={Play}
            onPress={submit}
            loading={create.isPending}
            disabled={create.isPending}
          >
            {t("sprintCreateAndStart")}
          </Button>
        </View>
      ) : (
        <View className="h-10" />
      )}
    </BottomSheet>
  );
}

type ChildBoardPickerProps = {
  boards: readonly SprintChildBoard[];
  picked: readonly string[];
  /** The sprint being created, named in the footer. */
  sprintName: string;
  onToggle: (id: string, ticked: boolean) => void;
  onSet: (ids: string[]) => void;
};

/**
 * The descendant boards that get the new sprint too, as a tree, searched by
 * name or prefix as on the web. A board the user is not an admin of cannot be
 * linked; a board with an active sprint stays pickable and says so in the
 * warning tone, because ticking it lets the new sprint take that one over.
 */
function ChildBoardPicker({ boards, picked, sprintName, onToggle, onSet }: ChildBoardPickerProps) {
  const { t } = useTranslation();
  const items: BoardPickItem[] = React.useMemo(
    () =>
      boards.map((board) => ({
        id: board.id,
        name: board.name,
        prefix: board.prefix,
        color: board.color ?? null,
        indent: Math.max(0, board.depth - 1),
        disabled: !board.canLink,
        note: !board.canLink
          ? t("sprintAdminRequired")
          : board.activeSprintId
            ? t("sprintChildBoardActive", { name: board.activeSprintName ?? "" })
            : null,
        noteTone: board.canLink ? "warning" : "muted",
      })),
    [boards, t]
  );

  return (
    <BoardPickList
      items={items}
      picked={picked}
      onToggle={(id) => onToggle(id, !picked.includes(id))}
      onSelectAll={() => onSet(selectAllIds(items))}
      onSelectNone={() => onSet([])}
      footer={t("sprintChildBoardsFooter", { name: sprintName })}
    />
  );
}

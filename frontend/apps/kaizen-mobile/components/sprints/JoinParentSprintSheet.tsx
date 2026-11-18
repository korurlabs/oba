import { onlineManager } from "@tanstack/react-query";
import { Link2 } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import { useSprintDates } from "@/components/boards/SprintHeader";
import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { EmptyState, InlineError } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import {
  repeatSummary,
  sprintBoardRows,
  type ParentSprintOption,
  type RepeatSummary,
  type SprintBoardRow,
} from "@/components/sprints/joinParentSprintModel";
import { seriesRunMessage } from "@/components/sprints/sprintModel";
import { useSprintSheetParams } from "@/components/sprints/useSprintSheetParams";
import { DismissWhen } from "@/components/ticket/DismissWhen";
import { Badge } from "@/components/ui/badge";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useBoard, useBoardTree } from "@/hooks/queries/boards";
import {
  useJoinParentSprint,
  useParentSprintOptions,
  useSprintDetail,
} from "@/hooks/queries/sprints";
import { useTranslation, type TranslationKey } from "@/i18n";
import type { SprintDetail } from "@/lib/schemas/sprints";

/**
 * Join a parent board's sprint, as on Kaizen web: the boards above this one,
 * nearest first, each with its relation; one without an active sprint, or
 * with a board on the way down where the user is not an admin, cannot be
 * picked and says why. Picking one shows its active sprint (dates, goal,
 * whether it repeats and what that means for this board, and the boards in
 * it as a tree with the joining ones marked) before Join sprint links it.
 * A refused join keeps the sheet open; the join hook toasts the refusal.
 */
export function JoinParentSprintSheet() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const { boardId } = useSprintSheetParams();
  const board = useBoard(boardId);
  const options = useParentSprintOptions(boardId);
  const join = useJoinParentSprint();
  const [selectedId, setSelectedId] = React.useState<string | null>(null);
  const [joined, setJoined] = React.useState<boolean>(false);

  if (!boardId) {
    return (
      <BottomSheet title={t("sprintJoinParentTitle")}>
        <EmptyState variant="error" title={t("sprintSheetError")} className="mt-8" />
      </BottomSheet>
    );
  }
  if (options.isLoading || board.isPending) {
    return (
      <BottomSheet title={t("sprintJoinParentTitle")}>
        <View className="items-center py-10">
          <ActivityIndicator color={colors.mutedForeground} />
        </View>
      </BottomSheet>
    );
  }

  const boardName: string = board.data?.name ?? "";
  const selected: ParentSprintOption | null =
    options.parentOptions.find((option) => option.board.id === selectedId) ?? null;
  const canJoin: boolean = !!selected?.activeSprint && !selected.boardWithoutRole;

  const submit = () => {
    if (!selected?.activeSprint || selected.boardWithoutRole) return;
    if (!onlineManager.isOnline()) {
      toast.error(t("offlineWriteBlocked"));
      return;
    }
    join.mutate(
      { sprintId: selected.activeSprint.id, linkPath: selected.linkPath },
      { onSuccess: () => setJoined(true) }
    );
  };

  return (
    <BottomSheet
      title={t("sprintJoinParentTitle")}
      confirmLabel={t("sprintJoinParentJoin")}
      onConfirm={submit}
      confirmDisabled={!canJoin}
      confirmLoading={join.isPending}
      scroll
    >
      <DismissWhen when={joined} />
      <Text variant="footnote" className="px-6 pt-2 text-muted-foreground">
        {t("sprintJoinParentDescription", { board: boardName })}
      </Text>
      {options.error ? (
        <InlineError
          message={t("sprintJoinParentLoadFailed")}
          onRetry={options.refetch}
          className="mx-4 mt-4"
        />
      ) : null}
      {options.parentOptions.length > 0 ? (
        <ListSection header={t("sprintJoinParentBoardLabel")}>
          {options.parentOptions.map((option) => (
            <ParentBoardRow
              key={option.board.id}
              option={option}
              checked={option.board.id === selectedId}
              disabled={join.isPending}
              onPick={setSelectedId}
            />
          ))}
        </ListSection>
      ) : options.error ? null : (
        <EmptyState icon={Link2} title={t("sprintJoinParentNoActiveSprint")} className="mt-8" />
      )}
      {selected?.activeSprint ? (
        <ParentSprintDetails
          parentBoard={selected.board}
          sprintId={selected.activeSprint.id}
          boardName={boardName}
          joiningIds={selected.linkPath.map((linked) => linked.id)}
        />
      ) : null}
      <View className="h-10" />
    </BottomSheet>
  );
}

const RELATION_KEY: Record<number, TranslationKey> = {
  1: "sprintJoinParentParent",
  2: "sprintJoinParentGrandparent",
};

type ParentBoardRowProps = {
  option: ParentSprintOption;
  checked: boolean;
  /** Blocks the pick while a join is being sent. */
  disabled: boolean;
  onPick: (boardId: string) => void;
};

/** An ancestor as a radio row, "{name} (parent)"; one that cannot be joined says why. */
function ParentBoardRow({ option, checked, disabled, onPick }: ParentBoardRowProps) {
  const { t } = useTranslation();
  const relation: string = t(RELATION_KEY[option.distance] ?? "sprintJoinParentAncestor");
  const reason: string | null = !option.activeSprint
    ? t("sprintJoinParentNoActiveSprint")
    : option.boardWithoutRole
      ? t("sprintJoinParentNoRole", { board: option.boardWithoutRole.name })
      : null;
  return (
    <ListRow
      title={`${option.board.name} (${relation})`}
      subtitle={reason ?? undefined}
      dot="bg-muted-foreground"
      dotColor={option.board.color ?? undefined}
      accessory="radio"
      checked={checked}
      disabled={disabled || reason !== null}
      onPress={() => onPick(option.board.id)}
      wrap
    />
  );
}

type ParentSprintDetailsProps = {
  parentBoard: ParentSprintOption["board"];
  sprintId: string;
  /** The board the sheet joins to the sprint. */
  boardName: string;
  /** The boards the join links the sprint to, this board among them. */
  joiningIds: readonly string[];
};

/** The picked board's active sprint, read in full because the sprint list carries no goal or boards. */
function ParentSprintDetails({
  parentBoard,
  sprintId,
  boardName,
  joiningIds,
}: ParentSprintDetailsProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const detail = useSprintDetail(parentBoard.id, sprintId);

  if (detail.isPending) {
    return (
      <View className="items-center py-8">
        <ActivityIndicator color={colors.mutedForeground} />
      </View>
    );
  }
  if (detail.isError) {
    return (
      <InlineError
        message={t("sprintJoinParentDetailsLoadFailed")}
        onRetry={() => void detail.refetch()}
        className="mx-4 mt-6"
      />
    );
  }
  return (
    <SprintDetailsBody
      sprint={detail.data}
      parentBoardName={parentBoard.name}
      boardName={boardName}
      joiningIds={joiningIds}
    />
  );
}

type SprintDetailsBodyProps = {
  sprint: SprintDetail;
  parentBoardName: string;
  boardName: string;
  joiningIds: readonly string[];
};

function SprintDetailsBody({
  sprint,
  parentBoardName,
  boardName,
  joiningIds,
}: SprintDetailsBodyProps) {
  const { t } = useTranslation();
  const { range } = useSprintDates(sprint);
  const tree = useBoardTree();
  const sprintBoards = sprint.boards ?? NO_BOARDS;
  // The tree is the one the options were read from, so it is already loaded here.
  const rows: SprintBoardRow[] = sprintBoardRows(
    tree.data ?? [],
    sprintBoards,
    new Set(joiningIds)
  );
  const repeat: RepeatSummary = repeatSummary(sprint.series, boardName);
  const repeatLines: string[] = [t(repeat.effect.key, repeat.effect.values)];
  if (repeat.offReason) {
    repeatLines.push(
      t("sprintJoinParentRepeatOffReason", { reason: seriesRunMessage(repeat.offReason, t) })
    );
  }
  const activeLabel: string = t("boardSprintActive");
  const joinsLabel: string = t("sprintJoinParentJoins");
  const goal: string | null = sprint.goal?.trim() ? sprint.goal : null;

  return (
    <>
      <ListSection header={t("sprintJoinParentActiveSprintOf", { board: parentBoardName })}>
        <ListRow
          title={sprint.name}
          accessibilityValue={activeLabel}
          trailing={
            <Badge
              variant="tag"
              className="bg-status-bg-in-progress"
              textClassName="text-foreground"
            >
              {activeLabel}
            </Badge>
          }
          wrap
        />
        {range ? <ListRow title={t("sprintJoinParentDates")} value={range} /> : null}
        {goal ? <ListRow title={t("sprintJoinParentGoal")} subtitle={goal} wrap /> : null}
        <ListRow
          title={t("sprintJoinParentRepeat")}
          value={t(repeat.value.key, repeat.value.values)}
          subtitle={repeatLines.join("\n")}
          wrap
        />
      </ListSection>
      <ListSection header={t("sprintJoinParentBoardsInSprint", { count: sprintBoards.length })}>
        {rows.map((row) => (
          <ListRow
            key={row.id}
            title={row.name}
            dot="bg-muted-foreground"
            dotColor={row.color ?? undefined}
            indent={row.depth}
            active={row.joins}
            accessibilityValue={row.joins ? joinsLabel : undefined}
            trailing={
              row.joins ? (
                <Badge
                  variant="tag"
                  className="bg-primary-fill"
                  textClassName="text-primary-foreground"
                >
                  {joinsLabel}
                </Badge>
              ) : undefined
            }
          />
        ))}
      </ListSection>
    </>
  );
}

const NO_BOARDS: NonNullable<SprintDetail["boards"]> = [];

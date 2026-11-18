import { useLocalSearchParams, useRouter } from "expo-router";
import { ArrowRightLeft } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, Platform, View } from "react-native";

import { flattenBoardTree, type BoardRow } from "@/components/boards/boardModel";
import { BoardPickList } from "@/components/boards/BoardPickList";
import type { BoardPickItem } from "@/components/boards/boardPickModel";
import {
  BULK_MOVE_SUB_TICKET_MODES,
  BULK_MOVE_TARGETS,
  bulkMoveActions,
  bulkMoveBody,
  bulkMoveSprintCandidates,
  bulkMoveTargetItems,
  chooseBulkMoveAction,
  chooseBulkMoveBoard,
  chooseBulkMoveSprint,
  chooseBulkMoveSubTickets,
  chooseBulkMoveTarget,
  DEFAULT_BULK_MOVE_CHOICES,
  effectiveBoardId,
  isBulkMoveValid,
  subTicketChoiceShown,
  type BulkMoveAction,
  type BulkMoveChoices,
  type BulkMoveSubTicketMode,
  type BulkMoveTarget,
  type TicketBulkMoveBody,
} from "@/components/boards/selection/bulkMoveModel";
import {
  clearBulkMove,
  useBulkMove,
  type BulkMoveSelection,
} from "@/components/boards/selection/bulkMoveStore";
import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { EmptyState, InlineError } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { Button } from "@/components/ui/button";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useBoardSprints, useBoardTree } from "@/hooks/queries/boards";
import { useBulkMoveTickets } from "@/hooks/queries/ticketMoves";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation, type TranslationKey } from "@/i18n";
import type { Sprint } from "@/lib/schemas/boards";
import { cn } from "@/lib/utils";

const TARGET_LABEL: Record<BulkMoveTarget, TranslationKey> = {
  current: "bulkMoveCurrentBoard",
  other: "bulkMoveOtherBoard",
};

const ACTION_LABEL: Record<BulkMoveAction, TranslationKey> = {
  MOVE_TO_BACKLOG: "bulkMoveToBacklog",
  MOVE_TO_SPRINT: "bulkMoveToSprint",
};

const SUB_TICKET_LABEL: Record<BulkMoveSubTicketMode, TranslationKey> = {
  NONE: "ticketMoveOnlyThis",
  UNFINISHED: "ticketMoveWithUnfinished",
};

/**
 * Move selected tickets, as Kaizen web's bulk move dialog: to this board or
 * another one, to its backlog or an open sprint, and, on this board, with or
 * without the unfinished sub-tickets. The selection comes from the board
 * screen through `bulkMoveStore`; a sheet restored without one says so.
 */
export function BulkMoveSheet() {
  const { t } = useTranslation();
  const router = useRouter();
  const params = useLocalSearchParams<{ boardId?: string | string[] }>();
  const boardId: string =
    (Array.isArray(params.boardId) ? params.boardId[0] : params.boardId) ?? "";
  const selection: BulkMoveSelection | null = useBulkMove();
  // The selection this sheet was opened with, kept while the sheet closes.
  const [owned] = React.useState<BulkMoveSelection | null>(selection);
  React.useEffect(() => () => clearBulkMove(owned), [owned]);

  if (!owned || owned.boardId !== boardId) {
    return (
      <BottomSheet title={t("bulkMoveTitle")}>
        <EmptyState
          title={t("bulkMoveGone")}
          actionLabel={t("boardBack")}
          onAction={() => router.back()}
          className="mt-8"
        />
      </BottomSheet>
    );
  }
  return <BulkMoveForm selection={owned} onDone={() => router.back()} />;
}

type BulkMoveFormProps = {
  selection: BulkMoveSelection;
  /** Closes the sheet once the tickets moved. */
  onDone: () => void;
};

function BulkMoveForm({ selection, onDone }: BulkMoveFormProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const online = useOnlineWriteGuard();
  const { boardId, ticketIds, source, currentSprintId, hasSubTickets } = selection;
  const [choices, setChoices] = React.useState<BulkMoveChoices>(DEFAULT_BULK_MOVE_CHOICES);
  const move = useBulkMoveTickets(boardId);
  const tree = useBoardTree();
  const rows: BoardRow[] = React.useMemo(() => flattenBoardTree(tree.data ?? []), [tree.data]);
  const receiving: string = effectiveBoardId(choices, boardId);
  const sprintMove: boolean = choices.action === "MOVE_TO_SPRINT";
  const sprints = useBoardSprints(sprintMove ? receiving : undefined);
  const busy: boolean = move.isPending;

  const targetItems: BoardPickItem[] = React.useMemo(
    () => bulkMoveTargetItems(rows, boardId, t("bulkMoveCurrentBoardHint")),
    [rows, boardId, t]
  );
  const sprintOptions: Sprint[] = React.useMemo(
    () => bulkMoveSprintCandidates(sprints.data ?? [], choices, currentSprintId),
    [sprints.data, choices, currentSprintId]
  );
  const showSubTickets: boolean = subTicketChoiceShown(choices, hasSubTickets);
  const valid: boolean = isBulkMoveValid(choices, ticketIds.length);
  const changed: boolean = JSON.stringify(choices) !== JSON.stringify(DEFAULT_BULK_MOVE_CHOICES);

  const send = () => {
    const body: TicketBulkMoveBody | null = bulkMoveBody(choices, ticketIds, hasSubTickets);
    if (!body || busy || !online()) return;
    move.mutate(body, {
      onSuccess: () => {
        selection.onMoved();
        onDone();
      },
      // The refusal is toasted; the sheet and the selection stay for another try.
      onError: (error: Error) => console.warn("[boards] bulk move refused", error.message),
    });
  };

  let otherBoard: React.ReactNode = null;
  if (choices.target === "other") {
    otherBoard = tree.data ? (
      <BoardPickList
        mode="single"
        items={targetItems}
        value={choices.targetBoardId}
        onChange={(id) => setChoices((current) => chooseBulkMoveBoard(current, id, boardId))}
        disabled={busy}
      />
    ) : tree.isError ? (
      <InlineError
        message={t("boardsError")}
        onRetry={() => void tree.refetch()}
        className="mx-4 mt-4"
      />
    ) : (
      <View className="items-center py-4">
        <ActivityIndicator color={colors.mutedForeground} />
      </View>
    );
  }

  let sprintRows: React.ReactNode = null;
  if (sprintMove) {
    if (sprints.isError) {
      sprintRows = (
        <InlineError
          message={t("boardSprintsError")}
          onRetry={() => void sprints.refetch()}
          className="mx-4"
        />
      );
    } else if (sprints.isPending) {
      sprintRows = (
        <View className="items-center py-4">
          <ActivityIndicator color={colors.mutedForeground} />
        </View>
      );
    } else if (sprintOptions.length === 0) {
      sprintRows = <ListRow title={t("bulkMoveNoSprints")} wrap accessory="none" />;
    } else {
      sprintRows = sprintOptions.map((sprint) => (
        <ListRow
          key={sprint.id}
          title={sprint.name}
          accessory="radio"
          checked={choices.targetSprintId === sprint.id}
          onPress={() => setChoices((current) => chooseBulkMoveSprint(current, sprint.id))}
          disabled={busy}
        />
      ));
    }
  }

  return (
    <BottomSheet title={t("bulkMoveTitle")} dirty={changed && !busy} scroll>
      <Text variant="footnote" className="px-6 pt-2 text-muted-foreground">
        {t("bulkMoveCount", { count: ticketIds.length })}
      </Text>

      <SectionLabel>{t("bulkMoveTargetBoard")}</SectionLabel>
      <View className="px-4">
        <SegmentedControl
          values={BULK_MOVE_TARGETS.map((target) => t(TARGET_LABEL[target]))}
          selectedIndex={BULK_MOVE_TARGETS.indexOf(choices.target)}
          onChange={(index: number) => {
            const target: BulkMoveTarget | undefined = BULK_MOVE_TARGETS[index];
            if (target && !busy) {
              setChoices((current) => chooseBulkMoveTarget(current, target, boardId));
            }
          }}
        />
      </View>
      {otherBoard}

      <ListSection header={t("bulkMoveAction")}>
        {bulkMoveActions(source).map((action) => (
          <ListRow
            key={action}
            title={t(ACTION_LABEL[action])}
            wrap
            accessory="radio"
            checked={choices.action === action}
            onPress={() => setChoices((current) => chooseBulkMoveAction(current, action))}
            disabled={busy}
          />
        ))}
      </ListSection>

      {sprintMove ? (
        <ListSection header={t("bulkMoveSelectSprint")}>{sprintRows}</ListSection>
      ) : null}

      {showSubTickets ? (
        <ListSection header={t("bulkMoveSubTickets")}>
          {BULK_MOVE_SUB_TICKET_MODES.map((mode) => (
            <ListRow
              key={mode}
              title={t(SUB_TICKET_LABEL[mode])}
              wrap
              accessory="radio"
              checked={choices.subTicketMode === mode}
              onPress={() => setChoices((current) => chooseBulkMoveSubTickets(current, mode))}
              disabled={busy}
            />
          ))}
        </ListSection>
      ) : null}

      <View className="px-4 pb-10 pt-6">
        <Button block icon={ArrowRightLeft} onPress={send} loading={busy} disabled={!valid || busy}>
          {t("bulkMoveConfirm")}
        </Button>
      </View>
    </BottomSheet>
  );
}

/** A section title over a control that is not a list, styled as a list section header. */
function SectionLabel({ children }: { children: string }) {
  return (
    <Text
      accessibilityRole="header"
      className={cn(
        Platform.OS === "ios"
          ? "px-8 pb-1.5 pt-6 font-sans text-footnote uppercase text-muted-foreground"
          : "px-4 pb-2 pt-4 font-sans-medium text-subhead text-link"
      )}
    >
      {children}
    </Text>
  );
}

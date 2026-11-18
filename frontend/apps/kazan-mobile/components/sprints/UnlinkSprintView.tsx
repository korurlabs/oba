import { TriangleAlert, Unlink } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, Platform, View } from "react-native";

import { flattenBoardTree, type BoardRow } from "@/components/boards/boardModel";
import { BoardPickList } from "@/components/boards/BoardPickList";
import type { BoardPickItem } from "@/components/boards/boardPickModel";
import { InlineError } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import type { SprintBoardRef } from "@/components/sprints/SprintBoardsTab";
import {
  chooseUnlinkAction,
  chooseUnlinkBoard,
  chooseUnlinkSprint,
  chooseUnlinkTarget,
  DEFAULT_UNLINK_CHOICES,
  effectiveBoardId,
  isUnlinkValid,
  UNLINK_TARGETS,
  UNLINK_TICKET_ACTIONS,
  unlinkBody,
  unlinkSprintCandidates,
  unlinkTargetItems,
  type SprintUnlinkBody,
  type UnlinkChoices,
  type UnlinkTarget,
  type UnlinkTicketAction,
} from "@/components/sprints/sprintBoardsModel";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Icon } from "@/components/ui/icon";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useBoardSprints, useBoardTree } from "@/hooks/queries/boards";
import type { useUnlinkSprint } from "@/hooks/queries/sprints";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation, type TranslationKey } from "@/i18n";
import type { Sprint } from "@/lib/schemas/boards";
import { cn } from "@/lib/utils";

const ACTION_COPY: Record<UnlinkTicketAction, { title: TranslationKey; body: TranslationKey }> = {
  MOVE_TO_BACKLOG: { title: "sprintUnlinkToBacklog", body: "sprintUnlinkToBacklogBody" },
  MOVE_TO_SPRINT: { title: "sprintUnlinkToSprint", body: "sprintUnlinkToSprintBody" },
  DELETE: { title: "sprintUnlinkDelete", body: "sprintUnlinkDeleteBody" },
};

const TARGET_LABEL: Record<UnlinkTarget, TranslationKey> = {
  current: "sprintUnlinkCurrentBoard",
  other: "sprintUnlinkOtherBoard",
};

type UnlinkSprintViewProps = {
  sprintId: string;
  /** The board the sprint is taken off. */
  board: SprintBoardRef;
  /** The unlink request, owned by Edit sprint so its Cancel waits while it is sent. */
  unlink: ReturnType<typeof useUnlinkSprint>;
  /** Returns to the Boards tab after the unlink. */
  onDone: () => void;
};

/**
 * Unlink sprint, as Kazan web's dialog: whether the unlinked board's tickets
 * of the sprint stay on that board or move to another one, and whether they
 * go to the backlog, to another open sprint, or are deleted. Unlink sends the
 * one request with no further question, as on the web; a refusal is toasted
 * and the choices stay.
 */
export function UnlinkSprintView({ sprintId, board, unlink, onDone }: UnlinkSprintViewProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const online = useOnlineWriteGuard();
  const [choices, setChoices] = React.useState<UnlinkChoices>(DEFAULT_UNLINK_CHOICES);
  const tree = useBoardTree();
  const rows: BoardRow[] = React.useMemo(() => flattenBoardTree(tree.data ?? []), [tree.data]);
  const receiving: string = effectiveBoardId(choices, board.id);
  const sprintMove: boolean = choices.ticketAction === "MOVE_TO_SPRINT";
  const sprints = useBoardSprints(sprintMove ? receiving : undefined);
  const busy: boolean = unlink.isPending;

  const targetItems: BoardPickItem[] = React.useMemo(
    () => unlinkTargetItems(rows, board.id, t("sprintUnlinkCurrentBoardHint")),
    [rows, board.id, t]
  );
  const sprintOptions: Sprint[] = React.useMemo(
    () => unlinkSprintCandidates(sprints.data ?? [], sprintId),
    [sprints.data, sprintId]
  );
  const valid: boolean = isUnlinkValid(choices);

  const send = () => {
    const body: SprintUnlinkBody | null = unlinkBody(choices, board.id);
    if (!body || busy || !online()) return;
    unlink.mutate(
      { unlinkedBoardId: board.id, body },
      {
        onSuccess: onDone,
        // The refusal is toasted; the view keeps the choices for another try.
        onError: (error: Error) => console.warn("[sprints] unlink failed", error.message),
      }
    );
  };

  let otherBoard: React.ReactNode = null;
  if (choices.target === "other") {
    otherBoard = tree.data ? (
      <BoardPickList
        mode="single"
        items={targetItems}
        value={choices.targetBoardId}
        onChange={(id) => setChoices((current) => chooseUnlinkBoard(current, id, board.id))}
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
      sprintRows = <ListRow title={t("sprintUnlinkNoPlannedSprints")} wrap accessory="none" />;
    } else {
      sprintRows = sprintOptions.map((sprint) => (
        <ListRow
          key={sprint.id}
          title={sprint.name}
          accessory="radio"
          checked={choices.targetSprintId === sprint.id}
          onPress={() => setChoices((current) => chooseUnlinkSprint(current, sprint.id))}
          disabled={busy}
        />
      ));
    }
  }

  return (
    <>
      <Text variant="footnote" className="px-6 pt-2 text-muted-foreground">
        {t("sprintUnlinkDescription")}
      </Text>

      <SectionLabel>{t("sprintUnlinkTargetBoard")}</SectionLabel>
      <View className="px-4">
        <SegmentedControl
          values={UNLINK_TARGETS.map((target) => t(TARGET_LABEL[target]))}
          selectedIndex={UNLINK_TARGETS.indexOf(choices.target)}
          onChange={(index: number) => {
            const target: UnlinkTarget | undefined = UNLINK_TARGETS[index];
            if (target && !busy) {
              setChoices((current) => chooseUnlinkTarget(current, target, board.id));
            }
          }}
        />
      </View>
      {otherBoard}

      <ListSection header={t("sprintUnlinkAction")}>
        {UNLINK_TICKET_ACTIONS.map((action) => (
          <ListRow
            key={action}
            title={t(ACTION_COPY[action].title)}
            subtitle={t(ACTION_COPY[action].body)}
            subtitleTone={action === "DELETE" ? "destructive" : "muted"}
            wrap
            accessory="radio"
            checked={choices.ticketAction === action}
            onPress={() => setChoices((current) => chooseUnlinkAction(current, action))}
            disabled={busy}
          />
        ))}
      </ListSection>

      {sprintMove ? (
        <ListSection header={t("sprintUnlinkTargetSprint")}>{sprintRows}</ListSection>
      ) : null}

      <Card className="mx-4 mt-6 gap-2">
        <View className="flex-row items-start gap-2">
          <Icon as={TriangleAlert} size={16} className="mt-0.5 text-warning" />
          <Text variant="subhead" className="flex-1">
            {t("sprintUnlinkWarning")}
          </Text>
        </View>
      </Card>

      <View className="px-4 pb-10 pt-4">
        <Button
          block
          variant="destructive"
          icon={Unlink}
          onPress={send}
          loading={busy}
          disabled={!valid || busy}
        >
          {busy ? t("sprintUnlinkUnlinking") : t("sprintUnlinkConfirm")}
        </Button>
      </View>
    </>
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

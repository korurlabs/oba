import { Link2, Plus } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, Platform, View } from "react-native";

import { flattenBoardTree, type BoardRow } from "@/components/boards/boardModel";
import { BoardPickList } from "@/components/boards/BoardPickList";
import type { BoardPickItem } from "@/components/boards/boardPickModel";
import type { BottomSheetProps } from "@/components/kazan/BottomSheet";
import { EmptyState, InlineError } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import {
  linkCandidates,
  linkedBoardRows,
  type LinkedBoardRow,
} from "@/components/sprints/sprintBoardsModel";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useBoardTree } from "@/hooks/queries/boards";
import { useSprintDetail } from "@/hooks/queries/sprints";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

/** A board that shares the sprint, as the unlink view names it. */
export type SprintBoardRef = { id: string; name: string };

/** The flattened board tree, with its load state, for the Boards tab and the link view. */
function useBoardRows() {
  const tree = useBoardTree();
  const rows: BoardRow[] = React.useMemo(() => flattenBoardTree(tree.data ?? []), [tree.data]);
  return { tree, rows };
}

/** The spinner or the retryable error of the board tree; null once it is loaded. */
function TreeState({ tree }: { tree: ReturnType<typeof useBoardTree> }) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  if (tree.isError) {
    return (
      <InlineError
        message={t("boardsError")}
        onRetry={() => void tree.refetch()}
        className="mx-4 mt-6"
      />
    );
  }
  if (tree.isPending) {
    return (
      <View className="items-center py-10">
        <ActivityIndicator color={colors.mutedForeground} />
      </View>
    );
  }
  return null;
}

type SprintBoardsTabProps = {
  /** The board Edit sprint was opened from. */
  boardId: string;
  sprintId: string;
  onLink: () => void;
  onUnlink: (board: SprintBoardRef) => void;
};

/**
 * The Boards tab of Edit sprint, as on Kazan web: the boards that share the
 * sprint, in board-tree order. Every board but the one Edit sprint was
 * opened from offers Unlink; "Link board" adds more. The tab writes at once,
 * apart from the sheet's Save, and the server decides who may link or unlink.
 */
export function SprintBoardsTab({ boardId, sprintId, onLink, onUnlink }: SprintBoardsTabProps) {
  const { t } = useTranslation();
  const sprint = useSprintDetail(boardId, sprintId);
  const { tree, rows } = useBoardRows();
  const linked: LinkedBoardRow[] = React.useMemo(
    () =>
      linkedBoardRows(
        rows,
        (sprint.data?.boards ?? []).map((board) => board.id),
        boardId
      ),
    [rows, sprint.data?.boards, boardId]
  );

  if (!tree.data) return <TreeState tree={tree} />;
  if (linked.length === 0) {
    return (
      <EmptyState
        icon={Link2}
        title={t("sprintBoardsEmpty")}
        message={t("sprintBoardsEmptyDescription")}
        actionLabel={t("sprintBoardsLinkBoard")}
        actionIcon={Plus}
        onAction={onLink}
        className="mt-8"
      />
    );
  }
  return (
    <ListSection header={t("sprintBoardsTitle")}>
      {linked.map(({ board, current }) => (
        <ListRow
          key={board.id}
          title={board.name}
          value={board.prefix ?? undefined}
          dot="bg-muted-foreground"
          dotColor={board.color ?? undefined}
          indent={board.depth}
          accessory="none"
          trailing={
            <Text
              variant="subhead"
              className={cn("pl-3", current ? "text-muted-foreground" : "text-link")}
            >
              {current ? t("sprintBoardsCurrentBoard") : t("sprintBoardsUnlink")}
            </Text>
          }
          onPress={current ? undefined : () => onUnlink({ id: board.id, name: board.name })}
        />
      ))}
      <ListRow icon={Plus} title={t("sprintBoardsLinkBoard")} onPress={onLink} />
    </ListSection>
  );
}

type LinkSprintBoardsProps = {
  /** Every board the sprint is linked to now. */
  linkedIds: readonly string[];
  /** The boards ticked so far, in pick order. */
  picked: readonly string[];
  onToggle: (boardId: string) => void;
  /** Blocks the list while the link is being sent. */
  disabled: boolean;
};

/**
 * The link view of the Boards tab: every board of the tree, those already
 * linked shown but not pickable, as the web's Link boards dialog.
 */
export function LinkSprintBoards({ linkedIds, picked, onToggle, disabled }: LinkSprintBoardsProps) {
  const { t } = useTranslation();
  const { tree, rows } = useBoardRows();
  const items: BoardPickItem[] = React.useMemo(
    () => linkCandidates(rows, linkedIds, t("sprintBoardsAlreadyLinked")),
    [rows, linkedIds, t]
  );

  if (!tree.data) return <TreeState tree={tree} />;
  return (
    <>
      <Text variant="footnote" className="px-6 pt-2 text-muted-foreground">
        {t("sprintBoardsEmptyDescription")}
      </Text>
      <BoardPickList items={items} picked={picked} onToggle={onToggle} disabled={disabled} />
    </>
  );
}

type SprintSubviewOptions = {
  /** Returns to the tabs of Edit sprint. */
  onBack: () => void;
  /** Blocks the way back, such as while the view's write is being sent. */
  backDisabled?: boolean;
  confirmLabel?: string;
  onConfirm?: () => void;
  confirmDisabled?: boolean;
  confirmLoading?: boolean;
};

const IS_ANDROID: boolean = Platform.OS === "android";

/**
 * The sheet props of a view shown in place of Edit sprint's tabs (link or
 * unlink): its Cancel returns to the tabs instead of closing Edit sprint. On
 * iOS Cancel takes the header's leading slot and the confirm stays in the
 * header; Android has no header actions, so both sit in the footer. Swiping
 * the sheet down still closes Edit sprint.
 */
export function sprintSubviewSheetProps(
  options: SprintSubviewOptions,
  cancelLabel: string
): Partial<BottomSheetProps> {
  if (!IS_ANDROID) {
    return {
      headerAction: {
        label: cancelLabel,
        onPress: options.onBack,
        disabled: options.backDisabled ?? false,
      },
      confirmLabel: options.confirmLabel,
      onConfirm: options.onConfirm,
      confirmDisabled: options.confirmDisabled,
      confirmLoading: options.confirmLoading,
    };
  }
  return {
    footer: (
      <View className="flex-row gap-3">
        <Button
          variant="ghost"
          className="flex-1"
          onPress={options.onBack}
          disabled={options.backDisabled ?? false}
        >
          {cancelLabel}
        </Button>
        {options.confirmLabel && options.onConfirm ? (
          <Button
            className="flex-1"
            onPress={options.onConfirm}
            loading={options.confirmLoading ?? false}
            disabled={(options.confirmDisabled ?? false) || (options.confirmLoading ?? false)}
          >
            {options.confirmLabel}
          </Button>
        ) : null}
      </View>
    ),
  };
}

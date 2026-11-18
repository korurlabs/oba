import { useLocalSearchParams, useRouter } from "expo-router";
import { Lock, Trash2 } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, Alert, View } from "react-native";

import { BoardPickList } from "@/components/boards/BoardPickList";
import { canDeleteBoard, flattenBoardTree, type BoardRow } from "@/components/boards/boardModel";
import type { BoardPickItem } from "@/components/boards/boardPickModel";
import {
  chooseChildBoardAction,
  chooseNewParent,
  chooseTargetBoard,
  chooseTargetSprint,
  chooseTicketAction,
  confirmLines,
  DEFAULT_DELETE_BOARD_CHOICES,
  DELETE_CHILD_BOARD_ACTIONS,
  DELETE_TICKET_ACTIONS,
  deleteBoardContext,
  deleteBoardParams,
  deletedBoardIds,
  isChildActionLocked,
  isDeleteBoardValid,
  isTicketMove,
  newParentRows,
  targetSprintCandidates,
  ticketTargetRows,
  type DeleteBoardChoices,
  type DeleteBoardContext,
  type DeleteChildBoardAction,
  type DeleteConfirmLine,
  type DeleteTicketAction,
} from "@/components/boards/settings/deleteBoardModel";
import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { EmptyState, InlineError } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { Button } from "@/components/ui/button";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import {
  useBoardSprints,
  useBoardTree,
  useDeleteBoard,
  useMyBoardRoleState,
  useOpenTicketCounts,
} from "@/hooks/queries/boards";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation, type TranslationKey } from "@/i18n";
import { toApiError } from "@/lib/api";
import type { Sprint } from "@/lib/schemas/boards";

const TICKET_ACTION_LABEL: Record<DeleteTicketAction, TranslationKey> = {
  DELETE: "deleteBoardTicketsDelete",
  MOVE_TO_BACKLOG: "deleteBoardTicketsBacklog",
  MOVE_TO_SPRINT: "deleteBoardTicketsSprint",
};

const CHILD_BOARD_ACTION_LABEL: Record<DeleteChildBoardAction, TranslationKey> = {
  DELETE: "deleteBoardChildrenDelete",
  PROMOTE: "deleteBoardChildrenPromote",
  MOVE: "deleteBoardChildrenMove",
};

/** The board and the tree the sheet works on. */
type DeleteBoardData = { board: BoardRow; rows: BoardRow[] };

/** A board row as a row of a target pick list: every one can be picked. */
function pickItem(row: BoardRow): BoardPickItem {
  return {
    id: row.id,
    name: row.name,
    prefix: row.prefix,
    color: row.color,
    indent: row.depth,
    disabled: false,
    note: null,
    noteTone: "muted",
  };
}

/**
 * Delete board, as Kaizen web's delete dialog: what happens to the board's
 * tickets (deleted, or moved to the backlog or an open sprint of another
 * board) and, when it has child boards, to them (deleted, made standalone, or
 * moved under a new parent). Only the board owner deletes. Delete asks once
 * more, naming every choice, before the one `DELETE` request is sent; Cancel
 * there keeps the choices.
 *
 * Once the request is sent the sheet keeps the board and tree it showed and
 * stops reading the board's role: a successful delete drops the board's
 * reads, and read again they would answer "not found" while the sheet closes.
 */
export function DeleteBoardSheet() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const router = useRouter();
  const params = useLocalSearchParams<{ boardId?: string | string[] }>();
  const boardId: string =
    (Array.isArray(params.boardId) ? params.boardId[0] : params.boardId) ?? "";
  const [sent, setSent] = React.useState<DeleteBoardData | null>(null);
  const tree = useBoardTree();
  const access = useMyBoardRoleState(sent ? undefined : boardId || undefined);
  const rows: BoardRow[] = React.useMemo(() => flattenBoardTree(tree.data ?? []), [tree.data]);
  const board: BoardRow | undefined = rows.find((row) => row.id === boardId);

  if (!sent) {
    if (access.error) {
      const kind: string = toApiError(access.error).kind;
      const noAccess: boolean = kind === "forbidden" || kind === "not-found";
      return (
        <BottomSheet title={t("deleteBoardTitle")}>
          <EmptyState
            variant={noAccess ? "empty" : "error"}
            icon={noAccess ? Lock : undefined}
            title={noAccess ? t("boardNoAccess") : t("boardError")}
            onAction={noAccess ? undefined : access.refetch}
            className="mt-8"
          />
        </BottomSheet>
      );
    }
    if (tree.isPending || access.isPending) {
      return (
        <BottomSheet title={t("deleteBoardTitle")}>
          <View className="items-center py-10">
            <ActivityIndicator color={colors.mutedForeground} />
          </View>
        </BottomSheet>
      );
    }
    if (!board) {
      return (
        <BottomSheet title={t("deleteBoardTitle")}>
          <EmptyState
            variant="error"
            title={t("boardsError")}
            onAction={tree.isError ? () => void tree.refetch() : undefined}
            className="mt-8"
          />
        </BottomSheet>
      );
    }
    if (!canDeleteBoard(access.role)) {
      return (
        <BottomSheet title={t("deleteBoardTitle")}>
          <EmptyState icon={Lock} title={t("boardNoAccess")} className="mt-8" />
        </BottomSheet>
      );
    }
  }

  const data: DeleteBoardData = sent ?? { board: board as BoardRow, rows };
  return (
    <DeleteBoardForm
      board={data.board}
      rows={data.rows}
      sent={sent !== null}
      onSend={() => setSent(data)}
      onFailed={() => setSent(null)}
      onDeleted={() => router.dismissTo("/boards")}
    />
  );
}

type DeleteBoardFormProps = DeleteBoardData & {
  /** True while the request is out, and after it succeeded. */
  sent: boolean;
  onSend: () => void;
  onFailed: () => void;
  /** Leaves the deleted board's screens. */
  onDeleted: () => void;
};

function DeleteBoardForm({ board, rows, sent, onSend, onFailed, onDeleted }: DeleteBoardFormProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const online = useOnlineWriteGuard();
  const ctx: DeleteBoardContext = React.useMemo(
    () => deleteBoardContext(rows, board.id),
    [rows, board.id]
  );
  const [choices, setChoices] = React.useState<DeleteBoardChoices>(DEFAULT_DELETE_BOARD_CHOICES);
  const remove = useDeleteBoard(board.id, { onBeforeInvalidate: onDeleted });
  const sprintMove: boolean = choices.ticketAction === "MOVE_TO_SPRINT";
  const sprints = useBoardSprints(sprintMove ? (choices.targetBoardId ?? undefined) : undefined);
  const openCounts = useOpenTicketCounts();
  const busy: boolean = sent || remove.isPending;

  const sprintOptions: Sprint[] = React.useMemo(
    () => targetSprintCandidates(sprints.data ?? []),
    [sprints.data]
  );
  const targetItems: BoardPickItem[] = React.useMemo(
    () => ticketTargetRows(rows, board.id).map(pickItem),
    [rows, board.id]
  );
  const parentItems: BoardPickItem[] = React.useMemo(
    () => newParentRows(rows, board.id).map(pickItem),
    [rows, board.id]
  );
  const locked: boolean = isChildActionLocked(choices, ctx);
  const valid: boolean = isDeleteBoardValid(choices, ctx);
  const changed: boolean = JSON.stringify(choices) !== JSON.stringify(DEFAULT_DELETE_BOARD_CHOICES);

  const send = () => {
    if (!online()) return;
    onSend();
    remove.mutate(
      { params: deleteBoardParams(choices, ctx), deletedBoardIds: deletedBoardIds(choices, ctx) },
      {
        onError: (error: Error) => {
          // The refusal is toasted with the server's message; the sheet keeps the choices.
          console.warn("[boards] delete failed", error.message);
          onFailed();
        },
      }
    );
  };

  const confirm = () => {
    if (!valid || busy || !online()) return;
    const lines: DeleteConfirmLine[] = confirmLines(choices, ctx, {
      rows,
      sprints: sprintOptions,
      openCounts: openCounts.isError ? undefined : openCounts.data,
    });
    Alert.alert(
      t("boardSettingsDeleteTitle", { name: board.name }),
      lines.map((line) => t(line.key, line.values)).join("\n"),
      [
        { text: t("kitCancel"), style: "cancel" },
        { text: t("boardSettingsDeleteConfirm"), style: "destructive", onPress: send },
      ]
    );
  };

  let sprintSection: React.ReactNode = null;
  if (sprintMove && choices.targetBoardId) {
    let sprintRows: React.ReactNode;
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
      sprintRows = <ListRow title={t("deleteBoardNoSprints")} wrap accessory="none" />;
    } else {
      sprintRows = sprintOptions.map((sprint) => (
        <ListRow
          key={sprint.id}
          title={sprint.name}
          accessory="radio"
          checked={choices.targetSprintId === sprint.id}
          onPress={() => setChoices((current) => chooseTargetSprint(current, sprint.id))}
          disabled={busy}
        />
      ));
    }
    sprintSection = <ListSection header={t("deleteBoardSprint")}>{sprintRows}</ListSection>;
  }

  return (
    <BottomSheet title={t("deleteBoardTitle")} dirty={!sent && changed} scroll>
      <ListSection footer={t("deleteBoardDescription")}>
        <ListRow
          title={board.name}
          value={board.prefix ?? undefined}
          dot="bg-muted-foreground"
          dotColor={board.color ?? undefined}
        />
      </ListSection>

      <ListSection header={t("deleteBoardTicketsTitle")}>
        {DELETE_TICKET_ACTIONS.map((action) => (
          <ListRow
            key={action}
            title={t(TICKET_ACTION_LABEL[action])}
            wrap
            accessory="radio"
            checked={choices.ticketAction === action}
            onPress={() => setChoices((current) => chooseTicketAction(current, action))}
            disabled={busy}
          />
        ))}
      </ListSection>

      {isTicketMove(choices.ticketAction) ? (
        <BoardPickList
          mode="single"
          header={t("deleteBoardTargetBoard")}
          items={targetItems}
          value={choices.targetBoardId}
          onChange={(id) => setChoices((current) => chooseTargetBoard(current, id, ctx))}
          disabled={busy}
        />
      ) : null}

      {sprintSection}

      {ctx.hasChildren ? (
        <>
          <ListSection header={t("deleteBoardChildrenTitle")}>
            {ctx.descendants.map((row) => (
              <ListRow
                key={row.board.id}
                title={row.board.name}
                value={row.board.prefix ?? undefined}
                dot="bg-muted-foreground"
                dotColor={row.board.color ?? undefined}
                indent={row.indent}
              />
            ))}
          </ListSection>
          <ListSection>
            {DELETE_CHILD_BOARD_ACTIONS.map((action) => (
              <ListRow
                key={action}
                title={t(CHILD_BOARD_ACTION_LABEL[action])}
                wrap
                accessory="radio"
                checked={choices.childBoardAction === action}
                onPress={() =>
                  setChoices((current) => chooseChildBoardAction(current, action, ctx))
                }
                disabled={busy || (locked && action !== "PROMOTE")}
              />
            ))}
          </ListSection>
          {choices.childBoardAction === "MOVE" ? (
            <BoardPickList
              mode="single"
              header={t("deleteBoardNewParent")}
              items={parentItems}
              value={choices.newParentBoardId}
              onChange={(id) => setChoices((current) => chooseNewParent(current, id))}
              disabled={busy}
            />
          ) : null}
        </>
      ) : null}

      <View className="px-4 pb-10 pt-6">
        <Button
          block
          variant="destructive"
          icon={Trash2}
          onPress={confirm}
          loading={busy}
          disabled={!valid}
        >
          {busy ? t("deleteBoardDeleting") : t("deleteBoardConfirm")}
        </Button>
      </View>
    </BottomSheet>
  );
}

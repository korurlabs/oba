import { useLocalSearchParams } from "expo-router";
import { X } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import { BoardPickList } from "@/components/boards/BoardPickList";
import { flattenBoardTree, type BoardRow } from "@/components/boards/boardModel";
import type { BoardPickItem } from "@/components/boards/boardPickModel";
import {
  addExistingBoardRows,
  addExistingCandidates,
  addExistingPreview,
  togglePick,
  type AddExistingCandidate,
  type AddExistingPreviewRow,
} from "@/components/boards/settings/boardSettingsModel";
import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { EmptyState } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { DismissWhen } from "@/components/ticket/DismissWhen";
import { IconButton } from "@/components/ui/icon-button";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useAddChildBoards, useBoardRoles } from "@/hooks/queries/boardSettings";
import { useBoardTree } from "@/hooks/queries/boards";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation, type TranslationKey } from "@/i18n";

/** The board roles that keep a board from being picked, by their spoken name. */
const BLOCKING_ROLE_LABEL: Record<string, TranslationKey> = {
  MEMBER: "ticketBoardRoleMember",
  VIEWER: "ticketBoardRoleViewer",
};

/**
 * Add existing boards as children, as Kaizen web's dialog: every board of the
 * tree except this board and its descendants, which would close a loop.
 * Boards where the reader is MEMBER or VIEWER cannot be picked; a picked
 * board brings its descendants, which leave the list and show in the preview
 * as "included". One `PUT /children` sends the picks in the order they were
 * made.
 */
export function AddChildBoardsSheet() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const params = useLocalSearchParams<{ boardId?: string | string[] }>();
  const boardId: string =
    (Array.isArray(params.boardId) ? params.boardId[0] : params.boardId) ?? "";
  const tree = useBoardTree();
  const rows: BoardRow[] = React.useMemo(() => flattenBoardTree(tree.data ?? []), [tree.data]);
  const board: BoardRow | undefined = rows.find((row) => row.id === boardId);

  if (tree.isPending) {
    return (
      <BottomSheet title={t("boardAddExistingTitle")}>
        <View className="items-center py-10">
          <ActivityIndicator color={colors.mutedForeground} />
        </View>
      </BottomSheet>
    );
  }
  if (!board) {
    return (
      <BottomSheet title={t("boardAddExistingTitle")}>
        <EmptyState
          variant="error"
          title={t("boardsError")}
          onAction={tree.isError ? () => void tree.refetch() : undefined}
          className="mt-8"
        />
      </BottomSheet>
    );
  }
  return <AddChildBoardsForm board={board} rows={rows} />;
}

type AddChildBoardsFormProps = {
  /** The board the picked boards move under. */
  board: BoardRow;
  /** The flattened board tree. */
  rows: readonly BoardRow[];
};

function AddChildBoardsForm({ board, rows }: AddChildBoardsFormProps) {
  const { t } = useTranslation();
  const online = useOnlineWriteGuard();
  const add = useAddChildBoards(board.id);
  const [picked, setPicked] = React.useState<string[]>([]);
  const [added, setAdded] = React.useState<boolean>(false);

  const candidateRows: BoardRow[] = React.useMemo(
    () => addExistingBoardRows(rows, board.id),
    [rows, board.id]
  );
  const candidateIds: string[] = React.useMemo(
    () => candidateRows.map((row) => row.id),
    [candidateRows]
  );
  const roles: ReadonlyMap<string, string> = useBoardRoles(candidateIds);

  const items: BoardPickItem[] = React.useMemo(
    () =>
      addExistingCandidates(candidateRows, picked, roles).map(
        ({ board: row, blockingRole }: AddExistingCandidate) => ({
          id: row.id,
          name: row.name,
          prefix: row.prefix,
          color: row.color,
          indent: row.depth,
          disabled: blockingRole !== null,
          note: blockingRole
            ? BLOCKING_ROLE_LABEL[blockingRole]
              ? t(BLOCKING_ROLE_LABEL[blockingRole]!)
              : blockingRole
            : null,
          noteTone: "warning" as const,
        })
      ),
    [candidateRows, picked, roles, t]
  );
  const preview: AddExistingPreviewRow[] = addExistingPreview(candidateRows, picked);

  const toggle = (id: string) => setPicked((current) => togglePick(candidateRows, current, id));

  const submit = () => {
    if (picked.length === 0 || add.isPending || added) return;
    if (!online()) return;
    add.mutate(
      { childBoardIds: picked },
      {
        onSuccess: () => {
          toast.success(t("boardAddExistingAdded"));
          setAdded(true);
        },
        // The app's error layer toasts the server's message, as the web does; the picks stay.
        onError: (error: Error) =>
          console.warn("[boardSettings] add children failed", error.message),
      }
    );
  };

  return (
    <BottomSheet
      title={t("boardAddExistingTitle")}
      confirmLabel={t("boardAddExistingConfirm")}
      onConfirm={submit}
      confirmDisabled={picked.length === 0}
      confirmLoading={add.isPending || added}
      dirty={!added && picked.length > 0}
      scroll
    >
      <DismissWhen when={added} />
      <Text variant="footnote" className="px-6 pt-2 text-muted-foreground">
        {t("boardAddExistingBody")}
      </Text>
      <BoardPickList items={items} picked={picked} onToggle={toggle} disabled={add.isPending} />
      {preview.length > 0 ? (
        <ListSection
          header={t("boardAddExistingPreview")}
          footer={t("boardAddExistingInfo", { boardName: board.name })}
        >
          <ListRow
            title={board.name}
            subtitle={t("boardAddExistingParent")}
            value={board.prefix ?? undefined}
            dot="bg-muted-foreground"
            dotColor={board.color ?? undefined}
          />
          {preview.map((row) => {
            const unpick = () => toggle(row.board.id);
            const unpickLabel: string = t("boardAddExistingUnpick", {
              name: row.board.name,
              parent: board.name,
            });
            return (
              <ListRow
                key={row.board.id}
                title={row.board.name}
                subtitle={row.picked ? undefined : t("boardAddExistingIncluded")}
                value={row.board.prefix ?? undefined}
                dot="bg-muted-foreground"
                dotColor={row.board.color ?? undefined}
                indent={row.indent + 1}
                // The row reads as one element, so screen readers reach the X as its action.
                actions={
                  row.picked && !add.isPending
                    ? [{ name: "unpick", label: unpickLabel, onAction: unpick }]
                    : undefined
                }
                trailing={
                  row.picked ? (
                    <IconButton
                      icon={X}
                      label={unpickLabel}
                      onPress={unpick}
                      disabled={add.isPending}
                    />
                  ) : undefined
                }
              />
            );
          })}
        </ListSection>
      ) : null}
      <View className="h-10" />
    </BottomSheet>
  );
}

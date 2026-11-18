import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import { ArrowRight, FolderTree, Link, Plus, Unlink } from "lucide-react-native";
import * as React from "react";
import { Alert, RefreshControl, ScrollView, View } from "react-native";

import { canEditBoard, flattenBoardTree, type BoardRow } from "@/components/boards/boardModel";
import { addChildBoardsHref, boardHref, newChildBoardHref } from "@/components/boards/boardRoutes";
import {
  canRemoveChild,
  childBoardRows,
  type ChildBoardRow,
} from "@/components/boards/settings/boardSettingsModel";
import { ActionSheet, type ActionSheetAction } from "@/components/kazan/ActionSheet";
import { EmptyState } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import { useRoleGatedMenu } from "@/components/kazan/useRoleGatedMenu";
import { IconButton } from "@/components/ui/icon-button";
import { SkeletonGroup, SkeletonRow } from "@/components/ui/skeleton";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useRemoveChildBoard, useBoardRoles } from "@/hooks/queries/boardSettings";
import { useBoardTree, useMyBoardRole, useMyBoardRoleState } from "@/hooks/queries/boards";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation } from "@/i18n";

const NO_IDS: readonly string[] = [];

/**
 * The child boards of a board: every descendant from the board tree, indented
 * per level, as Kazan web's hierarchy lists them. Board owners and admins add
 * a child with `+` (a new board or existing ones) and, on a direct child they
 * also own or administer, remove it from this board; every other tap opens
 * the board.
 */
export function ChildBoardsScreen() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const router = useRouter();
  const online = useOnlineWriteGuard();
  const params = useLocalSearchParams<{ boardId?: string | string[] }>();
  const boardId: string =
    (Array.isArray(params.boardId) ? params.boardId[0] : params.boardId) ?? "";
  const tree = useBoardTree();
  const canManage: boolean = canEditBoard(useMyBoardRole(boardId || undefined));
  const remove = useRemoveChildBoard();
  const [menuRow, setMenuRow] = React.useState<ChildBoardRow | null>(null);
  const [choosing, setChoosing] = React.useState<boolean>(false);
  const [refreshing, setRefreshing] = React.useState<boolean>(false);

  const rows: BoardRow[] = React.useMemo(() => flattenBoardTree(tree.data ?? []), [tree.data]);
  const board: BoardRow | undefined = rows.find((row) => row.id === boardId);
  const children: ChildBoardRow[] = React.useMemo(
    () => childBoardRows(rows, boardId),
    [rows, boardId]
  );
  // Remove also needs OWNER or ADMIN on the child, the role the server checks
  // for it, so a manager reads the member list of each direct child.
  const directIds: readonly string[] = React.useMemo(
    () => (canManage ? children.filter((row) => row.direct).map((row) => row.board.id) : NO_IDS),
    [canManage, children]
  );
  const roles: ReadonlyMap<string, string> = useBoardRoles(directIds);
  const closeMenu = React.useCallback(() => setMenuRow(null), []);
  // Remove depends on the role on the held child; its read is the one
  // `useBoardRoles` shares, so the menu opens once it has settled.
  const menuVisible: boolean = useRoleGatedMenu({
    requested: menuRow !== null,
    role: useMyBoardRoleState(menuRow?.board.id),
    onCancel: closeMenu,
    logTag: "boardSettings",
  });

  const openBoard = React.useCallback(
    (row: ChildBoardRow) => router.push(boardHref(row.board.id)),
    [router]
  );

  const confirmRemove = React.useCallback(
    (row: ChildBoardRow) => {
      if (!online()) return;
      const parent: string = board?.name ?? "";
      Alert.alert(
        t("boardChildBoardsRemoveTitle"),
        t("boardChildBoardsRemoveBody", { child: row.board.name, parent }),
        [
          { text: t("kitCancel"), style: "cancel" },
          {
            text: t("boardChildBoardsRemove"),
            style: "destructive",
            onPress: () =>
              // A failure is toasted by the hook, with the board's name above the server's message.
              remove.mutate(
                { childBoardId: row.board.id, childBoardName: row.board.name },
                {
                  onSuccess: () =>
                    toast.success(t("boardChildBoardsRemoved", { name: row.board.name, parent })),
                }
              ),
          },
        ]
      );
    },
    [online, board?.name, t, remove]
  );

  const refresh = React.useCallback(() => {
    setRefreshing(true);
    tree
      .refetch()
      .catch((error: unknown) => {
        // A failed read shows in the query state and the app's error layer; this only records the pull.
        console.warn(
          "[boardSettings] child boards refresh failed",
          error instanceof Error ? error.message : error
        );
      })
      .finally(() => setRefreshing(false));
  }, [tree]);

  const menuActions: ActionSheetAction[] = menuRow
    ? [
        { label: t("boardChildBoardsOpen"), icon: ArrowRight, onPress: () => openBoard(menuRow) },
        ...(canRemoveChild(menuRow, canManage, roles)
          ? [
              {
                label: t("boardChildBoardsRemove"),
                icon: Unlink,
                destructive: true,
                onPress: () => confirmRemove(menuRow),
              },
            ]
          : []),
      ]
    : [];

  const choiceActions: ActionSheetAction[] = [
    {
      label: t("boardChildBoardsNew"),
      icon: Plus,
      onPress: () => router.push(newChildBoardHref(boardId)),
    },
    {
      label: t("boardChildBoardsExisting"),
      icon: Link,
      onPress: () => router.push(addChildBoardsHref(boardId)),
    },
  ];

  const header = (
    <Stack.Screen
      options={{
        headerRight: canManage
          ? () => (
              <IconButton
                icon={Plus}
                label={t("boardChildBoardsAdd")}
                onPress={() => setChoosing(true)}
              />
            )
          : undefined,
      }}
    />
  );

  const sheets = (
    <>
      <ActionSheet
        visible={menuVisible}
        onClose={closeMenu}
        title={menuRow?.board.name}
        actions={menuActions}
      />
      <ActionSheet
        visible={choosing}
        onClose={() => setChoosing(false)}
        title={t("boardChildBoardsChoiceTitle")}
        message={t("boardChildBoardsChoiceBody")}
        actions={choiceActions}
      />
    </>
  );

  if (tree.isPending) {
    return (
      <View className="flex-1 bg-screen pt-6">
        {header}
        <SkeletonGroup>
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
        </SkeletonGroup>
        {sheets}
      </View>
    );
  }

  if (tree.isError && !tree.data) {
    return (
      <View className="flex-1 bg-screen">
        {header}
        <EmptyState
          variant="error"
          title={t("boardsError")}
          onAction={() => void tree.refetch()}
          className="mt-16"
        />
        {sheets}
      </View>
    );
  }

  return (
    <>
      {header}
      <ScrollView
        className="flex-1 bg-screen"
        contentInsetAdjustmentBehavior="automatic"
        contentContainerClassName="flex-grow pb-10"
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={refresh}
            tintColor={colors.mutedForeground}
          />
        }
      >
        {children.length === 0 ? (
          <EmptyState
            icon={FolderTree}
            title={t("boardChildBoardsEmpty")}
            actionLabel={canManage ? t("boardChildBoardsAdd") : undefined}
            onAction={canManage ? () => setChoosing(true) : undefined}
            className="mt-16"
          />
        ) : (
          <ListSection>
            {children.map((row) => (
              <ListRow
                key={row.board.id}
                title={row.board.name}
                value={row.board.prefix ?? undefined}
                dot="bg-muted-foreground"
                dotColor={row.board.color ?? undefined}
                indent={row.indent}
                onPress={canManage && row.direct ? () => setMenuRow(row) : () => openBoard(row)}
                disabled={remove.isPending}
              />
            ))}
          </ListSection>
        )}
      </ScrollView>
      {sheets}
    </>
  );
}

import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import { Folder, Pencil, Plus, Tag, Trash2 } from "lucide-react-native";
import * as React from "react";
import { RefreshControl, ScrollView, View } from "react-native";

import { canEditBoard } from "@/components/boards/boardModel";
import { boardOptionFormHref, type BoardOptionListKind } from "@/components/boards/boardRoutes";
import {
  BOARD_OPTION_TEXTS,
  useBoardOptionList,
  useConfirmDeleteBoardOption,
  type BoardOptionTexts,
} from "@/components/boards/settings/BoardOptionFormSheet";
import {
  boardOptionRows,
  type BoardOptionRow,
} from "@/components/boards/settings/boardSettingsModel";
import { ActionSheet, type ActionSheetAction } from "@/components/kazan/ActionSheet";
import { EmptyState } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import { IconButton } from "@/components/ui/icon-button";
import { SkeletonGroup, SkeletonRow } from "@/components/ui/skeleton";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useMyBoardRole } from "@/hooks/queries/boards";
import { useTranslation } from "@/i18n";
import { DEFAULT_OPTION_COLOR } from "@/lib/schemas/boards";

type BoardOptionsScreenProps = { kind: BoardOptionListKind };

/**
 * The labels or projects of a board, in one list as `GET /labels` or
 * `GET /projects` returns them: this board's and its related boards', an
 * option of another board carrying that board's name. Board owners and admins
 * add with `+`, edit by tapping a row and delete from the row's long-press
 * menu or the end of its sheet; anyone else reads the list.
 */
export function BoardOptionsScreen({ kind }: BoardOptionsScreenProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const router = useRouter();
  const params = useLocalSearchParams<{ boardId?: string | string[] }>();
  const boardId: string =
    (Array.isArray(params.boardId) ? params.boardId[0] : params.boardId) ?? "";
  const texts: BoardOptionTexts = BOARD_OPTION_TEXTS[kind];
  const list = useBoardOptionList(kind, boardId || undefined);
  const canManage: boolean = canEditBoard(useMyBoardRole(boardId || undefined));
  const { confirmDelete } = useConfirmDeleteBoardOption(kind);
  const [menuRow, setMenuRow] = React.useState<BoardOptionRow | null>(null);
  const [refreshing, setRefreshing] = React.useState<boolean>(false);

  const rows: BoardOptionRow[] = React.useMemo(
    () => boardOptionRows(list.data ?? [], boardId),
    [list.data, boardId]
  );

  const openAdd = React.useCallback(() => {
    router.push(boardOptionFormHref(boardId, kind));
  }, [router, boardId, kind]);

  const openEdit = React.useCallback(
    (row: BoardOptionRow) => {
      router.push(boardOptionFormHref(boardId, kind, row.option.id));
    },
    [router, boardId, kind]
  );

  const refresh = React.useCallback(() => {
    setRefreshing(true);
    list
      .refetch()
      .catch((error: unknown) => {
        // A failed read shows in the query state and the app's error layer; this only records the pull.
        console.warn(
          `[boardSettings] ${kind} refresh failed`,
          error instanceof Error ? error.message : error
        );
      })
      .finally(() => setRefreshing(false));
  }, [list, kind]);

  const menuActions: ActionSheetAction[] = menuRow
    ? [
        { label: t("boardOptionsEdit"), icon: Pencil, onPress: () => openEdit(menuRow) },
        {
          label: t("boardOptionsDelete"),
          icon: Trash2,
          destructive: true,
          onPress: () =>
            confirmDelete({ ownerBoardId: menuRow.option.boardId, optionId: menuRow.option.id }),
        },
      ]
    : [];

  const header = (
    <Stack.Screen
      options={{
        headerRight: canManage
          ? () => <IconButton icon={Plus} label={t(texts.add)} onPress={openAdd} />
          : undefined,
      }}
    />
  );

  if (list.isPending) {
    return (
      <View className="flex-1 bg-screen pt-6">
        {header}
        <SkeletonGroup>
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
        </SkeletonGroup>
      </View>
    );
  }

  if (list.isError && !list.data) {
    return (
      <View className="flex-1 bg-screen">
        {header}
        <EmptyState
          variant="error"
          title={t(texts.loadError)}
          onAction={() => void list.refetch()}
          className="mt-16"
        />
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
        {rows.length === 0 ? (
          <EmptyState
            icon={kind === "labels" ? Tag : Folder}
            title={t(texts.emptyTitle)}
            message={t(texts.emptyBody)}
            actionLabel={canManage ? t(texts.add) : undefined}
            onAction={canManage ? openAdd : undefined}
            className="mt-16"
          />
        ) : (
          <ListSection>
            {rows.map((row) => (
              <ListRow
                key={row.option.id}
                title={row.option.name}
                subtitle={row.ownerBoardName ?? undefined}
                dotColor={row.option.color ?? DEFAULT_OPTION_COLOR}
                onPress={canManage ? () => openEdit(row) : undefined}
                onLongPress={canManage ? () => setMenuRow(row) : undefined}
              />
            ))}
          </ListSection>
        )}
      </ScrollView>
      <ActionSheet
        visible={menuRow !== null}
        onClose={() => setMenuRow(null)}
        title={menuRow?.option.name}
        actions={menuActions}
      />
    </>
  );
}

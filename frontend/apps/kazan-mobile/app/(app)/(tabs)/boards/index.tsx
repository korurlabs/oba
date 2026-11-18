import { useQueryClient } from "@tanstack/react-query";
import * as Haptics from "expo-haptics";
import { useRouter } from "expo-router";
import { Building, Kanban, Pencil, Pin, PinOff, Plus, Search, Settings } from "lucide-react-native";
import * as React from "react";
import { Platform, RefreshControl, ScrollView, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { useMe } from "@/hooks/queries/users";
import { BoardListRow } from "@/components/boards/BoardListRow";
import {
  activeSprint,
  canCreateBoard,
  canEditBoard,
  flattenBoardTree,
  recentBoardRows,
  searchBoardRows,
  type BoardRow,
} from "@/components/boards/boardModel";
import { useBoardLinks } from "@/components/boards/boardNavigation";
import { pinnedBoardRows } from "@/components/boards/boardPins";
import { OrganizationChip } from "@/components/boards/OrganizationChip";
import { OpenP1IncidentBanner } from "@/components/incidents/OpenP1IncidentBanner";
import { ActionSheet, type ActionSheetAction } from "@/components/kazan/ActionSheet";
import { EmptyState, InlineError } from "@/components/kazan/EmptyState";
import { Fab, FAB_LIST_BOTTOM_PADDING } from "@/components/kazan/Fab";
import { ListSection } from "@/components/kazan/List";
import { OfflineBanner } from "@/components/kazan/OfflineBanner";
import { SearchField } from "@/components/kazan/SearchField";
import {
  TAB_ROOT_CONTENT_TOP,
  TabRootHeader,
  type TabRootHeaderAction,
} from "@/components/kazan/TabRootHeader";
import { useRoleGatedMenu } from "@/components/kazan/useRoleGatedMenu";
import { SkeletonGroup, SkeletonRow } from "@/components/ui/skeleton";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useBoardPins } from "@/hooks/queries/boardPins";
import {
  boardKeys,
  boardTicketKeys,
  useBoardTree,
  useMyBoardRoleState,
  useOpenTicketCounts,
  useSprintsOfBoards,
} from "@/hooks/queries/boards";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useOrganizationRole } from "@/hooks/useOrganizationRole";
import { useRecentBoards } from "@/hooks/useRecentBoards";
import { useTranslation } from "@/i18n";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import type { Sprint } from "@/lib/schemas/boards";

/**
 * The Boards tab root: the active organization, a banner while a P1 incident
 * is open, the boards the user pinned, the boards opened last on this phone,
 * and every other board, child boards under their parent, with its active
 * sprint and open tickets. An organization without boards gets "No boards
 * yet" instead.
 *
 * Long-pressing a board opens its menu: Pin or Unpin, Rename and, for a board
 * owner or admin, Board settings. Pins are the user's own and are the same
 * pins Kazan web's sidebar shows.
 *
 * New board is the screen's main action: the floating button on Android and
 * the plus on the title row on iOS, for every role above guest. Tickets are
 * created from a board. iOS switches the organization from the chip under
 * the title, Android from the building icon on the title row.
 */
export default function BoardsScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const colors = useThemeColors();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const me = useMe(true);
  const role: string | null = useOrganizationRole();
  const canCreate: boolean = canCreateBoard(role);
  const { openBoard, openBoardSettings, openNewBoard, openRenameBoard } = useBoardLinks();
  const pins = useBoardPins();
  const online = useOnlineWriteGuard();
  const [menuRow, setMenuRow] = React.useState<BoardRow | null>(null);
  const menuRoleState = useMyBoardRoleState(menuRow?.id);
  const closeMenu = React.useCallback(() => setMenuRow(null), []);
  const menuVisible: boolean = useRoleGatedMenu({
    requested: menuRow !== null,
    role: menuRoleState,
    onCancel: closeMenu,
    logTag: "boards",
  });
  const { recent } = useRecentBoards();
  const tree = useBoardTree();
  const openCounts = useOpenTicketCounts();
  const [refreshing, setRefreshing] = React.useState<boolean>(false);
  const [query, setQuery] = React.useState<string>("");

  const rows: BoardRow[] = React.useMemo(
    () => flattenBoardTree(tree.data ?? [], pins.sidebar),
    [tree.data, pins.sidebar]
  );
  const pinnedRows: BoardRow[] = React.useMemo(
    () => pinnedBoardRows(rows, pins.sidebar),
    [rows, pins.sidebar]
  );
  const pinnedIds: Set<string> = React.useMemo(
    () => new Set(pinnedRows.map((row) => row.id)),
    [pinnedRows]
  );
  // A pinned board shows once, in Pinned, as the web's sidebar shows it.
  const recentRows: BoardRow[] = React.useMemo(
    () => recentBoardRows(rows, recent).filter((row) => !pinnedIds.has(row.id)),
    [rows, recent, pinnedIds]
  );
  const unpinnedRows: BoardRow[] = React.useMemo(
    () => rows.filter((row) => !pinnedIds.has(row.id)),
    [rows, pinnedIds]
  );
  const boardIds: string[] = React.useMemo(() => rows.map((row) => row.id), [rows]);
  const sprintsByBoard: Record<string, Sprint[]> = useSprintsOfBoards(boardIds);
  const searching: boolean = query.trim().length > 0;
  const matchedRows: BoardRow[] = React.useMemo(() => searchBoardRows(rows, query), [rows, query]);
  const organizationName: string =
    me.data?.organizations?.find((organization) => organization.id === orgId)?.name ?? "";

  const openOrgSwitcher = React.useCallback(() => router.push("/boards/org-switcher"), [router]);

  const refresh = React.useCallback(() => {
    if (!orgId) return;
    setRefreshing(true);
    Promise.all([
      queryClient.refetchQueries({ queryKey: boardKeys.all(orgId), type: "active" }),
      queryClient.refetchQueries({ queryKey: boardTicketKeys.openByBoard(orgId), type: "active" }),
      queryClient.refetchQueries({ queryKey: boardTicketKeys.openP1(orgId), type: "active" }),
    ])
      .catch((error: unknown) => {
        // The list and its sections show their own failure; this only ends the pull.
        console.warn("[boards] refresh failed", error instanceof Error ? error.message : error);
      })
      .finally(() => setRefreshing(false));
  }, [orgId, queryClient]);

  const openBoardRow = React.useCallback((row: BoardRow) => openBoard(row.id), [openBoard]);
  const openMenu = React.useCallback((row: BoardRow) => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch((error: unknown) => {
      // Haptics are a courtesy: the menu opens without them, so the failure is only logged.
      console.warn("[boards] haptics failed", error instanceof Error ? error.message : error);
    });
    setMenuRow(row);
  }, []);

  const rowProps = (row: BoardRow) => ({
    row,
    activeSprint: sprintsByBoard[row.id] ? activeSprint(sprintsByBoard[row.id]) : undefined,
    openCount: openCounts.data?.[row.id],
    onPress: openBoardRow,
    onLongPress: openMenu,
  });

  const menuPinned: boolean = menuRow ? pins.isPinned(menuRow) : false;
  const menuActions: ActionSheetAction[] = menuRow
    ? [
        {
          label: menuPinned ? t("boardsUnpin") : t("boardsPin"),
          icon: menuPinned ? PinOff : Pin,
          disabled: !pins.ready,
          onPress: () => {
            if (online()) pins.togglePin(menuRow);
          },
        },
        { label: t("boardsRename"), icon: Pencil, onPress: () => openRenameBoard(menuRow.id) },
        // Board settings is a board owner's or admin's, as on the web. The menu
        // opens once the role is read, as iOS cannot add an item to a shown
        // sheet; a failed read opens it without Board settings.
        ...(canEditBoard(menuRoleState.role)
          ? [
              {
                label: t("boardSettings"),
                icon: Settings,
                onPress: () => openBoardSettings(menuRow.id),
              },
            ]
          : []),
      ]
    : [];

  const noBoards: boolean = tree.isSuccess && rows.length === 0;

  let list: React.ReactNode;
  if (tree.isPending) {
    list = (
      <SkeletonGroup>
        {[0, 1, 2, 3, 4].map((index) => (
          <SkeletonRow key={index} />
        ))}
      </SkeletonGroup>
    );
  } else if (tree.isError) {
    list = (
      <InlineError
        message={t("boardsError")}
        onRetry={() => void tree.refetch()}
        className="mx-4"
      />
    );
  } else if (noBoards) {
    list = (
      <EmptyState
        icon={Kanban}
        title={t("homeNoBoardsTitle")}
        message={t("homeNoBoardsBody")}
        actionLabel={canCreate ? t("homeNoBoardsAction") : undefined}
        onAction={canCreate ? openNewBoard : undefined}
        className="mt-16"
      />
    );
  } else if (searching && matchedRows.length === 0) {
    list = (
      <EmptyState
        icon={Search}
        title={t("boardsSearchEmpty", { query: query.trim() })}
        className="mt-8"
      />
    );
  } else if (searching) {
    list = (
      <ListSection header={t("boardsAll")}>
        {matchedRows.map((row) => (
          <BoardListRow key={row.id} variant="all" {...rowProps(row)} />
        ))}
      </ListSection>
    );
  } else {
    list = (
      <>
        {pinnedRows.length > 0 ? (
          <ListSection header={t("boardsPinned")}>
            {pinnedRows.map((row) => (
              <BoardListRow key={row.id} variant="recent" {...rowProps(row)} />
            ))}
          </ListSection>
        ) : null}
        {recentRows.length > 0 ? (
          <ListSection header={t("boardsRecent")}>
            {recentRows.map((row) => (
              <BoardListRow key={row.id} variant="recent" {...rowProps(row)} />
            ))}
          </ListSection>
        ) : null}
        {unpinnedRows.length > 0 ? (
          <ListSection
            header={t("boardsAll")}
            footer={Platform.OS === "ios" ? t("boardsAllFooter") : undefined}
          >
            {unpinnedRows.map((row) => (
              <BoardListRow key={row.id} variant="all" {...rowProps(row)} />
            ))}
          </ListSection>
        ) : null}
      </>
    );
  }

  const headerActions: TabRootHeaderAction[] =
    Platform.OS === "ios"
      ? canCreate
        ? [{ icon: Plus, label: t("boardsNewBoard"), onPress: openNewBoard }]
        : []
      : [{ icon: Building, label: t("boardsSwitchOrganization"), onPress: openOrgSwitcher }];

  return (
    <SafeAreaView edges={["top"]} className="flex-1 bg-screen">
      <TabRootHeader title={t("tabBoards")} actions={headerActions} />
      <OfflineBanner />
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        keyboardDismissMode="on-drag"
        keyboardShouldPersistTaps="handled"
        contentContainerClassName={TAB_ROOT_CONTENT_TOP}
        contentContainerStyle={{ paddingBottom: FAB_LIST_BOTTOM_PADDING + 40 }}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={refresh}
            tintColor={colors.mutedForeground}
          />
        }
      >
        {Platform.OS === "ios" && organizationName ? (
          <View className="mb-3.5 flex-row px-4">
            <OrganizationChip
              name={organizationName}
              accessibilityLabel={t("boardsOrganization", { name: organizationName })}
              onPress={openOrgSwitcher}
            />
          </View>
        ) : null}
        {!noBoards ? (
          <SearchField label={t("boardsSearchPlaceholder")} value={query} onChangeText={setQuery} />
        ) : null}
        <OpenP1IncidentBanner className="mx-4 mt-3.5" />
        {list}
      </ScrollView>
      <ActionSheet
        visible={menuVisible}
        onClose={closeMenu}
        title={menuRow?.name}
        actions={menuActions}
      />
      {canCreate && tree.isSuccess && !noBoards ? (
        <Fab label={t("boardsNewBoard")} extended onPress={openNewBoard} />
      ) : null}
    </SafeAreaView>
  );
}

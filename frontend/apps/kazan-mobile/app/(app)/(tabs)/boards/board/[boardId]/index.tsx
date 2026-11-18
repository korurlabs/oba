import { useIsFocused } from "@react-navigation/native";
import { useQueryClient } from "@tanstack/react-query";
import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import {
  ArrowRightLeft,
  Ellipsis,
  EllipsisVertical,
  ExternalLink,
  Flag,
  ListChecks,
  Lock,
  Plus,
  Search,
  Settings,
} from "lucide-react-native";
import * as React from "react";
import { BackHandler, Platform, RefreshControl, View } from "react-native";
import type { SearchBarCommands } from "react-native-screens";

import { useMe } from "@/hooks/queries/users";
import {
  boardRouteParamPatch,
  canEditBoard,
  canEditBoardTickets,
  canWriteBoard,
  parseBoardRoute,
  quarterOf,
  sprintForView,
  type BoardRouteState,
  type BoardView,
  type Quarter,
  type TicketScope,
} from "@/components/boards/boardModel";
import { useBoardLinks } from "@/components/boards/boardNavigation";
import { boardMilestonesHref, bulkMoveHref } from "@/components/boards/boardRoutes";
import {
  inListOrder,
  selectionHasSubTickets,
  unfinishedIds,
} from "@/components/boards/selection/bulkMoveModel";
import { showBulkMove } from "@/components/boards/selection/bulkMoveStore";
import { SelectionBar, SelectionTextButton } from "@/components/boards/selection/SelectionBar";
import {
  searchRestoreSteps,
  type SearchBarStep,
} from "@/components/boards/selection/selectionSearchModel";
import {
  useTicketSelection,
  type SelectionGroup,
} from "@/components/boards/selection/useTicketSelection";
import { useSprintActions } from "@/components/boards/useSprintActions";
import { BoardViewTabs } from "@/components/boards/BoardViewTabs";
import { useSprintDates, useSprintStatusLabel } from "@/components/boards/SprintHeader";
import { AllView } from "@/components/boards/views/AllView";
import { BacklogView } from "@/components/boards/views/BacklogView";
import { BoardListSkeleton } from "@/components/boards/views/BoardListSkeleton";
import { navBarNewTicket } from "@/components/boards/views/boardViewModel";
import type { BoardViewProps } from "@/components/boards/views/boardViewProps";
import { RoadmapView, useQuarterLabel } from "@/components/boards/views/RoadmapView";
import { SprintView } from "@/components/boards/views/SprintView";
import { ActionSheet, type ActionSheetAction } from "@/components/kazan/ActionSheet";
import { EmptyState } from "@/components/kazan/EmptyState";
import { BAR_TITLE_MAX_FONT_SCALE } from "@/components/kazan/stackHeader";
import { useRoleGatedMenu } from "@/components/kazan/useRoleGatedMenu";
import { IconButton } from "@/components/ui/icon-button";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import {
  boardKeys,
  boardTicketKeys,
  useBacklog,
  useBoard,
  useBoardSprints,
  useBoardTickets,
  useMyBoardRoleState,
} from "@/hooks/queries/boards";
import { useDebouncedSearch } from "@/hooks/useDebouncedSearch";
import { useOrganizationRole } from "@/hooks/useOrganizationRole";
import { useRecentBoards } from "@/hooks/useRecentBoards";
import { useTranslation } from "@/i18n";
import { toApiError } from "@/lib/api";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import type { BoardTicket, Sprint } from "@/lib/schemas/boards";

/**
 * A board: its views All, Sprint, Backlog and Roadmap under the nav bar. The
 * view, the Sprint layout, the picked sprint, the search text and the filters
 * live in the route params, so a board link reopens the same view and Back
 * leaves the board as it was.
 *
 * Selection mode picks tickets of one list, the sprint or the backlog, and
 * moves them together through the Move selected tickets sheet, as the web's
 * row selection and bulk move. It is offered in the lists, not on Kanban or
 * the Roadmap, to a board owner, admin or member, the web's check.
 */
export default function BoardScreen() {
  const params = useLocalSearchParams<Record<string, string | string[]>>();
  const boardId: string | undefined = Array.isArray(params.boardId)
    ? params.boardId[0]
    : params.boardId;
  const state: BoardRouteState = parseBoardRoute(params);
  const { t } = useTranslation();
  const router = useRouter();
  const focused: boolean = useIsFocused();
  const colors = useThemeColors();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const meId: string | undefined = useMe(true).data?.id;
  const organizationRole: string | null = useOrganizationRole();
  const boardRoleState = useMyBoardRoleState(boardId);
  const boardRole: string | null = boardRoleState.role;
  const canWrite: boolean = canWriteBoard(organizationRole, boardRole);
  const { openBoardSettings, openFilters, openNewTicket, openWebBoardPage } = useBoardLinks();
  const { remember } = useRecentBoards();
  const quarterLabel = useQuarterLabel();
  const sprintStatusLabel = useSprintStatusLabel();

  const board = useBoard(boardId);
  const sprints = useBoardSprints(boardId);
  const backlog = useBacklog(boardId);
  const sprint: Sprint | null = sprintForView(state.view, sprints.data ?? [], state.sprintId);
  const sprintScope: TicketScope | null = sprint ? { sprintId: sprint.id } : null;
  const backlogScope: TicketScope | null =
    backlog.data && (state.view === "all" || state.view === "backlog")
      ? { backlogId: backlog.data.id }
      : null;
  const sprintTickets = useBoardTickets(boardId, sprintScope, state.filters, state.search);
  const backlogTickets = useBoardTickets(boardId, backlogScope, state.filters, state.search);
  const { left } = useSprintDates(sprint);

  const [quarterOffset, setQuarterOffset] = React.useState<number>(0);
  const quarter: Quarter = React.useMemo(
    () => quarterOf(new Date(), quarterOffset),
    [quarterOffset]
  );
  const [menuOpen, setMenuOpen] = React.useState<boolean>(false);
  const closeMenu = React.useCallback(() => setMenuOpen(false), []);
  // The menu's sprint actions and Board settings depend on the board role.
  const menuVisible: boolean = useRoleGatedMenu({
    requested: menuOpen,
    role: boardRoleState,
    onCancel: closeMenu,
    logTag: "board",
  });
  const [refreshing, setRefreshing] = React.useState<boolean>(false);
  const {
    state: selectionState,
    selection,
    start: startSelection,
    end: endSelection,
    prune: pruneSelection,
  } = useTicketSelection();
  const selecting: boolean = selectionState !== null;
  // The lists that take a selection: All, Backlog, and a sprint shown as a list.
  const selectableView: boolean =
    state.view === "all" ||
    state.view === "backlog" ||
    (state.view === "sprint" && state.layout === "list" && sprint !== null);
  const canSelect: boolean = selectableView && canEditBoardTickets(boardRole);

  const boardLoaded: boolean = board.isSuccess;
  React.useEffect(() => {
    if (boardLoaded && boardId) remember(boardId);
  }, [boardLoaded, boardId, remember]);

  const onChange = React.useCallback(
    (next: BoardRouteState) => router.setParams(boardRouteParamPatch(next)),
    [router]
  );

  // A selection belongs to the list it was made in; another view, layout or
  // sprint, or a role that may no longer move tickets, ends it.
  const selectionPlace: string = `${state.view}:${state.layout}:${sprint?.id ?? ""}`;
  React.useEffect(() => {
    endSelection();
  }, [endSelection, selectionPlace]);
  React.useEffect(() => {
    if (selecting && !canSelect) endSelection();
  }, [canSelect, endSelection, selecting]);
  // Tickets that left their list, after a move, a refetch or a search, leave the selection.
  React.useEffect(() => {
    const rows: BoardTicket[] | undefined = sprintTickets.data;
    if (!rows) return;
    const listed: string[] = rows.map((row) => row.id);
    pruneSelection("sprint", listed);
  }, [pruneSelection, sprintTickets.data]);
  React.useEffect(() => {
    const rows: BoardTicket[] | undefined = backlogTickets.data;
    if (!rows) return;
    const listed: string[] = rows.map((row) => row.id);
    pruneSelection("backlog", listed);
  }, [backlogTickets.data, pruneSelection]);
  // Android's Back ends selection mode before it leaves the board. Only while
  // the board is on top: with the Move sheet open, Back closes the sheet.
  React.useEffect(() => {
    if (Platform.OS !== "android" || !selecting || !focused) return;
    const subscription = BackHandler.addEventListener("hardwareBackPress", () => {
      endSelection();
      return true;
    });
    return () => subscription.remove();
  }, [endSelection, focused, selecting]);

  // The header search writes `q` once typing pauses; the views then show the
  // result count or "No tickets match". A search from a link fills the bar.
  const stateRef = React.useRef<BoardRouteState>(state);
  React.useEffect(() => {
    stateRef.current = state;
  });
  const commitSearch = React.useCallback(
    (search: string) => onChange({ ...stateRef.current, search }),
    [onChange]
  );
  const [searchText, setSearchText] = useDebouncedSearch(state.search, commitSearch);
  const searchBarRef = React.useRef<SearchBarCommands>(null);
  const initialSearch = React.useRef<string>(state.search);
  React.useEffect(() => {
    if (board.data && initialSearch.current) {
      searchBarRef.current?.setText(initialSearch.current);
      initialSearch.current = "";
    }
  }, [board.data]);
  // On iOS the search field stays out of the header until the Search action
  // opens it, so the views keep their height; a search from a link opens it.
  // Android's native search is already an action that expands in the bar.
  const [searchOpen, setSearchOpen] = React.useState<boolean>(state.search !== "");
  const [focusSearch, setFocusSearch] = React.useState<boolean>(false);
  React.useEffect(() => {
    if (!focusSearch) return;
    const frame: number = requestAnimationFrame(() => {
      searchBarRef.current?.focus();
      setFocusSearch(false);
    });
    return () => cancelAnimationFrame(frame);
  }, [focusSearch]);
  // Selection mode takes search out of the header while the search in force
  // keeps filtering the list. The native search is closed first, while the
  // bar is still mounted: Android hides the title and the right buttons while
  // its search is open and shows them again only when it closes, so a bar
  // removed open would leave selection without its count and Cancel, and iOS
  // ends its active search presentation instead of losing it with the bar.
  // That close reports an emptied, closed field; the bar's handlers skip it so
  // `q` and the filtered list stay, and the selection is not pruned by it.
  const keepSearchRef = React.useRef<boolean>(false);
  const typeSearch = React.useCallback(
    (text: string) => {
      if (!keepSearchRef.current) setSearchText(text);
    },
    [setSearchText]
  );
  const closeSearch = React.useCallback(() => {
    if (keepSearchRef.current) return;
    setSearchText("");
    setSearchOpen(false);
  }, [setSearchText]);
  const beginSelection = React.useCallback(
    (group: SelectionGroup | null, ids: readonly string[]) => {
      keepSearchRef.current = true;
      searchBarRef.current?.cancelSearch();
      startSelection(group, ids);
    },
    [startSelection]
  );
  // An empty field folds back into the Search action; a search in force
  // returns to the field that comes back when selection ends.
  const searchTextRef = React.useRef<string>(searchText);
  React.useEffect(() => {
    searchTextRef.current = searchText;
  });
  const wasSelecting = React.useRef<boolean>(false);
  React.useEffect(() => {
    if (selecting) {
      wasSelecting.current = true;
      if (searchTextRef.current.trim() === "") setSearchOpen(false);
      return;
    }
    keepSearchRef.current = false;
    if (!wasSelecting.current) return;
    wasSelecting.current = false;
    const text: string = searchTextRef.current;
    const steps: SearchBarStep[] = searchRestoreSteps(Platform.OS, text);
    if (steps.length === 0) return;
    const frame: number = requestAnimationFrame(() => {
      const bar: SearchBarCommands | null = searchBarRef.current;
      if (!bar) return;
      for (const step of steps) {
        if (step === "focus") bar.focus();
        else if (step === "setText") bar.setText(text);
        else bar.blur();
      }
    });
    return () => cancelAnimationFrame(frame);
  }, [selecting]);

  const refresh = React.useCallback(() => {
    if (!orgId || !boardId) return;
    setRefreshing(true);
    Promise.all([
      queryClient.refetchQueries({ queryKey: boardKeys.all(orgId), type: "active" }),
      queryClient.refetchQueries({
        queryKey: boardTicketKeys.board(orgId, boardId),
        type: "active",
      }),
    ])
      .catch((error: unknown) => {
        // Each list shows its own failure with a retry; this only ends the pull.
        console.warn(
          "[boards] board refresh failed",
          error instanceof Error ? error.message : error
        );
      })
      .finally(() => setRefreshing(false));
  }, [boardId, orgId, queryClient]);

  const renderRefreshControl = React.useCallback(
    () => (
      <RefreshControl
        refreshing={refreshing}
        onRefresh={refresh}
        tintColor={colors.mutedForeground}
      />
    ),
    [colors.mutedForeground, refresh, refreshing]
  );

  const ticketCount = (list: BoardTicket[] | undefined): number => list?.length ?? 0;
  const subtitle: string | null = (() => {
    switch (state.view) {
      case "all":
        return sprintTickets.isSuccess || backlogTickets.isSuccess
          ? `${t("boardViewAll")} · ${t("boardTicketCount", {
              count: ticketCount(sprintTickets.data) + ticketCount(backlogTickets.data),
            })}`
          : t("boardViewAll");
      case "sprint":
        if (!sprint) return t("boardViewSprint");
        return [sprint.name, state.layout === "list" ? sprintStatusLabel(sprint.status) : left]
          .filter(Boolean)
          .join(" · ");
      case "backlog":
        return backlogTickets.isSuccess
          ? t("boardBacklogCount", { count: ticketCount(backlogTickets.data) })
          : t("boardViewBacklog");
      case "roadmap":
        return `${t("boardViewRoadmap")} · ${quarterLabel(quarter)}`;
    }
  })();

  // The sprint the view shows, for its Edit, Recurrence, Start and Complete actions.
  const sprintActions: ActionSheetAction[] = useSprintActions(boardId ?? "", sprint).actions;

  const groupRows = (group: SelectionGroup): BoardTicket[] =>
    (group === "sprint" ? sprintTickets.data : backlogTickets.data) ?? [];

  /** "Move unfinished tickets" of a list: selection mode with its open tickets picked. */
  const moveUnfinishedAction = (group: SelectionGroup, label: string): ActionSheetAction => {
    const loaded: boolean =
      group === "sprint" ? sprintTickets.isSuccess && sprint !== null : backlogTickets.isSuccess;
    const ids: string[] = loaded ? unfinishedIds(groupRows(group)) : [];
    return {
      label,
      icon: ArrowRightLeft,
      // A loaded list without open tickets starts an empty selection.
      disabled: !loaded,
      onPress: () => beginSelection(group, ids),
    };
  };

  const unfinishedGroups: SelectionGroup[] =
    state.view === "sprint"
      ? ["sprint"]
      : state.view === "backlog"
        ? ["backlog"]
        : sprint
          ? ["sprint", "backlog"]
          : ["backlog"];
  const selectionActions: ActionSheetAction[] = canSelect
    ? [
        {
          label: t("boardSelectTickets"),
          icon: ListChecks,
          // On Sprint the list is the sprint; elsewhere the first pick decides.
          onPress: () => beginSelection(state.view === "sprint" ? "sprint" : null, []),
        },
        ...unfinishedGroups.map((group) =>
          moveUnfinishedAction(
            group,
            state.view === "all"
              ? t("boardMoveUnfinishedFrom", {
                  group: group === "sprint" ? (sprint?.name ?? "") : t("boardBacklog"),
                })
              : t("boardMoveUnfinished")
          )
        ),
      ]
    : [];

  /** Hands the selection to the Move selected tickets sheet and opens it. */
  const openBulkMove = () => {
    if (!boardId || !selectionState?.group || selectionState.ids.size === 0) return;
    const group: SelectionGroup = selectionState.group;
    const rows: BoardTicket[] = groupRows(group);
    const ticketIds: string[] = inListOrder(rows, selectionState.ids);
    showBulkMove({
      boardId,
      ticketIds,
      source: group,
      currentSprintId: group === "sprint" ? (sprint?.id ?? null) : null,
      hasSubTickets: selectionHasSubTickets(rows, ticketIds),
      onMoved: endSelection,
    });
    router.push(bulkMoveHref(boardId));
  };

  // Milestones are every reader's, as on the web's Roadmap tab; Board settings
  // only a board owner's or admin's, as the web's settings menu. The menu
  // opens once the role is read, so the item is neither missing nor late.
  const menuActions: ActionSheetAction[] = board.data
    ? [
        ...sprintActions,
        ...selectionActions,
        {
          label: t("boardSettingsMilestones"),
          icon: Flag,
          onPress: () => boardId && router.push(boardMilestonesHref(boardId)),
        },
        ...(canEditBoard(boardRole)
          ? [
              {
                label: t("boardSettings"),
                icon: Settings,
                onPress: () => boardId && openBoardSettings(boardId),
              },
            ]
          : []),
        {
          label: t("boardOpenWeb"),
          icon: ExternalLink,
          onPress: () => openWebBoardPage(board.data.slug, "board"),
        },
      ]
    : [];

  const newTicketSprint: string | null | undefined =
    canWrite && board.data
      ? navBarNewTicket(state.view, sprint?.id ?? null, Platform.OS === "android")
      : undefined;

  const searchable: boolean = !!board.data && state.view !== "roadmap";
  const searchInHeader: boolean =
    searchable && !selecting && (Platform.OS === "android" || searchOpen);

  const showSearchAction: boolean = searchable && Platform.OS === "ios" && !searchOpen;
  const showMenuAction: boolean = !!board.data;

  // Without an action the header gets no right view at all: iOS draws any
  // view there, even an empty one, inside a glass capsule.
  const selectedCount: number = selectionState?.ids.size ?? 0;
  const headerRight = selecting
    ? () => <SelectionTextButton label={t("kitCancel")} onPress={endSelection} bold />
    : showSearchAction || newTicketSprint !== undefined || showMenuAction
      ? () => (
          <View className="flex-row items-center">
            {showSearchAction ? (
              <IconButton
                icon={Search}
                label={t("boardSearchPlaceholder")}
                onPress={() => {
                  setSearchOpen(true);
                  setFocusSearch(true);
                }}
              />
            ) : null}
            {newTicketSprint !== undefined ? (
              <IconButton
                icon={Plus}
                label={t("boardNewTicket")}
                onPress={() => boardId && openNewTicket(boardId, newTicketSprint)}
              />
            ) : null}
            {showMenuAction ? (
              <IconButton
                icon={Platform.OS === "ios" ? Ellipsis : EllipsisVertical}
                label={t("boardActions")}
                onPress={() => setMenuOpen(true)}
              />
            ) : null}
          </View>
        )
      : undefined;

  const screenOptions = (
    <Stack.Screen
      options={{
        headerBackVisible: !selecting,
        gestureEnabled: !selecting,
        headerTitle: () => (
          <View className={Platform.OS === "ios" ? "items-center" : "items-start"}>
            {selecting ? (
              <Text
                maxFontSizeMultiplier={BAR_TITLE_MAX_FONT_SCALE}
                variant="headline"
                numberOfLines={1}
                accessibilityLiveRegion="polite"
              >
                {t("boardSelectedCount", { count: selectedCount })}
              </Text>
            ) : (
              <>
                <Text
                  maxFontSizeMultiplier={BAR_TITLE_MAX_FONT_SCALE}
                  variant="headline"
                  numberOfLines={1}
                >
                  {board.data?.name ?? ""}
                </Text>
                {board.data && subtitle ? (
                  <Text
                    maxFontSizeMultiplier={BAR_TITLE_MAX_FONT_SCALE}
                    variant="caption"
                    className="text-muted-foreground"
                    numberOfLines={1}
                  >
                    {subtitle}
                  </Text>
                ) : null}
              </>
            )}
          </View>
        ),
        headerRight,
        headerSearchBarOptions: searchInHeader
          ? {
              ref: searchBarRef,
              placeholder: t("boardSearchPlaceholder"),
              hideWhenScrolling: false,
              autoCapitalize: "none",
              onChangeText: (event) => typeSearch(event.nativeEvent.text),
              onCancelButtonPress: closeSearch,
              onClose: closeSearch,
              // An emptied field that loses focus folds back into the action.
              onBlur: () => {
                if (keepSearchRef.current) return;
                if (Platform.OS === "ios" && searchText.trim() === "") setSearchOpen(false);
              },
            }
          : undefined,
      }}
    />
  );

  if (board.isError) {
    const kind: string = toApiError(board.error).kind;
    const noAccess: boolean = kind === "forbidden" || kind === "not-found";
    return (
      <View className="flex-1 bg-screen">
        {screenOptions}
        <EmptyState
          variant={noAccess ? "empty" : "error"}
          icon={noAccess ? Lock : undefined}
          title={noAccess ? t("boardNoAccess") : t("boardError")}
          actionLabel={noAccess ? t("boardBack") : undefined}
          onAction={noAccess ? () => router.back() : () => void board.refetch()}
          className="mt-16"
        />
      </View>
    );
  }

  if (!board.data) {
    return (
      <View className="flex-1 bg-screen">
        {screenOptions}
        <BoardListSkeleton />
      </View>
    );
  }

  const viewProps: BoardViewProps = {
    board: board.data,
    state,
    onChange,
    sprints,
    sprint,
    sprintTickets,
    backlogTickets,
    backlogId: backlog.data?.id ?? null,
    canWrite,
    meId,
    onOpenFilters: () => boardId && openFilters(boardId, state),
    selection,
    onStartSelection: beginSelection,
    renderRefreshControl,
  };

  return (
    <View className="flex-1 bg-screen">
      {screenOptions}
      {selecting ? null : (
        <BoardViewTabs
          value={state.view}
          onChange={(view: BoardView) => onChange({ ...state, view })}
        />
      )}
      <BoardBody
        viewProps={viewProps}
        backlogFailed={backlog.isError}
        onRetryBacklog={() => void backlog.refetch()}
        quarter={quarter}
        onShiftQuarter={(step) => setQuarterOffset((offset) => offset + step)}
      />
      {selecting ? <SelectionBar count={selectedCount} onMove={openBulkMove} /> : null}
      <ActionSheet
        visible={menuVisible}
        onClose={closeMenu}
        title={board.data.name}
        actions={menuActions}
        cancelLabel={t("kitCancel")}
      />
    </View>
  );
}

type BoardBodyProps = {
  viewProps: BoardViewProps;
  backlogFailed: boolean;
  onRetryBacklog: () => void;
  quarter: Quarter;
  onShiftQuarter: (step: -1 | 1) => void;
};

/**
 * The chosen view. The sprints and the backlog id scope every ticket list,
 * so while they load or after they fail the view waits on them first.
 */
function BoardBody({
  viewProps,
  backlogFailed,
  onRetryBacklog,
  quarter,
  onShiftQuarter,
}: BoardBodyProps) {
  const { t } = useTranslation();
  const { view } = viewProps.state;

  if (view === "roadmap") {
    return (
      <RoadmapView
        board={viewProps.board}
        quarter={quarter}
        onShiftQuarter={onShiftQuarter}
        renderRefreshControl={viewProps.renderRefreshControl}
      />
    );
  }
  if (viewProps.sprints.isError) {
    return (
      <EmptyState
        variant="error"
        title={t("boardSprintsError")}
        onAction={() => void viewProps.sprints.refetch()}
        className="mt-6"
      />
    );
  }
  if (backlogFailed && view !== "sprint") {
    return (
      <EmptyState
        variant="error"
        title={t("boardTicketsError")}
        onAction={onRetryBacklog}
        className="mt-6"
      />
    );
  }
  if (viewProps.sprints.isPending) return <BoardListSkeleton />;
  if (view === "sprint") return <SprintView {...viewProps} />;
  if (view === "backlog") return <BacklogView {...viewProps} />;
  return <AllView {...viewProps} />;
}

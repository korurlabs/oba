import { Check, ChevronsUpDown, Plus } from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable, SectionList, View } from "react-native";

import { BoardChips } from "@/components/boards/BoardChips";
import { BoardTicketCard } from "@/components/boards/BoardTicketCard";
import { sortBacklog, type BacklogSort } from "@/components/boards/boardModel";
import { useBoardLinks } from "@/components/boards/boardNavigation";
import {
  ListDragOverlay,
  ListDragRow,
  ListDropSlot,
  renderGestureScroll,
} from "@/components/boards/ListDrag";
import type { ListDropGroup } from "@/components/boards/listDragModel";
import { SelectableTicketRow } from "@/components/boards/selection/SelectableTicketRow";
import { SelectAllToggle } from "@/components/boards/selection/SelectionBar";
import { selectableIn, selectionGroupOf } from "@/components/boards/selection/useTicketSelection";
import { SprintHeader } from "@/components/boards/SprintHeader";
import { useSprintActions } from "@/components/boards/useSprintActions";
import { useBoardMoves, type TicketPlace } from "@/components/boards/useBoardMoves";
import { useListDrag } from "@/components/boards/useListDrag";
import { BoardListSkeleton } from "@/components/boards/views/BoardListSkeleton";
import type { BoardViewProps } from "@/components/boards/views/boardViewProps";
import { ActionSheet, type ActionSheetAction } from "@/components/kazan/ActionSheet";
import { InlineError } from "@/components/kazan/EmptyState";
import { FAB_LIST_BOTTOM_PADDING } from "@/components/kazan/Fab";
import { useCanManageSprints } from "@/components/sprints/useCanManageSprints";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useTranslation, type TranslationKey } from "@/i18n";
import type { BoardTicket, Sprint } from "@/lib/schemas/boards";
import { cn } from "@/lib/utils";

const SORT_LABEL: Record<BacklogSort, TranslationKey> = {
  rank: "boardSortRank",
  priority: "boardSortPriority",
  created: "boardSortCreated",
};

const SORTS: readonly BacklogSort[] = ["rank", "priority", "created"];

type SectionKey = "sprint" | "backlog";

type Section = { key: SectionKey; data: BoardTicket[] };

/**
 * The Backlog view: the next planned sprint with its tickets, then the
 * backlog in the chosen order (the team's rank by default), and "Create
 * sprint" floating above the tab bar for those who may plan. A member holds
 * a row to drag it between the sprint and the backlog, or to another
 * position in its group; the backlog takes drops only in rank order, the
 * order a drop changes. In selection mode the rows carry checkboxes, each
 * group's header a Select all, and Create sprint steps aside.
 */
function BacklogView({
  board,
  state,
  onChange,
  sprints,
  sprint,
  sprintTickets,
  backlogTickets,
  backlogId,
  canWrite,
  meId,
  onOpenFilters,
  selection,
  onStartSelection,
  renderRefreshControl,
}: BoardViewProps) {
  const { t } = useTranslation();
  const selecting: boolean = selection !== null;
  const { openTicket, openCreateSprint } = useBoardLinks();
  const sprintActions = useSprintActions(board.id, sprint);
  const [sprintMenuOpen, setSprintMenuOpen] = React.useState<boolean>(false);
  const canManageSprints: boolean = useCanManageSprints(board.id);
  const [sort, setSort] = React.useState<BacklogSort>("rank");
  const [sortOpen, setSortOpen] = React.useState<boolean>(false);

  const backlog: BoardTicket[] = React.useMemo(
    () => sortBacklog(backlogTickets.data ?? [], sort),
    [backlogTickets.data, sort]
  );
  const sprintList: BoardTicket[] = React.useMemo(
    () => sprintTickets.data ?? [],
    [sprintTickets.data]
  );
  const moves = useBoardMoves({
    boardId: board.id,
    sprints: sprints.data ?? NO_SPRINTS,
    backlogId,
    canWrite,
    onSelect: (ticket: BoardTicket, place: TicketPlace) =>
      onStartSelection(selectionGroupOf(place.scope), [ticket.id]),
  });
  // Moves read the lists in board order, whatever order the backlog is shown in.
  const places: Record<SectionKey, TicketPlace | null> = React.useMemo(
    () => ({
      sprint: sprint ? { scope: { sprintId: sprint.id }, rows: sprintList } : null,
      backlog: backlogId ? { scope: { backlogId }, rows: backlogTickets.data ?? [] } : null,
    }),
    [backlogId, backlogTickets.data, sprint, sprintList]
  );
  const dropGroups: ListDropGroup[] = React.useMemo(() => {
    const groups: ListDropGroup[] = [];
    if (places.sprint) {
      groups.push({ key: "sprint", place: places.sprint, rows: sprintList, accepts: true });
    }
    if (places.backlog) {
      groups.push({
        key: "backlog",
        place: places.backlog,
        rows: backlog,
        accepts: sort === "rank",
      });
    }
    return groups;
  }, [backlog, places, sort, sprintList]);
  const listDrag = useListDrag<Section>({ groups: dropGroups, moves, boardId: board.id });
  const { drag } = listDrag;
  const openRow = React.useCallback((ticket: BoardTicket) => openTicket(ticket.key), [openTicket]);

  const sections: Section[] = [];
  if (sprint) sections.push({ key: "sprint", data: sprintList });
  sections.push({ key: "backlog", data: backlog });

  const sortActions: ActionSheetAction[] = SORTS.map((value) => ({
    label: t(SORT_LABEL[value]),
    icon: value === sort ? Check : undefined,
    onPress: () => setSort(value),
  }));

  const renderSectionHeader = ({ section }: { section: Section }) => {
    if (section.key === "sprint" && sprint) {
      return (
        <View ref={(view) => listDrag.setHeaderRef("sprint", view)} className="gap-2 pb-2 pt-2">
          <SprintHeader
            sprint={sprint}
            boardId={board.id}
            tickets={sprintList}
            onMore={
              sprintActions.actions.length > 0 && !selecting
                ? () => setSprintMenuOpen(true)
                : undefined
            }
          />
          {selection && places.sprint ? (
            <View className="flex-row justify-end px-2">
              <SelectAllToggle
                selection={selection}
                group="sprint"
                rowIds={sprintList.map((ticket) => ticket.id)}
              />
            </View>
          ) : null}
          {listDrag.placeKey === "sprint" ? (
            <Text variant="footnote" className="px-4 font-sans-medium text-link">
              {`${sprintList.length + 1} · ${t("boardDropHere")}`}
            </Text>
          ) : null}
          {sprintTickets.isError ? (
            <InlineError
              message={t("boardTicketsError")}
              onRetry={() => void sprintTickets.refetch()}
              className="mx-4"
            />
          ) : null}
          {sprintTickets.isPending ? <BoardListSkeleton count={1} /> : null}
        </View>
      );
    }
    const dropHere: boolean = listDrag.placeKey === "backlog";
    return (
      <View ref={(view) => listDrag.setHeaderRef("backlog", view)} className="pb-2 pt-4">
        <View className="flex-row items-center gap-2 pl-4 pr-2">
          <Text className="font-sans-semibold text-title-3">{t("boardBacklog")}</Text>
          <Text
            variant="subhead"
            className={cn("flex-1", dropHere ? "text-link" : "text-muted-foreground")}
            numberOfLines={1}
          >
            {dropHere
              ? `${backlog.length + 1} · ${t("boardDropHere")}`
              : backlogTickets.isSuccess
                ? String(backlog.length)
                : "–"}
          </Text>
          {selection && places.backlog ? (
            <SelectAllToggle
              selection={selection}
              group="backlog"
              rowIds={backlog.map((ticket) => ticket.id)}
            />
          ) : null}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`${t("boardSortTitle")}: ${t(SORT_LABEL[sort])}`}
            onPress={() => setSortOpen(true)}
            hitSlop={8}
            className="min-h-11 flex-row items-center gap-1 px-2 active:opacity-70"
          >
            <Text className="font-sans text-subhead text-link">{t(SORT_LABEL[sort])}</Text>
            <Icon as={ChevronsUpDown} size={14} className="text-link" />
          </Pressable>
        </View>
        {backlogTickets.isError ? (
          <InlineError
            message={t("boardTicketsError")}
            onRetry={() => void backlogTickets.refetch()}
            className="mx-4"
          />
        ) : null}
        {backlogTickets.isPending ? <BoardListSkeleton /> : null}
        {backlogTickets.isSuccess && backlog.length === 0 ? (
          <Text variant="footnote" className="px-4 py-4 text-muted-foreground">
            {state.search.trim()
              ? t("boardSearchEmpty", { query: state.search.trim() })
              : t("boardViewEmpty")}
          </Text>
        ) : null}
      </View>
    );
  };

  return (
    <View className="flex-1">
      <View ref={listDrag.rootRef} onLayout={listDrag.onRootLayout} className="flex-1">
        <SectionList<BoardTicket, Section>
          ref={listDrag.listRef}
          renderScrollComponent={renderGestureScroll}
          scrollEnabled={!drag}
          onScroll={listDrag.onScroll}
          onContentSizeChange={listDrag.onContentSizeChange}
          scrollEventThrottle={16}
          extraData={selection ?? drag}
          className="flex-1"
          contentInsetAdjustmentBehavior="automatic"
          contentContainerStyle={{
            paddingBottom: Platform.OS === "android" ? FAB_LIST_BOTTOM_PADDING + 40 : 40,
          }}
          sections={sections}
          keyExtractor={(ticket) => ticket.id}
          ListHeaderComponent={
            selecting ? null : (
              <BoardChips
                state={state}
                meId={meId}
                onChange={onChange}
                onOpenFilters={onOpenFilters}
              />
            )
          }
          stickySectionHeadersEnabled={false}
          renderSectionHeader={renderSectionHeader}
          renderSectionFooter={({ section }) =>
            drag && listDrag.slotAtEnd(section.key) ? <ListDropSlot height={drag.height} /> : null
          }
          renderItem={({ item, section }) => {
            const place: TicketPlace | null = places[section.key];
            if (selection && place) {
              return (
                <SelectableTicketRow
                  ticket={item}
                  boardId={board.id}
                  group={section.key}
                  selected={selection.state.ids.has(item.id)}
                  disabled={!selectableIn(selection.state, section.key)}
                  onToggle={selection.toggle}
                  showPlanned={section.key === "backlog"}
                />
              );
            }
            return place ? (
              <ListDragRow
                ticket={item}
                groupKey={section.key}
                boardId={board.id}
                place={place}
                moves={moves}
                handlers={listDrag.handlers}
                showPlanned={section.key === "backlog"}
                onOpen={openRow}
                lifted={drag?.ticket.id === item.id}
                arrived={listDrag.arrivedId === item.id}
                slotHeight={
                  drag && listDrag.slotBeforeRow(section.key, item.id) ? drag.height : null
                }
              />
            ) : (
              <BoardTicketCard
                ticket={item}
                boardId={board.id}
                showPlanned={section.key === "backlog"}
                onPress={() => openTicket(item.key)}
                className="mx-4"
              />
            );
          }}
          ItemSeparatorComponent={Separator}
          refreshControl={renderRefreshControl()}
        />
        <ListDragOverlay
          drag={drag}
          translateX={listDrag.translateX}
          translateY={listDrag.translateY}
          scrollOffset={listDrag.scrollOffset}
          boardId={board.id}
          showPlanned={drag?.fromKey === "backlog"}
        />
      </View>
      {canManageSprints && !drag?.dragging && !selecting ? (
        <View pointerEvents="box-none" className="absolute bottom-4 left-0 right-0 items-end px-4">
          <Button
            variant="secondary"
            size="sm"
            icon={Plus}
            onPress={() => openCreateSprint(board.id)}
          >
            {t("boardCreateSprint")}
          </Button>
        </View>
      ) : null}
      <ActionSheet
        visible={sortOpen}
        onClose={() => setSortOpen(false)}
        title={t("boardSortTitle")}
        actions={sortActions}
      />
      {moves.sheets}
      {sprint ? (
        <ActionSheet
          visible={sprintMenuOpen}
          onClose={() => setSprintMenuOpen(false)}
          title={sprint.name}
          actions={sprintActions.actions}
          cancelLabel={t("kitCancel")}
        />
      ) : null}
    </View>
  );
}

const NO_SPRINTS: readonly Sprint[] = [];

function Separator() {
  return <View className="h-2" />;
}

export { BacklogView };

import { ChevronDown, ChevronRight, Inbox, Plus } from "lucide-react-native";
import * as React from "react";
import { Pressable, SectionList, View } from "react-native";

import { BoardChips } from "@/components/boards/BoardChips";
import { BoardTicketCard } from "@/components/boards/BoardTicketCard";
import { groupUnderParents, type ParentGroup } from "@/components/boards/boardModel";
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
import { useSprintDates, useSprintStatusLabel } from "@/components/boards/SprintHeader";
import { SprintPickerChip } from "@/components/boards/SprintPickerChip";
import { TicketGroupHeader } from "@/components/boards/TicketGroupHeader";
import { useBoardMoves, type TicketPlace } from "@/components/boards/useBoardMoves";
import { useListDrag } from "@/components/boards/useListDrag";
import type { BoardViewProps } from "@/components/boards/views/boardViewProps";
import { BoardListSkeleton } from "@/components/boards/views/BoardListSkeleton";
import { EmptyState, InlineError } from "@/components/kazan/EmptyState";
import { Icon } from "@/components/ui/icon";
import { IconButton } from "@/components/ui/icon-button";
import { Text } from "@/components/ui/text";
import { useTranslation } from "@/i18n";
import type { BoardTicket, Sprint } from "@/lib/schemas/boards";
import { cn } from "@/lib/utils";

type SectionKey = "sprint" | "backlog";

type Section = {
  key: SectionKey;
  title: string;
  detail: string | null;
  count: number;
  failed: boolean;
  loading: boolean;
  data: BoardTicket[];
};

type Grouped = { rows: BoardTicket[]; headers: Map<string, ParentGroup>; children: Set<string> };

/**
 * The view a board opens on: the chosen sprint and the backlog as two
 * collapsible groups, with a sprint picker, the filters and the search
 * results count. Sub-tickets whose parent sits in the other group or
 * elsewhere are gathered under a heading with the parent's progress. A member holds a row to drag it to another position in its
 * group or into the other group, which plans it into the sprint or the
 * backlog at that position. In selection mode each group's rows carry
 * checkboxes and its header a Select all.
 */
function AllView({
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
  const { openTicket, openNewTicket } = useBoardLinks();
  const selecting: boolean = selection !== null;
  const statusLabel = useSprintStatusLabel();
  const { left } = useSprintDates(sprint);
  const [collapsed, setCollapsed] = React.useState<ReadonlySet<SectionKey>>(new Set());
  const moves = useBoardMoves({
    boardId: board.id,
    sprints: sprints.data ?? NO_SPRINTS,
    backlogId,
    canWrite,
    onSelect: (ticket: BoardTicket, place: TicketPlace) =>
      onStartSelection(selectionGroupOf(place.scope), [ticket.id]),
  });
  const places: Record<SectionKey, TicketPlace | null> = React.useMemo(
    () => ({
      sprint: sprint ? { scope: { sprintId: sprint.id }, rows: sprintTickets.data ?? [] } : null,
      backlog: backlogId ? { scope: { backlogId }, rows: backlogTickets.data ?? [] } : null,
    }),
    [backlogId, backlogTickets.data, sprint, sprintTickets.data]
  );
  // The rows as the list shows them, parents' sub-tickets gathered; drops
  // land in this order, while moves read the board order of `places`.
  const grouped: Record<SectionKey, Grouped> = React.useMemo(() => {
    const sprintRows: BoardTicket[] = sprintTickets.data ?? [];
    const backlogRows: BoardTicket[] = backlogTickets.data ?? [];
    return {
      sprint: groupUnderParents(sprintRows, backlogRows),
      backlog: groupUnderParents(backlogRows, sprintRows),
    };
  }, [backlogTickets.data, sprintTickets.data]);
  const dropGroups: ListDropGroup[] = React.useMemo(
    () =>
      (["sprint", "backlog"] as const).flatMap((key) => {
        const place: TicketPlace | null = places[key];
        return place ? [{ key, place, rows: grouped[key].rows, accepts: true }] : [];
      }),
    [grouped, places]
  );
  const listDrag = useListDrag<Section>({ groups: dropGroups, moves, boardId: board.id });
  const { drag } = listDrag;

  const openRow = React.useCallback((ticket: BoardTicket) => openTicket(ticket.key), [openTicket]);

  const toggle = (key: SectionKey) =>
    setCollapsed((current) => {
      const next: Set<SectionKey> = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  const sections: Section[] = [];
  if (sprint) {
    const tickets: BoardTicket[] = sprintTickets.data ?? [];
    sections.push({
      key: "sprint",
      title: sprint.name,
      detail: [statusLabel(sprint.status), left].filter(Boolean).join(" · "),
      count: tickets.length,
      failed: sprintTickets.isError,
      loading: sprintTickets.isPending,
      data: collapsed.has("sprint") ? [] : grouped.sprint.rows,
    });
  }
  const backlog: BoardTicket[] = backlogTickets.data ?? [];
  sections.push({
    key: "backlog",
    title: t("boardBacklog"),
    detail: null,
    count: backlog.length,
    failed: backlogTickets.isError,
    loading: backlogTickets.isPending,
    data: collapsed.has("backlog") ? [] : grouped.backlog.rows,
  });

  const searching: boolean = state.search.trim().length > 0;
  const total: number = (sprint ? (sprintTickets.data?.length ?? 0) : 0) + backlog.length;
  const allLoaded: boolean = (!sprint || sprintTickets.isSuccess) && backlogTickets.isSuccess;

  const header = (
    <View>
      {selecting ? null : (
        <BoardChips
          state={state}
          meId={meId}
          onChange={onChange}
          onOpenFilters={onOpenFilters}
          showStatusAndAssignee
        >
          {sprint && (sprints.data?.length ?? 0) > 0 ? (
            <SprintPickerChip
              sprints={sprints.data ?? NO_SPRINTS}
              sprint={sprint}
              onChange={(sprintId) => onChange({ ...state, sprintId })}
            />
          ) : null}
        </BoardChips>
      )}
      {searching && allLoaded ? (
        <Text variant="footnote" className="px-4 pb-1 text-muted-foreground">
          {t("boardSearchResults", { count: total, query: state.search.trim() })}
        </Text>
      ) : null}
    </View>
  );

  const loading: boolean = sections.every((section) => section.loading);
  const empty: boolean = allLoaded && total === 0;

  return (
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
        contentContainerClassName="pb-10"
        sections={loading ? [] : sections}
        keyExtractor={(ticket) => ticket.id}
        ListHeaderComponent={header}
        ListEmptyComponent={loading ? <BoardListSkeleton /> : null}
        ListFooterComponent={
          empty ? (
            <EmptyState
              icon={Inbox}
              title={
                searching
                  ? t("boardSearchEmpty", { query: state.search.trim() })
                  : t("boardViewEmpty")
              }
              actionLabel={canWrite && !searching && !selecting ? t("boardNewTicket") : undefined}
              onAction={
                canWrite && !searching && !selecting ? () => openNewTicket(board.id) : undefined
              }
              className="mt-6"
            />
          ) : null
        }
        stickySectionHeadersEnabled={false}
        renderSectionHeader={({ section }) => (
          <View ref={(view) => listDrag.setHeaderRef(section.key, view)}>
            <View className="flex-row items-center gap-2 pl-4 pr-1 pt-4">
              <Pressable
                accessibilityRole="button"
                accessibilityState={{ expanded: !collapsed.has(section.key) }}
                accessibilityLabel={`${section.title}, ${section.count}`}
                onPress={() => toggle(section.key)}
                className="min-h-11 flex-1 flex-row items-center gap-2 active:opacity-70"
              >
                <Icon
                  as={collapsed.has(section.key) ? ChevronRight : ChevronDown}
                  size={16}
                  className="text-muted-foreground"
                />
                <Text
                  variant="subhead"
                  className="shrink font-sans-semibold text-foreground"
                  numberOfLines={1}
                >
                  {section.title}
                </Text>
                <Text
                  variant="footnote"
                  className={cn(
                    "shrink",
                    "font-sans-medium",
                    listDrag.placeKey === section.key ? "text-link" : "text-muted-foreground"
                  )}
                  numberOfLines={1}
                >
                  {listDrag.placeKey === section.key
                    ? `${section.count + 1} · ${t("boardDropHere")}`
                    : [section.detail, String(section.count)].filter(Boolean).join(" · ")}
                </Text>
              </Pressable>
              {selection && places[section.key] ? (
                <SelectAllToggle
                  selection={selection}
                  group={section.key}
                  rowIds={grouped[section.key].rows.map((ticket) => ticket.id)}
                />
              ) : canWrite && !selecting ? (
                <IconButton
                  icon={Plus}
                  label={t("boardAddTicketTo", { group: section.title })}
                  onPress={() =>
                    openNewTicket(board.id, section.key === "sprint" ? sprint?.id : null)
                  }
                />
              ) : null}
            </View>
            {section.failed ? (
              <InlineError
                message={t("boardTicketsError")}
                onRetry={() =>
                  void (section.key === "sprint"
                    ? sprintTickets.refetch()
                    : backlogTickets.refetch())
                }
                className="mx-4 mb-2"
              />
            ) : null}
            {section.loading ? <BoardListSkeleton count={1} /> : null}
          </View>
        )}
        renderSectionFooter={({ section }) =>
          drag && listDrag.slotAtEnd(section.key) ? <ListDropSlot height={drag.height} /> : null
        }
        renderItem={({ item, section }) => {
          const place: TicketPlace | null = places[section.key];
          const group: ParentGroup | undefined = grouped[section.key].headers.get(item.id);
          const child: boolean = grouped[section.key].children.has(item.id);
          const row: React.ReactElement =
            selection && place ? (
              <SelectableTicketRow
                ticket={item}
                boardId={board.id}
                group={section.key}
                selected={selection.state.ids.has(item.id)}
                disabled={!selectableIn(selection.state, section.key)}
                onToggle={selection.toggle}
              />
            ) : place ? (
              <ListDragRow
                ticket={item}
                groupKey={section.key}
                boardId={board.id}
                place={place}
                moves={moves}
                handlers={listDrag.handlers}
                showStatus
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
                showStatus
                onPress={() => openTicket(item.key)}
                className="mx-4"
              />
            );
          if (!group && !child) return row;
          return (
            <View>
              {group ? <TicketGroupHeader group={group} /> : null}
              <View className={child ? "pl-3" : undefined}>{row}</View>
            </View>
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
        showStatus
      />
      {moves.sheets}
    </View>
  );
}

const NO_SPRINTS: readonly Sprint[] = [];

function Separator() {
  return <View className="h-2" />;
}

export { AllView };

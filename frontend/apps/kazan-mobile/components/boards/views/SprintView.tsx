import { useRouter } from "expo-router";
import { CalendarPlus, CircleCheck, Inbox, Link2, Play, Repeat } from "lucide-react-native";
import * as React from "react";
import { Platform, SectionList, View } from "react-native";

import { BoardChips } from "@/components/boards/BoardChips";
import { groupByStatus, type StatusGroup } from "@/components/boards/boardModel";
import { useBoardLinks } from "@/components/boards/boardNavigation";
import { joinParentSprintHref } from "@/components/boards/boardRoutes";
import { KanbanBoard } from "@/components/boards/KanbanBoard";
import {
  ListDragOverlay,
  ListDragRow,
  ListDropSlot,
  renderGestureScroll,
} from "@/components/boards/ListDrag";
import type { ListDropGroup } from "@/components/boards/listDragModel";
import { SelectableTicketRow } from "@/components/boards/selection/SelectableTicketRow";
import { SelectAllToggle } from "@/components/boards/selection/SelectionBar";
import { SprintHeader } from "@/components/boards/SprintHeader";
import { useSprintActions } from "@/components/boards/useSprintActions";
import { useBoardMoves, type TicketPlace } from "@/components/boards/useBoardMoves";
import { useListDrag } from "@/components/boards/useListDrag";
import { BoardListSkeleton } from "@/components/boards/views/BoardListSkeleton";
import type { BoardViewProps } from "@/components/boards/views/boardViewProps";
import { LayoutToggle } from "@/components/boards/views/LayoutToggle";
import { ActionSheet } from "@/components/kazan/ActionSheet";
import { EmptyState, InlineError } from "@/components/kazan/EmptyState";
import { Fab, FAB_LIST_BOTTOM_PADDING } from "@/components/kazan/Fab";
import { StatusPill } from "@/components/kazan/TicketMeta";
import type { TicketStatus } from "@/components/kazan/ticketMetaModel";
import { useCanManageSprints } from "@/components/sprints/useCanManageSprints";
import { newTicketHref } from "@/components/ticket/ticketRoutes";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { useParentSprintOptions } from "@/hooks/queries/sprints";
import { useTranslation } from "@/i18n";
import type { BoardTicket, Sprint } from "@/lib/schemas/boards";

type Section = { status: string; data: BoardTicket[] };

/**
 * The chosen sprint: as Kanban columns by status, or as a list grouped by
 * status in board flow order under the sprint header, its actions menu, and
 * a row of Recurrence and Start or Complete. The toggle at the head
 * of the chip row switches between the two.
 * In the list a member holds a row to drag it to another position in its
 * status, or into another status group. New ticket from here starts in the
 * sprint, and from a Kanban column's plus also in that column's status.
 * Selection mode is a list mode: the rows carry checkboxes under one Select
 * all for the sprint, and the sprint's actions and New ticket step aside.
 * A board without sprints shows `NoSprintState` instead.
 */
function SprintView({
  board,
  state,
  onChange,
  sprints,
  sprint,
  sprintTickets,
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
  const router = useRouter();
  const { openTicket, openNewTicket, openCreateSprint, openEditSprint, openCompleteSprint } =
    useBoardLinks();
  const sprintActions = useSprintActions(board.id, sprint);
  const { starter } = sprintActions;
  const [sprintMenuOpen, setSprintMenuOpen] = React.useState<boolean>(false);
  const canManageSprints: boolean = useCanManageSprints(board.id);
  const tickets: BoardTicket[] = React.useMemo(
    () => sprintTickets.data ?? [],
    [sprintTickets.data]
  );
  const moves = useBoardMoves({
    boardId: board.id,
    sprints: sprints.data ?? NO_SPRINTS,
    backlogId,
    canWrite,
    onSelect:
      state.layout === "list"
        ? (ticket: BoardTicket) => onStartSelection("sprint", [ticket.id])
        : undefined,
  });
  const place: TicketPlace | null = React.useMemo(
    () => (sprint ? { scope: { sprintId: sprint.id }, rows: tickets } : null),
    [sprint, tickets]
  );
  const [kanbanDragging, setKanbanDragging] = React.useState<boolean>(false);
  const statusGroups: StatusGroup[] = React.useMemo(() => groupByStatus(tickets), [tickets]);
  const dropGroups: ListDropGroup[] = React.useMemo(
    () =>
      place
        ? statusGroups
            .filter((group) => group.tickets.length > 0)
            .map((group) => ({
              key: group.status,
              place,
              rows: group.tickets,
              status: group.status,
              accepts: true,
            }))
        : [],
    [place, statusGroups]
  );
  const listDrag = useListDrag<Section>({ groups: dropGroups, moves, boardId: board.id });
  const listDragging: boolean = !!listDrag.drag?.dragging;
  const dragging: boolean = state.layout === "kanban" ? kanbanDragging : listDragging;
  const openRow = React.useCallback((ticket: BoardTicket) => openTicket(ticket.key), [openTicket]);
  const sprintId: string | undefined = sprint?.id;
  const addToColumn = React.useCallback(
    (status: TicketStatus) => router.push(newTicketHref({ boardId: board.id, sprintId, status })),
    [board.id, router, sprintId]
  );

  if (sprints.isPending) return <BoardListSkeleton />;
  if (sprints.isError) {
    return (
      <EmptyState
        variant="error"
        title={t("boardSprintsError")}
        onAction={() => void sprints.refetch()}
      />
    );
  }
  if (!sprint) {
    return (
      <NoSprintState
        boardId={board.id}
        onCreateSprint={canManageSprints ? () => openCreateSprint(board.id) : undefined}
      />
    );
  }

  const chips = (
    <BoardChips state={state} meId={meId} onChange={onChange} onOpenFilters={onOpenFilters}>
      <LayoutToggle value={state.layout} onChange={(layout) => onChange({ ...state, layout })} />
    </BoardChips>
  );

  let body: React.ReactElement;
  if (sprintTickets.isPending) {
    body = (
      <View className="flex-1">
        {chips}
        <BoardListSkeleton />
      </View>
    );
  } else if (sprintTickets.isError) {
    body = (
      <View className="flex-1">
        {chips}
        <EmptyState
          variant="error"
          title={t("boardTicketsError")}
          onAction={() => void sprintTickets.refetch()}
        />
      </View>
    );
  } else if (state.layout === "kanban") {
    body = (
      <KanbanBoard
        tickets={tickets}
        boardId={board.id}
        onOpenTicket={(ticket) => openTicket(ticket.key)}
        header={chips}
        renderRefreshControl={renderRefreshControl}
        moves={moves}
        place={place ?? undefined}
        onDragChange={setKanbanDragging}
        onAddTicket={canWrite ? addToColumn : undefined}
      />
    );
  } else {
    const sections: Section[] = statusGroups
      .filter((group: StatusGroup) => group.tickets.length > 0)
      .map((group) => ({ status: group.status, data: group.tickets }));
    const { drag } = listDrag;
    const sprintPlace: TicketPlace = place ?? { scope: { sprintId: sprint.id }, rows: tickets };
    const searching: boolean = state.search.trim() !== "";
    const listedIds: string[] = sections.flatMap((section) =>
      section.data.map((ticket) => ticket.id)
    );
    body = (
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
            <View className="gap-1">
              {selecting ? null : chips}
              <SprintHeader
                sprint={sprint}
                boardId={board.id}
                tickets={tickets}
                onMore={
                  sprintActions.actions.length > 0 && !selecting
                    ? () => setSprintMenuOpen(true)
                    : undefined
                }
              />
              {selection ? (
                <View className="flex-row justify-end px-2">
                  <SelectAllToggle selection={selection} group="sprint" rowIds={listedIds} />
                </View>
              ) : null}
              {canManageSprints && sprint.status !== "COMPLETED" && !selecting ? (
                <View className="flex-row items-center gap-2 px-4 pt-1">
                  <Button
                    variant="secondary"
                    size="sm"
                    icon={Repeat}
                    className="flex-1"
                    onPress={() => openEditSprint(board.id, sprint.id, "automation")}
                  >
                    {t("boardRecurrence")}
                  </Button>
                  {sprint.status === "ACTIVE" ? (
                    <Button
                      size="sm"
                      icon={CircleCheck}
                      className="flex-1"
                      onPress={() => openCompleteSprint(board.id, sprint.id)}
                    >
                      {t("boardCompleteSprint")}
                    </Button>
                  ) : (
                    <Button
                      size="sm"
                      icon={Play}
                      className="flex-1"
                      loading={starter.pending}
                      disabled={starter.pending}
                      onPress={() => starter.start(sprint)}
                    >
                      {t("sprintStartAction")}
                    </Button>
                  )}
                </View>
              ) : null}
            </View>
          }
          ListFooterComponent={
            moves.canMove && sections.length > 0 && !selecting ? (
              <Text variant="footnote" className="px-4 pt-4 text-muted-foreground">
                {t("boardSwipeHint")}
              </Text>
            ) : null
          }
          ListEmptyComponent={
            <EmptyState
              icon={Inbox}
              title={
                searching
                  ? t("boardSearchEmpty", { query: state.search.trim() })
                  : t("boardViewEmpty")
              }
              actionLabel={canWrite && !searching && !selecting ? t("boardNewTicket") : undefined}
              onAction={
                canWrite && !searching && !selecting
                  ? () => openNewTicket(board.id, sprint.id)
                  : undefined
              }
              className="mt-6"
            />
          }
          stickySectionHeadersEnabled={false}
          renderSectionHeader={({ section }) => (
            <View
              ref={(view) => listDrag.setHeaderRef(section.status, view)}
              className="flex-row items-center gap-2 px-4 pb-2 pt-4"
            >
              <StatusPill status={section.status} />
              <Text variant="footnote" className="text-muted-foreground">
                {section.data.length}
              </Text>
            </View>
          )}
          renderSectionFooter={({ section }) =>
            drag && listDrag.slotAtEnd(section.status) ? (
              <ListDropSlot height={drag.height} />
            ) : null
          }
          renderItem={({ item, section }) =>
            selection ? (
              <SelectableTicketRow
                ticket={item}
                boardId={board.id}
                group="sprint"
                selected={selection.state.ids.has(item.id)}
                disabled={false}
                onToggle={selection.toggle}
              />
            ) : (
              <ListDragRow
                ticket={item}
                groupKey={section.status}
                boardId={board.id}
                place={sprintPlace}
                moves={moves}
                handlers={listDrag.handlers}
                onOpen={openRow}
                lifted={drag?.ticket.id === item.id}
                arrived={listDrag.arrivedId === item.id}
                slotHeight={
                  drag && listDrag.slotBeforeRow(section.status, item.id) ? drag.height : null
                }
              />
            )
          }
          ItemSeparatorComponent={Separator}
          refreshControl={renderRefreshControl()}
        />
        <ListDragOverlay
          drag={drag}
          translateX={listDrag.translateX}
          translateY={listDrag.translateY}
          scrollOffset={listDrag.scrollOffset}
          boardId={board.id}
        />
      </View>
    );
  }

  return (
    <View className="flex-1">
      {body}
      {moves.sheets}
      <ActionSheet
        visible={sprintMenuOpen}
        onClose={() => setSprintMenuOpen(false)}
        title={sprint.name}
        actions={sprintActions.actions}
        cancelLabel={t("kitCancel")}
      />
      {canWrite && !dragging && !selecting ? (
        <Fab label={t("boardNewTicket")} onPress={() => openNewTicket(board.id, sprint.id)} />
      ) : null}
    </View>
  );
}

type NoSprintStateProps = {
  boardId: string;
  /** Opens Create sprint; absent for a user who may not manage sprints. */
  onCreateSprint?: () => void;
};

/**
 * The Sprint view of a board without sprints. It offers joining the active
 * sprint of a board above first, when there is one the user may join, and
 * creating its own second, because a child board usually works in its
 * parent's sprint. The boards above are read only here, so a board that has
 * sprints never pays for the board tree, the user and the ancestors' reads.
 */
function NoSprintState({ boardId, onCreateSprint }: NoSprintStateProps) {
  const { t } = useTranslation();
  const router = useRouter();
  const parentSprints = useParentSprintOptions(boardId);

  const emptyState: React.ReactElement = parentSprints.hasJoinableSprint ? (
    <EmptyState
      icon={CalendarPlus}
      title={t("boardNoSprint")}
      message={t("sprintJoinParentEmptyDescription")}
      actionLabel={t("sprintJoinParentOpenButton")}
      actionIcon={Link2}
      onAction={() => router.push(joinParentSprintHref(boardId))}
      secondaryActionLabel={onCreateSprint ? t("sprintJoinParentCreateOwn") : undefined}
      onSecondaryAction={onCreateSprint}
    />
  ) : (
    <EmptyState
      icon={CalendarPlus}
      title={t("boardNoSprint")}
      message={t("boardNoSprintBody")}
      actionLabel={onCreateSprint ? t("boardCreateSprint") : undefined}
      onAction={onCreateSprint}
    />
  );
  if (!parentSprints.error) return emptyState;
  // A failed check would otherwise hide the join silently.
  return (
    <View className="flex-1">
      {emptyState}
      <InlineError
        message={t("sprintJoinParentLoadFailed")}
        onRetry={parentSprints.refetch}
        className="mx-4 mb-6"
      />
    </View>
  );
}

const NO_SPRINTS: readonly Sprint[] = [];

function Separator() {
  return <View className="h-2" />;
}

export { SprintView };

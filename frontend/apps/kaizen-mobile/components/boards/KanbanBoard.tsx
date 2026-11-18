import { ChevronLeft, ChevronRight, Plus } from "lucide-react-native";
import * as React from "react";
import {
  Platform,
  View,
  useWindowDimensions,
  type LayoutChangeEvent,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
  type RefreshControlProps,
} from "react-native";
import { Gesture, GestureDetector, ScrollView } from "react-native-gesture-handler";
import Animated, { useAnimatedStyle } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { BoardTicketCard } from "@/components/boards/BoardTicketCard";
import { groupByStatus, statusCounts, type StatusGroup } from "@/components/boards/boardModel";
import { LIFT_DELAY_MS } from "@/components/boards/ticketMoveModel";
import type { BoardMoves, CardMoveProps, TicketPlace } from "@/components/boards/useBoardMoves";
import {
  useKanbanDrag,
  type DragHandlers,
  type KanbanDrag,
} from "@/components/boards/useKanbanDrag";
import { DropPlaceholder } from "@/components/kaizen/DropPlaceholder";
import { DropStrip } from "@/components/kaizen/DropStrip";
import { resolveStatus, type TicketStatus } from "@/components/kaizen/ticketMetaModel";
import { Icon } from "@/components/ui/icon";
import { IconButton } from "@/components/ui/icon-button";
import { Text } from "@/components/ui/text";
import { useTranslation } from "@/i18n";
import type { BoardTicket } from "@/lib/schemas/boards";
import { cn } from "@/lib/utils";

/** The part of the next column that peeks in at the right edge, so the strip reads as scrollable. */
const PEEK = 40;
const GAP = 12;
const SIDE = 16;

type KanbanBoardProps = {
  tickets: readonly BoardTicket[];
  boardId: string;
  onOpenTicket: (ticket: BoardTicket) => void;
  /** Drawn above the column counter, such as the filter chips. */
  header?: React.ReactNode;
  /** A pull-to-refresh control for each column, which scrolls on its own. */
  renderRefreshControl?: () => React.ReactElement<RefreshControlProps>;
  /** The ticket moves of the board; without them, or without the right to move, cards do not drag. */
  moves?: BoardMoves;
  /** The sprint or backlog the tickets belong to. */
  place?: TicketPlace;
  /** Reports when a card is dragged, so the screen can hide what would cover the board. */
  onDragChange?: (dragging: boolean) => void;
  /** Opens New ticket in a column's status; without it the columns have no add button. */
  onAddTicket?: (status: TicketStatus) => void;
};

/**
 * The Sprint view's board layout: one column per status, paged sideways one
 * column at a time, each column scrolling on its own. On iOS page dots above
 * the tab bar tell where the strip stands; both platforms speak it as
 * "Column 2 of 5". A member holds a card to drag it to another column
 * or onto the drop strip that appears at the top while it is dragged, and
 * adds a ticket to a column with the plus in its header.
 */
function KanbanBoard({
  tickets,
  boardId,
  onOpenTicket,
  header,
  renderRefreshControl,
  moves,
  place,
  onDragChange,
  onAddTicket,
}: KanbanBoardProps) {
  const { t } = useTranslation();
  const { width } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const columnWidth: number = Math.max(width - SIDE - PEEK - GAP, 240);
  const pageWidth: number = columnWidth + GAP;
  const groups: StatusGroup[] = React.useMemo(() => groupByStatus(tickets), [tickets]);
  const [page, setPage] = React.useState<number>(0);
  const pagerScroll = React.useRef<React.ComponentRef<typeof ScrollView>>(null);
  const where: TicketPlace = place ?? { scope: { sprintId: "" }, rows: tickets };
  const canDrag: boolean = !!moves?.canMove && !!place;

  const scrollToPage = React.useCallback(
    (next: number) => {
      pagerScroll.current?.scrollTo({ x: next * pageWidth, animated: true });
      setPage(next);
    },
    [pageWidth]
  );

  const kanban = useKanbanDrag({
    groups,
    place: where,
    moves,
    columnWidth,
    pageWidth,
    side: SIDE,
    scrollToPage,
  });
  // The worklet below reads only the shared values: capturing the whole hook
  // result would hand its view refs to the UI runtime, which then flags every
  // later write to those refs.
  const { drag, onPagerScroll, onColumnScroll, translateX, translateY, scale } = kanban;
  const dragging: boolean = !!drag?.dragging;

  React.useEffect(() => {
    onDragChange?.(dragging);
  }, [dragging, onDragChange]);

  const cardMoves: Map<string, CardMoveProps | undefined> = React.useMemo(() => {
    const map = new Map<string, CardMoveProps | undefined>();
    if (!moves || !place) return map;
    for (const ticket of tickets) map.set(ticket.id, moves.cardMoves(ticket, place));
    return map;
  }, [moves, place, tickets]);

  const onScrollEnd = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const next: number = Math.round(event.nativeEvent.contentOffset.x / pageWidth);
    setPage(Math.min(Math.max(next, 0), groups.length - 1));
  };

  const overlayStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: translateX.value },
      { translateY: translateY.value },
      { scale: scale.value },
    ],
  }));

  return (
    <View ref={kanban.rootRef} onLayout={kanban.onRootLayout} className="flex-1">
      {header}
      <View ref={kanban.pagerRef} className="flex-1 pt-1">
        <ScrollView
          ref={pagerScroll}
          horizontal
          scrollEnabled={!drag}
          showsHorizontalScrollIndicator={false}
          snapToInterval={pageWidth}
          decelerationRate="fast"
          disableIntervalMomentum
          onMomentumScrollEnd={onScrollEnd}
          onScroll={(event) => onPagerScroll(event.nativeEvent.contentOffset.x)}
          scrollEventThrottle={16}
          contentContainerStyle={{ paddingHorizontal: SIDE, gap: GAP }}
          className="flex-1"
        >
          {groups.map((group, index) => (
            <View
              key={group.status}
              accessibilityLabel={`${t(resolveStatus(group.status).labelKey)}, ${group.tickets.length}, ${t("boardColumnOf", { index: index + 1, count: groups.length })}`}
              style={{ width: columnWidth }}
              className={cn("flex-1 rounded-xl", resolveStatus(group.status).columnClassName)}
            >
              <View
                className={cn(
                  "flex-row items-center gap-2 pl-3",
                  onAddTicket ? "pr-1 pt-1" : "pb-2 pr-3 pt-3"
                )}
              >
                <View
                  className={cn("size-2 rounded-full", resolveStatus(group.status).dotClassName)}
                />
                <Text
                  className="shrink font-sans-semibold text-subhead text-foreground"
                  numberOfLines={1}
                >
                  {t(resolveStatus(group.status).labelKey)}
                </Text>
                <Text variant="footnote" className="text-muted-foreground">
                  {group.tickets.length}
                </Text>
                {onAddTicket ? (
                  <>
                    <View className="flex-1" />
                    <IconButton
                      icon={Plus}
                      label={t("boardAddTicketTo", {
                        group: t(resolveStatus(group.status).labelKey),
                      })}
                      disabled={!!drag}
                      onPress={() => onAddTicket(group.status)}
                    />
                  </>
                ) : null}
              </View>
              <View ref={(view) => kanban.setColumnRef(group.status, view)} className="flex-1">
                <ScrollView
                  nestedScrollEnabled
                  scrollEnabled={!drag}
                  contentInsetAdjustmentBehavior="automatic"
                  refreshControl={renderRefreshControl?.()}
                  onScroll={(event) =>
                    onColumnScroll(group.status, event.nativeEvent.contentOffset.y)
                  }
                  scrollEventThrottle={16}
                  contentContainerClassName="gap-2 px-2 pb-4"
                  className="flex-1"
                >
                  <ColumnCards
                    group={group}
                    drag={drag}
                    targetIsNoop={kanban.targetIsNoop}
                    arrivedId={kanban.arrivedId}
                    boardId={boardId}
                    canDrag={canDrag}
                    handlers={kanban.handlers}
                    cardMoves={cardMoves}
                    onCardLayout={kanban.onCardLayout}
                    onOpenTicket={onOpenTicket}
                    emptyLabel={t("boardColumnEmpty")}
                  />
                </ScrollView>
              </View>
            </View>
          ))}
        </ScrollView>
        {drag?.dragging && drag.edge !== 0 ? (
          <View
            pointerEvents="none"
            className={cn(
              "absolute bottom-0 top-0 w-9 items-center justify-center bg-primary-light",
              drag.edge < 0 ? "left-0" : "right-0"
            )}
          >
            <Icon
              as={drag.edge < 0 ? ChevronLeft : ChevronRight}
              size={20}
              className="text-primary"
            />
          </View>
        ) : null}
      </View>
      {Platform.OS === "ios" && groups.length > 1 && !drag?.dragging ? (
        <View
          pointerEvents="none"
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
          style={{ bottom: insets.bottom + 8 }}
          className="absolute left-0 right-0 flex-row items-center justify-center gap-1.5"
        >
          {groups.map((group, index) => (
            <View
              key={group.status}
              className={cn(
                "h-1.5 rounded-full",
                index === page ? "w-[18px] bg-foreground" : "w-1.5 bg-border"
              )}
            />
          ))}
        </View>
      ) : null}
      {drag?.dragging ? (
        <DropStrip
          counts={statusCounts(tickets)}
          from={drag.from}
          over={drag.target?.kind === "strip" ? drag.target.status : null}
          onMeasure={kanban.onStripMeasure}
          className="absolute left-0 right-0 top-0"
        />
      ) : null}
      {drag ? (
        <Animated.View
          pointerEvents="none"
          style={[
            { position: "absolute", left: drag.left, top: drag.top, width: drag.width },
            overlayStyle,
          ]}
        >
          <BoardTicketCard
            ticket={drag.ticket}
            boardId={boardId}
            onPress={noop}
            elevated
            wrapTitle
          />
        </Animated.View>
      ) : null}
    </View>
  );
}

function noop(): void {
  // The lifted copy of a card takes no touches; the held card still owns the gesture.
}

type ColumnCardsProps = {
  group: StatusGroup;
  drag: KanbanDrag | null;
  targetIsNoop: boolean;
  arrivedId: string | null;
  boardId: string;
  canDrag: boolean;
  handlers: DragHandlers;
  cardMoves: Map<string, CardMoveProps | undefined>;
  onCardLayout: (ticketId: string, y: number, height: number) => void;
  onOpenTicket: (ticket: BoardTicket) => void;
  emptyLabel: string;
};

/** The cards of one column, with the slot a dragged card would land in. */
function ColumnCards({
  group,
  drag,
  targetIsNoop,
  arrivedId,
  boardId,
  canDrag,
  handlers,
  cardMoves,
  onCardLayout,
  onOpenTicket,
  emptyLabel,
}: ColumnCardsProps) {
  const target = drag?.dragging && drag.target?.kind === "column" ? drag.target : null;
  const slotHere: boolean = !!target && target.status === group.status && !targetIsNoop;
  const slotBeforeId: string | null = slotHere ? (target?.beforeId ?? null) : null;
  const placeholder = drag ? <DropPlaceholder key="drop-slot" height={drag.height} /> : null;

  if (group.tickets.length === 0) {
    return slotHere ? (
      placeholder
    ) : (
      <Text variant="footnote" className="px-2 py-6 text-center text-muted-foreground">
        {emptyLabel}
      </Text>
    );
  }

  return (
    <>
      {group.tickets.map((ticket) => (
        <React.Fragment key={ticket.id}>
          {slotHere && slotBeforeId === ticket.id ? placeholder : null}
          <DraggableCard
            ticket={ticket}
            status={group.status}
            boardId={boardId}
            canDrag={canDrag}
            lifted={drag?.ticket.id === ticket.id}
            arrived={arrivedId === ticket.id}
            handlers={handlers}
            moveProps={cardMoves.get(ticket.id)}
            onCardLayout={onCardLayout}
            onOpenTicket={onOpenTicket}
          />
        </React.Fragment>
      ))}
      {slotHere && slotBeforeId === null ? placeholder : null}
    </>
  );
}

type DraggableCardProps = {
  ticket: BoardTicket;
  status: TicketStatus;
  boardId: string;
  canDrag: boolean;
  /** The card is held: its slot stays as a half-faded, dashed ghost under the lifted copy. */
  lifted: boolean;
  /** The card has just been dropped here and is tinted primary-light for a moment. */
  arrived: boolean;
  handlers: DragHandlers;
  moveProps: CardMoveProps | undefined;
  onCardLayout: (ticketId: string, y: number, height: number) => void;
  onOpenTicket: (ticket: BoardTicket) => void;
};

const DraggableCard = React.memo(function DraggableCard({
  ticket,
  status,
  boardId,
  canDrag,
  lifted,
  arrived,
  handlers,
  moveProps,
  onCardLayout,
  onOpenTicket,
}: DraggableCardProps) {
  const gesture = React.useMemo(
    () =>
      Gesture.Pan()
        .runOnJS(true)
        .enabled(canDrag)
        .activateAfterLongPress(LIFT_DELAY_MS)
        .onStart((event) => handlers.lift(ticket, status, event))
        .onUpdate((event) => handlers.move(event))
        .onEnd((event, success) => handlers.drop(event, success))
        .onFinalize(() => handlers.finish(ticket.id)),
    [canDrag, handlers, status, ticket]
  );

  const onLayout = (event: LayoutChangeEvent) =>
    onCardLayout(ticket.id, event.nativeEvent.layout.y, event.nativeEvent.layout.height);

  return (
    <GestureDetector gesture={gesture}>
      <View onLayout={onLayout} className={lifted ? "opacity-50" : undefined}>
        <BoardTicketCard
          ticket={ticket}
          boardId={boardId}
          wrapTitle
          onPress={() => onOpenTicket(ticket)}
          accessibilityActions={moveProps?.accessibilityActions}
          onAccessibilityAction={moveProps?.onAccessibilityAction}
        />
        {lifted ? (
          <View
            pointerEvents="none"
            className="absolute inset-0 rounded-lg border-2 border-dashed border-primary"
          />
        ) : null}
        {arrived ? (
          <View
            pointerEvents="none"
            className="absolute inset-0 rounded-lg bg-primary-light opacity-50"
          />
        ) : null}
      </View>
    </GestureDetector>
  );
});

export { KanbanBoard };
export type { KanbanBoardProps };

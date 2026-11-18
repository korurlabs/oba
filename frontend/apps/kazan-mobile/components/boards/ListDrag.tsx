import * as React from "react";
import { View, type LayoutChangeEvent, type ScrollViewProps } from "react-native";
import { Gesture, GestureDetector, ScrollView } from "react-native-gesture-handler";
import Animated, { useAnimatedStyle, type SharedValue } from "react-native-reanimated";

import { BoardTicketCard } from "@/components/boards/BoardTicketCard";
import { TicketSwipeRow } from "@/components/boards/TicketSwipeRow";
import { LIFT_DELAY_MS } from "@/components/boards/ticketMoveModel";
import type { BoardMoves, TicketPlace } from "@/components/boards/useBoardMoves";
import type { ListDrag, ListDragHandlers } from "@/components/boards/useListDrag";
import { DropPlaceholder } from "@/components/kazan/DropPlaceholder";
import type { BoardTicket } from "@/lib/schemas/boards";
import { cn } from "@/lib/utils";

/**
 * The scroll view of a draggable board list. The gesture-handler scroll view
 * takes part in the row gestures, so a held row stops the list from
 * scrolling under it while a quick swipe still scrolls.
 */
function renderGestureScroll(props: ScrollViewProps): React.ReactElement<ScrollViewProps> {
  return <ScrollView {...props} />;
}

type ListDragRowProps = {
  ticket: BoardTicket;
  /** The list group the row sits in. */
  groupKey: string;
  boardId: string;
  place: TicketPlace;
  moves: BoardMoves;
  handlers: ListDragHandlers;
  onOpen: (ticket: BoardTicket) => void;
  showStatus?: boolean;
  /** Names the sprint a backlog row is planned in. */
  showPlanned?: boolean;
  /** The row is held: it stays in place as a faint ghost under the lifted copy. */
  lifted: boolean;
  /** The row has just been dropped here and is highlighted for a moment. */
  arrived: boolean;
  /** The height of the drop slot drawn above the row, or null for none. */
  slotHeight: number | null;
};

/**
 * A ticket row that a member holds to drag; a hold released without a drag
 * opens the ticket menu. The swipe actions and screen-reader moves of the row
 * stay as they are; a reader of the board gets a row that does not drag.
 */
const ListDragRow = React.memo(function ListDragRow({
  ticket,
  groupKey,
  boardId,
  place,
  moves,
  handlers,
  onOpen,
  showStatus = true,
  showPlanned = false,
  lifted,
  arrived,
  slotHeight,
}: ListDragRowProps) {
  const gesture = React.useMemo(
    () =>
      Gesture.Pan()
        .runOnJS(true)
        .enabled(moves.canMove)
        .activateAfterLongPress(LIFT_DELAY_MS)
        .onStart((event) => handlers.lift(ticket, groupKey, event))
        .onUpdate((event) => handlers.move(event))
        .onEnd((event, success) => handlers.drop(event, success))
        .onFinalize(() => handlers.finish(ticket.id)),
    [groupKey, handlers, moves.canMove, ticket]
  );

  const setRef = React.useCallback(
    (view: View | null) => handlers.setRowRef(ticket.id, view),
    [handlers, ticket.id]
  );

  const onLayout = (event: LayoutChangeEvent) =>
    handlers.onRowLayout(
      ticket.id,
      event.nativeEvent.layout.width,
      event.nativeEvent.layout.height
    );

  return (
    <>
      {slotHeight !== null ? <DropPlaceholder height={slotHeight} className="mx-4 mb-2" /> : null}
      <GestureDetector gesture={gesture}>
        <View ref={setRef} onLayout={onLayout} className={cn("mx-4", lifted && "opacity-30")}>
          <TicketSwipeRow
            ticket={ticket}
            boardId={boardId}
            place={place}
            moves={moves}
            showStatus={showStatus}
            showPlanned={showPlanned}
            menuOnHold={!moves.canMove}
            onOpen={onOpen}
          />
          {arrived ? (
            <View
              pointerEvents="none"
              className="absolute inset-0 rounded-lg border-2 border-primary"
            />
          ) : null}
        </View>
      </GestureDetector>
    </>
  );
});

/** The drop slot at the end of a group, drawn under its last row. */
function ListDropSlot({ height }: { height: number }) {
  return <DropPlaceholder height={height} className="mx-4 mt-2" />;
}

type ListDragOverlayProps = {
  drag: ListDrag | null;
  translateX: SharedValue<number>;
  translateY: SharedValue<number>;
  /** The list's scroll offset, which carries the group outline with the content. */
  scrollOffset: SharedValue<number>;
  boardId: string;
  showStatus?: boolean;
  /** Names the sprint the lifted row is planned in, as its row does. */
  showPlanned?: boolean;
};

/**
 * What a drag draws above the list: the outline of the group a drop would
 * plan the ticket into, and the lifted row following the finger.
 */
function ListDragOverlay({
  drag,
  translateX,
  translateY,
  scrollOffset,
  boardId,
  showStatus = true,
  showPlanned = false,
}: ListDragOverlayProps) {
  const cardStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: translateX.value }, { translateY: translateY.value }],
  }));
  const outlineStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: -scrollOffset.value }],
  }));

  if (!drag) return null;
  return (
    <>
      {drag.dragging && drag.outline ? (
        <Animated.View
          pointerEvents="none"
          style={[
            {
              position: "absolute",
              left: 8,
              right: 8,
              top: drag.outline.top,
              height: drag.outline.height,
            },
            outlineStyle,
          ]}
          className="rounded-xl border-2 border-primary"
        />
      ) : null}
      <Animated.View
        pointerEvents="none"
        style={[
          { position: "absolute", left: drag.left, top: drag.top, width: drag.width },
          cardStyle,
        ]}
      >
        <BoardTicketCard
          ticket={drag.ticket}
          boardId={boardId}
          showStatus={showStatus}
          showPlanned={showPlanned}
          onPress={noop}
          elevated
        />
      </Animated.View>
    </>
  );
}

function noop(): void {
  // The lifted copy of a row takes no touches; the held row still owns the gesture.
}

export { ListDragOverlay, ListDragRow, ListDropSlot, renderGestureScroll };
export type { ListDragOverlayProps, ListDragRowProps };

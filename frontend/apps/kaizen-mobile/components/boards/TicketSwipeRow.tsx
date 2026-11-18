import * as React from "react";

import { BoardTicketCard } from "@/components/boards/BoardTicketCard";
import type { BoardMoves, TicketPlace } from "@/components/boards/useBoardMoves";
import { SwipeRow } from "@/components/kaizen/SwipeRow";
import type { BoardTicket } from "@/lib/schemas/boards";

type TicketSwipeRowProps = {
  ticket: BoardTicket;
  boardId: string;
  /** The sprint or backlog the ticket sits in, with the list that holds it. */
  place: TicketPlace;
  moves: BoardMoves;
  onOpen: (ticket: BoardTicket) => void;
  showStatus?: boolean;
  /** Names the sprint a backlog row is planned in. */
  showPlanned?: boolean;
  /** A hold opens the ticket menu; off when a drag of the row owns the hold. */
  menuOnHold?: boolean;
  className?: string;
};

/**
 * A ticket row of a board list: swipe right moves it one status on, swipe
 * left offers Move and Delete, a hold opens its menu, and a screen reader
 * offers every move as an action. A reader of the board gets the plain card.
 */
function TicketSwipeRow({
  ticket,
  boardId,
  place,
  moves,
  onOpen,
  showStatus = true,
  showPlanned = false,
  menuOnHold = true,
  className,
}: TicketSwipeRowProps) {
  if (!moves.canMove) {
    return (
      <BoardTicketCard
        ticket={ticket}
        boardId={boardId}
        showStatus={showStatus}
        showPlanned={showPlanned}
        onPress={() => onOpen(ticket)}
        className={className}
      />
    );
  }
  return (
    <SwipeRow
      leading={moves.swipeNext(ticket, place)}
      trailing={moves.swipeMore(ticket, place)}
      className={className}
    >
      <BoardTicketCard
        ticket={ticket}
        boardId={boardId}
        showStatus={showStatus}
        showPlanned={showPlanned}
        onPress={() => onOpen(ticket)}
        onLongPress={menuOnHold ? () => moves.openMenu(ticket, place) : undefined}
        {...moves.cardMoves(ticket, place)}
      />
    </SwipeRow>
  );
}

export { TicketSwipeRow };
export type { TicketSwipeRowProps };

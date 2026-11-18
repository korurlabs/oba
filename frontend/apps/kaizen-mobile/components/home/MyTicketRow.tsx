import { TicketCard } from "@/components/kaizen/TicketCard";
import type { TicketListItem } from "@/lib/schemas/tickets";

type MyTicketRowProps = {
  ticket: TicketListItem;
  onPress: () => void;
};

/**
 * A ticket of My Tickets: the shared ticket card, with its board named above
 * it, because the list holds tickets of every board.
 */
function MyTicketRow({ ticket, onPress }: MyTicketRowProps) {
  return (
    <TicketCard
      ticket={ticket}
      boardName={ticket.board?.name ?? null}
      onPress={onPress}
      className="mx-4"
    />
  );
}

export { MyTicketRow };
export type { MyTicketRowProps };

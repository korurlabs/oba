"use client";

import { cn } from "@oba/ui/lib/utils";
import { TicketKeyLink } from "@/features/tickets/components/ticket-key-link";
import type { UpdateTicketResponse } from "../schemas";

interface UpdateTicketKeyProps {
  item: Pick<UpdateTicketResponse, "ticketId" | "boardId" | "ticketKey" | "canOpen">;
  className?: string;
}

/**
 * A ticket key in an update. It links to the ticket only when the server
 * says this reader can open it (a member of the ticket's board, ticket not
 * deleted); otherwise it is plain text with no hover, focus or tooltip, so
 * the feed never offers a link the ticket page would refuse.
 */
export function UpdateTicketKey({ item, className }: UpdateTicketKeyProps) {
  const base = "font-mono text-xs font-semibold whitespace-nowrap";

  if (item.canOpen && item.ticketId) {
    return (
      <TicketKeyLink
        ticketKey={item.ticketKey}
        className={cn(
          base,
          "text-primary focus-visible:ring-ring rounded-sm underline-offset-2 hover:underline focus-visible:ring-2 focus-visible:outline-none",
          className,
        )}
      >
        {item.ticketKey}
      </TicketKeyLink>
    );
  }

  return <span className={cn(base, "text-foreground", className)}>{item.ticketKey}</span>;
}

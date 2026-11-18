"use client";

import { Badge } from "@/components/ui/badge";
import { UpdateTicketKey } from "@/features/updates/components/update-ticket-key";
import type { PullRequestShareTicketResponse } from "../schemas";

/**
 * A related ticket read the way a daily update lists the tickets worked on:
 * the board as an outline badge, the key, then the title. The key links to
 * the ticket only when this reader can open it, through the same component
 * the daily update uses, so the two never disagree on when a key is a link.
 */
export function RelatedTicketRow({ ticket }: { ticket: PullRequestShareTicketResponse }) {
  return (
    <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
      <Badge variant="outline" className="max-w-48 truncate text-[11px] font-medium">
        {ticket.boardName}
      </Badge>
      <UpdateTicketKey item={ticket} />
      <span className="text-foreground min-w-0 break-words">{ticket.ticketTitle}</span>
    </div>
  );
}

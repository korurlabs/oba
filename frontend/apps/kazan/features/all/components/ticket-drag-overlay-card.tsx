/**
 * TicketDragOverlayCard
 *
 * What the pointer carries while a ticket row is dragged between the All
 * tab's sections. A detached table row loses its column widths and cell
 * styling, so the overlay is a self-contained card instead: type, key,
 * title and status -- enough to recognise the ticket mid-flight.
 */

"use client";

import { useTranslations } from "next-intl";
import type { TicketListResponse } from "@/features/tickets/schemas";
import { TicketTypeBadge } from "@/features/tickets/components/ticket-type-badge";
import { TicketStatusIcon } from "@/features/tickets/components/ticket-status-icon";

interface TicketDragOverlayCardProps {
  ticket: TicketListResponse;
}

export function TicketDragOverlayCard({ ticket }: TicketDragOverlayCardProps) {
  const t = useTranslations();

  return (
    <div className="bg-card ring-primary/30 flex max-w-md cursor-grabbing items-center gap-2 rounded-lg border px-3 py-2 opacity-95 shadow-lg ring-2">
      <TicketTypeBadge type={ticket.type} />
      <span className="text-foreground shrink-0 text-xs font-medium whitespace-nowrap">
        {ticket.key}
      </span>
      <span className="text-foreground truncate text-sm">{ticket.title}</span>
      <span className="ml-auto flex shrink-0 items-center gap-1.5">
        <TicketStatusIcon status={ticket.status} />
        <span className="text-muted-foreground text-xs whitespace-nowrap">
          {t(`ticket.status.${ticket.status}`)}
        </span>
      </span>
    </div>
  );
}

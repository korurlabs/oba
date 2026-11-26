"use client";

import { useTranslations } from "next-intl";
import { TicketSectionHeader } from "@/features/tickets/components/ticket-section-header";
import type { TicketListResponse, TicketStatus } from "@/features/tickets/schemas";

interface BacklogSectionHeaderProps {
  /** Tickets rendered by the backlog table (parent tickets only), for the classic status chips. */
  tickets?: TicketListResponse[];
  /** Currently selected statuses (the shared `statuses` URL filter). */
  selectedStatuses?: TicketStatus[];
  /** Toggles one status in the shared `statuses` URL filter. */
  onStatusToggle?: (status: TicketStatus) => void;
  /** Optional Add-to-Backlog click handler. When omitted the button is hidden. */
  onAddTicket?: () => void;
}

/**
 * Caption strip for the backlog table inside the All tab: the section's name
 * and its add action, the same shape as the sprint section above it.
 */
export function BacklogSectionHeader({
  tickets,
  selectedStatuses,
  onStatusToggle,
  onAddTicket,
}: BacklogSectionHeaderProps) {
  const t = useTranslations();

  return (
    <TicketSectionHeader
      title={t("backlog.title")}
      tickets={tickets}
      selectedStatuses={selectedStatuses}
      onStatusToggle={onStatusToggle}
      onAddTicket={onAddTicket}
      addLabel={t("all.addToBacklog")}
    />
  );
}

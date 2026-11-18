"use client";

import { useTranslations } from "next-intl";
import type { TicketStatus, TicketListResponse } from "@/features/tickets/schemas";
import { TicketSectionHeader } from "@/features/tickets/components/ticket-section-header";

interface BacklogSectionHeaderProps {
  /** Tickets rendered by the backlog table (parent tickets only). */
  tickets: TicketListResponse[];
  /** Currently selected statuses (the shared `statuses` URL filter). */
  selectedStatuses?: TicketStatus[];
  /** Toggles one status in the shared `statuses` URL filter. */
  onStatusToggle?: (status: TicketStatus) => void;
  /** Optional Add-to-Backlog click handler. When omitted the button is hidden. */
  onAddTicket?: () => void;
}

/**
 * Caption strip for the backlog table inside the All tab.
 *
 * Shares TicketSectionHeader with the sprint section so both tables carry
 * the same title / status-chips / add-action shape.
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

"use client";

import { useTranslations } from "next-intl";
import type { TicketStatus, TicketListResponse } from "@/features/tickets/schemas";
import { TicketSectionHeader } from "@/features/tickets/components/ticket-section-header";
import { SprintStatus, type SprintListResponse } from "../schemas";

interface SprintSectionHeaderProps {
  /** The currently selected sprint. Drives the completed-sprint add guard. */
  currentSprint: SprintListResponse;
  /** Tickets rendered by the sprint table (parent tickets only). */
  tickets: TicketListResponse[];
  /** Currently selected statuses (the shared `statuses` URL filter). */
  selectedStatuses?: TicketStatus[];
  /** Toggles one status in the shared `statuses` URL filter. */
  onStatusToggle?: (status: TicketStatus) => void;
  /** Optional Add-to-Sprint click handler. When omitted the button is hidden. */
  onAddTicket?: () => void;
}

/**
 * Caption strip for the sprint table inside the All tab.
 *
 * The sprint selector, its date range and the days-left badge used to live
 * here; they now sit in AllToolbar next to the search and filter controls,
 * which removes a whole band of chrome above the table. What is left is the
 * section title, the status chips and the Add-to-Sprint action.
 */
export function SprintSectionHeader({
  currentSprint,
  tickets,
  selectedStatuses,
  onStatusToggle,
  onAddTicket,
}: SprintSectionHeaderProps) {
  const t = useTranslations();

  const canAddTicket = currentSprint.status !== SprintStatus.COMPLETED;

  return (
    <TicketSectionHeader
      title={t("all.sprintSection")}
      tickets={tickets}
      selectedStatuses={selectedStatuses}
      onStatusToggle={onStatusToggle}
      onAddTicket={canAddTicket ? onAddTicket : undefined}
      addLabel={t("all.addToSprint")}
    />
  );
}

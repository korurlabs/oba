"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { TicketSectionHeader } from "@/features/tickets/components/ticket-section-header";
import type { TicketListResponse, TicketStatus } from "@/features/tickets/schemas";
import { SprintStatus, type SprintListResponse } from "../schemas";

interface SprintSectionHeaderProps {
  /** The currently selected sprint. Drives the completed-sprint add guard. */
  currentSprint: SprintListResponse;
  /** The sprint selector with the sprint's state, which leads the strip. */
  sprintContext: ReactNode;
  /** Optional Add-to-Sprint click handler. When omitted the button is hidden. */
  onAddTicket?: () => void;
  /** Tickets rendered by the sprint table (parent tickets only), for the classic status chips. */
  tickets?: TicketListResponse[];
  /** Currently selected statuses (the shared `statuses` URL filter). */
  selectedStatuses?: TicketStatus[];
  /** Toggles one status in the shared `statuses` URL filter. */
  onStatusToggle?: (status: TicketStatus) => void;
}

/**
 * Caption strip for the sprint table inside the All tab: which sprint the
 * table shows and its state on the left, Add to Sprint on the right. A
 * completed sprint takes no new tickets, so it shows no add action.
 */
export function SprintSectionHeader({
  currentSprint,
  sprintContext,
  onAddTicket,
  tickets,
  selectedStatuses,
  onStatusToggle,
}: SprintSectionHeaderProps) {
  const t = useTranslations();

  const canAddTicket = currentSprint.status !== SprintStatus.COMPLETED;

  return (
    <TicketSectionHeader
      leading={sprintContext}
      tickets={tickets}
      selectedStatuses={selectedStatuses}
      onStatusToggle={onStatusToggle}
      onAddTicket={canAddTicket ? onAddTicket : undefined}
      addLabel={t("all.addToSprint")}
    />
  );
}

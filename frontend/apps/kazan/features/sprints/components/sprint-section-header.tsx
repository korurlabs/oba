"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { TicketSectionHeader } from "@/features/tickets/components/ticket-section-header";
import { SprintStatus, type SprintListResponse } from "../schemas";

interface SprintSectionHeaderProps {
  /** The currently selected sprint. Drives the completed-sprint add guard. */
  currentSprint: SprintListResponse;
  /** The sprint selector with the sprint's state, which leads the strip. */
  sprintContext: ReactNode;
  /** Optional Add-to-Sprint click handler. When omitted the button is hidden. */
  onAddTicket?: () => void;
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
}: SprintSectionHeaderProps) {
  const t = useTranslations();

  const canAddTicket = currentSprint.status !== SprintStatus.COMPLETED;

  return (
    <TicketSectionHeader
      leading={sprintContext}
      onAddTicket={canAddTicket ? onAddTicket : undefined}
      addLabel={t("all.addToSprint")}
    />
  );
}

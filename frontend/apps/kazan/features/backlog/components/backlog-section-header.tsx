"use client";

import { useTranslations } from "next-intl";
import { TicketSectionHeader } from "@/features/tickets/components/ticket-section-header";

interface BacklogSectionHeaderProps {
  /** Optional Add-to-Backlog click handler. When omitted the button is hidden. */
  onAddTicket?: () => void;
}

/**
 * Caption strip for the backlog table inside the All tab: the section's name
 * and its add action, the same shape as the sprint section above it.
 */
export function BacklogSectionHeader({ onAddTicket }: BacklogSectionHeaderProps) {
  const t = useTranslations();

  return (
    <TicketSectionHeader
      title={t("backlog.title")}
      onAddTicket={onAddTicket}
      addLabel={t("all.addToBacklog")}
    />
  );
}

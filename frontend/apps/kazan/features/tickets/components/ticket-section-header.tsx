/**
 * TicketSectionHeader Component
 *
 * The single 32px strip that sits directly above a ticket table and acts as
 * its caption: what the table shows on the left (a section title, or the
 * sprint selector with the sprint's state) and that section's add action on
 * the right.
 *
 * The strip is flush-left on the same 24px rule as the board bar above it
 * so it reads as part of the table it belongs to rather than as another
 * band of page chrome.
 */

"use client";

import type { ReactNode } from "react";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useKazanAppearance } from "@/features/settings/hooks/use-kazan-appearance";
import type { TicketListResponse, TicketStatus } from "@/features/tickets/schemas";
import { TicketStatusSummary } from "./ticket-status-summary";

interface TicketSectionHeaderProps {
  /** Section label, e.g. "Backlog". */
  title?: string;
  /**
   * Replaces the title, for a strip whose left side is a control, such as
   * the sprint selector with the sprint's state.
   */
  leading?: ReactNode;
  /** Add-ticket click handler. When omitted the button is hidden. */
  onAddTicket?: () => void;
  /** Label for the add button. */
  addLabel?: string;
  /** Controls at the right end, before the add button. */
  actions?: ReactNode;
  /**
   * The tickets of the table, for the per-status chips the classic layout
   * shows after the title. Without them no chips are shown.
   */
  tickets?: TicketListResponse[];
  /** Currently selected statuses (the shared `statuses` URL filter). */
  selectedStatuses?: TicketStatus[];
  /** Toggles one status in the shared `statuses` URL filter. */
  onStatusToggle?: (status: TicketStatus) => void;
}

export function TicketSectionHeader({
  title,
  leading,
  onAddTicket,
  addLabel,
  actions,
  tickets,
  selectedStatuses,
  onStatusToggle,
}: TicketSectionHeaderProps) {
  const { layout } = useKazanAppearance();

  return (
    <div className="flex min-h-8 flex-wrap items-center gap-x-4 gap-y-2">
      {leading ?? (
        <h2 className="kazan-grid-label classic:text-foreground classic:text-sm classic:font-semibold inline-flex items-center gap-1.5 whitespace-nowrap">
          {title}
          {layout === "classic" && tickets && (
            <span className="text-muted-foreground font-normal">· {tickets.length}</span>
          )}
        </h2>
      )}

      {layout === "classic" && tickets && (
        <TicketStatusSummary
          tickets={tickets}
          showTotal={false}
          selectedStatuses={selectedStatuses}
          onStatusToggle={onStatusToggle}
          className="w-auto justify-start gap-x-1 gap-y-1 px-0 py-0"
        />
      )}

      {(actions || onAddTicket) && (
        <div className="ml-auto flex items-center gap-2">
          {actions}
          {onAddTicket && (
            <Button
              type="button"
              size="sm"
              variant="outline"
              className="gap-1"
              onClick={onAddTicket}
            >
              <Plus className="h-4 w-4" />
              <span className="hidden md:inline">{addLabel}</span>
            </Button>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * TicketSectionHeader Component
 *
 * The single 32px strip that sits directly above a ticket table and
 * acts as its caption: section title + count, the clickable status
 * chips, and that section's add action.
 *
 * It replaces the old centered "Total: n · To Do (n) · …" banner. The
 * strip is flush-left on the same 24px rule as the toolbar above it so
 * it reads as part of the table it belongs to rather than as another
 * band of page chrome.
 */

"use client";

import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { TicketStatus, TicketListResponse } from "@/features/tickets/schemas";
import { TicketStatusSummary } from "./ticket-status-summary";

// ── Types ────────────────────────────────────────────────────────────

interface TicketSectionHeaderProps {
  /** Section label, e.g. "Sprint" or "Backlog". */
  title: string;
  /** Tickets this section renders — drives the count and the chips. */
  tickets: TicketListResponse[];
  /** Currently selected statuses (the shared `statuses` URL filter). */
  selectedStatuses?: TicketStatus[];
  /** Toggles one status in the shared `statuses` URL filter. */
  onStatusToggle?: (status: TicketStatus) => void;
  /** Add-ticket click handler. When omitted the button is hidden. */
  onAddTicket?: () => void;
  /** Label for the add button. */
  addLabel: string;
}

// ── Main Component ───────────────────────────────────────────────────

export function TicketSectionHeader({
  title,
  tickets,
  selectedStatuses,
  onStatusToggle,
  onAddTicket,
  addLabel,
}: TicketSectionHeaderProps) {
  return (
    <div className="flex min-h-8 flex-wrap items-center gap-x-3 gap-y-2">
      <h2 className="text-foreground text-sm font-semibold whitespace-nowrap">
        {title}
        <span className="text-muted-foreground ml-1.5 font-normal">
          · {tickets.length}
        </span>
      </h2>

      <TicketStatusSummary
        tickets={tickets}
        showTotal={false}
        selectedStatuses={selectedStatuses}
        onStatusToggle={onStatusToggle}
        className="w-auto justify-start gap-x-1 gap-y-1 px-0 py-0"
      />

      {onAddTicket && (
        <Button
          type="button"
          size="sm"
          variant="outline"
          className="ml-auto gap-1"
          onClick={onAddTicket}
        >
          <Plus className="h-4 w-4" />
          <span className="hidden md:inline">{addLabel}</span>
        </Button>
      )}
    </div>
  );
}

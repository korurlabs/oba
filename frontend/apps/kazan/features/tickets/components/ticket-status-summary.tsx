/**
 * TicketStatusSummary Component
 *
 * Displays the per-status breakdown of a ticket list, optionally
 * preceded by a total. Groups tickets by status internally so
 * consumers only need to pass the tickets array.
 *
 * Each status reads as a neutral mono readout, "TO DO 05", in the grid
 * look: the words and the numbers carry the meaning, no colour does.
 */

"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { ListCheck } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { TicketStatus, type TicketListResponse } from "@/features/tickets/schemas";

// ── Types ────────────────────────────────────────────────────────────

interface TicketStatusSummaryProps {
  tickets: TicketListResponse[];
  /** Renders the leading "Total: n" group. Defaults to true. */
  showTotal?: boolean;
  /** Layout overrides merged over the default centered strip. */
  className?: string;
}

// ── Constants ────────────────────────────────────────────────────────

const STATUS_ORDER = [
  TicketStatus.TODO,
  TicketStatus.IN_PROGRESS,
  TicketStatus.WAITING_FOR_THIRD_PARTY,
  TicketStatus.IN_REVIEW,
  TicketStatus.DONE,
] as const;

/** Two digits at least, so the readouts line up as a counter does. */
function formatCount(count: number): string {
  return String(count).padStart(2, "0");
}

// ── Main Component ───────────────────────────────────────────────────

export function TicketStatusSummary({
  tickets,
  showTotal = true,
  className,
}: TicketStatusSummaryProps) {
  const t = useTranslations();
  const tTicket = useTranslations("ticket");

  const ticketsGroupedByStatus = useMemo(() => {
    return STATUS_ORDER.map((status) => ({
      status,
      count: tickets.filter((ticket) => ticket.status === status).length,
    }));
  }, [tickets]);

  return (
    <div
      className={cn("flex w-full flex-wrap items-center justify-center gap-4 px-4 py-1", className)}
    >
      {showTotal && (
        <div className="flex items-center gap-2">
          <ListCheck className="text-muted-foreground h-4 w-4" />
          <span className="text-muted-foreground font-mono text-[11px] font-medium tracking-[0.08em] uppercase tabular-nums">
            {t("total")} {formatCount(tickets.length)}
          </span>
        </div>
      )}
      {ticketsGroupedByStatus.map((group) => (
        <div key={group.status} className="text-muted-foreground flex items-center gap-2">
          <span className="font-mono text-[11px] font-medium tracking-[0.08em] whitespace-nowrap uppercase tabular-nums">
            {tTicket(`status.${group.status}`)}
            <span
              className={cn(
                "ml-1.5",
                group.count > 0 ? "text-foreground" : "text-muted-foreground/70"
              )}
            >
              {formatCount(group.count)}
            </span>
          </span>
        </div>
      ))}
    </div>
  );
}

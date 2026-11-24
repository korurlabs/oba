/**
 * TicketStatusSummary Component
 *
 * Displays the per-status breakdown of a ticket list, optionally
 * preceded by a total. Groups tickets by status internally so
 * consumers only need to pass the tickets array.
 *
 * Each status reads as a neutral mono readout, "TO DO 05", in the grid
 * look: the words and the numbers carry the meaning, no colour does.
 *
 * Two modes:
 * - Passive (default): each status renders as a static label + count.
 * - Interactive: pass `onStatusToggle` and each status renders as a
 *   toggle button that drives the `statuses` URL filter. Because the
 *   ticket list is filtered server-side, the per-status counts are
 *   meaningless once a status filter is active — so while
 *   `selectedStatuses` is non-empty the chips render label-only.
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
  /** Currently selected statuses (the `statuses` URL filter). */
  selectedStatuses?: TicketStatus[];
  /** When provided each status renders as a toggle button. */
  onStatusToggle?: (status: TicketStatus) => void;
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
  selectedStatuses,
  onStatusToggle,
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

  const selected = selectedStatuses ?? [];
  // A status filter is applied server-side, so every other status genuinely
  // has nothing left to count. Hide the numbers rather than show zeroes.
  const hasStatusFilter = selected.length > 0;

  return (
    <div
      className={cn(
        "flex w-full flex-wrap items-center justify-center gap-4 px-4 py-1",
        className,
      )}
    >
      {showTotal && (
        <div className="flex items-center gap-2">
          <ListCheck className="text-muted-foreground h-4 w-4" />
          <span className="text-muted-foreground font-mono text-[11px] font-medium tracking-[0.08em] uppercase tabular-nums">
            {t("total")} {formatCount(tickets.length)}
          </span>
        </div>
      )}
      {ticketsGroupedByStatus.map((group) => {
        const label = tTicket(`status.${group.status}`);
        const body = (
          <span className="font-mono text-[11px] font-medium tracking-[0.08em] whitespace-nowrap uppercase tabular-nums">
            {label}
            {!hasStatusFilter && (
              <span className={cn("ml-1.5", group.count > 0 ? "text-foreground" : "text-muted-foreground/70")}>
                {formatCount(group.count)}
              </span>
            )}
          </span>
        );

        if (!onStatusToggle) {
          return (
            <div
              key={group.status}
              className="text-muted-foreground flex items-center gap-2"
            >
              {body}
            </div>
          );
        }

        const isSelected = selected.includes(group.status);
        // The accessible name has to carry the count sighted users can see;
        // an aria-label that names only the status would hide it from AT.
        // While a status filter is active there is no count to announce.
        const accessibleLabel = hasStatusFilter
          ? tTicket("status.filterBy", { status: label })
          : tTicket("status.filterByWithCount", {
              status: label,
              count: group.count,
            });
        return (
          <button
            key={group.status}
            type="button"
            aria-pressed={isSelected}
            aria-label={accessibleLabel}
            onClick={() => onStatusToggle(group.status)}
            className={cn(
              // min-h-6 + py-1 keeps the hit target at the 24x24 CSS-px floor
              // (WCAG 2.2 SC 2.5.8) inside the 32px caption strip.
              "flex min-h-6 items-center gap-2 border px-2 py-1 transition-colors",
              "focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-none",
              isSelected
                ? "border-primary/50 bg-primary/5 text-korur-blue-strong"
                : "text-muted-foreground hover:border-border border-transparent hover:bg-primary/5",
            )}
          >
            {body}
          </button>
        );
      })}
    </div>
  );
}

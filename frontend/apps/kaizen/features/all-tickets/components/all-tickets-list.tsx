/**
 * AllTicketsList Component
 *
 * Table view for cross-board tickets using SenseyTable.
 * No drag-and-drop reordering (tickets span multiple boards).
 */

"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { VisibilityState } from "@tanstack/react-table";
import type { TicketListResponse } from "@/features/tickets/schemas";
import { TicketStatusSummary } from "@/features/tickets/components/ticket-status-summary";
import { SenseyTable } from "@oba/shared/components/sensey-table";
import { useAllTicketColumns } from "../hooks/use-all-ticket-columns";
import type { AllTicketListResponse } from "../schemas";

// ── Types ────────────────────────────────────────────────────────────

interface AllTicketsListProps {
  tickets: AllTicketListResponse[];
  onTicketClick: (ticket: TicketListResponse) => void;
  onCreateTicket: () => void;
  onCopyLink?: (ticket: TicketListResponse) => void;
  isLoading?: boolean;
  columnVisibility: VisibilityState;
  onColumnVisibilityChange: (visibility: VisibilityState) => void;
  columnOrder: string[];
  onColumnOrderChange: (order: string[]) => void;
}

// ── Main Component ───────────────────────────────────────────────────

export function AllTicketsList({
  tickets,
  onTicketClick,
  onCreateTicket,
  onCopyLink,
  isLoading = false,
  columnVisibility,
  onColumnVisibilityChange,
  columnOrder,
  onColumnOrderChange,
}: AllTicketsListProps) {
  const t = useTranslations();
  const columns = useAllTicketColumns({ onCopyLink });

  // Filter to parent tickets only (sub-tickets rendered as hierarchy)
  const parentTickets = useMemo(() => {
    return tickets.filter((ticket) => !ticket.parentTicketId);
  }, [tickets]);

  // ── Empty message ──────────────────────────────────────────────────

  const emptyMessage = (
    <div className="flex flex-col items-center justify-center py-8">
      <p className="text-muted-foreground text-sm">{t("ticket.noTickets")}</p>
    </div>
  );

  // ── Render ─────────────────────────────────────────────────────────

  return (
    <div className="flex h-full flex-col gap-4 overflow-hidden p-6">
      <TicketStatusSummary tickets={parentTickets as TicketListResponse[]} />
      <SenseyTable<TicketListResponse>
        columns={columns}
        data={parentTickets as TicketListResponse[]}
        isLoading={isLoading}
        emptyMessage={emptyMessage}
        hierarchyKey="subTickets"
        onRowClick={onTicketClick}
        virtualized={true}
        columnVisibility={columnVisibility}
        onColumnVisibilityChange={onColumnVisibilityChange}
        columnOrder={columnOrder}
        onColumnOrderChange={onColumnOrderChange}
      />
    </div>
  );
}

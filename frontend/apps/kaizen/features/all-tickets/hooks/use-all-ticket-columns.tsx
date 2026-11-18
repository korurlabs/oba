import { ColumnDef } from "@tanstack/react-table";
import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { TicketListResponse } from "@/features/tickets/schemas";
import type { AllTicketListResponse } from "../schemas";
import { useTicketColumns } from "@/features/tickets/hooks/use-ticket-columns";
import { Badge } from "@/components/ui/badge";

interface UseAllTicketColumnsOptions {
  onCopyLink?: (ticket: TicketListResponse) => void;
}

export const useAllTicketColumns = (options?: UseAllTicketColumnsOptions) => {
  const t = useTranslations();
  const baseColumns = useTicketColumns({ onCopyLink: options?.onCopyLink });

  return useMemo<ColumnDef<TicketListResponse, unknown>[]>(() => {
    const sprintColumn: ColumnDef<TicketListResponse, unknown> = {
      id: "sprint",
      accessorFn: (row) => (row as AllTicketListResponse).sprint?.name ?? "",
      header: t("ticket.table.sprint"),
      cell: ({ row }) => {
        const sprint = (row.original as AllTicketListResponse).sprint;
        if (!sprint) return <span className="text-muted-foreground text-xs">—</span>;
        return (
          <span className="text-muted-foreground text-xs whitespace-nowrap">
            {sprint.name}
          </span>
        );
      },
      sortingFn: "alphanumeric",
    };

    const backlogColumn: ColumnDef<TicketListResponse, unknown> = {
      id: "backlog",
      accessorFn: (row) => ((row as AllTicketListResponse).backlog ? "1" : "0"),
      header: t("ticket.table.backlog"),
      cell: ({ row }) => {
        const backlog = (row.original as AllTicketListResponse).backlog;
        if (!backlog) return <span className="text-muted-foreground text-xs">—</span>;
        return (
          <Badge variant="outline" className="text-xs font-normal whitespace-nowrap">
            {t("ticket.table.inBacklog")}
          </Badge>
        );
      },
      sortingFn: "alphanumeric",
    };

    const boardIndex = baseColumns.findIndex((col) => col.id === "board");
    const insertIndex = boardIndex !== -1 ? boardIndex + 1 : baseColumns.length;

    const columns = [...baseColumns];
    columns.splice(insertIndex, 0, sprintColumn, backlogColumn);
    return columns;
  }, [baseColumns, t]);
};

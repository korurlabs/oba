import { useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { useTranslations } from "next-intl";
import type { TicketListResponse } from "@/features/tickets/schemas";
import { useTicketColumns } from "@/features/tickets/hooks/use-ticket-columns";

/** Columns of the base ticket table that help to recognise a ticket. */
const PICKER_BASE_COLUMN_IDS = ["key", "title", "board", "status", "assignee"];

function columnId(column: ColumnDef<TicketListResponse, unknown>): string {
  return (
    (column as { accessorKey?: string }).accessorKey?.toString() ??
    (column as { id?: string }).id ??
    ""
  );
}

/**
 * Columns of a ticket picker: the base ticket columns that identify a
 * ticket, the title taking the free width, and the ticket's progress, which
 * a daily update line starts from.
 */
export const useTicketPickerColumns = () => {
  const t = useTranslations();
  const baseColumns = useTicketColumns();

  return useMemo<ColumnDef<TicketListResponse, unknown>[]>(() => {
    const picked = PICKER_BASE_COLUMN_IDS.flatMap((id) => {
      const column = baseColumns.find((candidate) => columnId(candidate) === id);
      if (!column) return [];
      if (id === "title") return [{ ...column, meta: { flex: true } }];
      if (id === "board") return [{ ...column, meta: undefined }];
      return [column];
    });

    const progressColumn: ColumnDef<TicketListResponse, unknown> = {
      id: "progress",
      accessorFn: (row) => row.percentage,
      header: t("updates.form.progressColumn"),
      cell: ({ row }) => (
        <span className="text-muted-foreground text-xs tabular-nums whitespace-nowrap">
          {row.original.percentage}%
        </span>
      ),
      sortingFn: "basic",
    };

    return [...picked, progressColumn];
  }, [baseColumns, t]);
};

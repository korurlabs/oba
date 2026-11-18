"use client";

import { useMemo } from "react";
import type { ColumnToggleItem } from "@oba/shared/components/sensey-table";
import { useTicketColumns } from "./use-ticket-columns";

/**
 * The entries of the Columns tab for a ticket table, one per ticket column.
 *
 * Every ticket table offers the same list, so a layout saved on one table (or
 * saved as the default) names the same column ids on every other table.
 */
export function useTicketColumnToggleItems(): ColumnToggleItem[] {
  const columns = useTicketColumns();
  return useMemo<ColumnToggleItem[]>(
    () =>
      columns.map((col) => {
        const id =
          (col as { accessorKey?: string }).accessorKey?.toString() ??
          (col as { id?: string }).id ??
          "";
        return {
          id,
          header: typeof col.header === "string" ? col.header : id,
          canHide: col.enableHiding !== false,
        };
      }),
    [columns],
  );
}

import { flexRender, type ColumnDef } from "@tanstack/react-table";
import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import type { TicketListResponse } from "@/features/tickets/schemas";
import { useTicketColumns } from "@/features/tickets/hooks/use-ticket-columns";

type UseTicketColumnsOptions = NonNullable<Parameters<typeof useTicketColumns>[0]>;

/**
 * The ticket columns, with the title of a nested sub-ticket that is planned
 * in a sprint followed by that sprint's name. The backlog nests every
 * sub-ticket of a parent, wherever it is planned, so without the name a
 * sub-ticket already planned reads as backlog work.
 */
export const useBacklogTicketColumns = (options?: UseTicketColumnsOptions) => {
  const t = useTranslations("backlog");
  const baseColumns = useTicketColumns(options);

  return useMemo<ColumnDef<TicketListResponse, unknown>[]>(
    () =>
      baseColumns.map((column) => {
        if (!("accessorKey" in column) || column.accessorKey !== "title") return column;
        return {
          ...column,
          cell: (context) => {
            const sprint = context.row.depth > 0 ? context.row.original.sprint : null;
            const title = flexRender(column.cell, context);
            if (!sprint) return title;
            return (
              <div className="flex min-w-0 items-center gap-2">
                <div className="min-w-0">{title}</div>
                <Badge variant="outline" className="shrink-0 text-[10px] font-normal">
                  {t("plannedInSprint", { sprint: sprint.name })}
                </Badge>
              </div>
            );
          },
        };
      }),
    [baseColumns, t]
  );
};

"use client";

import type { ReactNode } from "react";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type Header,
  type Row,
  type Cell,
} from "@tanstack/react-table";
import { TableLoading } from "@oba/ui/sky/page-states";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@oba/ui/sky/table";

interface DataTableProps<TData> {
  columns: ColumnDef<TData, unknown>[];
  data: TData[];
  isLoading: boolean;
  /** Names the list for assistive technology while its rows load. */
  loadingLabel: string;
  /** Shown in place of the table when the list has no rows. */
  emptyMessage: ReactNode;
  getRowId?: (row: TData) => string;
}

/**
 * A list of an Admin page in Kaya's table on a white rounded panel. The
 * columns are TanStack column definitions, so a column hook decides what
 * each cell shows. While the first answer loads it shows the list's outline,
 * and a list with no rows shows the page's empty state instead of a table.
 */
export function DataTable<TData>({
  columns,
  data,
  isLoading,
  loadingLabel,
  emptyMessage,
  getRowId,
}: DataTableProps<TData>) {
  const table = useReactTable<TData>({
    columns,
    data,
    getRowId,
    getCoreRowModel: getCoreRowModel(),
  });

  if (isLoading) return <TableLoading label={loadingLabel} />;
  if (data.length === 0) return <>{emptyMessage}</>;

  return (
    <div className="bg-card rounded-xl border px-4 py-2 shadow-sm">
      <Table>
        <TableHeader>
          {table.getHeaderGroups().map((group) => (
            <TableRow key={group.id} className="hover:bg-transparent">
              {group.headers.map((header: Header<TData, unknown>) => (
                <TableHead key={header.id}>
                  {header.isPlaceholder
                    ? null
                    : flexRender(header.column.columnDef.header, header.getContext())}
                </TableHead>
              ))}
            </TableRow>
          ))}
        </TableHeader>
        <TableBody>
          {table.getRowModel().rows.map((row: Row<TData>) => (
            <TableRow key={row.id}>
              {row.getVisibleCells().map((cell: Cell<TData, unknown>) => (
                <TableCell key={cell.id}>
                  {flexRender(cell.column.columnDef.cell, cell.getContext())}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

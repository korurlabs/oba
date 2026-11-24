"use client";

import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type Cell,
  type ColumnDef,
  type Header,
  type Row,
} from "@tanstack/react-table";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@oba/ui/sky/table";
import { cn } from "@/lib/utils";

/**
 * What a column may say about its layout: `flex` takes the free width, the
 * others keep the width their `size` gives, and `align` sets the text side.
 */
export type DataTableColumnMeta = { flex?: boolean; align?: "left" | "center" | "right" };

function columnMeta(meta: unknown): DataTableColumnMeta {
  return (meta as DataTableColumnMeta | undefined) ?? {};
}

const ALIGN_CLASS: Record<NonNullable<DataTableColumnMeta["align"]>, string> = {
  left: "text-left",
  center: "text-center",
  right: "text-right",
};

/** A column's width: fixed from its `size`, unless it takes the free width or names none. */
function widthStyle<T>(column: Header<T, unknown>["column"]): { width?: number } {
  const meta = columnMeta(column.columnDef.meta);
  if (meta.flex || column.columnDef.size === undefined) return {};
  return { width: column.getSize() };
}

interface DataTableProps<T> {
  columns: ColumnDef<T, unknown>[];
  data: T[];
  getRowId: (row: T) => string;
  /** Opens a row; the row's own link or button stays the way for the keyboard. */
  onRowClick?: (row: T) => void;
  /** Marks one row as the one shown elsewhere on the page. */
  selectedRowId?: string | null;
  /** Names the table for assistive technology. */
  label: string;
}

/**
 * A list as Otak and Kaya show one: the sky table on a white rounded panel.
 * The caller renders the loading, error and empty states in its place.
 */
export function DataTable<T>({
  columns,
  data,
  getRowId,
  onRowClick,
  selectedRowId,
  label,
}: DataTableProps<T>) {
  const table = useReactTable<T>({
    columns,
    data,
    getRowId: (row: T) => getRowId(row),
    getCoreRowModel: getCoreRowModel(),
  });
  // Fixed widths hold only when a column names one; otherwise the browser sizes them to the text.
  const fixedLayout = columns.some((column: ColumnDef<T, unknown>) => column.size !== undefined);

  return (
    <div className="bg-card overflow-hidden rounded-xl border shadow-sm">
      <Table aria-label={label} className={cn(fixedLayout && "table-fixed")}>
        <TableHeader>
          {table.getHeaderGroups().map((group) => (
            <TableRow key={group.id} className="hover:bg-transparent">
              {group.headers.map((header: Header<T, unknown>) => {
                const align = columnMeta(header.column.columnDef.meta).align ?? "left";
                return (
                  <TableHead
                    key={header.id}
                    style={widthStyle(header.column)}
                    className={cn("px-4", ALIGN_CLASS[align])}
                  >
                    {header.isPlaceholder
                      ? null
                      : flexRender(header.column.columnDef.header, header.getContext())}
                  </TableHead>
                );
              })}
            </TableRow>
          ))}
        </TableHeader>
        <TableBody>
          {table.getRowModel().rows.map((row: Row<T>) => (
            <TableRow
              key={row.id}
              onClick={onRowClick ? () => onRowClick(row.original) : undefined}
              data-state={selectedRowId === row.id ? "selected" : undefined}
              aria-selected={selectedRowId === undefined ? undefined : selectedRowId === row.id}
              className={cn(onRowClick && "cursor-pointer")}
            >
              {row.getVisibleCells().map((cell: Cell<T, unknown>) => {
                const align = columnMeta(cell.column.columnDef.meta).align ?? "left";
                return (
                  <TableCell key={cell.id} className={cn("h-12 px-4", ALIGN_CLASS[align])}>
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </TableCell>
                );
              })}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

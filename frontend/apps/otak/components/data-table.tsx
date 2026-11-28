"use client";

import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type Cell,
  type Column,
  type ColumnDef,
  type Header,
  type Row,
} from "@tanstack/react-table";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@oba/ui/sky/table";
import { cn } from "@/lib/utils";

/**
 * What a column may say about its layout: `flex` takes the free width, the
 * others keep the width their `size` gives, and `align` sets the text side,
 * such as right for money.
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

/** Whether a column keeps the width its `size` gives, rather than sharing the free width. */
function hasFixedWidth<T>(column: Column<T, unknown>): boolean {
  return !columnMeta(column.columnDef.meta).flex && column.columnDef.size !== undefined;
}

/** A column's width: fixed from its `size`, unless it takes the free width or names none. */
function widthStyle<T>(column: Column<T, unknown>): { width?: number } {
  return hasFixedWidth(column) ? { width: column.getSize() } : {};
}

/**
 * The least width a column without a fixed width keeps, so the name column
 * stays readable beside wide fixed columns.
 */
const FREE_COLUMN_MIN_WIDTH = 240;

/**
 * The narrowest the table may get. A fixed layout gives the free columns only
 * what the fixed ones leave, which is nothing once they fill the panel; with
 * this floor the panel scrolls sideways instead of collapsing those columns.
 */
function tableMinWidth<T>(columns: Column<T, unknown>[]): number {
  let width = 0;
  for (const column of columns) {
    width += hasFixedWidth(column) ? column.getSize() : FREE_COLUMN_MIN_WIDTH;
  }
  return width;
}

interface DataTableProps<T> {
  columns: ColumnDef<T, unknown>[];
  data: T[];
  getRowId: (row: T) => string;
  /** Opens a row; the row's own link stays the way for the keyboard. */
  onRowClick?: (row: T) => void;
  /** Names the table for assistive technology. */
  label: string;
  /** Marks the rows as stale while a newer page loads. */
  isFetching?: boolean;
}

/**
 * A list as Kaya shows one: the sky table on a white rounded panel. The
 * caller renders the loading, error and empty states in its place.
 */
export function DataTable<T>({
  columns,
  data,
  getRowId,
  onRowClick,
  label,
  isFetching = false,
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
      <Table
        aria-label={label}
        aria-busy={isFetching}
        className={cn(fixedLayout && "table-fixed")}
        style={fixedLayout ? { minWidth: tableMinWidth(table.getVisibleLeafColumns()) } : undefined}
      >
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
        <TableBody className={cn(isFetching && "opacity-60")}>
          {table.getRowModel().rows.map((row: Row<T>) => (
            <TableRow
              key={row.id}
              onClick={onRowClick ? () => onRowClick(row.original) : undefined}
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

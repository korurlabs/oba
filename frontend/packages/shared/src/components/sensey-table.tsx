"use client";

import {
  useRef,
  useMemo,
  useState,
  useCallback,
  useImperativeHandle,
  useLayoutEffect,
  type ReactNode,
  type Ref,
  type SyntheticEvent,
} from "react";
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  getExpandedRowModel,
  flexRender,
  type ColumnDef,
  type SortingState,
  type ExpandedState,
  type VisibilityState,
  type RowSelectionState,
  type ColumnSizingState,
  type Row,
  type Header,
  type Table as ReactTable,
} from "@tanstack/react-table";
import { useVirtualizer } from "@tanstack/react-virtual";
import {
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from "@oba/ui/components/table";
import {
  SenseyPagination,
  type PaginationData,
} from "@oba/shared/components/sensey-pagination";
import {
  DndContext,
  DragOverlay,
  closestCenter,
  defaultDropAnimationSideEffects,
  KeyboardSensor,
  PointerSensor,
  useDndMonitor,
  useSensor,
  useSensors,
  type DraggableSyntheticListeners,
  type DragEndEvent,
  type DragStartEvent,
  type DropAnimation,
  type UniqueIdentifier,
} from "@dnd-kit/core";
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
  useSortable,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Button } from "@oba/ui/components/button";
import { Checkbox } from "@oba/ui/components/checkbox";
import { Label } from "@oba/ui/components/label";
import { Popover, PopoverContent, PopoverTrigger } from "@oba/ui/components/popover";
import { cn } from "@oba/ui/lib/utils";
import {
  ArrowDown,
  ArrowUp,
  ArrowUpDown,
  ChevronRight,
  Columns3,
  GripVertical,
  Loader2,
  RotateCcw,
  Save,
} from "lucide-react";
import { Skeleton } from "@oba/ui/components/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@oba/ui/components/tooltip";
import { useTranslations } from "next-intl";
import { useRowMoveAnimation, type RowMoveRef } from "@oba/shared/hooks/use-row-move-animation";
import { prefersReducedMotion } from "@oba/shared/lib/reduced-motion";

// ── Types ────────────────────────────────────────────────────────────

export interface SenseyTablePaginationConfig {
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onPageSizeChange?: (size: number) => void;
  pageSizeOptions?: number[];
}

export interface SenseyTableDraggableConfig<TData> {
  enabled: boolean;
  onReorder: (activeRow: TData, overRow: TData, newIndex: number) => void;
  getId?: (row: TData) => string;
  /**
   * Opt out of the table's own DndContext / DragOverlay and render only the
   * SortableContext, so an ancestor can host a single DndContext spanning
   * several tables (cross-container drag between sections).
   *
   * When true the ancestor owns sensors, collision detection, the drag
   * overlay AND the drop resolution -- `onReorder` is never invoked, because
   * the table no longer sees the drag events.
   *
   * The table still shows a drop of one of its rows onto another of its rows
   * in the new order at once (unless a column sort is active), so the
   * ancestor must reorder on exactly that condition; otherwise the rows show
   * an order the data never takes until the data changes.
   *
   * Defaults to false: the table keeps its self-contained behaviour.
   */
  externalDndContext?: boolean;
  /**
   * Slide rows to their new place when the data reorders them (a move from
   * another table, a refetch, a rollback, a column sort) instead of letting
   * them jump. Honours reduced motion. Applies whether or not `enabled` is
   * set, so read-only viewers see the same movement. Defaults to false.
   */
  animateRowMoves?: boolean;
}

export interface SenseyTableProps<TData> {
  columns: ColumnDef<TData, unknown>[];
  data: TData[];
  pagination?: SenseyTablePaginationConfig;
  draggable?: SenseyTableDraggableConfig<TData>;
  sorting?: SortingState;
  onSortingChange?: (sorting: SortingState) => void;
  columnVisibility?: VisibilityState;
  onColumnVisibilityChange?: (visibility: VisibilityState) => void;
  columnOrder?: string[];
  onColumnOrderChange?: (order: string[]) => void;
  isLoading?: boolean;
  emptyMessage?: string | ReactNode;
  onRowClick?: (row: TData) => void;
  rowClassName?: string | ((row: TData) => string);
  className?: string;
  /** Estimated row height for virtualization (default: 48) */
  estimateRowHeight?: number;
  /** Enable virtualization (default: true when data length > 50) */
  virtualized?: boolean;
  /** Key in TData that contains child rows for hierarchy (e.g. "subTickets") */
  hierarchyKey?: keyof TData & string;
  /** Whether hierarchy rows are expanded by default (default: false) */
  defaultExpanded?: boolean;
  /** Controlled row selection state (record of rowId → boolean) */
  rowSelection?: RowSelectionState;
  /** Called when row selection changes */
  onRowSelectionChange?: (selection: RowSelectionState) => void;
  /** Custom function to get the row ID for selection (defaults to row index) */
  getRowId?: (row: TData) => string;
  /** Enable built-in row selection checkboxes (opt-in, default: false) */
  enableSelection?: boolean;
  /**
   * Marks a top-level row as a static group header: it is never selected or
   * dragged, it always shows its child rows, and those children are selected
   * and dragged like top-level rows, because they are the work the header
   * only groups.
   */
  isRowStatic?: (row: TData) => boolean;
  /**
   * Content of a static row, in one cell across every data column. Without
   * it a static row renders its column cells, muted.
   */
  renderStaticRow?: (row: TData) => ReactNode;
  /** Enable column width resizing via drag handles on header borders (default: true) */
  enableColumnResizing?: boolean;
  /** Controlled column sizing state (record of columnId → width in px) */
  columnSizing?: ColumnSizingState;
  /** Called when column sizing changes */
  onColumnSizingChange?: (sizing: ColumnSizingState) => void;
  /** @deprecated Use columnVisibility/onColumnVisibilityChange props instead */
  tableRef?: Ref<ReactTable<TData>>;
}

// ── Helpers ──────────────────────────────────────────────────────────

function hasTextSelection() {
  const selection = window.getSelection();
  return selection != null && selection.toString().length > 0;
}

/**
 * Elements inside a draggable row that keep their own pointer and key
 * handling: pressing one of them never starts a row drag. A cell renders
 * `data-row-drag-ignore` on any other element that must behave the same.
 */
const ROW_DRAG_IGNORE_SELECTOR =
  'button, a, input, textarea, select, [role="checkbox"], [role="menuitem"], [data-row-drag-ignore]';

/**
 * True when a press on a draggable row belongs to something else: a control
 * inside the row, or an element outside the row's DOM node. React bubbles
 * events from portals through the component tree, so a menu opened from the
 * row's actions cell reaches the row's handlers although it is rendered
 * elsewhere in the document.
 */
function isRowDragIgnored(event: SyntheticEvent<HTMLElement>): boolean {
  const row = event.currentTarget;
  const target = event.target;
  if (!(target instanceof Element) || !row.contains(target)) return true;
  const control = target.closest(ROW_DRAG_IGNORE_SELECTOR);
  return control !== null && row.contains(control);
}

/**
 * dnd-kit's activator listeners (pointer down, key down), each skipped when
 * the event belongs to a control in the row.
 */
function guardRowDragListeners(
  listeners: DraggableSyntheticListeners
): DraggableSyntheticListeners {
  if (!listeners) return undefined;
  const guarded: NonNullable<DraggableSyntheticListeners> = {};
  for (const [eventName, handler] of Object.entries(listeners)) {
    guarded[eventName] = (event: SyntheticEvent<HTMLElement>) => {
      if (isRowDragIgnored(event)) return;
      handler(event);
    };
  }
  return guarded;
}

/**
 * Drop animation of the row overlay: a short, purely decelerating glide into
 * the slot the row now occupies. The table renders the dropped order in the
 * commit that ends the drag, so the slot is the new one; dnd-kit's default
 * curve overshoots, which reads as a bounce once the row is already in place.
 * The real row is hidden for the duration so the overlay and the row are
 * never both visible. Same values as the ticket kanban's drop animation,
 * which this package cannot import.
 */
const ROW_DROP_ANIMATION: DropAnimation = {
  duration: 180,
  easing: "cubic-bezier(0.2, 0, 0, 1)",
  sideEffects: defaultDropAnimationSideEffects({
    styles: {
      active: { opacity: "0" },
    },
  }),
};

type ColumnMeta = { flex?: boolean; align?: "left" | "center" | "right" };

function getColumnMeta(meta: unknown): ColumnMeta {
  return (meta as ColumnMeta) ?? {};
}

/**
 * Resolves the inline width style for a cell. Returns a width object only when
 * the column has a fixed width to apply:
 *  - non-flex column whose consumer set an explicit `size` (tracked via the
 *    `explicitSizeIds` set built from the original `columns` prop), or
 *  - any column the user has resized (entry in `columnSizing`).
 *
 * For untouched flex/auto columns we return `undefined` so the table's
 * default auto-layout content sizing kicks in.
 *
 * NOTE: We deliberately do NOT use `column.columnDef.size`, because TanStack
 * merges `defaultColumn.size = 150` into every column, so that field is
 * always populated even when the consumer didn't set a size.
 *
 * Width is enforced via `width + minWidth + maxWidth` so that cell content
 * cannot push the column wider in `table-layout: auto`.
 */
function getCellWidthStyle(
  column: { id: string; columnDef: { meta?: unknown }; getSize: () => number },
  columnSizing: Record<string, number>,
  explicitSizeIds: Set<string>
): { width: number; minWidth: number; maxWidth: number } | undefined {
  const meta = getColumnMeta(column.columnDef.meta);
  const hasResizedSize = columnSizing[column.id] !== undefined;
  const hasExplicitSize = !meta.flex && explicitSizeIds.has(column.id);
  if (!hasExplicitSize && !hasResizedSize) return undefined;
  const size = column.getSize();
  return { width: size, minWidth: size, maxWidth: size };
}

/**
 * Builds a set of column IDs whose `size` was explicitly set by the consumer.
 * We need this because TanStack merges `defaultColumn.size = 150` into every
 * column's `columnDef`, making it impossible to detect "no size" by checking
 * `columnDef.size !== undefined` after the merge.
 */
function getExplicitSizeIds<TData>(columns: ColumnDef<TData, unknown>[]): Set<string> {
  const ids = new Set<string>();
  for (const col of columns) {
    if (col.size === undefined) continue;
    const id =
      (col as { id?: string }).id ?? (col as { accessorKey?: string }).accessorKey;
    if (typeof id === "string") ids.add(id);
  }
  return ids;
}

const ALIGN_CLASS = {
  left: "text-left",
  center: "text-center",
  right: "text-right",
} as const;

// ── Hierarchy Indicator Cell ──────────────────────────────────────────

function HierarchyIndicatorCell<TData>({
  row,
  canExpand,
}: {
  row: Row<TData>;
  canExpand: boolean;
}) {
  if (row.depth > 0) {
    const siblingCount = row.getParentRow()?.subRows.length ?? 0;
    const isLast = row.index === siblingCount - 1;
    return (
      <TableCell className="w-8 px-0 text-center">
        <span className="text-muted-foreground/50 text-sm font-light select-none">
          {isLast ? "└" : "├"}
        </span>
      </TableCell>
    );
  }

  return (
    <TableCell className="w-8 px-0 text-center">
      {canExpand ? (
        <button
          type="button"
          className="hover:bg-muted mx-auto flex h-5 w-5 shrink-0 items-center justify-center rounded"
          onClick={(e) => {
            e.stopPropagation();
            row.toggleExpanded();
          }}
        >
          <ChevronRight
            className={cn(
              "text-muted-foreground h-3.5 w-3.5 transition-transform",
              row.getIsExpanded() && "rotate-90"
            )}
          />
        </button>
      ) : null}
    </TableCell>
  );
}

// ── Selection Cells ──────────────────────────────────────────────────

function SelectionHeaderCell<TData>({ table }: { table: ReactTable<TData> }) {
  return (
    <TableHead className="bg-muted sticky top-0 z-10 w-10 px-2">
      <div
        className="flex items-center justify-center"
        onClick={(e) => e.stopPropagation()}
      >
        <Checkbox
          checked={
            table.getIsAllPageRowsSelected() ||
            (table.getIsSomePageRowsSelected() && "indeterminate")
          }
          onCheckedChange={(value) => table.toggleAllPageRowsSelected(!!value)}
          aria-label="Select all"
        />
      </div>
    </TableHead>
  );
}

function SelectionRowCell<TData>({ row }: { row: Row<TData> }) {
  if (!row.getCanSelect()) return <TableCell className="w-10 px-2" />;
  return (
    <TableCell className="w-10 px-2">
      <div
        className="flex items-center justify-center"
        onClick={(e) => e.stopPropagation()}
      >
        <Checkbox
          checked={row.getIsSelected()}
          onCheckedChange={(value) => row.toggleSelected(!!value)}
          aria-label="Select row"
        />
      </div>
    </TableCell>
  );
}

// ── External drag lifecycle ──────────────────────────────────────────

/**
 * Bridges an ancestor-owned DndContext back to this table's drag lifecycle.
 *
 * When `externalDndContext` is set the table no longer receives onDragStart /
 * onDragEnd, yet it still has to collapse hierarchy children for the drag,
 * hold row slides while dnd-kit moves the rows, and show a drop in its new
 * order in the commit that ends the drag. `useDndMonitor` must be called from
 * a descendant of the DndContext, hence this render-nothing child rather than
 * a hook in SenseyTable itself. dnd-kit calls the monitor in the same batch
 * as the ancestor's own `onDragEnd`, so both updates land in one commit.
 */
function ExternalDragLifecycle({
  onDragStart,
  onDragEnd,
  onDragCancel,
}: {
  onDragStart: (event: DragStartEvent) => void;
  onDragEnd: (event: DragEndEvent) => void;
  onDragCancel: () => void;
}) {
  // `useDndMonitor`'s effect keys on the listener object, so a fresh literal
  // per render unsubscribes and resubscribes on every commit -- including the
  // `setExpanded({})` commit that happens mid-drag.
  const listener = useMemo(
    () => ({
      onDragStart,
      onDragEnd,
      onDragCancel,
    }),
    [onDragStart, onDragEnd, onDragCancel]
  );

  useDndMonitor(listener);
  return null;
}

// ── Sortable Row ─────────────────────────────────────────────────────

/**
 * A row that is its own drag handle: pressing anywhere on a top-level row and
 * moving 8px (or Space / Enter on the focused row) starts the drag, except on
 * the controls `ROW_DRAG_IGNORE_SELECTOR` names. A short press stays a click.
 * Child rows of a hierarchy never drag and are not focusable, except the
 * children of a static row, which drag like top-level rows.
 */
function SortableRow<TData>({
  row,
  canDrag,
  onClick,
  rowClassName,
  getId,
  hierarchyKey,
  enableSelection,
  columnSizing,
  explicitSizeIds,
  moveRef,
}: {
  row: Row<TData>;
  /** False for hierarchy children, except the children of a static row. */
  canDrag: boolean;
  onClick?: (row: TData) => void;
  rowClassName?: string | ((row: TData) => string);
  getId: (row: TData) => string;
  hierarchyKey?: string;
  enableSelection?: boolean;
  columnSizing: ColumnSizingState;
  explicitSizeIds: Set<string>;
  moveRef?: RowMoveRef;
}) {
  const isChild = !canDrag;
  const id = getId(row.original);
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id,
    disabled: isChild,
    // dnd-kit ignores reduced motion: without a transition it neither slides
    // rows aside during a drag nor animates their index change after a drop.
    transition: prefersReducedMotion() ? null : undefined,
  });

  // The row is the activator node too: dnd-kit's keyboard sensor only starts
  // a drag for keys pressed on the activator itself, so keys pressed on a
  // control inside the row stay with that control.
  const rowRef = useCallback(
    (node: HTMLTableRowElement | null) => {
      setNodeRef(node);
      setActivatorNodeRef(node);
      moveRef?.(node);
    },
    [setNodeRef, setActivatorNodeRef, moveRef]
  );

  const dragListeners = useMemo(
    () => (isChild ? undefined : guardRowDragListeners(listeners)),
    [isChild, listeners]
  );

  // dnd-kit's `role="button"` is dropped: the element stays a table row for
  // assistive technology, and the sortable description still applies.
  const dragAttributes = isChild ? undefined : { ...attributes, role: undefined };

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  const resolvedClassName =
    typeof rowClassName === "function" ? rowClassName(row.original) : rowClassName;

  const canExpand = hierarchyKey && row.getCanExpand();

  return (
    <TableRow
      ref={rowRef}
      style={style}
      {...dragAttributes}
      {...dragListeners}
      className={cn(
        !isChild && "group",
        onClick && "cursor-pointer",
        isDragging && "bg-korur-blue-accent dark:bg-korur-slate opacity-50",
        resolvedClassName
      )}
      onClick={() => {
        if (!hasTextSelection()) onClick?.(row.original);
      }}
    >
      {enableSelection && <SelectionRowCell row={row} />}
      {hierarchyKey && <HierarchyIndicatorCell row={row} canExpand={!!canExpand} />}
      {row.getVisibleCells().map((cell, cellIndex) => {
        const cellAlign = getColumnMeta(cell.column.columnDef.meta).align;
        const widthStyle = getCellWidthStyle(cell.column, columnSizing, explicitSizeIds);
        const indentStyle =
          cellIndex === 0 && hierarchyKey && row.depth > 0
            ? { paddingLeft: `${row.depth * 14}px` }
            : undefined;
        const cellStyle =
          widthStyle || indentStyle ? { ...widthStyle, ...indentStyle } : undefined;
        return (
          <TableCell
            key={cell.id}
            className={cn(
              cellAlign && ALIGN_CLASS[cellAlign],
              widthStyle && "overflow-hidden"
            )}
            style={cellStyle}
          >
            {flexRender(cell.column.columnDef.cell, cell.getContext())}
          </TableCell>
        );
      })}
    </TableRow>
  );
}

// ── Standard Row ─────────────────────────────────────────────────────

function StandardRow<TData>({
  row,
  onClick,
  rowClassName,
  hierarchyKey,
  enableSelection,
  columnSizing,
  explicitSizeIds,
  moveRef,
}: {
  row: Row<TData>;
  onClick?: (row: TData) => void;
  rowClassName?: string | ((row: TData) => string);
  hierarchyKey?: string;
  enableSelection?: boolean;
  columnSizing: ColumnSizingState;
  explicitSizeIds: Set<string>;
  moveRef?: RowMoveRef;
}) {
  const resolvedClassName =
    typeof rowClassName === "function" ? rowClassName(row.original) : rowClassName;

  const canExpand = hierarchyKey && row.getCanExpand();

  return (
    <TableRow
      ref={moveRef}
      className={cn(onClick && "cursor-pointer", resolvedClassName)}
      onClick={() => {
        if (!hasTextSelection()) onClick?.(row.original);
      }}
    >
      {enableSelection && <SelectionRowCell row={row} />}
      {hierarchyKey && <HierarchyIndicatorCell row={row} canExpand={!!canExpand} />}
      {row.getVisibleCells().map((cell, cellIndex) => {
        const cellAlign = getColumnMeta(cell.column.columnDef.meta).align;
        const widthStyle = getCellWidthStyle(cell.column, columnSizing, explicitSizeIds);
        const indentStyle =
          cellIndex === 0 && hierarchyKey && row.depth > 0
            ? { paddingLeft: `${row.depth * 24}px` }
            : undefined;
        const cellStyle =
          widthStyle || indentStyle ? { ...widthStyle, ...indentStyle } : undefined;
        return (
          <TableCell
            key={cell.id}
            className={cn(
              cellAlign && ALIGN_CLASS[cellAlign],
              widthStyle && "overflow-hidden"
            )}
            style={cellStyle}
          >
            {flexRender(cell.column.columnDef.cell, cell.getContext())}
          </TableCell>
        );
      })}
    </TableRow>
  );
}

// ── Static Row ───────────────────────────────────────────────────────

/**
 * A group header row: muted, never selected or dragged, and always expanded,
 * so it has no chevron. It is announced as a header of level 1 and can be
 * focused and opened with Enter, like the rows under it can be clicked.
 */
function StaticRow<TData>({
  row,
  onClick,
  rowClassName,
  hierarchyKey,
  enableSelection,
  renderStaticRow,
  columnSizing,
  explicitSizeIds,
  moveRef,
}: {
  row: Row<TData>;
  onClick?: (row: TData) => void;
  rowClassName?: string | ((row: TData) => string);
  hierarchyKey?: string;
  enableSelection?: boolean;
  renderStaticRow?: (row: TData) => ReactNode;
  columnSizing: ColumnSizingState;
  explicitSizeIds: Set<string>;
  moveRef?: RowMoveRef;
}) {
  const resolvedClassName =
    typeof rowClassName === "function" ? rowClassName(row.original) : rowClassName;
  const visibleCells = row.getVisibleCells();

  return (
    <TableRow
      ref={moveRef}
      aria-level={1}
      tabIndex={onClick ? 0 : undefined}
      className={cn(
        "bg-muted/40 text-muted-foreground hover:bg-muted/60",
        onClick && "cursor-pointer",
        resolvedClassName
      )}
      onClick={() => {
        if (!hasTextSelection()) onClick?.(row.original);
      }}
      onKeyDown={(event) => {
        if (event.key === "Enter" && event.target === event.currentTarget) {
          onClick?.(row.original);
        }
      }}
    >
      {enableSelection && <TableCell className="w-10 px-2" />}
      {hierarchyKey && <TableCell className="w-8 px-0" />}
      {renderStaticRow ? (
        <TableCell colSpan={visibleCells.length} className="py-1.5">
          {renderStaticRow(row.original)}
        </TableCell>
      ) : (
        visibleCells.map((cell) => {
          const widthStyle = getCellWidthStyle(cell.column, columnSizing, explicitSizeIds);
          return (
            <TableCell
              key={cell.id}
              className={cn(widthStyle && "overflow-hidden")}
              style={widthStyle}
            >
              {flexRender(cell.column.columnDef.cell, cell.getContext())}
            </TableCell>
          );
        })
      )}
    </TableRow>
  );
}

// ── Skeleton Row ─────────────────────────────────────────────────────

function SkeletonRow({ columnCount }: { columnCount: number }) {
  return (
    <TableRow className="h-12">
      {Array.from({ length: columnCount }).map((_, i) => (
        <TableCell key={i}>
          <Skeleton className="h-4 w-full max-w-[120px]" />
        </TableCell>
      ))}
    </TableRow>
  );
}

// ── Dragged Row Spacer ───────────────────────────────────────────────

/** The size of a table body and its table, measured while it had rows. */
interface HeldTableBox {
  bodyHeight: number;
  tableWidth: number;
}

/** An empty row that fills a table body of `height` pixels. */
function DraggedRowSpacer({ columnCount, height }: { columnCount: number; height: number }) {
  return (
    <tr aria-hidden="true">
      <td colSpan={columnCount} style={{ height }} />
    </tr>
  );
}

// ── Column Resize Handle ─────────────────────────────────────────────

/**
 * Custom resize handle that uses the th's rendered width as the start size,
 * not `column.getSize()`. This matters for flex columns: TanStack's default
 * handler captures `column.getSize()` (which is the default `150` for unsized
 * columns) at mousedown, causing flex columns to suddenly snap to 150px when
 * the user starts dragging. By measuring the th's actual rendered width via
 * `getBoundingClientRect()`, the resize starts smoothly from where the user
 * sees the column.
 */
function ColumnResizeHandle<TData>({
  header,
  onResize,
  onResetSize,
}: {
  header: Header<TData, unknown>;
  onResize: (columnId: string, size: number) => void;
  onResetSize: (columnId: string) => void;
}) {
  const [isResizing, setIsResizing] = useState(false);
  const minSize = header.column.columnDef.minSize ?? 20;
  const maxSize = header.column.columnDef.maxSize ?? Number.MAX_SAFE_INTEGER;

  const handlePointerDown = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      e.preventDefault();
      e.stopPropagation();

      const th = e.currentTarget.closest("th");
      if (!th) return;

      const startX = e.clientX;
      const startWidth = th.getBoundingClientRect().width;
      const columnId = header.column.id;

      setIsResizing(true);
      // While dragging, suppress text selection and column-resize cursor jumps
      document.body.style.cursor = "col-resize";
      document.body.style.userSelect = "none";

      const onPointerMove = (event: PointerEvent) => {
        const delta = event.clientX - startX;
        const newSize = Math.max(minSize, Math.min(maxSize, startWidth + delta));
        onResize(columnId, newSize);
      };

      const onPointerUp = () => {
        setIsResizing(false);
        document.body.style.cursor = "";
        document.body.style.userSelect = "";
        window.removeEventListener("pointermove", onPointerMove);
        window.removeEventListener("pointerup", onPointerUp);
        window.removeEventListener("pointercancel", onPointerUp);
      };

      window.addEventListener("pointermove", onPointerMove);
      window.addEventListener("pointerup", onPointerUp);
      window.addEventListener("pointercancel", onPointerUp);
    },
    [header.column.id, minSize, maxSize, onResize]
  );

  return (
    <div
      role="separator"
      aria-orientation="vertical"
      aria-label="Resize column"
      onPointerDown={handlePointerDown}
      onClick={(e) => e.stopPropagation()}
      onDoubleClick={(e) => {
        e.stopPropagation();
        onResetSize(header.column.id);
      }}
      className={cn(
        "absolute top-0 right-0 z-20 flex h-full w-1.5 cursor-col-resize touch-none items-center justify-center select-none",
        "after:bg-border hover:after:bg-korur-blue after:h-2/3 after:w-px after:transition-colors",
        isResizing && "after:bg-primary after:w-0.5"
      )}
    />
  );
}

// ── Sort Header Icon ─────────────────────────────────────────────────

function SortIcon({ direction }: { direction: false | "asc" | "desc" }) {
  if (direction === "asc") {
    return <ArrowUp className="ml-1 h-3.5 w-3.5" />;
  }
  if (direction === "desc") {
    return <ArrowDown className="ml-1 h-3.5 w-3.5" />;
  }
  return <ArrowUpDown className="ml-1 h-3.5 w-3.5 opacity-0 group-hover/sortable:opacity-50" />;
}

// ── Column Visibility Toggle ─────────────────────────────────────────

export interface ColumnToggleItem {
  id: string;
  header: string;
  /** Whether the column can be hidden (default: true) */
  canHide?: boolean;
}

export interface ColumnVisibilityToggleProps {
  columns: ColumnToggleItem[];
  columnVisibility: VisibilityState;
  onColumnVisibilityChange: (visibility: VisibilityState) => void;
  columnOrder?: string[];
  onColumnOrderChange?: (order: string[]) => void;
  label?: string;
  /** Save current column settings as user preference */
  onSave?: () => void;
  /** Reset column settings to defaults */
  onReset?: () => void;
  /** Whether the save button should be disabled (no changes to save) */
  saveDisabled?: boolean;
  /** Whether a save/reset operation is in progress */
  isSaving?: boolean;
  /** Whether saved preferences exist (controls reset button visibility) */
  hasSavedPreferences?: boolean;
  /** Tooltip text for the save button */
  saveTooltip?: string;
  /**
   * Save the current column settings as the default of every table. When set,
   * the footer offers a table override next to the default: Save is labelled
   * "Save for this table" and Reset becomes "Use default".
   */
  onSaveAsDefault?: () => void;
  /** Whether this table has its own layout instead of following the default */
  isOverride?: boolean;
  /**
   * Whether the user saved a default layout. Without one, a table that follows
   * the default shows the built-in layout, and the status line says so.
   */
  hasDefault?: boolean;
}

function SortableColumnItem({
  column,
  isVisible,
  onToggle,
}: {
  column: ColumnToggleItem;
  isVisible: boolean;
  onToggle: (checked: boolean) => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: column.id,
  });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={cn(
        "flex items-center gap-2 rounded px-1 py-0.5",
        isDragging && "bg-muted opacity-50"
      )}
    >
      <button
        type="button"
        className="hover:bg-muted flex h-5 w-5 shrink-0 cursor-grab items-center justify-center rounded"
        {...attributes}
        {...listeners}
      >
        <GripVertical className="text-muted-foreground h-3.5 w-3.5" />
      </button>
      <Checkbox
        id={`col-vis-${column.id}`}
        checked={isVisible}
        onCheckedChange={(checked) => onToggle(!!checked)}
      />
      <Label htmlFor={`col-vis-${column.id}`} className="cursor-pointer text-sm font-normal">
        {column.header}
      </Label>
    </div>
  );
}

export type ColumnVisibilityContentProps = Omit<ColumnVisibilityToggleProps, "label"> & {
  /**
   * Suppress the built-in "Columns" heading. For callers that already render
   * their own "Columns" label around this content -- a popover tab, say --
   * where the heading would just be a duplicate. Defaults to false so
   * existing call sites are unchanged.
   */
  hideLabel?: boolean;
  /**
   * Classes for the root. Give it a bounded height (`h-full`, `max-h-*`) to
   * keep the footer in view: only the column list scrolls, the heading and
   * the save and reset buttons stay put. Without a bound the content takes
   * its natural height.
   */
  className?: string;
};

/**
 * The column list on its own, with no trigger and no popover around it.
 *
 * Split out of ColumnVisibilityToggle so a caller that already owns a popover
 * can render the list inline instead of nesting a second popover inside the
 * first. ColumnVisibilityToggle still renders exactly this behind its own
 * trigger, so existing call sites are unchanged.
 */
export function ColumnVisibilityContent({
  columns,
  columnVisibility,
  onColumnVisibilityChange,
  columnOrder,
  onColumnOrderChange,
  onSave,
  onReset,
  saveDisabled,
  isSaving,
  hasSavedPreferences,
  saveTooltip,
  onSaveAsDefault,
  isOverride = false,
  hasDefault = false,
  hideLabel = false,
  className,
}: ColumnVisibilityContentProps) {
  const t = useTranslations();
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  // Determine display order: use columnOrder if provided, otherwise use columns array order
  const orderedColumns = useMemo(() => {
    const hideableColumns = columns.filter((c) => c.canHide !== false);
    if (!columnOrder || columnOrder.length === 0) return hideableColumns;
    // Order by columnOrder, then append any columns not in the order
    const ordered: ColumnToggleItem[] = [];
    const remaining = new Set(hideableColumns.map((c) => c.id));
    for (const id of columnOrder) {
      const col = hideableColumns.find((c) => c.id === id);
      if (col) {
        ordered.push(col);
        remaining.delete(id);
      }
    }
    for (const col of hideableColumns) {
      if (remaining.has(col.id)) ordered.push(col);
    }
    return ordered;
  }, [columns, columnOrder]);

  const sortableIds = useMemo(() => orderedColumns.map((c) => c.id), [orderedColumns]);

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const { active, over } = event;
      if (!over || active.id === over.id || !onColumnOrderChange) return;

      const oldIndex = sortableIds.indexOf(active.id as string);
      const newIndex = sortableIds.indexOf(over.id as string);
      if (oldIndex === -1 || newIndex === -1) return;

      const newOrder = [...sortableIds];
      newOrder.splice(oldIndex, 1);
      newOrder.splice(newIndex, 0, active.id as string);
      onColumnOrderChange(newOrder);
    },
    [sortableIds, onColumnOrderChange]
  );

  const handleToggle = useCallback(
    (columnId: string, checked: boolean) => {
      onColumnVisibilityChange({
        ...columnVisibility,
        [columnId]: checked,
      });
    },
    [columnVisibility, onColumnVisibilityChange]
  );

  return (
    <div className={cn("flex min-h-0 flex-col", className)}>
      {!hideLabel && (
        <div className="mb-2 flex shrink-0 items-center gap-2">
          <Label className="text-muted-foreground text-xs font-medium">{t("columns")}</Label>
        </div>
      )}
      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={sortableIds} strategy={verticalListSortingStrategy}>
          <div className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto">
            {orderedColumns.map((column) => {
              const isVisible = columnVisibility[column.id] !== false;
              return (
                <SortableColumnItem
                  key={column.id}
                  column={column}
                  isVisible={isVisible}
                  onToggle={(checked) => handleToggle(column.id, checked)}
                />
              );
            })}
          </div>
        </SortableContext>
      </DndContext>

      {/* Table override and default */}
      {onSaveAsDefault && (
        <div className="mt-2 flex shrink-0 flex-col gap-1 border-t pt-2">
          <p className="text-muted-foreground px-1 text-xs" aria-live="polite">
            {isOverride
              ? t("preferences.customLayout")
              : hasDefault
                ? t("preferences.usingDefault")
                : t("preferences.usingBuiltInLayout")}
          </p>
          {onSave && (
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="sm"
                  className="justify-start gap-1.5 text-xs"
                  onClick={onSave}
                  disabled={saveDisabled || isSaving}
                >
                  {isSaving ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  ) : (
                    <Save className="h-3.5 w-3.5" />
                  )}
                  {t("preferences.saveForTable")}
                </Button>
              </TooltipTrigger>
              {saveTooltip && <TooltipContent>{saveTooltip}</TooltipContent>}
            </Tooltip>
          )}
          <Button
            variant="ghost"
            size="sm"
            className="justify-start gap-1.5 text-xs"
            onClick={onSaveAsDefault}
            disabled={isSaving}
          >
            <Columns3 className="h-3.5 w-3.5" />
            {t("preferences.saveAsDefault")}
          </Button>
          {onReset && isOverride && (
            <Button
              variant="ghost"
              size="sm"
              className="justify-start gap-1.5 text-xs"
              onClick={onReset}
              disabled={isSaving}
            >
              <RotateCcw className="h-3.5 w-3.5" />
              {t("preferences.useDefault")}
            </Button>
          )}
        </div>
      )}

      {/* Save / Reset preferences */}
      {!onSaveAsDefault && (onSave || onReset) && (
        <div className="mt-2 flex shrink-0 items-center gap-1 border-t pt-2">
          {onSave && (
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="sm"
                  className="flex-1 gap-1.5 text-xs"
                  onClick={onSave}
                  disabled={saveDisabled || isSaving}
                >
                  {isSaving ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  ) : (
                    <Save className="h-3.5 w-3.5" />
                  )}
                  {t("save")}
                </Button>
              </TooltipTrigger>
              {saveTooltip && <TooltipContent>{saveTooltip}</TooltipContent>}
            </Tooltip>
          )}
          {onReset && hasSavedPreferences && (
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="sm"
                  className="flex-1 gap-1.5 text-xs"
                  onClick={onReset}
                  disabled={isSaving}
                >
                  <RotateCcw className="h-3.5 w-3.5" />
                  {t("reset")}
                </Button>
              </TooltipTrigger>
              <TooltipContent>{t("resetTooltip")}</TooltipContent>
            </Tooltip>
          )}
        </div>
      )}
    </div>
  );
}

export function ColumnVisibilityToggle({ label, ...contentProps }: ColumnVisibilityToggleProps) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="sm" className="border-border h-8 gap-2 border px-3">
          <Columns3 className="text-foreground h-4 w-4" />
          {label && <span>{label}</span>}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-56 p-3">
        <ColumnVisibilityContent {...contentProps} />
      </PopoverContent>
    </Popover>
  );
}

// ── Main Component ───────────────────────────────────────────────────

const DEFAULT_SKELETON_ROWS = 5;

export function SenseyTable<TData>({
  columns,
  data,
  pagination,
  draggable,
  sorting: externalSorting,
  onSortingChange,
  columnVisibility: externalColumnVisibility,
  onColumnVisibilityChange,
  columnOrder: externalColumnOrder,
  onColumnOrderChange,
  isLoading = false,
  emptyMessage,
  onRowClick,
  rowClassName,
  className,
  estimateRowHeight = 42,
  virtualized: virtualizedProp,
  hierarchyKey,
  defaultExpanded = false,
  rowSelection: externalRowSelection,
  onRowSelectionChange,
  getRowId: getRowIdProp,
  enableSelection = false,
  isRowStatic,
  renderStaticRow,
  enableColumnResizing = true,
  columnSizing: externalColumnSizing,
  onColumnSizingChange,
  tableRef,
}: SenseyTableProps<TData>) {
  // Expanding state for hierarchy
  const [expanded, setExpanded] = useState<ExpandedState>(defaultExpanded ? true : {});

  // Sorting state: use external if provided, otherwise manage internally
  const [internalSorting, setInternalSorting] = useState<SortingState>([]);
  const sorting = externalSorting ?? internalSorting;
  const handleSortingChange = useCallback(
    (updater: SortingState | ((prev: SortingState) => SortingState)) => {
      const newSorting = typeof updater === "function" ? updater(sorting) : updater;
      if (onSortingChange) {
        onSortingChange(newSorting);
      } else {
        setInternalSorting(newSorting);
      }
    },
    [sorting, onSortingChange]
  );

  // Column visibility state: use external if provided, otherwise manage internally
  const [internalColumnVisibility, setInternalColumnVisibility] = useState<VisibilityState>({});
  const columnVisibility = externalColumnVisibility ?? internalColumnVisibility;
  const handleColumnVisibilityChange = useCallback(
    (updater: VisibilityState | ((prev: VisibilityState) => VisibilityState)) => {
      const newVisibility = typeof updater === "function" ? updater(columnVisibility) : updater;
      if (onColumnVisibilityChange) {
        onColumnVisibilityChange(newVisibility);
      } else {
        setInternalColumnVisibility(newVisibility);
      }
    },
    [columnVisibility, onColumnVisibilityChange]
  );

  // Column order state: use external if provided, otherwise manage internally
  const [internalColumnOrder, setInternalColumnOrder] = useState<string[]>([]);
  const columnOrder = externalColumnOrder ?? internalColumnOrder;
  const handleColumnOrderChange = useCallback(
    (updater: string[] | ((prev: string[]) => string[])) => {
      const newOrder = typeof updater === "function" ? updater(columnOrder) : updater;
      if (onColumnOrderChange) {
        onColumnOrderChange(newOrder);
      } else {
        setInternalColumnOrder(newOrder);
      }
    },
    [columnOrder, onColumnOrderChange]
  );

  // Column sizing state: use external if provided, otherwise manage internally
  const [internalColumnSizing, setInternalColumnSizing] = useState<ColumnSizingState>({});
  const columnSizing = externalColumnSizing ?? internalColumnSizing;
  const handleColumnSizingChange = useCallback(
    (updater: ColumnSizingState | ((prev: ColumnSizingState) => ColumnSizingState)) => {
      const newSizing = typeof updater === "function" ? updater(columnSizing) : updater;
      if (onColumnSizingChange) {
        onColumnSizingChange(newSizing);
      } else {
        setInternalColumnSizing(newSizing);
      }
    },
    [columnSizing, onColumnSizingChange]
  );

  // Row selection state: use external if provided, otherwise manage internally
  const [internalRowSelection, setInternalRowSelection] = useState<RowSelectionState>({});
  const rowSelection = externalRowSelection ?? internalRowSelection;
  const handleRowSelectionChange = useCallback(
    (updater: RowSelectionState | ((prev: RowSelectionState) => RowSelectionState)) => {
      const newSelection = typeof updater === "function" ? updater(rowSelection) : updater;
      if (onRowSelectionChange) {
        onRowSelectionChange(newSelection);
      } else {
        setInternalRowSelection(newSelection);
      }
    },
    [rowSelection, onRowSelectionChange]
  );

  const getSubRows = useMemo(() => {
    if (!hierarchyKey) return undefined;
    return (row: TData) => {
      const children = row[hierarchyKey];
      return (Array.isArray(children) ? children : undefined) as TData[] | undefined;
    };
  }, [hierarchyKey]);

  // Set of column IDs whose `size` was explicitly set by the consumer. Used to
  // skip TanStack's merged-in `defaultColumn.size = 150` so untouched columns
  // stay content-sized.
  const explicitSizeIds = useMemo(() => getExplicitSizeIds(columns), [columns]);

  // Custom resize callbacks bound to our (controlled or internal) column
  // sizing state. The custom resize handle calls these instead of TanStack's
  // built-in handler so we can use the th's rendered width as the start size
  // (important for flex columns, which would otherwise snap to 150px).
  const handleColumnResize = useCallback(
    (columnId: string, size: number) => {
      handleColumnSizingChange((prev) => ({
        ...prev,
        [columnId]: size,
      }));
    },
    [handleColumnSizingChange]
  );

  const handleColumnResetSize = useCallback(
    (columnId: string) => {
      handleColumnSizingChange((prev) => {
        if (prev[columnId] === undefined) return prev;
        const next = { ...prev };
        delete next[columnId];
        return next;
      });
    },
    [handleColumnSizingChange]
  );

  // The order a drop shows until the consumer's next `data` arrives. A drop
  // must render in its new order in the commit that ends the drag: dnd-kit
  // measures the dropped row's node in that commit to aim the overlay, and
  // keeps the other rows in their projected slots only while the order
  // matches. The consumer's optimistic patch reaches React a notify tick
  // later, so without this the rows show the old order for a frame and jump.
  // It is keyed on the identity of `data`: any new `data` (the patch, a
  // rollback, a refetch) replaces it without an effect.
  const [dropOrder, setDropOrder] = useState<{ base: TData[]; data: TData[] } | null>(null);
  if (dropOrder !== null && dropOrder.base !== data) {
    // Forget a superseded drop during render, so a rollback that hands back
    // the very array the drop was made on shows that array's own order.
    setDropOrder(null);
  }
  const isDropOrderShown = dropOrder !== null && dropOrder.base === data;
  const tableData = dropOrder !== null && dropOrder.base === data ? dropOrder.data : data;

  // Static rows are always expanded, during a drag too: their children are
  // the draggable rows, so collapsing them would hide what is being sorted.
  const staticRowIds = useMemo(() => {
    if (!isRowStatic) return [];
    const ids: string[] = [];
    tableData.forEach((row, index) => {
      if (isRowStatic(row)) ids.push(getRowIdProp ? getRowIdProp(row) : String(index));
    });
    return ids;
  }, [isRowStatic, tableData, getRowIdProp]);
  const effectiveExpanded = useMemo<ExpandedState>(() => {
    if (expanded === true || staticRowIds.length === 0) return expanded;
    const merged: Record<string, boolean> = { ...expanded };
    for (const id of staticRowIds) merged[id] = true;
    return merged;
  }, [expanded, staticRowIds]);

  const isStaticRow = useCallback(
    (row: Row<TData>) => row.depth === 0 && !!isRowStatic?.(row.original),
    [isRowStatic]
  );
  // A top-level row, or a child of a static row, is a row of its own for
  // selection and drag; every other hierarchy child moves with its parent.
  const isOwnRow = useCallback(
    (row: Row<TData>) => {
      if (isStaticRow(row)) return false;
      if (row.depth === 0) return true;
      const parent = row.getParentRow();
      return row.depth === 1 && parent !== undefined && isStaticRow(parent);
    },
    [isStaticRow]
  );

  const table = useReactTable({
    data: tableData,
    columns,
    state: {
      sorting,
      columnVisibility,
      rowSelection,
      columnSizing,
      ...(columnOrder.length > 0 ? { columnOrder } : {}),
      ...(hierarchyKey ? { expanded: effectiveExpanded } : {}),
    },
    enableColumnResizing,
    columnResizeMode: "onChange",
    onSortingChange: handleSortingChange,
    onColumnVisibilityChange: handleColumnVisibilityChange,
    onColumnOrderChange: handleColumnOrderChange,
    onColumnSizingChange: handleColumnSizingChange,
    onRowSelectionChange: handleRowSelectionChange,
    onExpandedChange: hierarchyKey ? setExpanded : undefined,
    enableRowSelection: (row) => {
      if (!enableSelection) return false;
      // Same rule as drag and drop: sub/child rows and static rows are excluded.
      if (hierarchyKey) return isOwnRow(row);
      return !isRowStatic?.(row.original);
    },
    getRowId: getRowIdProp,
    getSubRows,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: onSortingChange ? undefined : getSortedRowModel(),
    getExpandedRowModel: hierarchyKey ? getExpandedRowModel() : undefined,
    manualSorting: !!onSortingChange,
  });

  // Expose the table instance to consumers via ref
  useImperativeHandle(tableRef, () => table, [table]);

  const { rows } = table.getRowModel();

  // Virtualization
  const parentRef = useRef<HTMLDivElement>(null);
  const shouldVirtualize = virtualizedProp ?? data.length > 50;

  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => estimateRowHeight,
    enabled: shouldVirtualize,
    overscan: 10,
  });

  // DnD
  const getId = useCallback(
    (row: TData): string => {
      if (draggable?.getId) return draggable.getId(row);
      return (row as Record<string, unknown>).id as string;
    },
    [draggable]
  );

  const [activeRow, setActiveRow] = useState<Row<TData> | null>(null);
  // A drag in an ancestor-owned DndContext, reported by the monitor. It may be
  // a drag of another table's row; this table's rows still move with it.
  const [isExternalDragRunning, setIsExternalDragRunning] = useState(false);
  // Whether the running ancestor drag started on one of this table's rows,
  // which the ancestor may show in another table until the drag ends.
  const [isOwnRowDragged, setIsOwnRowDragged] = useState(false);
  const tableElementRef = useRef<HTMLTableElement>(null);
  const tableBodyRef = useRef<HTMLTableSectionElement>(null);
  const [heldBox, setHeldBox] = useState<HeldTableBox | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 8 },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  // Save expanded state before drag so we can restore it after
  const expandedBeforeDrag = useRef<ExpandedState>({});

  // Collapsing hierarchy children for the duration of a drag is not cosmetic:
  // child rows render a disabled sortable, so the band they occupy resolves to
  // whatever droppable sits behind them rather than to a sibling row. Leaving
  // them expanded turns drops in that band into no-ops.
  const collapseForDrag = useCallback(() => {
    if (!hierarchyKey) return;
    expandedBeforeDrag.current = expanded;
    setExpanded({});
  }, [hierarchyKey, expanded, setExpanded]);

  const restoreAfterDrag = useCallback(() => {
    if (!hierarchyKey) return;
    setExpanded(expandedBeforeDrag.current);
  }, [hierarchyKey, setExpanded]);

  /**
   * Shows a drop of top-level row `activeId` onto top-level row `overId` of
   * this table in its new order, with the same splice the consumer applies.
   * Nothing happens for any other drop, or while a column sort decides the
   * order, because the drop then does not change what the table shows.
   */
  const showDropOrder = useCallback(
    (activeId: UniqueIdentifier, overId: UniqueIdentifier | undefined) => {
      if (overId === undefined || activeId === overId || sorting.length > 0) return;
      const from = tableData.findIndex((row) => getId(row) === activeId);
      const to = tableData.findIndex((row) => getId(row) === overId);
      if (from === -1 || to === -1) return;
      setDropOrder({ base: data, data: arrayMove(tableData, from, to) });
    },
    [sorting, tableData, data, getId]
  );

  const handleDragStart = useCallback(
    (event: DragStartEvent) => {
      const row = rows.find((r) => getId(r.original) === event.active.id);
      setActiveRow(row ?? null);
      collapseForDrag();
    },
    [rows, getId, collapseForDrag]
  );

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const { active, over } = event;
      setActiveRow(null);
      restoreAfterDrag();

      if (!over || active.id === over.id) return;
      if (!draggable?.onReorder) return;

      const activeRow = rows.find((r) => getId(r.original) === active.id);
      const overRow = rows.find((r) => getId(r.original) === over.id);
      if (!activeRow || !overRow) return;

      showDropOrder(active.id, over.id);
      const newIndex = rows.indexOf(overRow);
      draggable.onReorder(activeRow.original, overRow.original, newIndex);
    },
    [rows, draggable, getId, restoreAfterDrag, showDropOrder]
  );

  // Escape mid-drag fires onDragCancel, not onDragEnd. It still has to clear
  // the overlay row and restore the hierarchy, but never reorders: the event
  // of a cancel still names the row the pointer was over.
  const handleDragCancel = useCallback(() => {
    setActiveRow(null);
    restoreAfterDrag();
  }, [restoreAfterDrag]);

  const sortableItems = useMemo(
    () => rows.filter(isOwnRow).map((r) => getId(r.original)),
    [rows, getId, isOwnRow]
  );

  const handleExternalDragStart = useCallback(
    (event: DragStartEvent) => {
      setIsExternalDragRunning(true);
      setIsOwnRowDragged(sortableItems.includes(String(event.active.id)));
      collapseForDrag();
    },
    [sortableItems, collapseForDrag]
  );

  const handleExternalDragCancel = useCallback(() => {
    setIsExternalDragRunning(false);
    setIsOwnRowDragged(false);
    setHeldBox(null);
    restoreAfterDrag();
  }, [restoreAfterDrag]);

  // While an ancestor's drag shows this table's only row in another table,
  // the table keeps the box it had with the row: the body's height and the
  // table's width, which keeps a horizontal scrollbar it had. Without it the
  // table would swap in its taller empty state, or lose that scrollbar, in
  // the middle of the drag, and everything below it would move under the
  // pointer, so the other table's rows jump and its sortable list moves the
  // row away from where it first appeared. The box is measured on every
  // commit in which the dragged row is still here, after the drag collapsed
  // the hierarchy.
  useLayoutEffect(() => {
    if (!isOwnRowDragged || rows.length === 0) return;
    const table = tableElementRef.current;
    const body = tableBodyRef.current;
    if (!table || !body) return;
    const bodyHeight = body.getBoundingClientRect().height;
    const tableWidth = table.getBoundingClientRect().width;
    setHeldBox((previous) =>
      previous?.bodyHeight === bodyHeight && previous.tableWidth === tableWidth
        ? previous
        : { bodyHeight, tableWidth }
    );
  }, [isOwnRowDragged, rows]);

  const heldEmptyBox = !isLoading && rows.length === 0 && isOwnRowDragged ? heldBox : null;

  const handleExternalDragEnd = useCallback(
    (event: DragEndEvent) => {
      handleExternalDragCancel();
      showDropOrder(event.active.id, event.over?.id);
    },
    [handleExternalDragCancel, showDropOrder]
  );

  const animateRowMoves = draggable?.animateRowMoves === true;
  const rowMoveIds = useMemo(
    () => (animateRowMoves && !isLoading ? rows.map((r) => getId(r.original)) : []),
    [animateRowMoves, isLoading, rows, getId]
  );
  const rowMoveRef = useRowMoveAnimation({
    enabled: animateRowMoves,
    rowIds: rowMoveIds,
    skip: activeRow !== null || isExternalDragRunning || isDropOrderShown,
  });
  const moveRefFor = (row: Row<TData>): RowMoveRef | undefined =>
    animateRowMoves ? rowMoveRef(getId(row.original)) : undefined;

  // Pagination data for SenseyPagination
  const paginationData: PaginationData | null = pagination
    ? {
        totalElements: pagination.totalElements,
        totalPages: pagination.totalPages,
        currentPage: pagination.page,
        pageSize: pagination.pageSize,
        first: pagination.page <= 1,
        last: pagination.page >= pagination.totalPages,
        empty: pagination.totalElements === 0,
      }
    : null;

  // Column count including the selection and hierarchy indicator columns
  const totalColumnCount = columns.length + (enableSelection ? 1 : 0) + (hierarchyKey ? 1 : 0);

  // ── Render helpers ─────────────────────────────────────────────────

  const renderHeaderGroups = () =>
    table.getHeaderGroups().map((headerGroup) => (
      <TableRow key={headerGroup.id}>
        {enableSelection && <SelectionHeaderCell table={table} />}
        {hierarchyKey && <TableHead className="bg-muted sticky top-0 z-10 w-8 px-0" />}
        {headerGroup.headers.map((header) => {
          const canSort = header.column.getCanSort();
          const sorted = header.column.getIsSorted();

          const meta = getColumnMeta(header.column.columnDef.meta);
          // Width strategy:
          //  - non-flex column whose consumer set explicit `size` → that size
          //  - any column the user has resized → resized size
          //  - flex column without resize → fills remaining space (`w-full`)
          //  - otherwise → content-sized via auto-layout
          //
          // We check `explicitSizeIds` (built from the original columns prop)
          // instead of `columnDef.size` because TanStack merges its default
          // `size: 150` into every column's `columnDef`.
          const hasResizedSize = columnSizing[header.column.id] !== undefined;
          const hasExplicitSize = !meta.flex && explicitSizeIds.has(header.column.id);
          const isFixedWidth = hasExplicitSize || hasResizedSize;
          const colWidth = isFixedWidth ? header.getSize() : undefined;

          const canResize = enableColumnResizing && header.column.getCanResize();

          return (
            <TableHead
              key={header.id}
              style={
                colWidth
                  ? { width: colWidth, maxWidth: colWidth, minWidth: colWidth }
                  : undefined
              }
              className={cn(
                "bg-muted sticky top-0 z-10",
                meta.flex && !colWidth && "w-full",
                meta.align && ALIGN_CLASS[meta.align],
                canSort && "group/sortable cursor-pointer select-none",
                sorted && "text-primary",
                colWidth && "overflow-hidden"
              )}
              onClick={canSort ? header.column.getToggleSortingHandler() : undefined}
            >
              <div className="flex items-center">
                <span className="text-xs font-semibold uppercase">
                  {header.isPlaceholder
                    ? null
                    : flexRender(header.column.columnDef.header, header.getContext())}
                </span>
                {canSort && <SortIcon direction={sorted} />}
              </div>
              {canResize && (
                <ColumnResizeHandle
                  header={header}
                  onResize={handleColumnResize}
                  onResetSize={handleColumnResetSize}
                />
              )}
            </TableHead>
          );
        })}
      </TableRow>
    ));

  const renderStaticRowElement = (row: Row<TData>) => (
    <StaticRow
      key={row.id}
      row={row}
      onClick={onRowClick}
      rowClassName={rowClassName}
      hierarchyKey={hierarchyKey}
      enableSelection={enableSelection}
      renderStaticRow={renderStaticRow}
      columnSizing={columnSizing}
      explicitSizeIds={explicitSizeIds}
      moveRef={moveRefFor(row)}
    />
  );

  const renderRows = () => {
    if (isLoading) {
      return Array.from({ length: DEFAULT_SKELETON_ROWS }).map((_, i) => (
        <SkeletonRow key={i} columnCount={totalColumnCount} />
      ));
    }

    if (rows.length === 0) {
      if (heldEmptyBox) {
        return <DraggedRowSpacer columnCount={totalColumnCount} height={heldEmptyBox.bodyHeight} />;
      }
      return (
        <TableRow>
          <TableCell colSpan={totalColumnCount} className="h-32 text-center">
            <div className="flex flex-col items-center justify-center py-8">
              {typeof emptyMessage === "string" || !emptyMessage ? (
                <p className="text-muted-foreground text-sm">{emptyMessage ?? "No data"}</p>
              ) : (
                emptyMessage
              )}
            </div>
          </TableCell>
        </TableRow>
      );
    }

    if (shouldVirtualize) {
      return renderVirtualizedRows();
    }

    if (draggable?.enabled) {
      return rows.map((row) =>
        isStaticRow(row) ? (
          renderStaticRowElement(row)
        ) : (
        <SortableRow
          key={row.id}
          row={row}
          canDrag={isOwnRow(row)}
          onClick={onRowClick}
          rowClassName={rowClassName}
          getId={getId}
          hierarchyKey={hierarchyKey}
          enableSelection={enableSelection}
          columnSizing={columnSizing}
          explicitSizeIds={explicitSizeIds}
          moveRef={moveRefFor(row)}
        />
        )
      );
    }

    return rows.map((row) =>
      isStaticRow(row) ? (
        renderStaticRowElement(row)
      ) : (
      <StandardRow
        key={row.id}
        row={row}
        onClick={onRowClick}
        rowClassName={rowClassName}
        hierarchyKey={hierarchyKey}
        enableSelection={enableSelection}
        columnSizing={columnSizing}
        explicitSizeIds={explicitSizeIds}
        moveRef={moveRefFor(row)}
      />
      )
    );
  };

  const renderVirtualizedRows = () => {
    const virtualRows = virtualizer.getVirtualItems();
    const totalSize = virtualizer.getTotalSize();
    const firstVirtualRow = virtualRows[0];
    const lastVirtualRow = virtualRows[virtualRows.length - 1];

    return (
      <>
        {firstVirtualRow && firstVirtualRow.start > 0 && (
          <tr>
            <td colSpan={totalColumnCount} style={{ height: firstVirtualRow.start }} />
          </tr>
        )}
        {virtualRows.map((virtualRow) => {
          const row = rows[virtualRow.index];
          if (!row) return null;
          if (isStaticRow(row)) return renderStaticRowElement(row);

          if (draggable?.enabled) {
            return (
              <SortableRow
                key={row.id}
                row={row}
                canDrag={isOwnRow(row)}
                onClick={onRowClick}
                rowClassName={rowClassName}
                getId={getId}
                hierarchyKey={hierarchyKey}
                enableSelection={enableSelection}
                columnSizing={columnSizing}
                explicitSizeIds={explicitSizeIds}
                moveRef={moveRefFor(row)}
              />
            );
          }

          return (
            <StandardRow
              key={row.id}
              row={row}
              onClick={onRowClick}
              rowClassName={rowClassName}
              hierarchyKey={hierarchyKey}
              enableSelection={enableSelection}
              columnSizing={columnSizing}
              explicitSizeIds={explicitSizeIds}
              moveRef={moveRefFor(row)}
            />
          );
        })}
        {lastVirtualRow && (
          <tr>
            <td
              colSpan={totalColumnCount}
              style={{
                height: totalSize - (lastVirtualRow.start + lastVirtualRow.size),
              }}
            />
          </tr>
        )}
      </>
    );
  };

  // ── Main render ────────────────────────────────────────────────────
  const tableContent = (
    <div
      ref={shouldVirtualize ? parentRef : undefined}
      className={cn(
        "overflow-auto rounded-lg border",
        // The virtualizer measures THIS element as its scroll viewport, so it
        // needs a bound on its height -- but a MAXIMUM, never a fixed size.
        //
        // `min-h-0` alone is that maximum. As a flex item this div is
        // `flex: 0 1 auto`, so it is content-sized while it fits (the border
        // hugs the rows; no stray bottom border floating under a short list)
        // and flex-shrink clamps it to the leftover space once the rows
        // overflow -- which is only allowed because `min-h-0` lifts the
        // default `min-height: auto` floor. That clamp is what turns
        // `overflow-auto` into a real scrollport on both axes (the inner
        // table carries `min-w-[800px]`) and gives the `sticky top-0` header
        // row something to stick to.
        //
        // NOT `flex-1`: that sets `flex-grow: 1`, which is what stretched a
        // one-row table to the full panel height and left a bottom border
        // hundreds of pixels under the only row. And no `max-h-full` either:
        // a percentage max-height resolves against the WHOLE parent content
        // box, so where the table is the second flex child (all-tickets-list,
        // after TicketStatusSummary) it is looser than the shrink clamp and
        // never binds -- measured 679px either way. Redundant, and it would
        // read as if the siblings were being accounted for twice.
        //
        // Deliberately NOT a measured pixel height: this used to be a max-h
        // arbitrary value built by interpolating a ref into a template
        // literal, which emits no CSS at all because Tailwind only scans
        // literal source text. `min-h-0` is a real utility, and it is inert
        // outside a flex container, so non-flex call sites are unaffected.
        shouldVirtualize && "min-h-0",
        className
      )}
    >
      <Table
        ref={tableElementRef}
        className="min-w-[800px]"
        style={heldEmptyBox ? { minWidth: heldEmptyBox.tableWidth } : undefined}
      >
        <TableHeader>{renderHeaderGroups()}</TableHeader>
        <TableBody ref={tableBodyRef}>{renderRows()}</TableBody>
      </Table>

      {pagination && paginationData && (
        <div className="border-t px-4 py-2">
          <SenseyPagination
            pagination={paginationData}
            page={pagination.page}
            amount={pagination.pageSize}
            onPageChange={pagination.onPageChange}
            onAmountChange={
              pagination.onPageSizeChange ??
              (() => {
                /* noop */
              })
            }
            pageSizeOptions={pagination.pageSizeOptions}
          />
        </div>
      )}
    </div>
  );

  // An ancestor owns the DndContext (cross-container drag). Register the rows
  // as sortables and let the ancestor resolve the drop, but keep the table's
  // own drag lifecycle -- the expand state, the drop order and the row slides
  // live here, so the ancestor cannot drive them.
  if (draggable?.enabled && draggable.externalDndContext) {
    return (
      <SortableContext items={sortableItems} strategy={verticalListSortingStrategy}>
        <ExternalDragLifecycle
          onDragStart={handleExternalDragStart}
          onDragEnd={handleExternalDragEnd}
          onDragCancel={handleExternalDragCancel}
        />
        {tableContent}
      </SortableContext>
    );
  }

  if (draggable?.enabled) {
    return (
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragStart={handleDragStart}
        onDragEnd={handleDragEnd}
        onDragCancel={handleDragCancel}
      >
        <SortableContext items={sortableItems} strategy={verticalListSortingStrategy}>
          {tableContent}
        </SortableContext>

        {/*
          `handleDragEnd` renders the dropped order in the same commit that
          clears `activeRow`, so the row's node is already in its new slot
          when dnd-kit measures it: the overlay glides there, never back to
          where the row came from.
        */}
        <DragOverlay dropAnimation={ROW_DROP_ANIMATION}>
          {activeRow ? (
            <table className="w-full border-separate border-spacing-0">
              <tbody>
                <tr className="bg-card rounded-lg border opacity-80 shadow-lg">
                  {enableSelection && <td className="w-10 px-2" />}
                  {hierarchyKey && <td className="w-8 px-0" />}
                  {activeRow.getVisibleCells().map((cell) => (
                    <td key={cell.id} className="px-4 py-3">
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </td>
                  ))}
                </tr>
              </tbody>
            </table>
          ) : null}
        </DragOverlay>
      </DndContext>
    );
  }

  return tableContent;
}

// ── Re-exported types for consumer convenience ──────────────────────
export type { VisibilityState, RowSelectionState } from "@tanstack/react-table";

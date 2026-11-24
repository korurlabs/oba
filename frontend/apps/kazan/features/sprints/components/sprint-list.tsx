/**
 * SprintList Component
 *
 * Table view for sprint tickets with drag-and-drop reordering,
 * built on SenseyTable. There is no pagination: the sprint query
 * fetches the whole filtered set and the table virtualizes it.
 */

"use client";

import { useCallback, useMemo } from "react";
import { useTranslations } from "next-intl";
import type { VisibilityState } from "@tanstack/react-table";
import { type TicketListResponse } from "@/features/tickets/schemas";
import { useSearchPathFilter } from "@/features/tickets/lib/search-match";
import type { SubTicketMoveChoice } from "@/features/tickets/components/sub-ticket-choice";
import { useUpdateTicketOrder } from "@/features/tickets/hooks/use-tickets";
import {
  SenseyTable,
  type SenseyTableDraggableConfig,
  type RowSelectionState,
} from "@oba/shared/components/sensey-table";
import { cn } from "@oba/ui/lib/utils";
import { useTicketColumns } from "@/features/tickets/hooks/use-ticket-columns";
import {
  groupListRows,
  isGroupHeader,
  keepSprintSubTickets,
  type SprintListTicket,
} from "@/lib/utils/ticket-groups";
import { TicketGroupHeaderRowContent } from "./ticket-group-header-row";

// ── Types ────────────────────────────────────────────────────────────

interface SprintListProps {
  boardId: string;
  tickets: TicketListResponse[];
  onTicketClick: (ticket: TicketListResponse) => void;
  onCreateTicket: () => void;
  isLoading?: boolean;
  columnVisibility: VisibilityState;
  onColumnVisibilityChange: (visibility: VisibilityState) => void;
  columnOrder: string[];
  onColumnOrderChange: (order: string[]) => void;
  // Row actions
  onMoveToSprint?: (ticketId: string, sprintId: string, subTickets?: SubTicketMoveChoice) => void;
  onMoveToBacklog?: (ticketId: string, subTickets?: SubTicketMoveChoice) => void;
  onDeleteTicket?: (ticketId: string) => void;
  onEditTicket?: (ticket: TicketListResponse) => void;
  onCopyLink?: (ticket: TicketListResponse) => void;
  sprints?: Array<{ id: string; name: string; status: string }>;
  currentSprintId?: string;
  onBulkAction?: () => void;
  // Row selection
  rowSelection?: RowSelectionState;
  onRowSelectionChange?: (selection: RowSelectionState) => void;
  enableSelection?: boolean;
  /**
   * Whether any ticket filter is currently narrowing the query, used only to
   * pick the empty message: an empty result under a filter is "nothing
   * matched", not "the sprint is empty". Passed in rather than read from a
   * filter hook here because the two pages that render this list own their
   * own filter state.
   */
  hasActiveFilters?: boolean;
  /**
   * Defer drag-and-drop to an ancestor DndContext instead of the table's own.
   * Set by the All tab, where a single context spans the sprint and backlog
   * sections so rows can be dragged between them; the ancestor then owns the
   * drop resolution and `onReorder` below is never reached.
   */
  externalDndContext?: boolean;
  /**
   * Whether the user may reorder rows by dragging them. Viewers get rows
   * that do not drag. Defaults to true.
   */
  canReorder?: boolean;
  /**
   * Rings the table while a row from another table hovers its section, so
   * the user sees where the row will land. Only the table lights up, never
   * the section's header or selection bar.
   */
  isDropTarget?: boolean;
  /** Row to tint, the one that just arrived from another table. */
  highlightedRowId?: string | null;
  /**
   * The active search, if any: the list then shows only the sub-tickets whose
   * title or key matches it, or that lead to one that does, expanded. A ticket
   * can be listed because one of its sub-tickets matched.
   */
  search?: string | null;
}

// ── Main Component ───────────────────────────────────────────────────

export function SprintList({
  boardId,
  tickets,
  onTicketClick,
  onCreateTicket,
  isLoading = false,
  columnVisibility,
  onColumnVisibilityChange,
  columnOrder,
  onColumnOrderChange,
  onMoveToSprint,
  onMoveToBacklog,
  onDeleteTicket,
  onEditTicket,
  onCopyLink,
  sprints,
  currentSprintId,
  onBulkAction,
  rowSelection,
  onRowSelectionChange,
  enableSelection = false,
  hasActiveFilters = false,
  externalDndContext = false,
  canReorder = true,
  isDropTarget = false,
  highlightedRowId = null,
  search,
}: SprintListProps) {
  const subRowFilter = useSearchPathFilter<TicketListResponse>(search);
  const t = useTranslations();

  const updateOrderMutation = useUpdateTicketOrder();
  const columns = useTicketColumns({
    boardId,
    onMoveToSprint,
    onMoveToBacklog,
    onDelete: onDeleteTicket,
    onEdit: onEditTicket,
    onCopyLink,
    sprints,
    currentSprintId,
    onBulkAction,
  });

  // A sub-ticket planned here without its parent is shown under a header row
  // for that parent. The caption strips count through the same module, so
  // they never count the header. The table nests only a ticket's sub-tickets
  // of this sprint (`sprintSubTickets`); the others belong to the list they
  // are planned in. `subTickets` stays complete for the progress column and
  // the move dialogs of the row actions.
  const rows = useMemo(
    () => groupListRows(currentSprintId ? keepSprintSubTickets(tickets, currentSprintId) : tickets),
    [tickets, currentSprintId]
  );
  const renderStaticRow = useCallback(
    (row: TicketListResponse) =>
      isGroupHeader(row) ? (
        <TicketGroupHeaderRowContent header={row} currentSprintId={currentSprintId} />
      ) : null,
    [currentSprintId]
  );

  // ── Drag-and-drop config ───────────────────────────────────────────

  const draggableConfig = useMemo<SenseyTableDraggableConfig<TicketListResponse>>(
    () => ({
      enabled: canReorder,
      externalDndContext,
      animateRowMoves: true,
      onReorder: (activeTicket, overTicket) => {
        const ticketBoardId = activeTicket.board?.id ?? boardId;
        updateOrderMutation.mutate({
          boardId: ticketBoardId,
          ticketId: activeTicket.id,
          targetTicketId: overTicket.id,
          data: { order: overTicket.displayOrder },
        });
      },
      getId: (row: TicketListResponse) => row.id,
    }),
    [boardId, updateOrderMutation, externalDndContext, canReorder]
  );

  const rowClassName = useCallback(
    (ticket: TicketListResponse) => (ticket.id === highlightedRowId ? "bg-primary/10" : ""),
    [highlightedRowId]
  );

  // ── Empty message ──────────────────────────────────────────────────

  // SenseyTable already centres and pads whatever it is given here, so this
  // is the bare content -- wrapping it again would double the padding.
  const emptyMessage = hasActiveFilters ? (
    <p className="text-muted-foreground text-sm">{t("filters.noResults")}</p>
  ) : (
    <>
      <p className="text-muted-foreground text-sm">{t("sprint.noTicketsInSprint")}</p>
      <p className="text-muted-foreground mt-1 text-xs">{t("sprint.noTicketsDescription")}</p>
    </>
  );

  // ── Render ─────────────────────────────────────────────────────────

  // The drop ring is a box-shadow 2px outside the table's box, and the
  // wrapper's `overflow-hidden` would cut its top edge, where the box meets
  // the wrapper. `pt-0.5` gives the ring that room inside the clip; `-mt-0.5`
  // takes it back outside, so the table does not move down on any page.
  return (
    <div className="-mt-0.5 flex h-full flex-col overflow-hidden px-6 pt-0.5 pb-6">
      <SenseyTable<SprintListTicket>
        columns={columns}
        data={rows}
        isRowStatic={isGroupHeader}
        renderStaticRow={renderStaticRow}
        draggable={draggableConfig}
        isLoading={isLoading}
        emptyMessage={emptyMessage}
        hierarchyKey={currentSprintId ? "sprintSubTickets" : "subTickets"}
        expandAll={!!search}
        subRowFilter={subRowFilter}
        onRowClick={onTicketClick}
        virtualized={true}
        columnVisibility={columnVisibility}
        onColumnVisibilityChange={onColumnVisibilityChange}
        columnOrder={columnOrder}
        onColumnOrderChange={onColumnOrderChange}
        enableSelection={enableSelection}
        rowSelection={rowSelection}
        onRowSelectionChange={onRowSelectionChange}
        getRowId={(row) => row.id}
        rowClassName={rowClassName}
        className={cn(
          "transition-[box-shadow,background-color] duration-150",
          isDropTarget && "ring-primary/60 bg-primary/5 ring-2"
        )}
      />
    </div>
  );
}

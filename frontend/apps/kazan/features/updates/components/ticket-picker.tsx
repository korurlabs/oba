"use client";

import { useCallback, useId, useMemo } from "react";
import { useTranslations } from "next-intl";
import { AlertCircle } from "lucide-react";
import {
  SenseyTable,
  type RowSelectionState,
} from "@oba/shared/components/sensey-table";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { Button } from "@/components/ui/button";
import { TicketFilterPanel } from "@/features/tickets/components/ticket-filter-panel";
import type { TicketListResponse } from "@/features/tickets/schemas";
import { isOnSearchPath } from "@/features/tickets/lib/search-match";
import { useTicketPickerColumns } from "../hooks/use-ticket-picker-columns";
import type { useTicketPicker } from "../hooks/use-ticket-picker";

interface TicketPickerProps {
  /**
   * The picker's list and filters. The screen owns them, so the
   * selected-tickets card reads the same list as the table.
   */
  picker: ReturnType<typeof useTicketPicker>;
  /** IDs of the tickets on the selected-tickets card, in card order. */
  selectedIds: string[];
  /** Tickets ticked in the table, and IDs unticked there. */
  onSelectionChange: (added: TicketListResponse[], removedIds: string[]) => void;
  /** The section's heading. */
  title: string;
  /** The line under the heading: what the ticked tickets are for. */
  description: string;
  /** Tooltip of the filter panel's save button: where the filters are saved for. */
  saveFiltersTooltip: string;
}

/**
 * The all-tickets list with row selection, kept in step with the
 * selected-tickets card: ticking a row adds the ticket to the card and
 * unticking removes it. The card is the source of truth, so a selected
 * ticket the current filters hide stays selected. Sub-tickets are listed
 * under their root ticket and are picked on their own; while a search is
 * active every root is expanded and shows only the sub-tickets that match or
 * lead to one that does, because a root can be listed only because one of its
 * sub-tickets matched.
 */
export function TicketPicker({
  picker,
  selectedIds,
  onSelectionChange,
  title,
  description,
  saveFiltersTooltip,
}: TicketPickerProps) {
  const t = useTranslations();
  const columns = useTicketPickerColumns();
  const titleId = useId();

  const rowSelection = useMemo<RowSelectionState>(
    () => Object.fromEntries(selectedIds.map((id) => [id, true])),
    [selectedIds],
  );

  // Row selection keeps the IDs of rows the table does not show, so an ID
  // missing from the next selection was unticked in the table.
  // Every ticket the table can show: the roots and, nested at any depth, their
  // sub-tickets, so a ticked sub-row is found as well as a ticked root.
  const listedTickets = useMemo((): TicketListResponse[] => {
    const all: TicketListResponse[] = [];
    const collect = (tickets: readonly TicketListResponse[]) => {
      for (const ticket of tickets) {
        all.push(ticket);
        collect(ticket.subTickets ?? []);
      }
    };
    collect(picker.tickets as TicketListResponse[]);
    return all;
  }, [picker.tickets]);

  const handleRowSelectionChange = useCallback(
    (next: RowSelectionState) => {
      const nextIds = new Set(Object.keys(next).filter((id) => next[id]));
      const current = new Set(selectedIds);
      const removedIds = selectedIds.filter((id) => !nextIds.has(id));
      const added = listedTickets.filter(
        (ticket) => nextIds.has(ticket.id) && !current.has(ticket.id),
      );
      if (added.length > 0 || removedIds.length > 0) {
        onSelectionChange(added as TicketListResponse[], removedIds);
      }
    },
    [selectedIds, listedTickets, onSelectionChange],
  );

  const toggleTicket = useCallback(
    (ticket: TicketListResponse) => {
      if (selectedIds.includes(ticket.id)) {
        onSelectionChange([], [ticket.id]);
      } else {
        onSelectionChange([ticket], []);
      }
    },
    [selectedIds, onSelectionChange],
  );

  // The search input reports its value on mount and whenever the value it is
  // given changes, so only a real change counts as one; an echo would end the
  // starting filters before the reader touched anything.
  const currentSearch = picker.filters.search;
  const setPickerFilters = picker.setFilters;
  const handleSearchChange = useCallback(
    (search: string | undefined) => {
      const next = search || null;
      if (next === (currentSearch ?? null)) return;
      void setPickerFilters({ search: next });
    },
    [currentSearch, setPickerFilters],
  );

  const emptyMessage =
    picker.activeFilterCount > 0 ? (
      <div className="flex flex-col items-center gap-3">
        <p className="text-muted-foreground text-sm">{t("filters.noResults")}</p>
        <Button type="button" variant="outline" size="sm" onClick={picker.clearFilters}>
          {t("filters.clearAll")}
        </Button>
      </div>
    ) : (
      <p className="text-muted-foreground text-sm">{t("ticket.noTickets")}</p>
    );

  return (
    <section
      aria-labelledby={titleId}
      className="border-border bg-card flex min-w-0 flex-col rounded-lg border"
    >
      <div className="border-border border-b px-4 py-3">
        <h2 id={titleId} className="text-foreground text-sm font-semibold">
          {title}
        </h2>
        <p className="text-muted-foreground text-xs">{description}</p>
      </div>

      {/* The toolbar never wraps, so the filter count badge cannot push the
          filter button onto a row of its own. */}
      <div className="border-border flex items-center gap-2 border-b px-4 py-2">
        <SenseySearchInput
          value={picker.filters.search}
          onSearchChange={handleSearchChange}
          className="w-40 sm:w-56"
        />
        <div className="flex-1" />
        <TicketFilterPanel
          filters={picker.filters}
          onChange={picker.setFilters}
          onClear={picker.clearFilters}
          activeCount={picker.activeFilterCount}
          boardId=""
          boardOptions={picker.boardOptions}
          onSavePreferences={() => picker.prefs.saveFilters(picker.filters)}
          onResetPreferences={async () => {
            if (await picker.prefs.resetFilters()) {
              picker.clearFilters();
            }
          }}
          saveDisabled={!picker.prefs.hasFilterChanges(picker.filters)}
          isSaving={picker.prefs.isSaving}
          hasSavedPreferences={picker.prefs.hasSavedFilters}
          saveTooltip={saveFiltersTooltip}
        />
      </div>

      {/* A bounded height, so the virtualized table scrolls inside the section. */}
      <div className="flex max-h-[28rem] min-h-0 flex-col p-3 lg:max-h-[calc(100vh-14rem)]">
        {picker.isError ? (
          <div className="flex flex-1 flex-col items-center justify-center p-6 text-center">
            <AlertCircle className="text-destructive h-6 w-6" />
            <p className="text-foreground mt-2 text-sm font-medium">
              {t("updates.form.ticketsLoadError")}
            </p>
            {picker.error?.message && (
              <p className="text-muted-foreground mt-1 max-w-md text-xs">
                {picker.error.message}
              </p>
            )}
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="mt-3"
              disabled={picker.isFetching}
              onClick={() => void picker.refetch()}
            >
              {t("updates.retry")}
            </Button>
          </div>
        ) : (
          <SenseyTable<TicketListResponse>
            columns={columns}
            data={picker.tickets as TicketListResponse[]}
            hierarchyKey="subTickets"
            selectableSubRows
            expandAll={!!picker.filters.search}
            subRowFilter={picker.filters.search ? isOnSearchPath : undefined}
            isLoading={picker.isLoading}
            emptyMessage={emptyMessage}
            onRowClick={toggleTicket}
            enableSelection
            rowSelection={rowSelection}
            onRowSelectionChange={handleRowSelectionChange}
            getRowId={(row) => row.id}
            virtualized
          />
        )}
      </div>
    </section>
  );
}

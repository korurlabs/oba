"use client";

import { useTranslations } from "next-intl";
import type { VisibilityState } from "@tanstack/react-table";
import { ViewToggle } from "@/components/layout/view-toggle";
import {
  ColumnVisibilityToggle,
  type ColumnToggleItem,
} from "@oba/shared/components/sensey-table";
import { TicketFilterPanel } from "@/features/tickets/components/ticket-filter-panel";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { AddTicketButton } from "@/components/ui/add-ticket-button";
import type { AllTicketsPageState } from "../hooks/use-all-tickets-page";
import type { useBoardPreferences } from "@/hooks/use-board-preferences";

interface AllTicketsToolbarProps {
  page: AllTicketsPageState;
  boardPrefs: ReturnType<typeof useBoardPreferences>;
  columnToggleItems: ColumnToggleItem[];
  columnVisibility: VisibilityState;
  onColumnVisibilityChange: (visibility: VisibilityState) => void;
  columnOrder: string[];
  onColumnOrderChange: (order: string[]) => void;
}

export function AllTicketsToolbar({
  page,
  boardPrefs,
  columnToggleItems,
  columnVisibility,
  onColumnVisibilityChange,
  columnOrder,
  onColumnOrderChange,
}: AllTicketsToolbarProps) {
  const {
    ticketCount,
    viewMode,
    setViewMode: onViewModeChange,
    filters,
    setFilters: onFiltersChange,
    clearFilters: onFiltersClear,
    resetToBaseline,
    hasFilterChanges,
    activeFilterCount,
    boardOptions,
    filterPanelOptions,
    handleSearchChange: onSearchChange,
    openCreateTicketDialog,
  } = page;

  const t = useTranslations();

  return (
    <div className="border-border bg-card border-b px-4 py-2 lg:px-6">
      <div className="flex flex-wrap items-center gap-2 lg:gap-3">
        {/* Ticket count */}
        <span className="text-muted-foreground hidden text-sm sm:inline">
          {ticketCount} {t("ticket.tickets")}
        </span>

        {/* Search */}
        <SenseySearchInput
          value={filters.search}
          onSearchChange={onSearchChange}
          className="w-40 sm:w-56"
        />

        {/* Spacer */}
        <div className="flex-1" />

        {/* Right side controls */}
        <div className="flex items-center gap-2">
          <ViewToggle view={viewMode} onViewChange={onViewModeChange} />
          <ColumnVisibilityToggle
            columns={columnToggleItems}
            columnVisibility={columnVisibility}
            onColumnVisibilityChange={onColumnVisibilityChange}
            columnOrder={columnOrder}
            onColumnOrderChange={onColumnOrderChange}
            onSave={() => boardPrefs.saveColumns(columnVisibility, columnOrder)}
            onSaveAsDefault={() => boardPrefs.saveColumnsAsDefault(columnVisibility, columnOrder)}
            onReset={() => boardPrefs.resetColumns()}
            saveDisabled={!boardPrefs.hasColumnChanges(columnVisibility, columnOrder)}
            isSaving={boardPrefs.isSaving}
            isOverride={boardPrefs.hasColumnOverride}
            hasDefault={boardPrefs.hasColumnDefault}
            saveTooltip={t("preferences.saveColumnsTooltip")}
          />
          <TicketFilterPanel
            filters={filters}
            onChange={onFiltersChange}
            onClear={onFiltersClear}
            activeCount={activeFilterCount}
            boardId=""
            boardOptions={boardOptions}
            {...filterPanelOptions}
            onSavePreferences={() => boardPrefs.saveFilters(filters)}
            onResetPreferences={async () => {
              if (await boardPrefs.resetFilters()) {
                resetToBaseline();
              }
            }}
            saveDisabled={!hasFilterChanges}
            isSaving={boardPrefs.isSaving}
            hasSavedPreferences={boardPrefs.hasSavedFilterSet}
            saveTooltip={t("preferences.saveFiltersTooltip")}
          />
          <AddTicketButton onClick={() => openCreateTicketDialog()} />
        </div>
      </div>
    </div>
  );
}

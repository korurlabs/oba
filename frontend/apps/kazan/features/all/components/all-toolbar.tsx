"use client";

import { useTranslations } from "next-intl";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { BoardBarControls } from "@/components/layout/board-bar-slot";
import { ViewOptionsPopover } from "@/features/tickets/components/view-options-popover";
import type { ColumnToggleItem } from "@oba/shared/components/sensey-table";
import type { AllPageState } from "../hooks/use-all-page";

interface AllToolbarProps {
  boardId: string;
  page: AllPageState;
  columnToggleItems: ColumnToggleItem[];
}

/**
 * The view controls of the All tab, shown in the board bar: search and the
 * View popover (child boards, columns, filters). The sprint selector and its
 * state lead the sprint section's strip instead, next to the tickets they
 * choose.
 *
 * Intentionally omits:
 * - ViewToggle (the All tab is list-only)
 * - AddTicketButton (each section provides its own add button above its table)
 *
 * The sprint and backlog tables share one column layout, so the Columns tab
 * changes both at once; it is the board's layout, the same one the Sprint and
 * Backlog tabs show.
 */
export function AllToolbar({ boardId, page, columnToggleItems }: AllToolbarProps) {
  const t = useTranslations();
  const {
    filters,
    setFilters,
    clearFilters,
    resetToBaseline,
    hasFilterChanges,
    activeFilterCount,
    includeAllBoards,
    setIncludeAllBoards,
    boardOptions,
    boardPrefs,
  } = page;

  return (
    <BoardBarControls>
      <SenseySearchInput
        value={filters.search}
        onSearchChange={(search) => setFilters({ search: search ?? null })}
        placeholder={t("search")}
        className="w-32 sm:w-56"
      />
      <ViewOptionsPopover
        includeAllBoards={includeAllBoards}
        onIncludeAllBoardsChange={setIncludeAllBoards}
        columns={{
          columns: columnToggleItems,
          columnVisibility: boardPrefs.columnVisibility,
          onColumnVisibilityChange: boardPrefs.setColumnVisibility,
          columnOrder: boardPrefs.columnOrder,
          onColumnOrderChange: boardPrefs.setColumnOrder,
          onSave: () => boardPrefs.saveColumns(boardPrefs.columnVisibility, boardPrefs.columnOrder),
          onSaveAsDefault: () =>
            boardPrefs.saveColumnsAsDefault(boardPrefs.columnVisibility, boardPrefs.columnOrder),
          onReset: () => boardPrefs.resetColumns(),
          saveDisabled: !boardPrefs.hasColumnChanges(
            boardPrefs.columnVisibility,
            boardPrefs.columnOrder
          ),
          isSaving: boardPrefs.isSaving,
          isOverride: boardPrefs.hasColumnOverride,
          hasDefault: boardPrefs.hasColumnDefault,
          saveTooltip: t("preferences.saveColumnsTooltip"),
        }}
        filters={{
          filters,
          onChange: setFilters,
          onClear: clearFilters,
          activeCount: activeFilterCount,
          boardId,
          boardOptions,
          onSavePreferences: () => boardPrefs.saveFilters(filters),
          onResetPreferences: async () => {
            if (await boardPrefs.resetFilters()) {
              resetToBaseline();
            }
          },
          saveDisabled: !hasFilterChanges,
          isSaving: boardPrefs.isSaving,
          hasSavedPreferences: boardPrefs.hasSavedFilterSet,
          saveTooltip: t("preferences.saveFiltersTooltip"),
        }}
      />
    </BoardBarControls>
  );
}

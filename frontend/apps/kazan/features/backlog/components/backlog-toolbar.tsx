"use client";

import { useTranslations } from "next-intl";
import type { VisibilityState } from "@tanstack/react-table";
import { Button } from "@/components/ui/button";
import { ViewToggle } from "@/components/layout/view-toggle";
import { BoardBarControls } from "@/components/layout/board-bar-slot";
import { Sparkles } from "lucide-react";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import type { ColumnToggleItem } from "@oba/shared/components/sensey-table";
import { ViewOptionsPopover } from "@/features/tickets/components/view-options-popover";
import { SelectionActionBar } from "@/features/tickets/components/selection-action-bar";
import { TicketSectionHeader } from "@/features/tickets/components/ticket-section-header";
import { AddTicketButton } from "@/components/ui/add-ticket-button";
import type { BacklogPageState } from "../hooks/use-backlog-page";
import { useBoardRoleContext } from "@/features/boards/providers/board-role-provider";

interface BacklogToolbarProps {
  boardId: string;
  page: BacklogPageState;
  columnToggleItems: ColumnToggleItem[];
  columnVisibility: VisibilityState;
  onColumnVisibilityChange: (visibility: VisibilityState) => void;
  columnOrder: string[];
  onColumnOrderChange: (order: string[]) => void;
}

/**
 * The controls of the Backlog tab. How the tickets are looked at (search,
 * list or kanban, columns and filters) sits in the board bar; the backlog
 * label and the add actions are the strip directly above the table.
 */
export function BacklogToolbar({
  boardId,
  page,
  columnToggleItems,
  columnVisibility,
  onColumnVisibilityChange,
  columnOrder,
  onColumnOrderChange,
}: BacklogToolbarProps) {
  const {
    viewMode,
    setViewMode: onViewModeChange,
    filters,
    setFilters: onFiltersChange,
    clearFilters: onFiltersClear,
    resetToBaseline,
    hasFilterChanges,
    activeFilterCount,
    includeChildBoards: includeAllBoards,
    setIncludeChildBoards: onIncludeAllBoardsChange,
    boardOptions,
    handleSearchChange: onSearchChange,
    openCreateTicketDialog,
    handleShowAICreator: onShowAICreator,
    boardPrefs,
    selectedTicketIds,
    setRowSelection,
    setShowBulkMoveDialog,
    handleBulkDelete,
    isBulkDeletePending,
  } = page;

  const t = useTranslations();
  const { currentUserRole, canEdit: canEditRole, isLoading } = useBoardRoleContext();
  const hasSelection = selectedTicketIds.length > 0 && viewMode === "list";
  const canAddTicket = currentUserRole !== "VIEWER" && !isLoading;

  return (
    <>
      <BoardBarControls>
        <SenseySearchInput
          value={filters.search}
          onSearchChange={onSearchChange}
          placeholder={t("search")}
          className="w-32 sm:w-56"
        />
        <ViewToggle view={viewMode} onViewChange={onViewModeChange} />
        <ViewOptionsPopover
          includeAllBoards={includeAllBoards}
          onIncludeAllBoardsChange={onIncludeAllBoardsChange}
          columns={{
            columns: columnToggleItems,
            columnVisibility,
            onColumnVisibilityChange,
            columnOrder,
            onColumnOrderChange,
            onSave: () => boardPrefs.saveColumns(columnVisibility, columnOrder),
            onSaveAsDefault: () => boardPrefs.saveColumnsAsDefault(columnVisibility, columnOrder),
            onReset: () => boardPrefs.resetColumns(),
            saveDisabled: !boardPrefs.hasColumnChanges(columnVisibility, columnOrder),
            isSaving: boardPrefs.isSaving,
            isOverride: boardPrefs.hasColumnOverride,
            hasDefault: boardPrefs.hasColumnDefault,
            saveTooltip: t("preferences.saveColumnsTooltip"),
          }}
          filters={{
            filters,
            onChange: onFiltersChange,
            onClear: onFiltersClear,
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

      <div className="px-4 py-2 lg:px-6">
        {hasSelection && canEditRole && (
          <SelectionActionBar
            className="mb-2"
            selectedCount={selectedTicketIds.length}
            boardId={boardId}
            selectedTicketIds={selectedTicketIds}
            tickets={page.tickets}
            onMove={() => setShowBulkMoveDialog(true)}
            onDelete={handleBulkDelete}
            onClear={() => setRowSelection({})}
            isDeleting={isBulkDeletePending}
          />
        )}
        <TicketSectionHeader
          title={t("backlog.title")}
          actions={
            <>
              <Button
                variant="outline"
                size="sm"
                className="border-border gap-1"
                onClick={onShowAICreator}
              >
                <Sparkles className="h-4 w-4" />
                <span className="hidden lg:inline">AI</span>
              </Button>
              {canAddTicket && <AddTicketButton onClick={() => openCreateTicketDialog()} />}
            </>
          }
        />
      </div>
    </>
  );
}

"use client";

import { useTranslations } from "next-intl";
import type { VisibilityState } from "@tanstack/react-table";
import { ViewToggle } from "@/components/layout/view-toggle";
import { BoardBarControls } from "@/components/layout/board-bar-slot";
import { CheckCircle2 } from "lucide-react";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import type { ColumnToggleItem } from "@oba/shared/components/sensey-table";
import { ViewOptionsPopover } from "@/features/tickets/components/view-options-popover";
import { SelectionActionBar } from "@/features/tickets/components/selection-action-bar";
import { TicketSectionHeader } from "@/features/tickets/components/ticket-section-header";
import { SprintContext } from "./sprint-context";
import { SprintStatus } from "../schemas";
import type { SprintPageState } from "../hooks/use-sprint-page";
import { AddTicketButton } from "@/components/ui/add-ticket-button";
import { Button } from "@/components/ui/button";
import { useBoardRoleContext } from "@/features/boards/providers/board-role-provider";

interface SprintToolbarProps {
  boardId: string;
  page: SprintPageState;
  columnToggleItems: ColumnToggleItem[];
  columnVisibility: VisibilityState;
  onColumnVisibilityChange: (visibility: VisibilityState) => void;
  columnOrder: string[];
  onColumnOrderChange: (order: string[]) => void;
}

/**
 * The controls of the Sprint tab. How the tickets are looked at (search,
 * list or kanban, columns and filters) sits in the board bar; what is looked
 * at (the sprint, its state and the add action) is the strip directly above
 * the table.
 */
export function SprintToolbar({
  boardId,
  page,
  columnToggleItems,
  columnVisibility,
  onColumnVisibilityChange,
  columnOrder,
  onColumnOrderChange,
}: SprintToolbarProps) {
  const {
    sprints,
    currentSprint,
    setSelectedSprintId: onSprintChange,
    openCreateSprintDialog: onCreateSprint,
    viewMode,
    setViewMode: onViewModeChange,
    filters,
    setFilters: onFiltersChange,
    clearFilters: onFiltersClear,
    resetToBaseline,
    hasFilterChanges,
    activeFilterCount,
    includeAllBoards,
    setIncludeAllBoards: onIncludeAllBoardsChange,
    boardOptions,
    handleCreateTicket,
    boardPrefs,
    selectedTicketIds,
    setRowSelection,
    setShowBulkMoveDialog,
    handleBulkDelete,
    isBulkDeletePending,
  } = page;

  const currentSprintStatus = currentSprint?.status;
  const t = useTranslations();
  const { canManage, canEdit: canEditRole, currentUserRole, isLoading } = useBoardRoleContext();
  const hasSelection = selectedTicketIds.length > 0 && viewMode === "list";
  const ticketData = page.tickets?.data;
  const canAddTicket =
    currentUserRole !== "VIEWER" && !isLoading && currentSprintStatus !== SprintStatus.COMPLETED;

  return (
    <>
      <BoardBarControls>
        <SenseySearchInput
          value={filters.search}
          onSearchChange={(search) => onFiltersChange({ search: search ?? null })}
          placeholder={t("ticket.searchPlaceholder")}
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
            tickets={ticketData}
            onMove={() => setShowBulkMoveDialog(true)}
            onDelete={handleBulkDelete}
            onClear={() => setRowSelection({})}
            isDeleting={isBulkDeletePending}
          />
        )}
        <TicketSectionHeader
          leading={
            <div className="flex flex-wrap items-center gap-2">
              <SprintContext
                sprints={sprints}
                currentSprint={currentSprint}
                onSprintChange={onSprintChange}
                onCreateSprint={onCreateSprint}
                canManage={canManage}
                onEditSprint={page.handleOpenEditSprintDialog}
                onDeleteSprint={page.handleOpenDeleteSprintDialog}
              />
              {/* Sprint lifecycle actions, next to the state they change */}
              {canManage && currentSprintStatus === SprintStatus.PLANNED && (
                <Button
                  size="sm"
                  variant="outline"
                  className="border-border h-8 gap-2 px-3"
                  onClick={page.handleStartSprint}
                  disabled={page.isStartingSprintPending}
                >
                  {t("sprint.start")}
                </Button>
              )}
              {canManage && currentSprintStatus === SprintStatus.ACTIVE && (
                <Button
                  size="sm"
                  variant="outline"
                  className="border-primary/50 text-korur-blue-strong"
                  onClick={() => page.setShowCompleteSprintDialog(true)}
                >
                  <CheckCircle2 className="h-4 w-4" />
                  {t("sprint.complete")}
                </Button>
              )}
            </div>
          }
          actions={
            canAddTicket ? <AddTicketButton onClick={() => handleCreateTicket()} /> : undefined
          }
        />
      </div>
    </>
  );
}

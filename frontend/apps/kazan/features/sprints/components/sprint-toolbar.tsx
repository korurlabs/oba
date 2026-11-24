"use client";

import { useTranslations } from "next-intl";
import type { VisibilityState } from "@tanstack/react-table";
import { cn } from "@oba/ui/lib/utils";
import { ViewToggle } from "@/components/layout/view-toggle";
import { CheckCircle2 } from "lucide-react";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import type { ColumnToggleItem } from "@oba/shared/components/sensey-table";
import { ViewOptionsPopover } from "@/features/tickets/components/view-options-popover";
import { SelectionActionBar } from "@/features/tickets/components/selection-action-bar";
import { SprintSelector } from "./sprint-selector";
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
    daysRemaining,
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

  const currentSprintId = currentSprint?.id ?? null;
  const currentSprintStatus = currentSprint?.status;
  const t = useTranslations();
  const { canManage, canEdit: canEditRole, currentUserRole, isLoading } = useBoardRoleContext();
  const hasSelection = selectedTicketIds.length > 0 && viewMode === "list";

  return (
    <div className="border-border bg-card border-b px-4 py-2 lg:px-6">
      {/* Selection action bar */}
      {hasSelection && canEditRole && (
        <SelectionActionBar
          className="mb-2"
          selectedCount={selectedTicketIds.length}
          boardId={boardId}
          selectedTicketIds={selectedTicketIds}
          tickets={page.tickets?.data}
          onMove={() => setShowBulkMoveDialog(true)}
          onDelete={handleBulkDelete}
          onClear={() => setRowSelection({})}
          isDeleting={isBulkDeletePending}
        />
      )}
      <div className="flex flex-wrap items-center gap-2 lg:gap-3">
        {/* Sprint selector */}
        <SprintSelector
          sprints={sprints}
          currentSprintId={currentSprintId}
          onSprintChange={onSprintChange}
          onCreateSprint={onCreateSprint}
          canCreateSprint={canManage}
          onEditSprint={canManage ? page.handleOpenEditSprintDialog : undefined}
          onDeleteSprint={canManage ? page.handleOpenDeleteSprintDialog : undefined}
        />

        {/*
          Days left -- the only sprint timing this row still carries. The
          literal date range moved into the sprint dropdown, where the user is
          comparing sprints and the dates are worth their width; here they were
          a lot of text on every visit for something the badge already says. A
          sprint that has not started shows nothing: `Start Sprint`, below,
          already states where it is.
        */}
        {currentSprintStatus === "ACTIVE" && daysRemaining > 0 && (
          <span
            className={cn(
              "kazan-grid-chip py-1",
              daysRemaining <= 2 ? "border-primary/50 text-korur-blue-strong" : "text-muted-foreground"
            )}
          >
            {t("sprint.daysLeft", { count: daysRemaining })}
          </span>
        )}

        {/* Sprint lifecycle actions -- grouped with sprint info */}
        {canManage && currentSprintStatus === "PLANNED" && (
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
        {canManage && currentSprintStatus === "ACTIVE" && (
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

        {/* Spacer to push right side controls */}
        <div className="flex-1" />

        {/* Right side: search, view mode, view options, add ticket */}
        {/*
          Wraps within itself, not just as a block: below `sm` the search
          plus three controls exceed the viewport, and wrapping here drops
          the overflow onto a second right-aligned line rather than pushing
          a horizontal scrollbar onto the page.
        */}
        <div className="flex flex-wrap items-center justify-end gap-2">
          <SenseySearchInput
            value={filters.search}
            onSearchChange={(search) => onFiltersChange({ search: search ?? null })}
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
          {currentUserRole !== "VIEWER" &&
            !isLoading &&
            currentSprintStatus !== SprintStatus.COMPLETED && (
              <AddTicketButton onClick={() => handleCreateTicket()} />
            )}
        </div>
      </div>
    </div>
  );
}

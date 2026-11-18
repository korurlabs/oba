"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { cn } from "@oba/ui/lib/utils";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { ViewOptionsPopover } from "@/features/tickets/components/view-options-popover";
import { SprintSelector } from "@/features/sprints/components/sprint-selector";
import { getDaysRemaining } from "@/features/sprints/lib/sprint-dates";
import { SprintStatus } from "@/features/sprints/schemas";
import { useBoardRoleContext } from "@/features/boards/providers/board-role-provider";
import type { ColumnToggleItem } from "@oba/shared/components/sensey-table";
import type { AllPageState } from "../hooks/use-all-page";

interface AllToolbarProps {
  boardId: string;
  page: AllPageState;
  columnToggleItems: ColumnToggleItem[];
}

/**
 * Toolbar for the All tab.
 *
 * Left half is sprint context (which sprint you are looking at and how long
 * is left); right half is how you are looking at it (search, the child-board
 * toggle, filters). This mirrors the dedicated Sprint tab's
 * SprintToolbar, and it is what lets the sprint section below the toolbar
 * shrink to a single caption strip.
 *
 * A board with no sprint has nothing to put in the left half, so the whole
 * control cluster collapses flush-left rather than hugging the right edge with
 * an empty gap beside it.
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
  const { canManage } = useBoardRoleContext();
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
    sprints,
    currentSprint,
  } = page;

  // One clock read per mount rather than one per render: reading Date.now()
  // in the render body is impure and can straddle a day boundary between the
  // server render and hydration.
  const now = useMemo(() => Date.now(), []);
  // Shares `getDaysRemaining` with `useSprintPage`, so the badge renders the
  // same value the Sprint tab toolbar shows for the same sprint.
  const daysRemaining = getDaysRemaining(currentSprint?.endDate, now);
  // Pink is reserved for "you are here" and "this sprint is active". The
  // days-left badge only earns an accent when time is genuinely short.
  const isDeadlineUrgent = daysRemaining <= 2;

  return (
    <div className="border-border bg-card border-b px-4 py-2 lg:px-6">
      <div className="flex flex-wrap items-center gap-2 lg:gap-3">
        {/* Sprint context — which sprint the section below is showing */}
        {currentSprint && (
          <>
            <SprintSelector
              sprints={sprints}
              currentSprintId={currentSprint.id}
              onSprintChange={page.setSelectedSprintId}
              onCreateSprint={page.openCreateSprintDialog}
              canCreateSprint={canManage}
              onEditSprint={canManage ? page.handleOpenEditSprintDialog : undefined}
              onDeleteSprint={canManage ? page.handleOpenDeleteSprintDialog : undefined}
            />
            {/*
              Days left only. The literal date range moved into the sprint
              dropdown, where the user is comparing sprints; mirrors
              SprintToolbar so the two tabs read the same.
            */}
            {currentSprint.status === SprintStatus.ACTIVE && daysRemaining > 0 && (
              <span
                className={cn(
                  "rounded px-2 py-0.5 text-xs font-medium whitespace-nowrap",
                  isDeadlineUrgent
                    ? "bg-primary/15 text-primary"
                    : "bg-muted text-muted-foreground",
                )}
              >
                {t("sprint.daysLeft", { count: daysRemaining })}
              </span>
            )}
          </>
        )}

        {/*
          Spacer — only when the left node has something in it. A board with no
          sprint has an empty left node, so pushing the controls right would
          just mirror the dead-horizontal-space hole this layout closed; there
          the whole cluster stays flush-left on the same 24px rule as the rows
          below it, and the spacer moves after it.
        */}
        {currentSprint && <div className="flex-1" />}

        {/* Controls — how you are looking at it */}
        {/* Wraps within itself below `sm` rather than overflowing. */}
        <div className="flex flex-wrap items-center justify-end gap-2">
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
              onSave: () =>
                boardPrefs.saveColumns(boardPrefs.columnVisibility, boardPrefs.columnOrder),
              onSaveAsDefault: () =>
                boardPrefs.saveColumnsAsDefault(boardPrefs.columnVisibility, boardPrefs.columnOrder),
              onReset: () => boardPrefs.resetColumns(),
              saveDisabled: !boardPrefs.hasColumnChanges(
                boardPrefs.columnVisibility,
                boardPrefs.columnOrder,
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
        </div>
      </div>
    </div>
  );
}

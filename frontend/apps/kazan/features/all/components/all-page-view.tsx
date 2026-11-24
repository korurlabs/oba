"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { Loader2 } from "lucide-react";
import { SprintList } from "@/features/sprints/components/sprint-list";
import { SprintSectionHeader } from "@/features/sprints/components/sprint-section-header";
import { CreateSprintDialog } from "@/features/sprints/components/create-sprint-dialog";
import { EditSprintDialog } from "@/features/sprints/components/edit-sprint-dialog";
import { DeleteSprintDialog } from "@/features/sprints/components/delete-sprint-dialog";
import { BacklogList } from "@/features/backlog/components/backlog-list";
import { BacklogSectionHeader } from "@/features/backlog/components/backlog-section-header";
import { BulkMoveTicketsDialog } from "@/features/tickets/components/bulk-move-tickets-dialog";
import { SelectionActionBar } from "@/features/tickets/components/selection-action-bar";
import { AllToolbar } from "@/features/all/components/all-toolbar";
import { AllDropSection } from "@/features/all/components/all-drop-section";
import { TicketDragOverlayCard } from "@/features/all/components/ticket-drag-overlay-card";
import { useAllPage } from "@/features/all/hooks/use-all-page";
import { useAllSectionDnd } from "@/features/all/hooks/use-all-section-dnd";
import { useTicketColumnToggleItems } from "@/features/tickets/hooks/use-ticket-column-toggle-items";
import {
  ALL_BACKLOG_CONTAINER_ID,
  ALL_SPRINT_CONTAINER_ID,
} from "@/features/all/lib/all-section-dnd";
import { useBoardRoleContext } from "@/features/boards/providers/board-role-provider";
import { useBoardViewTitle } from "@/features/boards/hooks/use-board-view-title";
import { KANBAN_DROP_ANIMATION } from "@/features/tickets/lib/kanban-config";
import { groupListRows, listWorkItems } from "@/lib/utils/ticket-groups";
import { DndContext, DragOverlay } from "@dnd-kit/core";

interface AllPageViewProps {
  boardId: string;
}

export function AllPageView({ boardId }: AllPageViewProps) {
  const t = useTranslations();
  const page = useAllPage(boardId);
  useBoardViewTitle(boardId, t("all.title"));
  // `canManage` is not read here: the sprint create/edit/delete gates moved
  // into AllToolbar together with the SprintSelector.
  const { currentUserRole, canEdit, isLoading: isRoleLoading } = useBoardRoleContext();
  const canCreateTickets = !isRoleLoading && currentUserRole !== "VIEWER";
  const { columnVisibility, setColumnVisibility, columnOrder, setColumnOrder } = page.boardPrefs;
  const columnToggleItems = useTicketColumnToggleItems();

  const hasSprintSelection = page.sprintSelectedTicketIds.length > 0 && canEdit;
  const hasBacklogSelection = page.backlogSelectedTicketIds.length > 0 && canEdit;
  // Show the sprint section whenever the board has any sprints. The selector
  // lets the user pick which one; auto-select gives ACTIVE > PLANNED > first.
  const showSprintSection = page.sprints.length > 0 && page.currentSprint !== null;
  // Initial loading: render the toolbar unconditionally and only swap the
  // content area for a spinner. Keeping the toolbar mounted across re-renders
  // preserves UI state inside it (e.g. the filter popover open state) when
  // filter clicks trigger ticket refetches. Mirrors the Backlog page pattern.
  const showInitialLoader =
    page.isLoading && page.sprintTickets.length === 0 && page.backlogTickets.length === 0;

  // Cross-container drag: a single DndContext spans both sections so a row
  // can be dragged over the boundary. Both SenseyTables opt out of their own
  // context (`externalDndContext`) and this hook resolves every drop --
  // reorder inside the origin section, move across it.
  const dnd = useAllSectionDnd({
    boardId,
    sprintTickets: page.sprintTickets,
    backlogTickets: page.backlogTickets,
    currentSprintId: page.currentSprint?.id,
    backlogId: page.backlogId,
    canMove: canEdit,
  });
  // Everything in a section counts and lists the rows its table shows, which
  // include a dropped row on its way to the other section.
  // The caption strips count the work items of the rows their tables show:
  // a header row for a parent planned elsewhere is not counted, its
  // sub-tickets in the section are.
  const sprintWorkItems = useMemo(
    () => listWorkItems(groupListRows(dnd.sprintTickets)),
    [dnd.sprintTickets]
  );
  const backlogWorkItems = useMemo(
    () => listWorkItems(groupListRows(dnd.backlogTickets)),
    [dnd.backlogTickets]
  );

  return (
    <div className="flex h-full flex-col">
      <AllToolbar boardId={boardId} page={page} columnToggleItems={columnToggleItems} />

      <div id="table-target" className="flex-1 overflow-auto">
        {showInitialLoader ? (
          <div className="flex h-full flex-col items-center justify-center">
            <Loader2 className="text-muted-foreground h-8 w-8 animate-spin" />
            <p className="text-muted-foreground mt-3 text-sm">{t("common.loading")}</p>
          </div>
        ) : (
          <DndContext
            sensors={dnd.sensors}
            collisionDetection={dnd.collisionDetection}
            accessibility={{ announcements: dnd.announcements }}
            onDragStart={dnd.handleDragStart}
            onDragOver={dnd.handleDragOver}
            onDragEnd={dnd.handleDragEnd}
            onDragCancel={dnd.handleDragCancel}
          >
            <div className="flex flex-col gap-4 pt-4">
              {/* Sprint section -- shown when the board has any sprints */}
              {showSprintSection && page.currentSprint && (
                <AllDropSection
                  id={ALL_SPRINT_CONTAINER_ID}
                  label={t("all.dnd.sprintSection")}
                >
                  <section className="flex flex-col gap-2">
                    <div className="px-6">
                      <SprintSectionHeader
                        currentSprint={page.currentSprint}
                        tickets={sprintWorkItems}
                        selectedStatuses={page.filters.statuses ?? undefined}
                        onStatusToggle={page.toggleStatusFilter}
                        onAddTicket={canCreateTickets ? page.openSprintAddTicketDialog : undefined}
                      />
                    </div>

                    {/* Sprint selection action bar */}
                    {hasSprintSelection && (
                      <SelectionActionBar
                        className="mx-6"
                        selectedCount={page.sprintSelectedTicketIds.length}
                        boardId={boardId}
                        selectedTicketIds={page.sprintSelectedTicketIds}
                        tickets={dnd.sprintTickets}
                        onMove={() => page.setShowSprintBulkMoveDialog(true)}
                        onDelete={page.handleSprintBulkDelete}
                        onClear={() => page.setSprintRowSelection({})}
                        isDeleting={page.isBulkDeletePending}
                      />
                    )}

                    <SprintList
                      search={page.filters.search}
                      boardId={boardId}
                      tickets={dnd.sprintTickets}
                      onTicketClick={page.handleTicketClick}
                      onCreateTicket={() => page.handleCreateTicket()}
                      isLoading={page.sprintTicketsLoading}
                      columnVisibility={columnVisibility}
                      onColumnVisibilityChange={setColumnVisibility}
                      columnOrder={columnOrder}
                      onColumnOrderChange={setColumnOrder}
                      onMoveToSprint={canEdit ? page.handleMoveToSprint : undefined}
                      onMoveToBacklog={canEdit ? page.handleMoveToBacklog : undefined}
                      onDeleteTicket={canEdit ? page.handleDeleteTicket : undefined}
                      onEditTicket={page.handleTicketClick}
                      onCopyLink={page.copyTicketLink}
                      sprints={page.sprints}
                      currentSprintId={page.currentSprint.id}
                      onBulkAction={
                        canEdit ? () => page.setShowSprintBulkMoveDialog(true) : undefined
                      }
                      enableSelection={canEdit}
                      rowSelection={page.sprintRowSelection}
                      onRowSelectionChange={page.setSprintRowSelection}
                      hasActiveFilters={page.activeFilterCount > 0}
                      externalDndContext
                      canReorder={canEdit}
                      isDropTarget={dnd.highlightedContainerId === ALL_SPRINT_CONTAINER_ID}
                      highlightedRowId={dnd.recentlyMovedTicketId}
                    />
                  </section>
                </AllDropSection>
              )}

              {/* Backlog section -- always shown */}
              <AllDropSection
                id={ALL_BACKLOG_CONTAINER_ID}
                label={t("all.dnd.backlogSection")}
              >
                <section className="flex flex-col gap-2">
                  <div className="px-6">
                    <BacklogSectionHeader
                      tickets={backlogWorkItems}
                      selectedStatuses={page.filters.statuses ?? undefined}
                      onStatusToggle={page.toggleStatusFilter}
                      onAddTicket={canCreateTickets ? page.openBacklogAddTicketDialog : undefined}
                    />
                  </div>

                  {/* Backlog selection action bar */}
                  {hasBacklogSelection && (
                    <SelectionActionBar
                      className="mx-6"
                      selectedCount={page.backlogSelectedTicketIds.length}
                      boardId={boardId}
                      selectedTicketIds={page.backlogSelectedTicketIds}
                      tickets={dnd.backlogTickets}
                      onMove={() => page.setShowBacklogBulkMoveDialog(true)}
                      onDelete={page.handleBacklogBulkDelete}
                      onClear={() => page.setBacklogRowSelection({})}
                      isDeleting={page.isBulkDeletePending}
                    />
                  )}

                  <BacklogList
                    search={page.filters.search}
                    boardId={boardId}
                    tickets={dnd.backlogTickets}
                    onTicketClick={page.handleTicketClick}
                    onMoveToSprint={canEdit ? page.handleMoveToSprint : undefined}
                    onDeleteTicket={canEdit ? page.handleDeleteTicket : undefined}
                    onEditTicket={page.handleTicketClick}
                    onCopyLink={page.copyTicketLink}
                    isLoading={page.backlogTicketsLoading}
                    sprints={page.sprints}
                    onBulkAction={
                      canEdit ? () => page.setShowBacklogBulkMoveDialog(true) : undefined
                    }
                    enableSelection={canEdit}
                    rowSelection={page.backlogRowSelection}
                    onRowSelectionChange={page.setBacklogRowSelection}
                    columnVisibility={columnVisibility}
                    onColumnVisibilityChange={setColumnVisibility}
                    columnOrder={columnOrder}
                    onColumnOrderChange={setColumnOrder}
                    hasActiveFilters={page.activeFilterCount > 0}
                    externalDndContext
                    canReorder={canEdit}
                    isDropTarget={dnd.highlightedContainerId === ALL_BACKLOG_CONTAINER_ID}
                    highlightedRowId={dnd.recentlyMovedTicketId}
                  />
                </section>
              </AllDropSection>
            </div>

            {/*
              A cross-section drop is shown in the target section from the
              commit that ends the drag (`dnd.sprintTickets` and
              `dnd.backlogTickets`), so this animation measures the row's new
              home instead of gliding the card back to the section it came from.
            */}
            <DragOverlay dropAnimation={KANBAN_DROP_ANIMATION}>
              {dnd.activeTicket ? <TicketDragOverlayCard ticket={dnd.activeTicket} /> : null}
            </DragOverlay>
          </DndContext>
        )}
      </div>

      {/* Sprint bulk move dialog -- targets the selected sprint */}
      {page.currentSprint && (
        <BulkMoveTicketsDialog
          open={page.showSprintBulkMoveDialog}
          onOpenChange={page.setShowSprintBulkMoveDialog}
          boardId={boardId}
          selectedTicketIds={
            page.sprintSelectedTicketIds.length > 0
              ? page.sprintSelectedTicketIds
              : page.sprintIncompleteTicketIds
          }
          onBulkMove={page.handleSprintBulkMove}
          isPending={page.isBulkMovePending}
          source="sprint"
          currentSprintId={page.currentSprint.id}
          tickets={page.sprintTickets}
        />
      )}

      {/* Backlog bulk move dialog */}
      <BulkMoveTicketsDialog
        open={page.showBacklogBulkMoveDialog}
        onOpenChange={page.setShowBacklogBulkMoveDialog}
        boardId={boardId}
        selectedTicketIds={
          page.backlogSelectedTicketIds.length > 0
            ? page.backlogSelectedTicketIds
            : page.backlogIncompleteTicketIds
        }
        onBulkMove={page.handleBacklogBulkMove}
        isPending={page.isBulkMovePending}
        source="backlog"
        tickets={page.backlogTickets}
      />

      {/* Create Sprint Dialog */}
      <CreateSprintDialog
        open={page.showCreateSprintDialog}
        boardId={boardId}
        onOpenChange={(open) => {
          if (!open) page.closeDialog();
        }}
        onCreateSprint={page.handleCreateSprint}
      />

      {/* Edit Sprint Dialog */}
      {page.editTargetSprint && (
        <EditSprintDialog
          open={page.showEditSprintDialog}
          onOpenChange={page.handleEditSprintDialogChange}
          boardId={boardId}
          sprint={page.editTargetSprint}
          onUpdateSprint={page.handleUpdateSprint}
        />
      )}

      {/* Delete Sprint Dialog */}
      {page.deleteTargetSprint && (
        <DeleteSprintDialog
          open={page.showDeleteSprintDialog}
          onOpenChange={page.handleDeleteSprintDialogChange}
          boardId={boardId}
          sprint={page.deleteTargetSprint}
          onDeleteSprint={page.handleDeleteSprint}
        />
      )}
    </div>
  );
}

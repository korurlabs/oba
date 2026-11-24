"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Calendar, Link2, Loader2 } from "lucide-react";
import { SprintList } from "@/features/sprints/components/sprint-list";
import { SprintKanban } from "@/features/sprints/components/sprint-kanban";
import { CreateSprintDialog } from "@/features/sprints/components/create-sprint-dialog";
import { JoinParentSprintDialog } from "@/features/sprints/components/join-parent-sprint-dialog";
import { useParentSprintOptions } from "@/features/sprints/hooks/use-join-parent-sprint";
import { CompleteSprintDialog } from "@/features/sprints/components/complete-sprint-dialog";
import { StartSprintDialog } from "@/features/sprints/components/start-sprint-dialog";
import { EditSprintDialog } from "@/features/sprints/components/edit-sprint-dialog";
import { DeleteSprintDialog } from "@/features/sprints/components/delete-sprint-dialog";
import { BulkMoveTicketsDialog } from "@/features/tickets/components/bulk-move-tickets-dialog";
import { IncidentCloseDialog } from "@/features/tickets/components/incident-close-dialog";
import { SprintToolbar } from "@/features/sprints/components/sprint-toolbar";
import { useSprintPage } from "@/features/sprints/hooks/use-sprint-page";
import { useBoard } from "@/features/boards/hooks/use-boards";
import { EmptyState } from "@/components/ui/empty-state";
import { useTicketColumnToggleItems } from "@/features/tickets/hooks/use-ticket-column-toggle-items";
import { useBoardRoleContext } from "@/features/boards/providers/board-role-provider";
import { useBoardViewTitle } from "@/features/boards/hooks/use-board-view-title";

interface SprintPageViewProps {
  boardId: string;
}

export function SprintPageView({ boardId }: SprintPageViewProps) {
  const t = useTranslations();
  const page = useSprintPage(boardId);
  useBoardViewTitle(boardId, page.currentSprint?.name ?? t("sprint.title"));
  const { currentUserRole, canManage, canEdit, isLoading: isRoleLoading } = useBoardRoleContext();
  const canCreateTickets = !isRoleLoading && currentUserRole !== "VIEWER";
  const { columnVisibility, setColumnVisibility, columnOrder, setColumnOrder } = page.boardPrefs;

  const columnToggleItems = useTicketColumnToggleItems();

  // Show loading skeleton during initial load
  if (page.isLoading && page.sprints.length === 0) {
    return (
      <div className="flex h-full flex-col items-center justify-center">
        <Loader2 className="text-muted-foreground h-8 w-8 animate-spin" />
        <p className="text-muted-foreground mt-3 text-sm">{t("common.loading")}</p>
      </div>
    );
  }

  // No sprints exist yet
  if (!page.isLoading && page.sprints.length === 0) {
    return (
      <div className="flex h-full flex-col">
        <NoSprintState
          boardId={boardId}
          onCreateSprint={canManage ? page.openCreateSprintDialog : undefined}
        />
        <CreateSprintDialog
          open={page.showCreateSprintDialog}
          boardId={boardId}
          onOpenChange={(open) => {
            if (!open) page.closeDialog();
          }}
          onCreateSprint={page.handleCreateSprint}
        />
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      {/* View controls in the board bar, the sprint strip above the table */}
      <SprintToolbar
        boardId={boardId}
        page={page}
        columnToggleItems={columnToggleItems}
        columnVisibility={columnVisibility}
        onColumnVisibilityChange={setColumnVisibility}
        columnOrder={columnOrder}
        onColumnOrderChange={setColumnOrder}
      />

      {/* Sprint Content */}
      <div id="table-target" className="flex-1 overflow-auto">
        {!page.currentSprint ? (
          <div className="flex h-full flex-col items-center justify-center p-6 text-center">
            <p className="text-muted-foreground text-sm">{t("sprint.noActiveSprint")}</p>
            <p className="text-muted-foreground mt-1 text-xs">
              {t("sprint.noActiveSprintDescription")}
            </p>
          </div>
        ) : page.viewMode === "list" ? (
          // The list is all this column holds; min-h-0 lets the table shrink
          // and scroll internally rather than pushing the page taller. The
          // sprint strip above already leaves the gap over the header row.
          <div className="flex h-full flex-col">
            <div className="flex min-h-0 flex-1 flex-col">
              <SprintList
                search={page.filters.search}
                boardId={boardId}
                tickets={page.tickets?.data ?? []}
                onTicketClick={page.handleTicketClick}
                onCreateTicket={() => page.handleCreateTicket()}
                isLoading={page.ticketsLoading}
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
                currentSprintId={page.currentSprint?.id}
                onBulkAction={canEdit ? () => page.setShowBulkMoveDialog(true) : undefined}
                enableSelection={canEdit}
                rowSelection={page.rowSelection}
                onRowSelectionChange={page.setRowSelection}
                hasActiveFilters={page.activeFilterCount > 0}
                canReorder={canEdit}
              />
            </div>
          </div>
        ) : (
          <SprintKanban
            search={page.filters.search}
            currentBoardId={boardId}
            tickets={page.tickets?.data ?? []}
            currentSprintId={page.currentSprint?.id}
            onStatusChange={page.handleStatusChange}
            onTicketClick={page.handleTicketClick}
            onSubTicketClick={page.handleTicketClick}
            onCreateTicket={page.handleCreateTicket}
            onSubTicketToggle={page.handleSubTicketToggle}
            onRequestCreateSubTicket={page.handleRequestCreateSubTicket}
            isLoading={page.ticketsLoading}
            canCreateTickets={canCreateTickets}
            canEdit={canEdit}
          />
        )}
      </div>

      {/* Create Sprint Dialog */}
      <CreateSprintDialog
        open={page.showCreateSprintDialog}
        boardId={boardId}
        onOpenChange={(open) => {
          if (!open) page.closeDialog();
        }}
        onCreateSprint={page.handleCreateSprint}
      />

      {/* Start Sprint Dialog, only when other active sprints block the start */}
      {page.currentSprint && page.startPreview && (
        <StartSprintDialog
          open={page.showStartSprintDialog}
          onOpenChange={page.setShowStartSprintDialog}
          sprint={page.currentSprint}
          preview={page.startPreview}
          onStartSprint={page.handleStartSprintWithTakeover}
        />
      )}

      {/* Complete Sprint Dialog */}
      {page.currentSprint && (
        <CompleteSprintDialog
          open={page.showCompleteSprintDialog}
          onOpenChange={page.setShowCompleteSprintDialog}
          boardId={boardId}
          sprint={page.currentSprint}
          currentSprintId={page.currentSprint.id}
          completedTicketsCount={page.completedTicketsCount}
          incompleteTicketsCount={page.incompleteTicketsCount}
          onCompleteSprint={page.handleCompleteSprint}
        />
      )}

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

      {/* Bulk Move Tickets Dialog */}
      <BulkMoveTicketsDialog
        open={page.showBulkMoveDialog}
        onOpenChange={page.setShowBulkMoveDialog}
        boardId={boardId}
        selectedTicketIds={
          page.selectedTicketIds.length > 0 ? page.selectedTicketIds : page.incompleteTicketIds
        }
        onBulkMove={page.handleBulkMove}
        isPending={page.isBulkMovePending}
        source="sprint"
        currentSprintId={page.currentSprint?.id}
        tickets={page.tickets?.data}
      />

      <IncidentCloseDialog {...page.incidentCloseDialogProps} />
    </div>
  );
}

/**
 * The Sprint tab of a board without sprints. A child board usually works in
 * the sprint of a board above it, so joining that sprint is offered first and
 * creating a sprint of its own second.
 */
function NoSprintState({
  boardId,
  onCreateSprint,
}: {
  boardId: string;
  onCreateSprint?: () => void;
}) {
  const t = useTranslations("sprint");
  const [showJoinDialog, setShowJoinDialog] = useState(false);
  const { data: board } = useBoard(boardId);
  const { parentOptions, hasJoinableSprint } = useParentSprintOptions(boardId);

  const joinAction = hasJoinableSprint
    ? { label: t("joinParent.openButton"), onClick: () => setShowJoinDialog(true), Icon: Link2 }
    : undefined;
  const createAction = onCreateSprint
    ? {
        label: hasJoinableSprint ? t("joinParent.createOwn") : t("createFirst"),
        onClick: onCreateSprint,
      }
    : undefined;

  return (
    <>
      <EmptyState
        Icon={Calendar}
        title={t("noActiveSprint")}
        description={
          hasJoinableSprint ? t("joinParent.emptyDescription") : t("noActiveSprintDescription")
        }
        action={joinAction ?? createAction}
        secondaryAction={joinAction ? createAction : undefined}
      />
      {hasJoinableSprint && (
        <JoinParentSprintDialog
          open={showJoinDialog}
          onOpenChange={setShowJoinDialog}
          boardName={board?.data?.name ?? ""}
          parentOptions={parentOptions}
        />
      )}
    </>
  );
}

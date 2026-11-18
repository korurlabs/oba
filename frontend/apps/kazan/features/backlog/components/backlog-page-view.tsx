"use client";

import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Plus, Loader2 } from "lucide-react";
import { AITicketCreator } from "@/features/ai/components/ai-ticket-creator";
import { BulkMoveTicketsDialog } from "@/features/tickets/components/bulk-move-tickets-dialog";
import { IncidentCloseDialog } from "@/features/tickets/components/incident-close-dialog";
import { BacklogList } from "@/features/backlog/components/backlog-list";
import { BacklogKanban } from "@/features/backlog/components/backlog-kanban";
import { BacklogToolbar } from "@/features/backlog/components/backlog-toolbar";
import { useBacklogPage } from "@/features/backlog/hooks/use-backlog-page";
import { EmptyState } from "@/components/ui/empty-state";
import type { TicketListResponse } from "@/features/tickets/schemas";
import { useTicketColumnToggleItems } from "@/features/tickets/hooks/use-ticket-column-toggle-items";
import { useBoardRoleContext } from "@/features/boards/providers/board-role-provider";
import { useBoardViewTitle } from "@/features/boards/hooks/use-board-view-title";

interface BacklogPageViewProps {
  boardId: string;
}

export function BacklogPageView({ boardId }: BacklogPageViewProps) {
  const t = useTranslations();
  const page = useBacklogPage(boardId);
  useBoardViewTitle(boardId, t("backlog.title"));
  const { currentUserRole, canEdit, isLoading: isRoleLoading } = useBoardRoleContext();
  const canCreateTickets = !isRoleLoading && currentUserRole !== "VIEWER";
  const { columnVisibility, setColumnVisibility, columnOrder, setColumnOrder } = page.boardPrefs;

  const columnToggleItems = useTicketColumnToggleItems();

  return (
    <div className="bg-background flex h-full flex-col">
      <BacklogToolbar
        boardId={boardId}
        page={page}
        columnToggleItems={columnToggleItems}
        columnVisibility={columnVisibility}
        onColumnVisibilityChange={setColumnVisibility}
        columnOrder={columnOrder}
        onColumnOrderChange={setColumnOrder}
      />

      {/* Backlog Content */}
      <div id="table-target" className="flex-1 overflow-auto">
        {page.isLoading ? (
          <div className="bg-background flex h-full flex-col items-center justify-center">
            <Loader2 className="text-muted-foreground h-8 w-8 animate-spin" />
            <p className="text-muted-foreground mt-3 text-sm">{t("common.loading")}</p>
          </div>
        ) : page.tickets.length === 0 && page.activeFilterCount === 0 ? (
          <BacklogEmptyState onCreateClick={canCreateTickets ? () => page.openCreateTicketDialog() : undefined} />
        ) : page.tickets.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <p className="text-muted-foreground text-sm">{t("filters.noResults")}</p>
            <Button variant="outline" className="mt-4" onClick={page.clearFilters}>
              {t("filters.clearAll")}
            </Button>
          </div>
        ) : page.viewMode === "list" ? (
          // `pt-4` matches the gap the Sprint tab's deleted search row used to
          // open above the table, and the `pt-4` the All tab already carries.
          // Padding on an `h-full` box, so it eats into the 100% height rather
          // than adding to it -- BacklogList still scrolls inside the shell.
          <div className="flex h-full flex-col pt-4">
            <BacklogList
              boardId={boardId}
              tickets={page.tickets as TicketListResponse[]}
              onTicketClick={page.handleTicketClick}
              sprints={page.sprints}
              onMoveToSprint={canEdit ? page.handleMoveToSprint : undefined}
              onDeleteTicket={canEdit ? page.handleDeleteTicket : undefined}
              onEditTicket={page.handleTicketClick}
              onCopyLink={page.copyTicketLink}
              onSprintsNeeded={page.handleSprintsNeeded}
              onBulkAction={canEdit ? () => page.setShowBulkMoveDialog(true) : undefined}
              enableSelection={canEdit}
              rowSelection={page.rowSelection}
              onRowSelectionChange={page.setRowSelection}
              columnVisibility={columnVisibility}
              onColumnVisibilityChange={setColumnVisibility}
              columnOrder={columnOrder}
              onColumnOrderChange={setColumnOrder}
              canReorder={canEdit}
            />
          </div>
        ) : (
          <BacklogKanban
            tickets={page.tickets as TicketListResponse[]}
            onTicketClick={page.handleTicketClick}
            onSubTicketClick={page.handleTicketClick}
            onCreateTicket={page.handleCreateTicket}
            onSubTicketToggle={page.handleSubTicketToggle}
            onRequestCreateSubTicket={page.handleRequestCreateSubTicket}
            isLoading={page.ticketsLoading}
            canEdit={canCreateTickets}
            canCreateTickets={canCreateTickets}
          />
        )}
      </div>

      {/* AI Ticket Creator Dialog */}
      <AITicketCreator
        boardId={boardId}
        open={page.showAICreator}
        onOpenChange={page.setShowAICreator}
      />

      {/* Bulk Move Tickets Dialog */}
      <BulkMoveTicketsDialog
        open={page.showBulkMoveDialog}
        onOpenChange={page.setShowBulkMoveDialog}
        boardId={boardId}
        selectedTicketIds={
          page.selectedTicketIds.length > 0
            ? page.selectedTicketIds
            : page.incompleteTicketIds
        }
        onBulkMove={page.handleBulkMove}
        isPending={page.isBulkMovePending}
        source="backlog"
        tickets={page.tickets}
      />

      <IncidentCloseDialog {...page.incidentCloseDialogProps} />
    </div>
  );
}

function BacklogEmptyState({ onCreateClick }: { onCreateClick?: () => void }) {
  const t = useTranslations();

  return (
    <EmptyState
      Icon={Plus}
      title={t("ticket.noTickets")}
      description={t("backlog.emptyDescription")}
      action={onCreateClick ? { label: t("ticket.create"), onClick: onCreateClick } : undefined}
    />
  );
}

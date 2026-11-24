"use client";

import { useTranslations } from "next-intl";
import { Loader2, Siren, Ticket } from "lucide-react";
import { Button } from "@/components/ui/button";
import { AllTicketsToolbar } from "@/features/all-tickets/components/all-tickets-toolbar";
import { AllTicketsList } from "@/features/all-tickets/components/all-tickets-list";
import { AllTicketsKanban } from "@/features/all-tickets/components/all-tickets-kanban";
import {
  useAllTicketsPage,
  type AllTicketsVariant,
} from "@/features/all-tickets/hooks/use-all-tickets-page";
import { EmptyState } from "@/components/ui/empty-state";
import { IncidentCloseDialog } from "@/features/tickets/components/incident-close-dialog";
import { useTicketColumnToggleItems } from "@/features/tickets/hooks/use-ticket-column-toggle-items";

interface AllTicketsViewProps {
  variant?: AllTicketsVariant;
}

export function AllTicketsView({ variant = "tickets" }: AllTicketsViewProps) {
  const t = useTranslations();
  const page = useAllTicketsPage(variant);
  const isIncidents = variant === "incidents";

  // Column visibility/order persisted via user preferences under the page's key
  const { boardPrefs } = page;
  const {
    columnVisibility,
    setColumnVisibility,
    columnOrder,
    setColumnOrder,
  } = boardPrefs;

  const columnToggleItems = useTicketColumnToggleItems();

  return (
    <div className="flex h-full flex-col">
      {/* Toolbar */}
      <AllTicketsToolbar
        page={page}
        boardPrefs={boardPrefs}
        columnToggleItems={columnToggleItems}
        columnVisibility={columnVisibility}
        onColumnVisibilityChange={setColumnVisibility}
        columnOrder={columnOrder}
        onColumnOrderChange={setColumnOrder}
      />

      {/* Content */}
      <div id="table-target" className="flex-1 overflow-auto">
        {page.isLoading ? (
          <div className="flex h-full flex-col items-center justify-center">
            <Loader2 className="text-muted-foreground h-8 w-8 animate-spin" />
            <p className="text-muted-foreground mt-3 text-sm">{t("common.loading")}</p>
          </div>
        ) : page.tickets.length === 0 && page.activeFilterCount === 0 ? (
          <EmptyState
            Icon={isIncidents ? Siren : Ticket}
            title={isIncidents ? t("incidentsPage.emptyTitle") : t("ticket.noTickets")}
            description={
              isIncidents
                ? t("incidentsPage.emptyDescription")
                : t("allTicketsPage.emptyDescription")
            }
          />
        ) : page.tickets.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <p className="text-muted-foreground text-sm">{t("filters.noResults")}</p>
            <Button variant="outline" className="mt-4" onClick={page.clearFilters}>
              {t("filters.clearAll")}
            </Button>
          </div>
        ) : page.viewMode === "list" ? (
          <AllTicketsList
            search={page.filters.search}
            tickets={page.tickets}
            onTicketClick={page.handleTicketClick}
            onCreateTicket={() => page.handleCreateTicket()}
            onCopyLink={page.copyTicketLink}
            isLoading={page.ticketsLoading}
            columnVisibility={columnVisibility}
            onColumnVisibilityChange={setColumnVisibility}
            columnOrder={columnOrder}
            onColumnOrderChange={setColumnOrder}
          />
        ) : (
          <AllTicketsKanban
            search={page.filters.search}
            tickets={page.tickets}
            onStatusChange={page.handleStatusChange}
            onTicketClick={page.handleTicketClick}
            onSubTicketClick={page.handleTicketClick}
            onCreateTicket={page.handleCreateTicket}
            onSubTicketToggle={page.handleSubTicketToggle}
            onRequestCreateSubTicket={page.handleRequestCreateSubTicket}
            isLoading={page.ticketsLoading}
          />
        )}
      </div>

      <IncidentCloseDialog {...page.incidentCloseDialogProps} />
    </div>
  );
}

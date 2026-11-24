/**
 * BacklogKanban Component
 *
 * Card view for backlog tickets.
 *
 * Unlike SprintKanban this renders a SINGLE "To Do" column and has no
 * drag-to-change-status. A ticket that sits in the backlog may only carry the
 * TODO status -- the backend resets anything else on every write path
 * (TicketStatusNormalizer). Rendering the other three columns would offer two
 * actions the save silently discards: dragging a card into IN_PROGRESS /
 * IN_REVIEW / DONE, and the per-column "Add Ticket" button, which passes the
 * column's status into the create dialog. Both are removed here rather than
 * left wired to an endpoint that throws the choice away.
 *
 * Sub-tickets ARE exempt from that rule (their status is the completion
 * checkbox that feeds the parent's percentage), so `onSubTicketToggle` stays.
 *
 * The cards are shown most urgent first, like every kanban column; tickets of
 * equal priority keep the board order the list arrives in.
 */

"use client";

import { useMemo } from "react";
import { TicketStatus } from "@/types/enums";
import type { TicketListResponse } from "@/features/tickets/schemas";
import { TicketCard, TicketCardSkeleton } from "@/features/tickets/components/ticket-card";
import { cn } from "@oba/ui/lib/utils";
import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { KANBAN_COLUMNS } from "@/features/tickets/lib/kanban-config";
import { sortByPriority } from "@/features/tickets/lib/ticket-priority-order";

// The backlog only ever holds TODO tickets, so it borrows the TODO column's
// styling. The cards are NOT filtered by status though: a ticket written before
// the rule existed may still carry IN_PROGRESS in the database, and hiding it
// here would make it unreachable from this view.
const TODO_COLUMN = KANBAN_COLUMNS.find((column) => column.id === TicketStatus.TODO);
const BACKLOG_COLUMN = {
  colorClass: TODO_COLUMN?.colorClass ?? "bg-muted/50",
  borderClass: TODO_COLUMN?.borderClass ?? "border-l-4 border-l-gray-300",
  countClass: TODO_COLUMN?.countClass ?? "bg-muted text-foreground",
};

interface BacklogKanbanProps {
  tickets: TicketListResponse[];
  onTicketClick: (ticket: TicketListResponse) => void;
  onSubTicketClick?: (subTicket: TicketListResponse) => void;
  onCreateTicket: (status: TicketStatus) => void;
  onSubTicketToggle?: (subTicketId: string, completed: boolean) => Promise<void>;
  onRequestCreateSubTicket?: (parentId: string) => void;
  /** Whether the current user can edit tickets (toggle sub-ticket complete, quick-add). Defaults to true. */
  canEdit?: boolean;
  isLoading?: boolean;
  canCreateTickets?: boolean;
  /** While a search is active: show only the sub-tickets on a card that match or lead to a match, expanded. */
  showSearchMatches?: boolean;
}

export function BacklogKanban({
  tickets,
  onTicketClick,
  onSubTicketClick,
  onCreateTicket,
  onSubTicketToggle,
  onRequestCreateSubTicket,
  canEdit = true,
  isLoading = false,
  showSearchMatches = false,
  canCreateTickets = true,
}: BacklogKanbanProps) {
  const t = useTranslations();
  const sortedTickets = useMemo(() => sortByPriority(tickets), [tickets]);

  if (isLoading) {
    return (
      <div className="p-4 lg:p-6">
        <div className="mx-auto flex max-w-3xl flex-col gap-3">
          <div className={cn("rounded-lg p-3", BACKLOG_COLUMN.colorClass)}>
            <div className="h-6 w-24 animate-pulse rounded" />
          </div>
          <TicketCardSkeleton />
          <TicketCardSkeleton />
          <TicketCardSkeleton />
        </div>
      </div>
    );
  }

  return (
    <div className="p-4 lg:p-6">
      <div className="mx-auto flex max-w-3xl flex-col gap-3">
        {/* Column Header */}
        <div
          className={cn(
            "flex items-center justify-between rounded-xl p-3.5",
            BACKLOG_COLUMN.colorClass,
            BACKLOG_COLUMN.borderClass
          )}
        >
          <div className="flex flex-col">
            <h3 className="text-foreground text-sm font-semibold">{t("backlog.title")}</h3>
            <p className="text-muted-foreground text-xs">{t("ticket.backlogStatusHint")}</p>
          </div>
          <span
            className={cn(
              "flex h-6 min-w-6 items-center justify-center rounded-full px-2 text-xs font-semibold",
              BACKLOG_COLUMN.countClass
            )}
          >
            {tickets.length}
          </span>
        </div>

        <div className="flex min-h-[500px] flex-col gap-3">
          {tickets.length === 0 ? (
            <div className="flex flex-1 flex-col items-center justify-center py-8">
              {canCreateTickets && (
                <div className="bg-background mb-3 flex h-10 w-10 items-center justify-center rounded-xl">
                  <Plus className="text-muted-foreground h-5 w-5" />
                </div>
              )}
              <p className="text-muted-foreground text-center text-sm">
                {t("backlog.noTicketsInColumn")}
              </p>
            </div>
          ) : (
            sortedTickets.map((ticket) => (
              <TicketCard
                key={ticket.id}
                ticket={ticket}
                onClick={() => onTicketClick(ticket)}
                isDraggable={false}
                onSubTicketToggle={onSubTicketToggle}
                onSubTicketClick={onSubTicketClick}
                onRequestCreateSubTicket={onRequestCreateSubTicket}
                canEdit={canEdit}
                showSearchMatches={showSearchMatches}
              />
            ))
          )}

          {/* Add Ticket Button. Always creates a TODO ticket -- the only status
              the backlog accepts. */}
          {canCreateTickets && (
            <button
              type="button"
              onClick={() => onCreateTicket(TicketStatus.TODO)}
              className="hover:border-primary hover:text-primary hover:bg-primary/10 dark:hover:bg-accent dark:hover:text-foreground text-foreground mt-2 flex w-full cursor-pointer items-center justify-center gap-2 rounded-lg border-2 border-dashed border-border py-2.5 text-sm transition-all duration-200"
            >
              <Plus className="h-4 w-4" />
              {t("sprint.addTicket")}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

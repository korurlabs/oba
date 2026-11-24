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
import {
  KanbanAddTicketButton,
  KanbanColumnHeader,
  KanbanColumnHeaderSkeleton,
  KanbanEmptyColumn,
} from "@/features/tickets/components/kanban-column";
import { useTranslations } from "next-intl";
import { sortByPriority } from "@/features/tickets/lib/ticket-priority-order";

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
  /** The active search, if any: each card then lists only the sub-tickets whose title or key matches it, or that lead to one that does, expanded. */
  search?: string | null;
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
  search,
  canCreateTickets = true,
}: BacklogKanbanProps) {
  const t = useTranslations();
  const sortedTickets = useMemo(() => sortByPriority(tickets), [tickets]);

  if (isLoading) {
    return (
      <div className="p-4 lg:p-6">
        <div className="mx-auto flex max-w-3xl flex-col gap-3">
          <KanbanColumnHeaderSkeleton />
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
        {/* The cards are NOT filtered by status: a ticket written before the
            TODO-only rule existed may still carry IN_PROGRESS in the database,
            and hiding it here would make it unreachable from this view. */}
        <KanbanColumnHeader
          title={t("backlog.title")}
          hint={t("ticket.backlogStatusHint")}
          count={tickets.length}
        />

        <div className="flex min-h-[500px] flex-col gap-3">
          {tickets.length === 0 ? (
            <KanbanEmptyColumn
              message={t("backlog.noTicketsInColumn")}
              showAddHint={canCreateTickets}
            />
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
                search={search}
              />
            ))
          )}

          {/* Add Ticket Button. Always creates a TODO ticket -- the only status
              the backlog accepts. */}
          {canCreateTickets && (
            <KanbanAddTicketButton
              label={t("sprint.addTicket")}
              onClick={() => onCreateTicket(TicketStatus.TODO)}
            />
          )}
        </div>
      </div>
    </div>
  );
}

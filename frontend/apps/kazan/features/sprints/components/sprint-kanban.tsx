/**
 * SprintKanban Component
 *
 * Kanban board view for sprint tickets
 * Per visual-design-reference.md section 5:
 * - 5 columns: TODO, IN_PROGRESS, WAITING_FOR_THIRD_PARTY, IN_REVIEW, DONE
 * - Column headers with count
 * - Column colors (gray-50, blue-50, purple-50, green-50)
 * - Drag states with dashed border, light Korur blue background
 */

"use client";

import { TicketStatus } from "@/types/enums";
import type { TicketListResponse } from "@/features/tickets/schemas";
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from "@dnd-kit/core";
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { TicketCard, TicketCardSkeleton } from "@/features/tickets/components/ticket-card";
import { cn } from "@oba/ui/lib/utils";
import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import {
  KANBAN_COLUMNS,
  KANBAN_DRAG_ACTIVATION_DISTANCE,
  KANBAN_DROP_ANIMATION,
  VALID_KANBAN_STATUSES,
  useKanbanDropHandoff,
  kanbanCollisionDetection,
  getTicketsByStatus,
  DroppableColumn,
} from "@/features/tickets/lib/kanban-config";

interface SprintKanbanProps {
  currentBoardId: string;
  tickets: TicketListResponse[];
  currentSprintId?: string;
  onStatusChange: (ticketId: string, newStatus: TicketStatus) => Promise<void>;
  onTicketClick: (ticket: TicketListResponse) => void;
  onSubTicketClick?: (subTicket: TicketListResponse) => void;
  onCreateTicket: (status: TicketStatus) => void;
  onSubTicketToggle?: (subTicketId: string, completed: boolean) => Promise<void>;
  onRequestCreateSubTicket?: (parentId: string) => void;
  /** Whether the current user can edit tickets (drag between columns, toggle sub-ticket complete, quick-add). Defaults to true. */
  canEdit?: boolean;
  isLoading?: boolean;
  canCreateTickets?: boolean;
  /** The active search, if any: each card then lists only the sub-tickets whose title or key matches it, or that lead to one that does, expanded. */
  search?: string | null;
}

export function SprintKanban({
  currentBoardId,
  tickets,
  currentSprintId,
  onStatusChange,
  onTicketClick,
  onSubTicketClick,
  onCreateTicket,
  onSubTicketToggle,
  onRequestCreateSubTicket,
  canEdit = true,
  isLoading = false,
  search,
  canCreateTickets = true,
}: SprintKanbanProps) {
  const t = useTranslations();
  const { activeTicket, displayTickets, beginDrag, releaseDrag, handOffTo } =
    useKanbanDropHandoff(tickets);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: KANBAN_DRAG_ACTIVATION_DISTANCE,
      },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  const handleDragStart = (event: DragStartEvent) => {
    const ticket = tickets.find((t) => t.id === event.active.id);
    beginDrag(ticket ?? null);
  };

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;

    if (!over) {
      releaseDrag();
      return;
    }

    const ticketId = active.id as string;
    const ticket = tickets.find((t) => t.id === ticketId);
    if (!ticket) {
      releaseDrag();
      return;
    }

    // Resolve the target status
    let newStatus: TicketStatus;
    if (VALID_KANBAN_STATUSES.has(over.id as TicketStatus)) {
      // Dropped on the column droppable directly
      newStatus = over.id as TicketStatus;
    } else {
      // Dropped on a sortable item — read the containerId from sortable data
      const containerId = over.data.current?.sortable?.containerId as TicketStatus | undefined;
      if (containerId && VALID_KANBAN_STATUSES.has(containerId)) {
        newStatus = containerId;
      } else {
        // Fallback: find the ticket's status
        const overTicket = tickets.find((t) => t.id === over.id);
        if (!overTicket) {
          releaseDrag();
          return;
        }
        newStatus = overTicket.status;
      }
    }

    if (ticket.status === newStatus) {
      releaseDrag();
      return;
    }

    // The card renders in its new column from this commit on, so the drop
    // animation glides there. Dragging is not blocked while the request runs;
    // a failure rolls the cache back and toasts in `useUpdateTicket`.
    handOffTo(ticketId, newStatus, Promise.resolve(onStatusChange(ticketId, newStatus)));
  };

  if (isLoading) {
    return (
      <div className="overflow-x-auto p-4 lg:p-6">
        <div className="grid min-w-[1000px] grid-cols-5 gap-4">
          {KANBAN_COLUMNS.map((column) => (
            <div key={column.id} className="flex flex-col gap-3">
              <div className={cn("rounded-lg p-3", column.colorClass)}>
                <div className="h-6 w-24 animate-pulse rounded" />
              </div>
              <div className="flex flex-col gap-3">
                <TicketCardSkeleton />
                <TicketCardSkeleton />
                <TicketCardSkeleton />
                <TicketCardSkeleton />
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={kanbanCollisionDetection}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
      onDragCancel={releaseDrag}
    >
      <div className="overflow-x-auto p-4 lg:p-6">
        <div className="grid min-w-[1000px] grid-cols-5 gap-4">
          {KANBAN_COLUMNS.map((column) => {
            const columnTickets = getTicketsByStatus(displayTickets, column.id);

            return (
              <div key={column.id} className="flex min-h-[500px] flex-col gap-3">
                {/* Column Header */}
                <div
                  className={cn(
                    "flex items-center justify-between rounded-xl p-3.5",
                    column.colorClass,
                    column.borderClass
                  )}
                >
                  <h3 className="text-foreground text-sm font-semibold">
                    {t(`ticket.status.${column.id}`)}
                  </h3>
                  <span
                    className={cn(
                      "flex h-6 min-w-6 items-center justify-center rounded-full px-2 text-xs font-semibold",
                      column.countClass
                    )}
                  >
                    {columnTickets.length}
                  </span>
                </div>

                {/* Droppable Area */}
                <SortableContext
                  id={column.id}
                  items={columnTickets.map((t) => t.id)}
                  strategy={verticalListSortingStrategy}
                >
                  <DroppableColumn
                    id={column.id}
                    isOver={activeTicket !== null && activeTicket.status !== column.id}
                  >
                    {columnTickets.length === 0 ? (
                      <div className="flex flex-1 flex-col items-center justify-center py-8">
                        {canCreateTickets && (
                          <div className="bg-background mb-3 flex h-10 w-10 items-center justify-center rounded-xl">
                            <Plus className="text-muted-foreground h-5 w-5" />
                          </div>
                        )}
                        <p className="text-muted-foreground text-center text-sm">
                          {t("sprint.noTicketsInColumn")}
                        </p>
                      </div>
                    ) : (
                      columnTickets.map((ticket) => (
                        <TicketCard
                          currentBoardId={currentBoardId}
                          currentSprintId={currentSprintId}
                          key={ticket.id}
                          ticket={ticket}
                          onClick={() => onTicketClick(ticket)}
                          isDraggable={canEdit}
                          onSubTicketToggle={onSubTicketToggle}
                          onSubTicketClick={onSubTicketClick}
                          onRequestCreateSubTicket={onRequestCreateSubTicket}
                          canEdit={canEdit}
                          search={search}
                        />
                      ))
                    )}

                    {/* Add Ticket Button */}
                    {canCreateTickets && (
                      <button
                        type="button"
                        onClick={() => onCreateTicket(column.id)}
                        className="hover:border-primary hover:text-primary hover:bg-primary/10 dark:hover:bg-accent dark:hover:text-foreground text-foreground border-border mt-2 flex w-full cursor-pointer items-center justify-center gap-2 rounded-lg border-2 border-dashed py-2.5 text-sm transition-all duration-200"
                      >
                        <Plus className="h-4 w-4" />
                        {t("sprint.addTicket")}
                      </button>
                    )}
                  </DroppableColumn>
                </SortableContext>
              </div>
            );
          })}
        </div>
      </div>

      {/* Drag Overlay */}
      <DragOverlay dropAnimation={KANBAN_DROP_ANIMATION}>
        {activeTicket ? <TicketCard ticket={activeTicket} isDraggable={false} /> : null}
      </DragOverlay>
    </DndContext>
  );
}

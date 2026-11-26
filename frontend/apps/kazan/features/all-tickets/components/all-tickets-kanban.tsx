/**
 * AllTicketsKanban Component
 *
 * Kanban board view for cross-board tickets with 5 status columns.
 * Reuses the same DnD pattern as SprintKanban.
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
import {
  KanbanAddTicketButton,
  KanbanColumnHeader,
  KanbanColumnHeaderSkeleton,
  KanbanEmptyColumn,
} from "@/features/tickets/components/kanban-column";
import { useTranslations } from "next-intl";
import type { AllTicketListResponse } from "../schemas";
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

interface AllTicketsKanbanProps {
  tickets: AllTicketListResponse[];
  onStatusChange: (ticketId: string, newStatus: TicketStatus) => Promise<void>;
  onTicketClick: (ticket: TicketListResponse) => void;
  onSubTicketClick?: (subTicket: TicketListResponse) => void;
  onCreateTicket: (status: TicketStatus) => void;
  onSubTicketToggle?: (subTicketId: string, completed: boolean) => Promise<void>;
  onRequestCreateSubTicket?: (parentId: string) => void;
  /** Whether the current user can edit tickets (toggle sub-ticket complete, quick-add). Defaults to true. */
  canEdit?: boolean;
  isLoading?: boolean;
  /** The active search, if any: each card then lists only the sub-tickets whose title or key matches it, or that lead to one that does, expanded. */
  search?: string | null;
}

export function AllTicketsKanban({
  tickets,
  onStatusChange,
  onTicketClick,
  onSubTicketClick,
  onCreateTicket,
  onSubTicketToggle,
  onRequestCreateSubTicket,
  canEdit = true,
  isLoading = false,
  search,
}: AllTicketsKanbanProps) {
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
    }),
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
      newStatus = over.id as TicketStatus;
    } else {
      const containerId = over.data.current?.sortable?.containerId as TicketStatus | undefined;
      if (containerId && VALID_KANBAN_STATUSES.has(containerId)) {
        newStatus = containerId;
      } else {
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
              <KanbanColumnHeaderSkeleton />
              <div className="flex flex-col gap-3">
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
          {KANBAN_COLUMNS.map((column, columnIndex) => {
            const columnTickets = getTicketsByStatus(displayTickets, column.id);

            return (
              <div key={column.id} className="flex min-h-[500px] flex-col gap-3">
                <KanbanColumnHeader
                  index={columnIndex + 1}
                  classicEdgeClass={column.classicEdgeClass}
                  title={t(`ticket.status.${column.id}`)}
                  count={columnTickets.length}
                />

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
                      <KanbanEmptyColumn
                        message={t("allTicketsPage.noTicketsInColumn")}
                        showAddHint
                      />
                    ) : (
                      columnTickets.map((ticket) => (
                        <TicketCard
                          key={ticket.id}
                          ticket={ticket as TicketListResponse}
                          onClick={() => onTicketClick(ticket as TicketListResponse)}
                          isDraggable={true}
                          onSubTicketToggle={onSubTicketToggle}
                          onSubTicketClick={onSubTicketClick}
                          onRequestCreateSubTicket={onRequestCreateSubTicket}
                          showBoardName={true}
                          canEdit={canEdit}
                          search={search}
                        />
                      ))
                    )}

                    {/* Add Ticket Button */}
                    <KanbanAddTicketButton
                      label={t("allTicketsPage.addTicket")}
                      onClick={() => onCreateTicket(column.id)}
                    />
                  </DroppableColumn>
                </SortableContext>
              </div>
            );
          })}
        </div>
      </div>

      {/* Drag Overlay */}
      <DragOverlay dropAnimation={KANBAN_DROP_ANIMATION}>
        {activeTicket ? (
          <TicketCard ticket={activeTicket as TicketListResponse} isDraggable={false} />
        ) : null}
      </DragOverlay>
    </DndContext>
  );
}

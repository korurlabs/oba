/**
 * TicketCard Component
 *
 * Displays a ticket in Kanban board view with inline sub-tickets
 * Matches the new Sensey design system
 */

"use client";

import { memo, useMemo, useState, useEffect } from "react";
import type { TicketListResponse } from "@/features/tickets/schemas";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { TicketTypeBadge } from "./ticket-type-badge";
import { TicketPriorityIcon } from "./ticket-priority-icon";
import { InlineSubTickets } from "./inline-sub-tickets";
import { useSearchPathFilter } from "@/features/tickets/lib/search-match";
import { UserMultipleAvatar } from "@/components/ui/user-avatar";
import { Badge } from "@/components/ui/badge";
import { cn } from "@oba/ui/lib/utils";
import { User as UserIcon } from "lucide-react";
import { getPriorityLabel } from "./ticket-priority-icon";
import { DueDateBadge } from "./due-date-badge";
import { LabelBadgeList } from "@/features/labels/components/label-badge";
import { ProjectBadge } from "@/features/projects/components/project-badge";
import { MilestoneBadge } from "@/features/milestones/components/milestone-badge";
import { useTranslations } from "next-intl";
import { useRouter } from "@oba/shared/libs/i18n/navigation";
import { BOARD_TAB, boardPath } from "@/features/boards/lib/board-route";

interface TicketCardProps {
  ticket: TicketListResponse;
  onClick?: () => void;
  isDraggable?: boolean;
  isDragging?: boolean;
  onSubTicketToggle?: (subTicketId: string, completed: boolean) => Promise<void>;
  onSubTicketClick?: (subTicket: TicketListResponse) => void;
  onRequestCreateSubTicket?: (parentId: string) => void;
  className?: string;
  currentBoardId?: string;
  currentSprintId?: string;
  /** Always show the board name on the card (useful for cross-board views) */
  showBoardName?: boolean;
  /** Whether the current user can edit tickets (toggle sub-ticket complete, quick-add). Defaults to true. */
  canEdit?: boolean;
  /**
   * The active search, if any: the card then lists only the sub-tickets whose
   * title or key matches it, or that lead to one that does, expanded, since the
   * card can be listed because one of its sub-tickets matched. The progress bar
   * still counts every sub-ticket, and the reader can still collapse the list.
   */
  search?: string | null;
}

export const TicketCard = memo(function TicketCard({
  ticket,
  onClick,
  isDraggable = true,
  isDragging: externalDragging,
  onSubTicketToggle,
  onSubTicketClick,
  onRequestCreateSubTicket,
  className,
  currentBoardId,
  currentSprintId,
  showBoardName = false,
  canEdit = true,
  search,
}: TicketCardProps) {
  const tTicket = useTranslations("ticket");
  const subTickets = useMemo(() => ticket.subTickets ?? [], [ticket.subTickets]);
  const searchFilter = useSearchPathFilter<TicketListResponse>(search);
  const shownSubTickets = useMemo(
    () => (searchFilter ? subTickets.filter(searchFilter) : subTickets),
    [searchFilter, subTickets]
  );
  // A search expands the card when it has a sub-ticket to show.
  const expandForSearch = !!searchFilter && shownSubTickets.length > 0;
  const [isSubTicketsExpanded, setIsSubTicketsExpanded] = useState(expandForSearch);
  // Follow `expandForSearch` when it changes; adjusted during render so the
  // card never shows a frame with the old expansion.
  const [lastExpandForSearch, setLastExpandForSearch] = useState(expandForSearch);
  if (lastExpandForSearch !== expandForSearch) {
    setLastExpandForSearch(expandForSearch);
    setIsSubTicketsExpanded(expandForSearch);
  }
  const router = useRouter();
  // Check if ticket is from a different board (child board ticket)
  const isFromDifferentBoard = currentBoardId && ticket.board && ticket.board.id !== currentBoardId;
  const ticketBoardSlug = ticket.board?.slug;
  // Show board name when explicitly requested (cross-board views) or when from different board
  const shouldShowBoard = showBoardName && ticket.board;

  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging: internalDragging,
  } = useSortable({
    id: ticket.id,
    disabled: !isDraggable,
  });

  const isDragging = externalDragging || internalDragging;

  // Auto-collapse sub-tickets when dragging
  useEffect(() => {
    if (isDragging) {
      setIsSubTicketsExpanded(false);
    }
  }, [isDragging]);

  // Memoize style calculation.
  //
  // The sortable `transition` (a `transform ...` transition used to displace
  // the siblings around the gap) is applied to NON-dragging cards only. On the
  // card being dragged it would fight dnd-kit, which writes a new transform
  // every frame -- the card would lag behind the pointer and, on drop, ease
  // back toward its old slot before snapping. Dropping it there leaves the
  // class-level `border-color/box-shadow/opacity` transition in effect, which
  // is what fades the card to 50% as the drag starts.
  const style = useMemo(
    () => ({
      transform: CSS.Transform.toString(transform),
      transition: isDragging ? undefined : (transition ?? undefined),
      // Scoped to cards that are actually moving so the compositor hint is not
      // left on every card on the board.
      willChange: transform ? "transform" : undefined,
    }),
    [transform, transition, isDragging]
  );

  const assignees = useMemo(() => ticket.assignees ?? [], [ticket.assignees]);

  const handleSubTicketToggle = async (subTicketId: string, completed: boolean) => {
    if (onSubTicketToggle) {
      await onSubTicketToggle(subTicketId, completed);
    }
  };

  const handleRequestCreateSubTicket = () => {
    if (onRequestCreateSubTicket) {
      onRequestCreateSubTicket(ticket.id);
    }
  };

  const cardContent = (
    <div
      ref={setNodeRef}
      style={style}
      {...attributes}
      {...listeners}
      onClick={onClick}
      className={cn(
        // The column already tells the status, so the card stays neutral
        // apart from its faint blue fade; a primary edge on hover is the only
        // cue that it opens.
        "group kazan-card border-border relative cursor-pointer rounded-none border p-[14px]",
        "transition-[border-color,box-shadow,opacity] duration-150 ease-in-out",
        "hover:border-primary/50",
        isDragging && "opacity-50 shadow-xl",
        className
      )}
    >
      {isFromDifferentBoard && currentSprintId && ticketBoardSlug && (
        <button
          type="button"
          className="text-muted-foreground mb-2 cursor-pointer text-xs font-medium"
          onClick={(e) => {
            e.stopPropagation();
            // A sprint slug belongs to one board, and the card knows only the
            // sprint's id, so the child board's Sprint tab resolves the id to
            // that board's slug for the sprint.
            router.push(
              `${boardPath(ticketBoardSlug, BOARD_TAB.sprint)}?sprintId=${encodeURIComponent(currentSprintId)}`,
            );
          }}
        >
          <span className="kazan-grid-label hover:text-primary inline-flex items-center gap-1.5">
            {tTicket("boardField")}: {ticket.board?.prefix}
          </span>
        </button>
      )}
      {shouldShowBoard && !isFromDifferentBoard && (
        <div className="kazan-grid-label mb-2 flex items-center gap-1.5">
          {tTicket("boardField")}: {ticket.board?.prefix}
        </div>
      )}
      {/* A sub-ticket planned apart from its parent names the parent, since
          the board has no row for it. */}
      {ticket.parentTicket && (
        <div
          className="text-muted-foreground mb-1.5 truncate text-xs"
          title={`${ticket.parentTicket.key} ${ticket.parentTicket.title}`}
        >
          <span className="font-mono font-medium">{ticket.parentTicket.key}</span>{" "}
          {ticket.parentTicket.title}
        </div>
      )}
      {/* Header: Type Badge + Key */}
      <div className="mb-2.5 flex items-center justify-between gap-2">
        <TicketTypeBadge type={ticket.type} />
        <span className="text-muted-foreground font-mono text-xs font-semibold tracking-[0.08em] uppercase">
          {ticket.key}
        </span>
      </div>

      {/* Title (max 2 lines) */}
      <h4 className="text-foreground mb-3 line-clamp-2 text-sm leading-snug font-medium">
        {ticket.title}
      </h4>

      {/* Project, Milestone & Labels */}
      {(ticket.project || ticket.milestone || ticket.labels?.length) ? (
        <div className="mb-2.5 flex flex-wrap items-center gap-1.5">
          {ticket.project && (
            <ProjectBadge project={ticket.project} size="sm" />
          )}
          {ticket.milestone && (
            <MilestoneBadge milestone={ticket.milestone} size="sm" />
          )}
          {ticket.labels?.length ? (
            <LabelBadgeList labels={ticket.labels} maxVisible={3} size="sm" />
          ) : null}
        </div>
      ) : null}

      {/* Due Date (shown only when set) */}
      {ticket.dueDate && (
        <div className="mb-2.5">
          <DueDateBadge dueDate={ticket.dueDate} />
        </div>
      )}

      {/* Footer: Priority, Progress, Mission Rank, Assignee */}
      <div className="flex items-center justify-between gap-2">
        {/* A narrow column wraps this group rather than pushing the assignee off the card */}
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          {/* Priority with icon and text */}
          <div className="flex items-center gap-1">
            <TicketPriorityIcon priority={ticket.priority} size={14} />
            <span className="text-muted-foreground text-xs font-medium">
              {getPriorityLabel(ticket.priority)}
            </span>
          </div>
          <span
            className="text-foreground text-[13px] tabular-nums"
            title={tTicket("fields.percentage")}
          >
            {ticket.percentage}%
          </span>
          {/* Every ticket has a Mission Rank: the column is required, so no empty state */}
          <Badge
            variant="outline"
            className="text-[11px] font-medium"
            title={tTicket("fields.missionRank")}
          >
            {tTicket(`missionRank.${ticket.missionRank}`)}
          </Badge>
        </div>

        {/* Assignees: a narrow card wraps the name onto a second line before it cuts it */}
        {assignees.length > 0 ? (
          <UserMultipleAvatar
            users={assignees}
            size="xs"
            nameClassName="line-clamp-2 whitespace-normal wrap-anywhere"
          />
        ) : (
          <div className="bg-muted flex h-5 w-5 items-center justify-center">
            <UserIcon className="text-muted-foreground h-3 w-3" />
          </div>
        )}
      </div>
      {/* Inline Sub-tickets (Notion-style) */}
      {subTickets.length > 0 && (
        <div className="mt-3">
          <InlineSubTickets
            subTickets={subTickets}
            shownSubTickets={shownSubTickets}
            isExpanded={isSubTicketsExpanded}
            onToggleExpand={(e) => {
              e.stopPropagation();
              setIsSubTicketsExpanded(!isSubTicketsExpanded);
            }}
            onToggleComplete={handleSubTicketToggle}
            onSubTicketClick={onSubTicketClick}
            onRequestCreateSubTicket={
              onRequestCreateSubTicket ? handleRequestCreateSubTicket : undefined
            }
            canEdit={canEdit}
            maxVisible={searchFilter ? shownSubTickets.length : 3}
            // On the cross-board board no sprint is shown; the card's own
            // sprint is then the one its sub-tickets are compared with.
            currentSprintId={currentSprintId ?? ticket.sprint?.id}
          />
        </div>
      )}
    </div>
  );

  return cardContent;
});

// Skeleton loading state for TicketCard
export function TicketCardSkeleton() {
  return (
    <div className="border-border bg-card dark:bg-muted animate-pulse rounded-none border p-3.5">
      <div className="mb-2.5 flex items-center justify-between">
        <div className="bg-muted h-5 w-14" />
        <div className="bg-muted h-4 w-16" />
      </div>
      <div className="mb-3 space-y-2">
        <div className="bg-muted h-4 w-full" />
        <div className="bg-muted h-4 w-3/4" />
      </div>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="bg-muted h-4 w-4" />
        </div>
        <div className="bg-muted h-6 w-6 rounded-full" />
      </div>
    </div>
  );
}

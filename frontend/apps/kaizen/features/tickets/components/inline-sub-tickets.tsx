'use client';

import { useState } from 'react';
import type { TicketListResponse } from '@/features/tickets/schemas';
import { cn } from '@oba/ui/lib/utils';
import { SubTicketProgressBar } from './sub-ticket-progress-bar';
import { subTicketProgress } from '@/lib/utils/ticket-groups';
import { useTranslations } from 'next-intl';
import { Check, ChevronDown, Plus } from 'lucide-react';

interface InlineSubTicketsProps {
  subTickets: TicketListResponse[];
  isExpanded: boolean;
  onToggleExpand: (e: React.MouseEvent<HTMLButtonElement>) => void;
  onToggleComplete: (id: string, completed: boolean) => void;
  onSubTicketClick?: (subTicket: TicketListResponse) => void;
  onRequestCreateSubTicket?: () => void;
  /** Whether the current user can edit tickets (toggle complete, quick-add). Defaults to true. */
  canEdit?: boolean;
  maxVisible?: number;
  /**
   * The sprint the card is shown in. The progress bar splits the unfinished
   * sub-tickets into this sprint, other sprints and the backlog.
   */
  currentSprintId?: string | null;
  className?: string;
}

export function InlineSubTickets({
  subTickets,
  isExpanded,
  onToggleExpand,
  onToggleComplete,
  onSubTicketClick,
  onRequestCreateSubTicket,
  canEdit = true,
  maxVisible = 3,
  currentSprintId,
  className,
}: InlineSubTicketsProps) {
  const t = useTranslations('ticket.inlineSubTickets');
  const [showAll, setShowAll] = useState(false);

  const completed = subTickets.filter((t) => t.status === 'DONE').length;
  const total = subTickets.length;

  // Determine which tickets to show
  const visibleTickets = showAll ? subTickets : subTickets.slice(0, maxVisible);
  const hiddenCount = subTickets.length - maxVisible;

  if (total === 0) return null;

  return (
    <div className={cn('space-y-1.5 w-full', className)}>
      {/* Progress bar (always visible) */}
      <SubTicketProgressBar
        completed={completed}
        total={total}
        breakdown={subTicketProgress(subTickets, currentSprintId)}
        onClick={onToggleExpand}
        size="sm"
      />

      {/* Expanded section */}
      {isExpanded && (
        <div
          className={cn(
            'mt-2 pt-2 border-t border-border space-y-1.5',
            'animate-in slide-in-from-top-1 duration-200'
          )}
          onClick={(e) => e.stopPropagation()}
        >
          {/* Sub-ticket list */}
          {visibleTickets.map((subTicket) => {
            const isDone = subTicket.status === 'DONE';
            return (
              <div
                key={subTicket.id}
                className={cn(
                  'flex items-center gap-2.5 rounded-md bg-muted/50 px-2.5 py-2 group',
                  onSubTicketClick && 'cursor-pointer hover:bg-muted'
                )}
                onClick={(e) => {
                  e.stopPropagation();
                  onSubTicketClick?.(subTicket);
                }}
              >
                <button
                  type="button"
                  disabled={!canEdit}
                  onClick={(e) => {
                    e.stopPropagation();
                    onToggleComplete(subTicket.id, !isDone);
                  }}
                  className={cn(
                    'flex h-4.5 w-4.5 shrink-0 items-center justify-center rounded-full border-2 transition-colors',
                    isDone
                      ? 'border-primary bg-primary'
                      : 'border-primary/60 bg-transparent hover:border-primary',
                    !canEdit && 'opacity-50 cursor-not-allowed'
                  )}
                  aria-label={isDone ? t('markIncomplete') : t('markComplete')}
                >
                  {isDone && <Check className="h-3 w-3 text-white" />}
                </button>
                <span
                  className={cn(
                    'flex-1 truncate text-xs',
                    isDone && 'text-muted-foreground line-through'
                  )}
                >
                  {subTicket.title}
                </span>
                <span className="text-[11px] font-mono text-muted-foreground shrink-0">
                  {subTicket.key}
                </span>
              </div>
            );
          })}

          {/* Show more button */}
          {hiddenCount > 0 && !showAll && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                setShowAll(true);
              }}
              className="flex items-center gap-1 text-xs text-primary hover:text-primary/80 py-1 px-1"
            >
              <ChevronDown className="h-3.5 w-3.5" />
              {t('showMore', { count: hiddenCount })}
            </button>
          )}

          {/* Show less button */}
          {showAll && hiddenCount > 0 && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                setShowAll(false);
              }}
              className="flex items-center gap-1 text-xs text-primary hover:text-primary/80 py-1 px-1"
            >
              <ChevronDown className="h-3.5 w-3.5 rotate-180" />
              {t('showLess')}
            </button>
          )}

          {/* Add sub-ticket button */}
          {canEdit && onRequestCreateSubTicket && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onRequestCreateSubTicket();
              }}
              className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground py-1 px-1 mt-1"
            >
              <Plus className="h-3.5 w-3.5" />
              {t('quickAdd')}
            </button>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * TicketActionsMenu Component
 *
 * Dropdown menu for ticket actions (Edit, Delete, Move, etc.)
 */

'use client';

import { useState } from 'react';
import type { TicketListResponse } from '@/features/tickets/schemas';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Button, buttonVariants } from '@/components/ui/button';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import {
  MoreVertical,
  Pencil,
  Trash2,
  Copy,
  MoveRight,
  ListTree,
  Archive,
  Link as LinkIcon,
} from 'lucide-react';
import { useTranslations } from 'next-intl';
import { MoveToSprintDialog } from './move-to-sprint-dialog';
import { hasSubTickets, type SubTicketMoveChoice } from './sub-ticket-choice';

interface Sprint {
  id: string;
  name: string;
  status: string;
}

interface TicketActionsMenuProps {
  ticket?: TicketListResponse;
  boardId?: string;
  onEdit?: () => void;
  onDelete?: () => void;
  onDuplicate?: () => void;
  onMove?: () => void;
  onCreateSubTicket?: () => void;
  /** `subTickets` is set when the ticket has sub-tickets: the user chose which come along. */
  onMoveToSprint?: (sprintId: string, subTickets?: SubTicketMoveChoice) => void;
  onMoveToBacklog?: (subTickets?: SubTicketMoveChoice) => void;
  /**
   * Copies the ticket's deep-link URL to the clipboard. Receives the
   * ticket so callers can build the link from `ticket.key`. The menu
   * shows the option whenever this callback is provided AND a ticket is
   * present, regardless of which other row actions are available.
   */
  onCopyLink?: (ticket: TicketListResponse) => void;
  sprints?: Sprint[];
  currentSprintId?: string;
  isInBacklog?: boolean;
  isPending?: boolean;
}

export function TicketActionsMenu({
  ticket,
  onEdit,
  onDelete,
  onDuplicate,
  onMove,
  onCreateSubTicket,
  onMoveToSprint,
  onMoveToBacklog,
  onCopyLink,
  boardId,
  sprints = [],
  currentSprintId,
  isInBacklog = false,
  isPending,
}: TicketActionsMenuProps) {
  const t = useTranslations('ticket');
  const tCommon = useTranslations('common');

  const [showMoveToSprintDialog, setShowMoveToSprintDialog] = useState(false);
  const [showMoveToBacklogDialog, setShowMoveToBacklogDialog] = useState(false);
  // A parent asks which sub-tickets come along before it moves to the backlog.
  const asksForSubTickets = hasSubTickets(ticket);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  // Filter out current sprint from options
  const availableSprints = sprints.filter(
    (s) => s.id !== currentSprintId && s.status !== 'COMPLETED'
  );

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="ghost"
            className="min-h-[44px] min-w-[44px] p-0"
            onClick={(e) => e.stopPropagation()}
          >
            <MoreVertical size={16} />
            <span className="sr-only">{t('actions.openMenu')}</span>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-48">
          {onEdit && (
            <DropdownMenuItem onClick={onEdit}>
              <Pencil size={14} className="mr-2" />
              {t('actions.edit')}
            </DropdownMenuItem>
          )}

          {onCopyLink && ticket && (
            <DropdownMenuItem onClick={() => onCopyLink(ticket)}>
              <LinkIcon size={14} className="mr-2" />
              {t('actions.copyLink')}
            </DropdownMenuItem>
          )}

          {onCreateSubTicket && (
            <DropdownMenuItem onClick={onCreateSubTicket}>
              <ListTree size={14} className="mr-2" />
              {t('actions.createSubTicket')}
            </DropdownMenuItem>
          )}

          {onDuplicate && (
            <DropdownMenuItem onClick={onDuplicate}>
              <Copy size={14} className="mr-2" />
              {t('actions.duplicate')}
            </DropdownMenuItem>
          )}

          {/* Move to Sprint -- opens dialog */}
          {onMoveToSprint && availableSprints.length > 0 && (
            <>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={() => setShowMoveToSprintDialog(true)}>
                <MoveRight size={14} className="mr-2" />
                {t('actions.moveToSprint')}
              </DropdownMenuItem>
            </>
          )}

          {/* Move to Backlog option */}
          {onMoveToBacklog && !isInBacklog && (
            <DropdownMenuItem
              onClick={() =>
                asksForSubTickets ? setShowMoveToBacklogDialog(true) : onMoveToBacklog()
              }
            >
              <Archive size={14} className="mr-2" />
              {t('actions.moveToBacklog')}
            </DropdownMenuItem>
          )}

          {/* Legacy Move option */}
          {onMove && (
            <DropdownMenuItem onClick={onMove}>
              <MoveRight size={14} className="mr-2" />
              {t('actions.moveTo')}
            </DropdownMenuItem>
          )}

          {onDelete && (
            <>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onClick={() => setShowDeleteConfirm(true)}
                className="text-destructive focus:bg-destructive focus:text-destructive-foreground"
              >
                <Trash2 size={14} className="mr-2" />
                {t('actions.delete')}
              </DropdownMenuItem>
            </>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      {/* Move to Sprint Dialog */}
      {onMoveToSprint && boardId && (
        <MoveToSprintDialog
          open={showMoveToSprintDialog}
          onOpenChange={setShowMoveToSprintDialog}
          boardId={boardId}
          currentSprintId={currentSprintId}
          ticket={ticket}
          onConfirm={({ sprintId, subTickets }) => {
            if (!sprintId) return;
            onMoveToSprint(sprintId, subTickets);
            setShowMoveToSprintDialog(false);
          }}
          isPending={isPending}
        />
      )}

      {/* Move to Backlog Dialog -- only for a ticket with sub-tickets */}
      {onMoveToBacklog && asksForSubTickets && (
        <MoveToSprintDialog
          open={showMoveToBacklogDialog}
          onOpenChange={setShowMoveToBacklogDialog}
          // No sprint list is loaded for a move to the backlog.
          boardId={boardId ?? ""}
          ticket={ticket}
          target="backlog"
          onConfirm={({ subTickets }) => {
            onMoveToBacklog(subTickets);
            setShowMoveToBacklogDialog(false);
          }}
          isPending={isPending}
        />
      )}

      {/* Delete Confirmation */}
      {onDelete && (
        <AlertDialog open={showDeleteConfirm} onOpenChange={setShowDeleteConfirm}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>{tCommon('confirmDeleteTitle')}</AlertDialogTitle>
              <AlertDialogDescription>{tCommon('confirmDelete')}</AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>{tCommon('cancel')}</AlertDialogCancel>
              <AlertDialogAction
                className={buttonVariants({ variant: 'destructive' })}
                onClick={onDelete}
              >
                {tCommon('delete')}
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      )}
    </>
  );
}

/**
 * SelectionActionBar
 *
 * The bar that appears above a ticket table once rows are selected:
 * "N ticket(s) selected | Move Selected Tickets | Attach to milestone |
 * Create update | Delete Selected | Clear selection".
 *
 * It is shared by the Backlog toolbar, the Sprint toolbar and both sections of
 * the All tab, which previously each carried their own copy of the same JSX.
 * Only the outer spacing differed, so that stays a `className` the caller
 * passes in.
 *
 * The delete confirmation lives here rather than in each caller: every call
 * site needs the identical "this cannot be undone" AlertDialog, and keeping it
 * next to the button makes it impossible for one of them to forget it. The
 * milestone picker lives here for the same reason.
 *
 * "Create update" opens the create update screen with exactly the selected
 * tickets. It links by ticket key, which resolves on any board, because the
 * table can list tickets of child boards and of sprints shared with other
 * boards.
 */

"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { ArrowRightLeft, Flag, MessageSquarePlus, Trash2, X } from "lucide-react";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { cn } from "@oba/ui/lib/utils";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { AttachMilestoneDialog } from "@/features/milestones/components/attach-milestone-dialog";
import { updateCreatePathForTickets } from "@/features/updates/lib/create-update-path";
import { UPDATE_TICKETS_MAX } from "@/features/updates/schemas";
import type { TicketListResponse } from "../schemas";

interface SelectionActionBarProps {
  /** Number of selected rows, rendered in the label and the confirmation. */
  selectedCount: number;
  /** Opens the bulk move dialog. */
  onMove: () => void;
  /** Deletes the selection. May be async; rejections are logged, not swallowed. */
  onDelete: () => void | Promise<unknown>;
  /** Clears the row selection. */
  onClear: () => void;
  /** True while a bulk delete is in flight. */
  isDeleting?: boolean;
  /** Outer spacing -- `mb-2` in the toolbars, `mx-6` in the All sections. */
  className?: string;
  /** Board being viewed; with `selectedTicketIds`, enables "Attach to milestone". */
  boardId?: string;
  /** Ids of the selected tickets, sent to the milestone link endpoint. */
  selectedTicketIds?: string[];
  /**
   * The tickets of the table, sub-tickets included. With `selectedTicketIds`,
   * enables "Create update", which needs the keys of the selected tickets.
   */
  tickets?: readonly TicketListResponse[];
}

export function SelectionActionBar({
  selectedCount,
  onMove,
  onDelete,
  onClear,
  isDeleting = false,
  className,
  boardId,
  selectedTicketIds,
  tickets,
}: SelectionActionBarProps) {
  const t = useTranslations();
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [showAttachMilestone, setShowAttachMilestone] = useState(false);
  const canAttachMilestone = !!boardId && !!selectedTicketIds?.length;
  const selectedTicketKeys = useMemo(
    () => findTicketKeys(tickets ?? [], selectedTicketIds ?? []),
    [tickets, selectedTicketIds],
  );
  const tooManyForUpdate = selectedTicketKeys.length > UPDATE_TICKETS_MAX;

  const handleConfirmDelete = () => {
    // The mutation surfaces its own success/failure toasts. This catch only
    // stops a rejection from escaping the click handler as an unhandled
    // rejection, and still logs it (rule 10).
    Promise.resolve(onDelete()).catch((error: unknown) => {
      console.error("[selection-action-bar] bulk delete failed", error);
    });
  };

  return (
    <>
      {/* The actions wrap to further rows when they do not fit, as on a
        phone, so every one stays reachable; where they fit, they keep one row. */}
      <div
        className={cn(
          "bg-primary/5 border-primary/20 flex flex-wrap items-center gap-x-3 gap-y-1.5 rounded-md border px-3 py-1.5",
          className,
        )}
      >
        <span className="text-primary text-sm font-medium">
          {t("ticket.selection.count", { count: selectedCount })}
        </span>
        <Button size="sm" variant="outline" className="h-7 gap-1.5 text-xs" onClick={onMove}>
          <ArrowRightLeft className="h-3.5 w-3.5" />
          {t("ticket.bulkMove.title")}
        </Button>
        {canAttachMilestone && (
          <Button
            size="sm"
            variant="outline"
            className="h-7 gap-1.5 text-xs"
            onClick={() => setShowAttachMilestone(true)}
          >
            <Flag className="h-3.5 w-3.5" />
            {t("milestone.attach.action")}
          </Button>
        )}
        {selectedTicketKeys.length > 0 &&
          (tooManyForUpdate ? (
            // A disabled button gets no pointer events, so the wrapper
            // carries the reason.
            <span title={t("updates.fromSelection.tooMany", { max: UPDATE_TICKETS_MAX })}>
              <Button size="sm" variant="outline" className="h-7 gap-1.5 text-xs" disabled>
                <MessageSquarePlus className="h-3.5 w-3.5" />
                {t("updates.fromSelection.action")}
              </Button>
            </span>
          ) : (
            <Button size="sm" variant="outline" className="h-7 gap-1.5 text-xs" asChild>
              <Link href={updateCreatePathForTickets(selectedTicketKeys)}>
                <MessageSquarePlus className="h-3.5 w-3.5" />
                {t("updates.fromSelection.action")}
              </Link>
            </Button>
          ))}
        <Button
          size="sm"
          variant="destructive"
          className="h-7 gap-1.5 text-xs"
          onClick={() => setShowDeleteConfirm(true)}
          disabled={isDeleting}
        >
          <Trash2 className="h-3.5 w-3.5" />
          {isDeleting ? t("ticket.bulkDelete.pending") : t("ticket.bulkDelete.action")}
        </Button>
        <Button size="sm" variant="ghost" className="h-7 gap-1 text-xs" onClick={onClear}>
          <X className="h-3.5 w-3.5" />
          {t("common.clearSelection")}
        </Button>
      </div>

      {boardId && selectedTicketIds && canAttachMilestone && (
        <AttachMilestoneDialog
          open={showAttachMilestone}
          onOpenChange={setShowAttachMilestone}
          boardId={boardId}
          ticketIds={selectedTicketIds}
          onAttached={onClear}
        />
      )}

      <AlertDialog open={showDeleteConfirm} onOpenChange={setShowDeleteConfirm}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {t("ticket.bulkDelete.confirmTitle", { count: selectedCount })}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {t("ticket.bulkDelete.confirmDescription", { count: selectedCount })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t("common.cancel")}</AlertDialogCancel>
            <AlertDialogAction
              className={buttonVariants({ variant: "destructive" })}
              onClick={handleConfirmDelete}
            >
              {t("common.delete")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

/**
 * The keys of the selected tickets in selection order. A selected ID that is
 * no longer in the table (filtered out after it was ticked) has no key here
 * and is left out.
 */
function findTicketKeys(
  tickets: readonly TicketListResponse[],
  selectedIds: readonly string[],
): string[] {
  const keyById = new Map<string, string>();
  for (const ticket of tickets) {
    keyById.set(ticket.id, ticket.key);
    for (const sub of ticket.subTickets ?? []) keyById.set(sub.id, sub.key);
  }
  return selectedIds.flatMap((id) => {
    const key = keyById.get(id);
    return key ? [key] : [];
  });
}

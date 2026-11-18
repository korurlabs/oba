"use client";

import { useState } from "react";
import { Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
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
import { Button, buttonVariants } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { InfoTip } from "@/components/ui/info-tip";
import { Skeleton } from "@/components/ui/skeleton";
import { TicketKeyLink } from "@/features/tickets/components/ticket-key-link";
import {
  useBulkDeleteTickets,
  type BulkDeleteTicketTarget,
} from "@/features/tickets/hooks/use-bulk-delete-tickets";
import { useRestoreTickets } from "@/features/tickets/hooks/use-restore-tickets";
import { cn } from "@/lib/utils";
import { useHomeStaleBacklog } from "../hooks/use-home-stale-backlog";
import type { HomeStaleTicketResponse } from "../schemas";
import { HomeSectionError } from "./home-section-error";

const SECTION_CLASS = "bg-card text-card-foreground border-border flex flex-col gap-4 rounded-2xl border p-4 sm:p-5";
const DAY_MS = 86_400_000;

function daysSince(instant: string, now: number): number {
  return Math.max(0, Math.floor((now - Date.parse(instant)) / DAY_MS));
}

function toTarget(ticket: HomeStaleTicketResponse): BulkDeleteTicketTarget {
  return { boardId: ticket.boardId, ticketId: ticket.id };
}

function StaleRow({
  ticket,
  now,
  selected,
  onSelectedChange,
  onDelete,
  disabled,
}: {
  ticket: HomeStaleTicketResponse;
  now: number;
  selected: boolean;
  onSelectedChange: (selected: boolean) => void;
  onDelete: () => void;
  disabled: boolean;
}) {
  const t = useTranslations("home.staleBacklog");
  const lastActivity = t("daysAgo", { count: daysSince(ticket.lastActivityAt, now) });

  return (
    <tr className={cn("border-border border-t", selected && "bg-korur-blue-accent dark:bg-korur-slate")}>
      <td className="w-8 py-2 pl-2 align-middle">
        {ticket.canDelete && (
          <Checkbox
            checked={selected}
            onCheckedChange={(checked) => onSelectedChange(checked === true)}
            aria-label={t("selectTicket", { key: ticket.key })}
            disabled={disabled}
          />
        )}
      </td>
      <td className="w-24 py-2 pr-2 align-middle whitespace-nowrap">
        <TicketKeyLink
          ticketKey={ticket.key}
          className="text-korur-blue-strong dark:text-korur-blue text-xs font-medium hover:underline"
        >
          {ticket.key}
        </TicketKeyLink>
      </td>
      <td className="py-2 pr-2 align-middle">
        <span className="text-foreground block truncate text-sm">{ticket.title}</span>
        <span className="text-muted-foreground block truncate text-xs sm:hidden">
          {ticket.boardName}
          {" · "}
          {lastActivity}
        </span>
      </td>
      <td className="text-muted-foreground hidden w-40 truncate py-2 pr-2 align-middle text-sm sm:table-cell">
        {ticket.boardName}
      </td>
      <td className="text-muted-foreground hidden w-28 py-2 pr-2 align-middle text-sm whitespace-nowrap tabular-nums sm:table-cell">
        <time dateTime={ticket.lastActivityAt}>{lastActivity}</time>
      </td>
      <td className="w-10 py-1 align-middle">
        {ticket.canDelete && (
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label={t("deleteTicket", { key: ticket.key })}
            disabled={disabled}
            onClick={onDelete}
          >
            <Trash2 aria-hidden className="text-muted-foreground" />
          </Button>
        )}
      </td>
    </tr>
  );
}

/**
 * Backlog tickets on the user's boards that nobody edited or commented on for
 * 30 days, oldest first, so the backlog can be pruned from home. The server
 * sends at most 50 and the total; the title shows the total.
 *
 * Deleting is a soft delete through the same per-ticket requests as the board
 * views, always behind a confirmation, and the toast offers an Undo that
 * restores exactly the tickets that were deleted. Rows on a board where the
 * user is only a viewer carry no checkbox and no delete icon, because the
 * server would refuse the delete. After a partial failure the failed rows
 * stay selected, so a retry is one click.
 */
export function HomeStaleBacklog() {
  const t = useTranslations("home.staleBacklog");
  const { data, isPending, isError, refetch, isRefetching } = useHomeStaleBacklog();
  const bulkDelete = useBulkDeleteTickets({ successToast: false });
  const restore = useRestoreTickets();
  const [selectedIds, setSelectedIds] = useState<ReadonlySet<string>>(() => new Set());
  const [pendingDelete, setPendingDelete] = useState<readonly HomeStaleTicketResponse[] | null>(null);

  const backlog = data?.data;
  const tickets = backlog?.tickets ?? [];
  const deletable = tickets.filter((ticket) => ticket.canDelete);
  // Derived from the current rows, so a ticket that left the list on a
  // refetch never stays counted as selected.
  const selected = deletable.filter((ticket) => selectedIds.has(ticket.id));
  const allSelected = deletable.length > 0 && selected.length === deletable.length;
  const busy = bulkDelete.isPending || restore.isPending;
  const now = Date.now();

  const setTicketSelected = (ticketId: string, isSelected: boolean) => {
    setSelectedIds((previous) => {
      const next = new Set(previous);
      if (isSelected) {
        next.add(ticketId);
      } else {
        next.delete(ticketId);
      }
      return next;
    });
  };

  const undo = (targets: readonly BulkDeleteTicketTarget[]) => {
    restore.mutate(
      { targets },
      {
        onSuccess: (result) => {
          if (result.succeeded.length > 0) {
            toast.success(t("restored", { count: result.succeeded.length }));
          }
        },
      },
    );
  };

  const confirmDelete = () => {
    if (!pendingDelete || pendingDelete.length === 0) {
      return;
    }
    bulkDelete.mutate(
      { targets: pendingDelete.map(toTarget) },
      {
        onSuccess: (result) => {
          const deletedIds = new Set(result.succeeded.map((target) => target.ticketId));
          const failedIds = result.failed.map((failure) => failure.target.ticketId);
          setSelectedIds((previous) => {
            const next = new Set([...previous].filter((ticketId) => !deletedIds.has(ticketId)));
            failedIds.forEach((ticketId) => next.add(ticketId));
            return next;
          });
          if (result.succeeded.length > 0) {
            const deleted = result.succeeded;
            toast(t("deleted", { count: deleted.length }), {
              action: { label: t("undo"), onClick: () => undo(deleted) },
            });
          }
        },
      },
    );
    setPendingDelete(null);
  };

  const heading = backlog ? t("titleWithTotal", { total: backlog.total }) : t("title");

  return (
    <section aria-labelledby="home-stale-backlog-heading" className={SECTION_CLASS}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h2 id="home-stale-backlog-heading" className="text-foreground text-base font-semibold">
            {heading}
          </h2>
          <InfoTip label={t("infoLabel")}>{t("info")}</InfoTip>
        </div>
        {deletable.length > 0 && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="gap-2"
            disabled={selected.length === 0 || busy}
            onClick={() => setPendingDelete(selected)}
          >
            <Trash2 aria-hidden />
            {selected.length > 0
              ? t("deleteSelectedCount", { count: selected.length })
              : t("deleteSelected")}
          </Button>
        )}
      </div>

      {isError || (!isPending && !backlog) ? (
        <HomeSectionError
          message={t("error")}
          isRetrying={isRefetching}
          onRetry={() => void refetch()}
        />
      ) : isPending ? (
        <div aria-busy className="flex flex-col gap-2">
          {Array.from({ length: 4 }, (_, index) => (
            <Skeleton key={index} className="h-9 w-full" />
          ))}
        </div>
      ) : tickets.length === 0 ? (
        <p className="text-muted-foreground py-6 text-center text-sm">{t("empty")}</p>
      ) : (
        <table className="w-full table-fixed border-collapse text-left">
          <caption className="sr-only">{heading}</caption>
          <thead>
            <tr className="text-muted-foreground text-xs">
              <th scope="col" className="w-8 pb-2 pl-2 font-medium">
                {deletable.length > 0 && (
                  <Checkbox
                    checked={allSelected ? true : selected.length > 0 ? "indeterminate" : false}
                    onCheckedChange={(checked) =>
                      setSelectedIds(
                        checked === true ? new Set(deletable.map((ticket) => ticket.id)) : new Set(),
                      )
                    }
                    aria-label={t("selectAll")}
                    disabled={busy}
                  />
                )}
              </th>
              <th scope="col" className="w-24 pr-2 pb-2 font-medium">
                {t("columns.key")}
              </th>
              <th scope="col" className="pr-2 pb-2 font-medium">
                {t("columns.title")}
              </th>
              <th scope="col" className="hidden w-40 pr-2 pb-2 font-medium sm:table-cell">
                {t("columns.board")}
              </th>
              <th scope="col" className="hidden w-28 pr-2 pb-2 font-medium sm:table-cell">
                {t("columns.lastActivity")}
              </th>
              <th scope="col" className="w-10 pb-2">
                <span className="sr-only">{t("columns.actions")}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {tickets.map((ticket) => (
              <StaleRow
                key={ticket.id}
                ticket={ticket}
                now={now}
                selected={selectedIds.has(ticket.id)}
                onSelectedChange={(isSelected) => setTicketSelected(ticket.id, isSelected)}
                onDelete={() => setPendingDelete([ticket])}
                disabled={busy}
              />
            ))}
          </tbody>
        </table>
      )}

      <AlertDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => {
          if (!open) {
            setPendingDelete(null);
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {t("confirmTitle", { count: pendingDelete?.length ?? 0 })}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {t("confirmDescription", { count: pendingDelete?.length ?? 0 })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t("cancel")}</AlertDialogCancel>
            <AlertDialogAction
              className={buttonVariants({ variant: "destructive" })}
              onClick={confirmDelete}
            >
              {t("delete")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  );
}

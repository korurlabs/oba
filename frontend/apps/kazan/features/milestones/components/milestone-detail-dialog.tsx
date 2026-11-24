"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { CalendarRange, CheckCircle2, Loader2, Pencil, RotateCcw, Trash2, X } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Button, buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
import { useAllTickets } from "@/features/all-tickets/hooks/use-all-tickets";
import { TicketStatusIcon } from "@/features/tickets/components/ticket-status-icon";
import { ticketDetailPath } from "@/features/tickets/lib/ticket-route-params";
import {
  useCloseMilestone,
  useDeleteMilestone,
  useMilestone,
  useReopenMilestone,
  useUnlinkMilestoneTickets,
} from "../hooks/use-milestones";
import { formatDateOnly } from "@oba/shared/lib/date-only";
import { MILESTONE_HEALTH_STYLES } from "../lib/milestone-format";
import { MilestoneHealthBadge } from "./milestone-badge";
import { MilestoneFormDialog } from "./milestone-form-dialog";
import { MilestoneDependencies } from "./milestone-dependencies";
import { useBoardRole } from "@/features/boards/hooks/use-board-role";

interface MilestoneDetailDialogProps {
  /** Board the milestone belongs to (not necessarily the board being viewed). */
  boardId: string | undefined;
  milestoneId: string | undefined;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Dialog for one milestone: dates, progress, health, the attached tickets
 * and the admin actions. Every action is offered; the server answers a
 * too-low role with 403, which the mutation hooks surface as a toast.
 */
export function MilestoneDetailDialog({
  boardId,
  milestoneId,
  open,
  onOpenChange,
}: MilestoneDetailDialogProps) {
  const t = useTranslations("milestone");
  const tCommon = useTranslations("common");
  const tStatus = useTranslations("ticket.status");
  const locale = useLocale();
  const [showEdit, setShowEdit] = useState(false);
  const [showDelete, setShowDelete] = useState(false);

  const {
    data: detailResponse,
    isLoading,
    isError,
  } = useMilestone(
    open ? boardId : undefined,
    open ? milestoneId : undefined,
  );
  const milestone = detailResponse?.data;
  const { canManage } = useBoardRole(milestone?.boardId ?? "");

  const ticketParams = open && milestoneId ? { milestoneId } : undefined;
  const {
    data: ticketsResponse,
    isLoading: ticketsLoading,
    isError: ticketsError,
  } = useAllTickets(ticketParams);
  const tickets = ticketsResponse?.data ?? [];

  const closeMilestone = useCloseMilestone();
  const reopenMilestone = useReopenMilestone();
  const unlinkTickets = useUnlinkMilestoneTickets();
  const deleteMilestone = useDeleteMilestone({ onBeforeInvalidate: () => onOpenChange(false) });

  const percent =
    milestone && milestone.totalCount > 0
      ? Math.round((milestone.doneCount / milestone.totalCount) * 100)
      : 0;

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="flex max-h-[85vh] max-w-[calc(100%-2rem)] flex-col gap-0 overflow-hidden p-0 sm:max-w-2xl">
          <DialogHeader className="border-border border-b p-6 pb-4">
            {milestone ? (
              <>
                <DialogTitle className="flex items-center gap-2 pr-8 break-words">
                  {milestone.color && (
                    <span
                      className="h-3 w-3 shrink-0 rounded-sm"
                      style={{ backgroundColor: milestone.color }}
                      aria-hidden
                    />
                  )}
                  <span className="min-w-0">{milestone.name}</span>
                </DialogTitle>
                <DialogDescription>
                  {t("sheet.boardLine", {
                    board: milestone.boardName,
                    prefix: milestone.boardPrefix,
                  })}
                </DialogDescription>
              </>
            ) : (
              <>
                <DialogTitle className="sr-only">{t("sheet.loadingTitle")}</DialogTitle>
                <Skeleton className="h-6 w-48" />
                <Skeleton className="h-4 w-32" />
              </>
            )}
          </DialogHeader>

          <div className="min-h-0 flex-1 space-y-6 overflow-y-auto p-6">
            {isError ? (
              <p className="text-destructive text-sm">{t("sheet.loadError")}</p>
            ) : isLoading || !milestone ? (
              <div className="space-y-3">
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-2 w-full" />
              </div>
            ) : (
              <>
                <section className="space-y-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <MilestoneHealthBadge health={milestone.health} />
                    <span className="kazan-grid-chip text-muted-foreground">
                      {t(`status.${milestone.status}`)}
                    </span>
                  </div>
                  <div className="text-muted-foreground flex items-center gap-2 text-sm">
                    <CalendarRange className="h-4 w-4 shrink-0" aria-hidden />
                    <span>
                      {milestone.startDate
                        ? t("sheet.dateRange", {
                            start: formatDateOnly(milestone.startDate, locale),
                            target: formatDateOnly(milestone.targetDate, locale),
                          })
                        : t("sheet.targetOnly", {
                            target: formatDateOnly(milestone.targetDate, locale),
                          })}
                    </span>
                  </div>
                  <div className="space-y-1">
                    <div className="flex justify-between text-xs">
                      <span className="text-muted-foreground">{t("sheet.progress")}</span>
                      <span className="text-foreground font-medium">
                        {t("progressCount", {
                          done: milestone.doneCount,
                          total: milestone.totalCount,
                        })}
                      </span>
                    </div>
                    <div
                      className="bg-primary/10 h-2 w-full overflow-hidden"
                      role="progressbar"
                      aria-valuemin={0}
                      aria-valuemax={100}
                      aria-valuenow={percent}
                      aria-label={t("sheet.progress")}
                    >
                      <div
                        className={cn("h-full", MILESTONE_HEALTH_STYLES[milestone.health].bar)}
                        style={{ width: `${percent}%` }}
                      />
                    </div>
                  </div>
                  {milestone.description && (
                    <p className="text-foreground text-sm whitespace-pre-wrap">
                      {milestone.description}
                    </p>
                  )}
                </section>

                <div className="flex flex-wrap gap-2">
                  <Button size="sm" variant="outline" onClick={() => setShowEdit(true)}>
                    <Pencil className="h-3.5 w-3.5" />
                    {t("actions.edit")}
                  </Button>
                  {milestone.status === "OPEN" ? (
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={closeMilestone.isPending}
                      onClick={() =>
                        closeMilestone.mutate({
                          boardId: milestone.boardId,
                          milestoneId: milestone.id,
                        })
                      }
                    >
                      <CheckCircle2 className="h-3.5 w-3.5" />
                      {t("actions.close")}
                    </Button>
                  ) : (
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={reopenMilestone.isPending}
                      onClick={() =>
                        reopenMilestone.mutate({
                          boardId: milestone.boardId,
                          milestoneId: milestone.id,
                        })
                      }
                    >
                      <RotateCcw className="h-3.5 w-3.5" />
                      {t("actions.reopen")}
                    </Button>
                  )}
                  <Button size="sm" variant="destructive" onClick={() => setShowDelete(true)}>
                    <Trash2 className="h-3.5 w-3.5" />
                    {tCommon("delete")}
                  </Button>
                </div>

                <MilestoneDependencies milestone={milestone} canManage={canManage} />

                <section className="space-y-2">
                  <h3 className="text-foreground border-border border-b pb-1 text-xs font-semibold">
                    {t("sheet.tickets", { count: tickets.length })}
                  </h3>
                  {ticketsLoading ? (
                    <div className="space-y-2">
                      <Skeleton className="h-8 w-full" />
                      <Skeleton className="h-8 w-full" />
                    </div>
                  ) : ticketsError ? (
                    <p className="text-destructive text-sm">{t("sheet.ticketsLoadError")}</p>
                  ) : tickets.length === 0 ? (
                    <p className="text-muted-foreground text-sm">{t("sheet.noTickets")}</p>
                  ) : (
                    <ul className="divide-border divide-y">
                      {tickets.map((ticket) => (
                        <li key={ticket.id} className="flex items-center gap-2 py-2">
                          <TicketStatusIcon status={ticket.status} />
                          {ticket.board?.slug ? (
                            <Link
                              href={ticketDetailPath(ticket.board.slug, ticket.key)}
                              className="hover:text-primary flex min-w-0 flex-1 items-center gap-2 text-sm"
                            >
                              <span className="text-muted-foreground shrink-0 font-mono text-xs">
                                {ticket.key}
                              </span>
                              <span className="truncate" title={ticket.title}>
                                {ticket.title}
                              </span>
                            </Link>
                          ) : (
                            <span className="flex min-w-0 flex-1 items-center gap-2 text-sm">
                              <span className="text-muted-foreground shrink-0 font-mono text-xs">
                                {ticket.key}
                              </span>
                              <span className="truncate" title={ticket.title}>
                                {ticket.title}
                              </span>
                            </span>
                          )}
                          <span className="text-muted-foreground shrink-0 text-xs">
                            {tStatus(ticket.status)}
                          </span>
                          <Button
                            size="icon"
                            variant="ghost"
                            className="h-7 w-7 shrink-0"
                            aria-label={t("actions.detachTicket", { key: ticket.key })}
                            disabled={unlinkTickets.isPending}
                            onClick={() =>
                              unlinkTickets.mutate({
                                boardId: milestone.boardId,
                                milestoneId: milestone.id,
                                data: { ticketIds: [ticket.id] },
                              })
                            }
                          >
                            <X className="h-3.5 w-3.5" />
                          </Button>
                        </li>
                      ))}
                    </ul>
                  )}
                </section>
              </>
            )}
          </div>
        </DialogContent>
      </Dialog>

      {milestone && (
        <MilestoneFormDialog
          open={showEdit}
          onOpenChange={setShowEdit}
          boardId={milestone.boardId}
          milestone={milestone}
        />
      )}

      <AlertDialog open={showDelete} onOpenChange={setShowDelete}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("deleteDialog.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("deleteDialog.description", { name: milestone?.name ?? "" })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{tCommon("cancel")}</AlertDialogCancel>
            <AlertDialogAction
              className={buttonVariants({ variant: "destructive" })}
              disabled={deleteMilestone.isPending}
              onClick={() => {
                if (!milestone) return;
                deleteMilestone.mutate({ boardId: milestone.boardId, milestoneId: milestone.id });
              }}
            >
              {deleteMilestone.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {tCommon("delete")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

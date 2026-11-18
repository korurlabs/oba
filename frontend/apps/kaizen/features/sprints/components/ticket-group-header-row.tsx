"use client";

import { useTranslations } from "next-intl";
import { TicketStatus } from "@/features/tickets/schemas";
import { SubTicketProgressBar } from "@/features/tickets/components/sub-ticket-progress-bar";
import { subTicketProgress, type TicketGroupHeaderRow } from "@/lib/utils/ticket-groups";

interface TicketGroupHeaderRowContentProps {
  header: TicketGroupHeaderRow;
  /** Sprint the list shows; none on the backlog. */
  currentSprintId?: string;
}

/**
 * Content of the header row a sprint or backlog table shows for a parent
 * that is planned elsewhere: its key, title and progress over all its
 * sub-tickets, wherever they are planned. Used by both lists, so a parent
 * reads the same in each.
 */
export function TicketGroupHeaderRowContent({
  header,
  currentSprintId,
}: TicketGroupHeaderRowContentProps) {
  const t = useTranslations("ticket.groupHeader");
  const progress = subTicketProgress(header.groupParent.subTickets, currentSprintId);

  return (
    <div className="flex min-w-0 items-center gap-3">
      <span className="font-mono text-xs font-medium whitespace-nowrap">{header.key}</span>
      <span
        className={
          header.status === TicketStatus.DONE ? "truncate text-sm line-through" : "truncate text-sm"
        }
      >
        {header.title}
      </span>
      <span className="text-[10px] tracking-wide whitespace-nowrap uppercase">{t("parent")}</span>
      <SubTicketProgressBar
        className="ml-auto w-40 shrink-0"
        completed={progress.done}
        total={progress.total}
        breakdown={progress}
      />
    </div>
  );
}

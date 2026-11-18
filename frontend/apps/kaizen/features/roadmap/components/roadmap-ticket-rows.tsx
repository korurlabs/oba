"use client";

import type { CSSProperties, ReactNode } from "react";
import { useTranslations } from "next-intl";
import { cn } from "@oba/ui/lib/utils";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Skeleton } from "@/components/ui/skeleton";
import { useAllTickets } from "@/features/all-tickets/hooks/use-all-tickets";
import { TicketStatusIcon } from "@/features/tickets/components/ticket-status-icon";
import { ticketDetailPath } from "@/features/tickets/lib/ticket-route-params";
import type { TicketStatus } from "@/features/tickets/schemas";

export const TICKET_ROW_HEIGHT_PX = 32;

const STATUS_CHIP: Record<TicketStatus, string> = {
  TODO: "bg-muted text-muted-foreground",
  IN_PROGRESS: "bg-blue-500/15 text-blue-700 dark:text-blue-300",
  WAITING_FOR_THIRD_PARTY: "bg-amber-500/15 text-amber-700 dark:text-amber-300",
  IN_REVIEW: "bg-purple-500/15 text-purple-700 dark:text-purple-300",
  DONE: "bg-green-500/15 text-green-700 dark:text-green-300",
};

interface RoadmapTicketRowsProps {
  milestoneId: string;
  nameColumnWidth: number;
  /** Background shared with the milestone row, so the expanded block reads as one. */
  rowStyle: CSSProperties;
}

/**
 * The tickets of an expanded milestone, one row each: status, key and title
 * (a link to the ticket) in the name column and a status chip at the left
 * edge of the visible timeline. Mounted only while the milestone is
 * expanded, so the tickets load on demand.
 */
export function RoadmapTicketRows({ milestoneId, nameColumnWidth, rowStyle }: RoadmapTicketRowsProps) {
  const t = useTranslations("roadmap");
  const tStatus = useTranslations("ticket.status");
  const { data, isLoading, isError } = useAllTickets({ milestoneId });
  const tickets = data?.data ?? [];

  const message = (content: ReactNode, className?: string) => (
    <TicketRowShell nameColumnWidth={nameColumnWidth} rowStyle={rowStyle}>
      <span className={cn("text-muted-foreground truncate text-xs", className)}>{content}</span>
    </TicketRowShell>
  );

  if (isLoading) {
    return message(<Skeleton className="h-3 w-32" />);
  }
  if (isError) {
    return message(t("tickets.loadError"), "text-destructive");
  }
  if (tickets.length === 0) {
    return message(t("tickets.none"));
  }

  return (
    <>
      {tickets.map((ticket) => {
        const content = (
          <>
            <TicketStatusIcon status={ticket.status} />
            <span className="text-muted-foreground shrink-0 font-mono text-[11px]">
              {ticket.key}
            </span>
            <span className="text-foreground truncate text-xs" title={ticket.title}>
              {ticket.title}
            </span>
          </>
        );
        return (
          <TicketRowShell
            key={ticket.id}
            nameColumnWidth={nameColumnWidth}
            rowStyle={rowStyle}
            chip={
              <span
                className={cn(
                  "rounded px-1.5 py-0.5 text-[10px] font-semibold tracking-wide whitespace-nowrap uppercase",
                  STATUS_CHIP[ticket.status],
                )}
              >
                {tStatus(ticket.status)}
              </span>
            }
          >
            {ticket.board?.slug ? (
              <Link
                href={ticketDetailPath(ticket.board.slug, ticket.key)}
                className="hover:text-primary focus-visible:ring-ring flex min-w-0 flex-1 items-center gap-2 rounded-sm focus-visible:ring-2 focus-visible:outline-none"
              >
                {content}
              </Link>
            ) : (
              <span className="flex min-w-0 flex-1 items-center gap-2">{content}</span>
            )}
          </TicketRowShell>
        );
      })}
    </>
  );
}

function TicketRowShell({
  nameColumnWidth,
  rowStyle,
  chip,
  children,
}: {
  nameColumnWidth: number;
  rowStyle: CSSProperties;
  chip?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="flex" style={{ ...rowStyle, height: TICKET_ROW_HEIGHT_PX }}>
      <div
        className="border-border sticky left-0 z-20 flex shrink-0 items-center gap-2 border-r pr-2 pl-12"
        style={{ ...rowStyle, width: nameColumnWidth }}
      >
        {children}
      </div>
      <div className="relative flex flex-1 items-center">
        {chip && (
          <div className="sticky z-10 px-2" style={{ left: nameColumnWidth }}>
            {chip}
          </div>
        )}
      </div>
    </div>
  );
}

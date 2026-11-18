"use client";

import { useId, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { X } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { UpdateTicketResponse } from "../schemas";
import { UpdateTicketKey } from "./update-ticket-key";

interface SelectedTicketsSectionProps {
  title: string;
  /** How many tickets are selected, shown as "{count} of {max}". */
  count: number;
  /** The most tickets the form can send; above it the count turns red and `tooManyMessage` shows. */
  max: number;
  tooManyMessage: string;
  /** Shown instead of the list while `isEmpty` is true. */
  emptyMessage: string;
  isEmpty: boolean;
  /** Buttons beside the count, such as a bulk add. */
  actions?: ReactNode;
  /** The rows, each an `li`. */
  children: ReactNode;
}

/**
 * The card that lists the tickets ticked in a ticket picker, in the order
 * they were picked, with the count against the limit. Going over the limit
 * is not prevented here: the count turns red and an alert names the limit,
 * and the form's validation keeps it from being sent.
 */
export function SelectedTicketsSection({
  title,
  count,
  max,
  tooManyMessage,
  emptyMessage,
  isEmpty,
  actions,
  children,
}: SelectedTicketsSectionProps) {
  const t = useTranslations("updates.form");
  const titleId = useId();
  const tooMany = count > max;

  return (
    <section aria-labelledby={titleId} className="border-border bg-card rounded-lg border">
      <div className="border-border flex flex-wrap items-center gap-2 border-b px-4 py-3">
        <h2 id={titleId} className="text-foreground text-sm font-semibold">
          {title}
        </h2>
        <div className="ml-auto flex items-center gap-2">
          {actions}
          <span
            className={cn(
              "text-muted-foreground text-xs tabular-nums",
              tooMany && "text-destructive font-medium",
            )}
          >
            {t("selectedCount", { count, max })}
          </span>
        </div>
      </div>

      {tooMany && (
        <p role="alert" className="text-destructive px-4 pt-3 text-[0.8rem] font-medium">
          {tooManyMessage}
        </p>
      )}

      {isEmpty ? (
        <p className="text-muted-foreground px-4 py-8 text-center text-sm">{emptyMessage}</p>
      ) : (
        <ul className="divide-border divide-y">{children}</ul>
      )}
    </section>
  );
}

interface SelectedTicketSummaryProps {
  ticket: Pick<UpdateTicketResponse, "boardId" | "boardName" | "ticketKey" | "ticketTitle">;
  /** Accessible name and tooltip of the remove button; it names the ticket. */
  removeLabel: string;
  onRemove: () => void;
  disabled?: boolean;
}

/**
 * The head of a selected ticket row: the board as an outline badge, the key,
 * the title and a remove button. The key is plain text, because following a
 * link here would leave the unsent form.
 */
export function SelectedTicketSummary({
  ticket,
  removeLabel,
  onRemove,
  disabled = false,
}: SelectedTicketSummaryProps) {
  return (
    <div className="flex min-w-0 items-start gap-2">
      <div className="flex min-w-0 flex-1 flex-wrap items-center gap-x-2 gap-y-1">
        {ticket.boardName && (
          <Badge variant="outline" className="max-w-40 truncate text-[11px] font-medium">
            {ticket.boardName}
          </Badge>
        )}
        <UpdateTicketKey item={{ ...ticket, ticketId: null, canOpen: false }} />
        <span className="text-foreground min-w-0 text-sm break-words">{ticket.ticketTitle}</span>
      </div>
      {/* type="button": the row sits inside a form. */}
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        className="text-muted-foreground hover:text-foreground shrink-0"
        aria-label={removeLabel}
        title={removeLabel}
        disabled={disabled}
        onClick={onRemove}
      >
        <X className="h-4 w-4" />
      </Button>
    </div>
  );
}

/**
 * Marks a selected ticket the reader can no longer open: they left its
 * board, or it was deleted. `hint` says what happens to the row.
 */
export function SelectedTicketNoAccessBadge({ hint }: { hint: string }) {
  const t = useTranslations("updates.form");
  return (
    <Badge variant="outline" className="text-muted-foreground text-[11px] font-normal" title={hint}>
      {t("noAccess")}
    </Badge>
  );
}

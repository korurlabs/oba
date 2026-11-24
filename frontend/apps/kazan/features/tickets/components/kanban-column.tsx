"use client";

import type { ReactNode } from "react";
import { Plus } from "lucide-react";

/**
 * The parts every kanban column shares (sprint, backlog and all tickets), in
 * Kazan's grid look: a mono label over a hairline, the count in a square, and
 * dashed neutral slots. Status is told by the label and the column position,
 * never by a colour, so the board reads the same for every reader.
 */

interface KanbanColumnHeaderProps {
  title: string;
  /** The column's position from 1, shown as "01". Left out for a single column. */
  index?: number;
  /** A line under the label that explains what the column holds. */
  hint?: ReactNode;
  count: number;
}

export function KanbanColumnHeader({ title, index, hint, count }: KanbanColumnHeaderProps) {
  return (
    <div className="border-border flex items-center justify-between gap-2 border-b pb-2">
      <div className="flex min-w-0 flex-col gap-1">
        <h3 className="kazan-grid-label inline-flex min-w-0 items-center gap-1.5">
          {index !== undefined && (
            <>
              <span className="tabular-nums">{String(index).padStart(2, "0")}</span>
              <span aria-hidden>·</span>
            </>
          )}
          <span className="truncate">{title}</span>
        </h3>
        {hint && <p className="text-muted-foreground text-xs">{hint}</p>}
      </div>
      {/* The count can run to three digits, so the square widens instead of clipping. */}
      <span className="kazan-grid-number h-5 w-auto min-w-5 px-1">{count}</span>
    </div>
  );
}

/** Holds the header's place while the tickets load. */
export function KanbanColumnHeaderSkeleton() {
  return (
    <div className="border-border flex items-center justify-between gap-2 border-b pb-2">
      <div className="bg-muted h-4 w-24 animate-pulse" />
      <div className="bg-muted size-5 animate-pulse" />
    </div>
  );
}

interface KanbanEmptyColumnProps {
  message: string;
  /** The plus mark only hints at adding a ticket, so it shows only to someone who can. */
  showAddHint?: boolean;
}

export function KanbanEmptyColumn({ message, showAddHint = false }: KanbanEmptyColumnProps) {
  return (
    <div className="border-border flex flex-1 flex-col items-center justify-center rounded-none border border-dashed py-8">
      {showAddHint && (
        <div className="border-border mb-3 flex size-10 items-center justify-center border border-dashed">
          <Plus className="text-muted-foreground size-5" />
        </div>
      )}
      <p className="text-muted-foreground text-center text-sm">{message}</p>
    </div>
  );
}

interface KanbanAddTicketButtonProps {
  label: string;
  onClick: () => void;
}

export function KanbanAddTicketButton({ label, onClick }: KanbanAddTicketButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="text-foreground border-border hover:border-primary hover:text-primary hover:bg-primary/5 dark:hover:bg-accent dark:hover:text-foreground mt-2 flex w-full cursor-pointer items-center justify-center gap-2 rounded-none border border-dashed py-2.5 text-sm transition-colors duration-200"
    >
      <Plus className="size-4" />
      {label}
    </button>
  );
}

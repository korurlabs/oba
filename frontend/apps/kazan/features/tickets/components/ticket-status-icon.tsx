/**
 * TicketStatusIcon Component
 *
 * The ticket status as a small square glyph in neutral ink, Kazan's grid
 * marker. Each status has its own shape, so a list reads the same for every
 * reader and on any screen, without telling status apart by colour:
 * - TODO: an empty square
 * - IN_PROGRESS: the left half filled
 * - WAITING_FOR_THIRD_PARTY: a dashed square, the work is parked outside
 * - IN_REVIEW: a square with a filled core
 * - DONE: a solid square in the primary ink
 *
 * An unknown status, such as one added on the server before this client knows
 * it, falls back to the TODO entry, so a page that lists it does not crash.
 */

import type { ReactNode } from 'react';
import type { TicketStatus } from '@/features/tickets/schemas';
import { cn } from '@oba/ui/lib/utils';

interface TicketStatusIconProps {
  status: TicketStatus;
  className?: string;
  size?: number;
}

/* The glyphs are drawn on a 10 by 10 grid; the frame's stroke sits on the half pixel. */
const FRAME = { x: 0.75, y: 0.75, width: 8.5, height: 8.5 };

const statusConfig: Record<TicketStatus, { label: string; glyph: ReactNode; colorClass: string }> = {
  TODO: {
    label: 'Todo',
    colorClass: 'text-foreground/55',
    glyph: <rect {...FRAME} fill="none" stroke="currentColor" strokeWidth={1.5} />,
  },
  IN_PROGRESS: {
    label: 'In Progress',
    colorClass: 'text-foreground/75',
    glyph: (
      <>
        <rect {...FRAME} fill="none" stroke="currentColor" strokeWidth={1.5} />
        <rect x={0.75} y={0.75} width={4.25} height={8.5} fill="currentColor" />
      </>
    ),
  },
  WAITING_FOR_THIRD_PARTY: {
    label: 'Waiting for 3rd party',
    colorClass: 'text-foreground/75',
    glyph: (
      <rect {...FRAME} fill="none" stroke="currentColor" strokeWidth={1.5} strokeDasharray="2 1.4" />
    ),
  },
  IN_REVIEW: {
    label: 'In Review',
    colorClass: 'text-foreground/75',
    glyph: (
      <>
        <rect {...FRAME} fill="none" stroke="currentColor" strokeWidth={1.5} />
        <rect x={3} y={3} width={4} height={4} fill="currentColor" />
      </>
    ),
  },
  DONE: {
    label: 'Done',
    colorClass: 'text-korur-blue-strong',
    glyph: <rect x={0} y={0} width={10} height={10} fill="currentColor" />,
  },
};

function getStatusConfig(status: TicketStatus): (typeof statusConfig)[TicketStatus] {
  return statusConfig[status] ?? statusConfig.TODO;
}

export function TicketStatusIcon({ status, className, size = 10 }: TicketStatusIconProps) {
  const config = getStatusConfig(status);

  return (
    <svg
      viewBox="0 0 10 10"
      width={size}
      height={size}
      className={cn('inline-block shrink-0', config.colorClass, className)}
      role="img"
      aria-label={`Status: ${config.label}`}
    >
      {config.glyph}
    </svg>
  );
}

// Utility to get status display name
export function getStatusLabel(status: TicketStatus): string {
  return getStatusConfig(status).label;
}

// Utility to get the status glyph's ink class
export function getStatusColorClass(status: TicketStatus): string {
  return getStatusConfig(status).colorClass;
}

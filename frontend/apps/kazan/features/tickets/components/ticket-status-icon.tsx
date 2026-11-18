/**
 * TicketStatusIcon Component
 *
 * Displays a colored dot for ticket status levels
 * Colors match the Kanban column colors:
 * - TODO: gray
 * - IN_PROGRESS: blue
 * - WAITING_FOR_THIRD_PARTY: amber
 * - IN_REVIEW: purple
 * - DONE: green
 *
 * An unknown status, such as one added on the server before this client knows
 * it, falls back to the TODO entry, so a page that lists it does not crash.
 */

import type { TicketStatus } from '@/features/tickets/schemas';
import { cn } from '@oba/ui/lib/utils';

interface TicketStatusIconProps {
  status: TicketStatus;
  className?: string;
  size?: number;
}

const statusConfig: Record<
  TicketStatus,
  { colorClass: string; label: string }
> = {
  TODO: {
    colorClass: 'bg-gray-400',
    label: 'Todo',
  },
  IN_PROGRESS: {
    colorClass: 'bg-blue-400',
    label: 'In Progress',
  },
  WAITING_FOR_THIRD_PARTY: {
    colorClass: 'bg-amber-400',
    label: 'Waiting for 3rd party',
  },
  IN_REVIEW: {
    colorClass: 'bg-purple-400',
    label: 'In Review',
  },
  DONE: {
    colorClass: 'bg-green-400',
    label: 'Done',
  },
};

function getStatusConfig(status: TicketStatus): { colorClass: string; label: string } {
  return statusConfig[status] ?? statusConfig.TODO;
}

export function TicketStatusIcon({
  status,
  className,
  size = 8,
}: TicketStatusIconProps) {
  const config = getStatusConfig(status);

  return (
    <span
      className={cn('inline-block shrink-0 rounded-full', config.colorClass, className)}
      style={{ width: size, height: size }}
      role="img"
      aria-label={`Status: ${config.label}`}
    />
  );
}

// Utility to get status display name
export function getStatusLabel(status: TicketStatus): string {
  return getStatusConfig(status).label;
}

// Utility to get status color class
export function getStatusColorClass(status: TicketStatus): string {
  return getStatusConfig(status).colorClass;
}

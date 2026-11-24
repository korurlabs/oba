/**
 * TicketPriorityIcon Component
 *
 * The priority as an arrow in muted ink: the arrow's shape and direction tell
 * the level, and the top of each scale (HIGHEST, and P1 for incidents) is the
 * only one in red, so the tickets that need attention now stand out instead of
 * every level competing for it. HIGH and P2 sit in full ink, one step above
 * the muted rest.
 */

import type { TicketPriority as Priority } from '@/features/tickets/schemas';
import {
  ArrowDown, ChevronsUp,
  ChevronDown,
  AlertTriangle,
  ChevronUp,
  ChevronsDown,
  Minus
} from 'lucide-react';
import { cn } from '@oba/ui/lib/utils';

interface TicketPriorityIconProps {
  priority: Priority;
  className?: string;
  size?: number;
}

const priorityConfig: Record<
  Priority,
  { icon: typeof ArrowDown; className: string; label: string }
> = {
  LOWEST: {
    icon: ChevronsDown,
    className: 'text-muted-foreground',
    label: 'Lowest',
  },
  LOW: {
    icon: ChevronDown,
    className: 'text-muted-foreground',
    label: 'Low',
  },
  MEDIUM: {
    icon: Minus,
    className: 'text-muted-foreground',
    label: 'Medium',
  },
  HIGH: {
    icon: ChevronUp,
    className: 'text-foreground',
    label: 'High',
  },
  HIGHEST: {
    icon: ChevronsUp,
    className: 'text-red-600',
    label: 'Highest',
  },
  P1: {
    icon: AlertTriangle,
    className: 'text-red-600',
    label: 'P1',
  },
  P2: {
    icon: ChevronsUp,
    className: 'text-foreground',
    label: 'P2',
  },
  P3: {
    icon: ChevronUp,
    className: 'text-muted-foreground',
    label: 'P3',
  },
};

export function TicketPriorityIcon({
  priority,
  className,
  size = 16,
}: TicketPriorityIconProps) {
  const config = priorityConfig[priority];
  const Icon = config.icon;

  return (
    <Icon
      className={cn(config.className, className)}
      size={size}
      aria-label={`Priority: ${config.label}`}
    />
  );
}

// Utility to get priority display name
export function getPriorityLabel(priority: Priority): string {
  return priorityConfig[priority].label;
}

// Utility to get priority color class
export function getPriorityColorClass(priority: Priority): string {
  return priorityConfig[priority].className;
}

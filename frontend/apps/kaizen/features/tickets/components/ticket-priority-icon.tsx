/**
 * TicketPriorityIcon Component
 *
 * Displays an icon for ticket priority levels
 * Per visual-design-reference.md section 7:
 * - LOWEST: gray arrow down
 * - LOW: blue arrow down
 * - MEDIUM: gray arrow right
 * - HIGH: orange arrow up
 * - HIGHEST: red double arrow up
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
    className: 'text-gray-400',
    label: 'Lowest',
  },
  LOW: {
    icon: ChevronDown,
    className: 'text-blue-500',
    label: 'Low',
  },
  MEDIUM: {
    icon: Minus,
    className: 'text-gray-500',
    label: 'Medium',
  },
  HIGH: {
    icon: ChevronUp,
    className: 'text-orange-500',
    label: 'High',
  },
  HIGHEST: {
    icon: ChevronsUp,
    className: 'text-red-500',
    label: 'Highest',
  },
  P1: {
    icon: AlertTriangle,
    className: 'text-red-600',
    label: 'P1',
  },
  P2: {
    icon: ChevronsUp,
    className: 'text-orange-600',
    label: 'P2',
  },
  P3: {
    icon: ChevronUp,
    className: 'text-yellow-600',
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

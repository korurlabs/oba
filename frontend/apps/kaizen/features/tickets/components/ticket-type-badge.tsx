/**
 * TicketTypeBadge Component
 *
 * Displays a colored badge for ticket types
 * - BUG: red
 * - FEATURE: green
 * - TECH_DEBT: amber
 * - IMPROVEMENT: blue
 * - INCIDENT: Korur blue
 * - PROBLEM: fuchsia
 * - REFACTOR: slate
 *
 * An unknown type, such as one added on the server before this client knows
 * it, renders with the FEATURE colours and its own name, so the card still shows.
 */

import type { TicketType } from '@/features/tickets/schemas';
import { cn } from '@oba/ui/lib/utils';

interface TicketTypeBadgeProps {
  type: TicketType;
  size?: 'sm' | 'md';
  className?: string;
}

const typeConfig: Record<TicketType, { label: string; className: string }> = {
  BUG: {
    label: 'BUG',
    className: 'bg-red-100 dark:bg-red-900 text-red-600 dark:text-red-400',
  },
  FEATURE: {
    label: 'FEATURE',
    className: 'bg-green-100 dark:bg-green-900 text-green-600 dark:text-green-400',
  },
  TECH_DEBT: {
    label: 'TECH DEBT',
    className: 'bg-amber-100 dark:bg-amber-900 text-amber-700 dark:text-amber-400',
  },
  IMPROVEMENT: {
    label: 'IMPROVEMENT',
    className: 'bg-blue-100 dark:bg-blue-900 text-blue-600 dark:text-blue-400',
  },
  INCIDENT: {
    label: 'INCIDENT',
    className: 'bg-korur-blue-light dark:bg-korur-slate text-korur-blue-strong dark:text-korur-blue',
  },
  PROBLEM: {
    label: 'PROBLEM',
    className: 'bg-fuchsia-100 dark:bg-fuchsia-900 text-fuchsia-600 dark:text-fuchsia-400',
  },
  REFACTOR: {
    label: 'REFACTOR',
    className: 'bg-slate-200 dark:bg-slate-800 text-slate-600 dark:text-slate-300',
  },
};

export function TicketTypeBadge({
  type,
  size = 'md',
  className,
}: TicketTypeBadgeProps) {
  const config = typeConfig[type] ?? { ...typeConfig.FEATURE, label: String(type) };

  return (
    <span
      className={cn(
        'inline-flex items-center justify-center rounded',
        size === 'sm' ? 'px-1 py-0.5 text-[9px]' : 'px-1.5 py-0.5 text-[10px]',
        'font-semibold uppercase tracking-wider',
        config.className,
        className
      )}
    >
      {config.label}
    </span>
  );
}

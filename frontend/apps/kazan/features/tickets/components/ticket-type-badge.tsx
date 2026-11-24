/**
 * TicketTypeBadge Component
 *
 * The ticket type as a square outline chip in neutral ink. Red marks what is
 * broken, graded by urgency so a reader scanning a board finds the worst
 * first: an INCIDENT is live damage and gets a solid red chip, a BUG a red
 * outline, and a PROBLEM (a root cause still being found) only a faint red
 * tint. Every other type is plain information and stays calm.
 *
 * An unknown type, such as one added on the server before this client knows
 * it, renders as a neutral chip with its own name, so the card still shows.
 */

import type { TicketType } from '@/features/tickets/schemas';
import { cn } from '@oba/ui/lib/utils';

interface TicketTypeBadgeProps {
  type: TicketType;
  size?: 'sm' | 'md';
  className?: string;
}

const INCIDENT_CLASS = 'border-red-600 bg-red-600 text-white dark:border-red-500 dark:bg-red-500';
const BUG_CLASS = 'border-red-600/40 text-red-700 dark:border-red-400/50 dark:text-red-400';
const PROBLEM_CLASS = 'border-red-600/20 text-red-900/70 dark:border-red-400/25 dark:text-red-300/80';

const typeConfig: Record<TicketType, { label: string; className?: string }> = {
  BUG: { label: 'BUG', className: BUG_CLASS },
  FEATURE: { label: 'FEATURE' },
  TECH_DEBT: { label: 'TECH DEBT' },
  IMPROVEMENT: { label: 'IMPROVEMENT' },
  INCIDENT: { label: 'INCIDENT', className: INCIDENT_CLASS },
  PROBLEM: { label: 'PROBLEM', className: PROBLEM_CLASS },
  REFACTOR: { label: 'REFACTOR' },
};

export function TicketTypeBadge({
  type,
  size = 'md',
  className,
}: TicketTypeBadgeProps) {
  const config = typeConfig[type] ?? { label: String(type) };

  return (
    <span
      className={cn(
        'kazan-grid-chip justify-center font-semibold',
        size === 'sm' ? 'px-1 text-[10px]' : 'text-[11px]',
        config.className,
        className
      )}
    >
      {config.label}
    </span>
  );
}

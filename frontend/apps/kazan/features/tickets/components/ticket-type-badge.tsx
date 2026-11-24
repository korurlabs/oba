/**
 * TicketTypeBadge Component
 *
 * The ticket type as a square outline chip in neutral ink. Only BUG and
 * INCIDENT carry a red accent, because they report something broken and a
 * reader scanning a board should find them first; every other type is plain
 * information and stays calm.
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

const ALERT_CLASS = 'border-red-600/40 text-red-700 dark:border-red-400/50 dark:text-red-400';

const typeConfig: Record<TicketType, { label: string; className?: string }> = {
  BUG: { label: 'BUG', className: ALERT_CLASS },
  FEATURE: { label: 'FEATURE' },
  TECH_DEBT: { label: 'TECH DEBT' },
  IMPROVEMENT: { label: 'IMPROVEMENT' },
  INCIDENT: { label: 'INCIDENT', className: ALERT_CLASS },
  PROBLEM: { label: 'PROBLEM' },
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

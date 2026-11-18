/**
 * The one urgency scale of ticket priorities, shared by every view that
 * orders tickets by priority, so they never disagree.
 *
 * Not marked "use client": it holds no component, and a server module may
 * import it.
 */
import type { TicketPriority } from "../schemas";

// Urgency rank, ascending. INCIDENT priorities (P1 to P3) share the field with the
// LOWEST to HIGHEST scale, so both are interleaved here in the same order as the
// backend enum kaizen_ticket_priority_enum, which orders server-side sorts.
export const PRIORITY_ORDER: Record<TicketPriority, number> = {
  LOWEST: 0,
  LOW: 1,
  MEDIUM: 2,
  P3: 3,
  HIGH: 4,
  HIGHEST: 5,
  P2: 6,
  P1: 7,
};

/**
 * Comparator for `Array.prototype.sort`: the most urgent priority first and a
 * missing priority last. Equal priorities compare as 0, so the stable sort
 * keeps their incoming order.
 */
export function compareByUrgency(
  a: TicketPriority | null | undefined,
  b: TicketPriority | null | undefined,
): number {
  const rankA = a ? PRIORITY_ORDER[a] : -1;
  const rankB = b ? PRIORITY_ORDER[b] : -1;
  return rankB - rankA;
}

/**
 * A copy of `items` ordered most urgent first, a missing priority last. The
 * sort is stable, so items of equal priority keep the order they arrive in:
 * the author's order on a daily update card, the board order or the page sort
 * in a kanban column.
 */
export function sortByPriority<T extends { priority?: TicketPriority | null }>(
  items: readonly T[],
): T[] {
  return [...items].sort((a, b) => compareByUrgency(a.priority, b.priority));
}

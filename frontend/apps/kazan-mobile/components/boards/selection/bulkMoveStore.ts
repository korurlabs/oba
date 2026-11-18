import { useSyncExternalStore } from "react";

import type { BulkMoveSource } from "@/components/boards/selection/bulkMoveModel";

/*
 * The Move sheet is a route of its own, so it cannot take the selection as a
 * prop. The board screen puts the selection here before it opens the sheet,
 * and the sheet reads it. One Move sheet is open at a time; a sheet restored
 * from a link finds the store empty.
 */

/** The tickets a Move sheet moves and where they come from. */
export interface BulkMoveSelection {
  /** The viewed board, which the request is sent to. */
  boardId: string;
  /** The selected tickets, in the order the list shows them. */
  ticketIds: string[];
  source: BulkMoveSource;
  /** The viewed sprint the tickets come from; null for the backlog. */
  currentSprintId: string | null;
  /** A selected ticket has sub-tickets, so the sub-ticket choice may show. */
  hasSubTickets: boolean;
  /** Ends selection mode once the tickets moved. */
  onMoved: () => void;
}

let open: BulkMoveSelection | null = null;
const listeners = new Set<() => void>();

function notify(): void {
  for (const listener of listeners) listener();
}

/** Hands `selection` to the Move sheet that opens next. */
export function showBulkMove(selection: BulkMoveSelection): void {
  open = selection;
  notify();
}

/** Clears the selection only when it is still `owned`, so a newer sheet's selection survives. */
export function clearBulkMove(owned: BulkMoveSelection | null): void {
  if (owned && open === owned) {
    open = null;
    notify();
  }
}

/** The selection of the Move sheet, or null without one. */
export function getBulkMove(): BulkMoveSelection | null {
  return open;
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The selection of the Move sheet, re-rendering when it changes. */
export function useBulkMove(): BulkMoveSelection | null {
  return useSyncExternalStore(subscribe, getBulkMove, getBulkMove);
}

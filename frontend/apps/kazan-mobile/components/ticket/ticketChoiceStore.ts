import { useSyncExternalStore } from "react";

import type { ChoicePicker } from "@/components/ticket/ticketPickers";

/*
 * The choice sheet is a route of its own, so it cannot take the picker as a
 * prop. The ticket screen puts the picker here before it opens the sheet, and
 * the sheet reads it. One choice sheet is open at a time.
 */

let open: ChoicePicker | null = null;
const listeners = new Set<() => void>();

function notify(): void {
  for (const listener of listeners) listener();
}

/** Hands `picker` to the choice sheet that opens next. */
export function showTicketChoice(picker: ChoicePicker): void {
  open = picker;
  notify();
}

/** Clears the picker only when it is still `owned`, so a newer sheet's picker survives. */
export function clearTicketChoice(owned: ChoicePicker | null): void {
  if (owned && open === owned) {
    open = null;
    notify();
  }
}

/** The picker of the choice sheet, or null without one. */
export function getTicketChoice(): ChoicePicker | null {
  return open;
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/**
 * Runs `run` once no choice sheet is open: at once, or when the open sheet
 * goes. A sheet that a pick opens waits for the choice sheet to leave the
 * stack, because the choice sheet closes itself by going back, which would
 * also take a sheet pushed over it.
 */
export function whenTicketChoiceClosed(run: () => void): void {
  if (!open) {
    run();
    return;
  }
  const unsubscribe = subscribe(() => {
    if (open) return;
    unsubscribe();
    run();
  });
}

/** The picker of the choice sheet, re-rendering when it changes. */
export function useTicketChoice(): ChoicePicker | null {
  return useSyncExternalStore(subscribe, getTicketChoice, getTicketChoice);
}

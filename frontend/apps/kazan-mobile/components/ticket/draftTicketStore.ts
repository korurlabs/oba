import { useSyncExternalStore } from "react";

import type { PendingImage } from "@/lib/schemas/tickets";

/*
 * The New ticket screen and its field sheets are separate routes, so a sheet
 * cannot take the draft as a prop. The screen publishes the fields its
 * sheets edit, with the change function that writes back into its state; a
 * sheet reads them here. One New ticket screen is open at a time, because
 * the screen offers nothing that opens another.
 */

/** The fields of a new ticket that its sheets edit. */
export interface DraftTicketFields {
  /** The board whose members and labels the pickers list. */
  boardId: string | null;
  assigneeIds: readonly string[];
  labelIds: readonly string[];
  description: string;
  /**
   * The images added to the description and not uploaded yet, in the order
   * added; the description holds their `kazan-pending:` tags until Create
   * uploads them.
   */
  images: readonly PendingImage[];
}

/** What a sheet may change; the board is chosen on the screen. */
export type DraftTicketChange = Partial<Omit<DraftTicketFields, "boardId">>;

/** The open New ticket screen's fields and the way to change them. */
export interface DraftTicketSession {
  fields: DraftTicketFields;
  change: (patch: DraftTicketChange) => void;
}

let session: DraftTicketSession | null = null;
const listeners = new Set<() => void>();

function notify(): void {
  for (const listener of listeners) listener();
}

/** Publishes the open screen's draft, or null once the screen closes. */
export function publishDraftTicket(next: DraftTicketSession | null): void {
  session = next;
  notify();
}

/** Clears the draft only when it is still `owned`, so a newer screen's draft survives. */
export function withdrawDraftTicket(owned: DraftTicketSession | null): void {
  if (owned && session === owned) publishDraftTicket(null);
}

/** The open New ticket screen's draft, or null without one. */
export function getDraftTicket(): DraftTicketSession | null {
  return session;
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The open New ticket screen's draft, re-rendering when it changes. */
export function useDraftTicket(): DraftTicketSession | null {
  return useSyncExternalStore(subscribe, getDraftTicket, getDraftTicket);
}

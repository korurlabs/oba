import { useSyncExternalStore } from "react";

import type { IncidentCloseValues } from "@/components/ticket/incidentCloseModel";
import type { TicketRef } from "@/hooks/queries/ticketDetail";

/*
 * The Close incident sheet is a route of its own, so it cannot take its
 * question as a prop or answer through a callback prop. The screen that sets
 * an incident to Done puts the question here before it opens the sheet, and
 * the sheet answers it: the texts on confirm, null when it closes otherwise.
 * One question is open at a time; a newer one ends the older as a cancel.
 *
 * The texts typed into a confirmed sheet are kept as a draft per incident
 * until the close lands, so a close that fails and is tried again starts from
 * what was typed rather than from the stored texts.
 */

/** The draft key of an incident that is not created yet. */
export const NEW_INCIDENT_DRAFT = "new";

/** A question of the Close incident sheet. */
export interface IncidentCloseRequest {
  /** The incident being closed, whose stored texts fill the fields; absent on New ticket. */
  ticket?: TicketRef;
  /** The draft key: the ticket id, or `NEW_INCIDENT_DRAFT`. */
  draftKey: string;
  /** Receives the answer once; later calls are ignored. */
  settle: (values: IncidentCloseValues | null) => void;
}

let open: IncidentCloseRequest | null = null;
const drafts = new Map<string, IncidentCloseValues>();
const listeners = new Set<() => void>();

function notify(): void {
  for (const listener of listeners) listener();
}

/**
 * Asks the Close incident sheet that opens next for the texts of `ticket`
 * (absent for an incident not created yet). Resolves with the trimmed texts
 * on confirm, or with null when the sheet closes without them or a newer
 * question replaces this one.
 */
export function askIncidentClose(ticket?: TicketRef): Promise<IncidentCloseValues | null> {
  const previous: IncidentCloseRequest | null = open;
  return new Promise<IncidentCloseValues | null>((resolve) => {
    let settled = false;
    const request: IncidentCloseRequest = {
      ticket,
      draftKey: ticket?.ticketId ?? NEW_INCIDENT_DRAFT,
      settle: (values) => {
        if (settled) return;
        settled = true;
        resolve(values);
      },
    };
    open = request;
    previous?.settle(null);
    notify();
  });
}

/**
 * Answers `request` and clears it when it is still the open one. Confirmed
 * texts are kept as the incident's draft.
 */
export function settleIncidentClose(
  request: IncidentCloseRequest,
  values: IncidentCloseValues | null
): void {
  if (values) drafts.set(request.draftKey, values);
  if (open === request) {
    open = null;
    notify();
  }
  request.settle(values);
}

/** The question of the Close incident sheet, or null without one. */
export function getIncidentClose(): IncidentCloseRequest | null {
  return open;
}

/** The texts typed for an incident whose close has not landed yet. */
export function incidentCloseDraft(draftKey: string): IncidentCloseValues | undefined {
  return drafts.get(draftKey);
}

/** Drops the draft once the close has landed. */
export function forgetIncidentCloseDraft(draftKey: string): void {
  drafts.delete(draftKey);
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The question of the Close incident sheet, re-rendering when it changes. */
export function useIncidentClose(): IncidentCloseRequest | null {
  return useSyncExternalStore(subscribe, getIncidentClose, getIncidentClose);
}

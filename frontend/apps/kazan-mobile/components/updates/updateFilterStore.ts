import { useSyncExternalStore } from "react";

import { NO_UPDATE_FILTER, type StoredUpdateFilter } from "@/components/updates/updateModel";

/*
 * The filter of the daily updates channel. The filter sheet is its own
 * route and cannot reach the screen's state, so both read and write it
 * here. It is kept per organization and lives as long as the app runs: the
 * channel reopens as the reader left it, and another organization's person
 * never narrows this one.
 */

const filters = new Map<string, StoredUpdateFilter>();
const listeners = new Set<() => void>();

function notify(): void {
  for (const listener of listeners) listener();
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The organization's filter; today and nobody while none was set. */
export function updateFilterOf(orgId: string | null): StoredUpdateFilter {
  return (orgId ? filters.get(orgId) : undefined) ?? NO_UPDATE_FILTER;
}

/** Replaces the organization's filter. */
export function setUpdateFilter(orgId: string, filter: StoredUpdateFilter): void {
  filters.set(orgId, filter);
  notify();
}

/** Forgets every organization's filter, so the next user of this phone starts on today. */
export function clearUpdateFilters(): void {
  filters.clear();
  notify();
}

/** The organization's stored filter, re-read whenever it changes. */
export function useStoredUpdateFilter(orgId: string | null): StoredUpdateFilter {
  const read = (): StoredUpdateFilter => updateFilterOf(orgId);
  return useSyncExternalStore(subscribe, read, read);
}

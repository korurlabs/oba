import { useSyncExternalStore } from "react";

/*
 * The members the reader has nudged, per organization and business day. Not
 * posted does not say who was nudged, so the sheet remembers each 201 and
 * each "already nudged" refusal, and shows Nudged in place of the button for
 * as long as the app runs. The server still refuses a second nudge the same
 * day, whoever sends it.
 */

const EMPTY: ReadonlySet<string> = new Set<string>();
const nudged = new Map<string, ReadonlySet<string>>();
const listeners = new Set<() => void>();

function keyOf(orgId: string, day: string): string {
  return `${orgId}|${day}`;
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The members nudged in the organization on the day. */
export function nudgedMembersOf(orgId: string | null, day: string): ReadonlySet<string> {
  return (orgId ? nudged.get(keyOf(orgId, day)) : undefined) ?? EMPTY;
}

/** Records that the member was nudged in the organization on the day. */
export function markNudged(orgId: string, day: string, userId: string): void {
  const current: ReadonlySet<string> = nudgedMembersOf(orgId, day);
  if (current.has(userId)) return;
  nudged.set(keyOf(orgId, day), new Set<string>([...current, userId]));
  for (const listener of listeners) listener();
}

/** Forgets every nudge, so the next user of this phone does not see another's as theirs. */
export function clearNudges(): void {
  nudged.clear();
  for (const listener of listeners) listener();
}

/** The members nudged in the organization on the day, re-read whenever one is added. */
export function useNudgedMembers(orgId: string | null, day: string): ReadonlySet<string> {
  const read = (): ReadonlySet<string> => nudgedMembersOf(orgId, day);
  return useSyncExternalStore(subscribe, read, read);
}

import { useSyncExternalStore } from "react";

import { NO_PULL_REQUEST_FILTER } from "@/components/pull-requests/pullRequestModel";
import type { PullRequestChannelFilter } from "@/lib/schemas/pullRequests";

/*
 * The filter of the pull requests channel. The filter sheet is its own
 * route and cannot reach the screen's state, so both read and write it
 * here. It is kept per organization and lives as long as the app runs: the
 * channel reopens as the reader left it, and another organization's person
 * never narrows this one.
 */

const filters = new Map<string, PullRequestChannelFilter>();
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

/** The organization's filter; no filter while none was set. */
export function pullRequestFilterOf(orgId: string | null): PullRequestChannelFilter {
  return (orgId ? filters.get(orgId) : undefined) ?? NO_PULL_REQUEST_FILTER;
}

/** Replaces the organization's filter. */
export function setPullRequestFilter(orgId: string, filter: PullRequestChannelFilter): void {
  filters.set(orgId, filter);
  notify();
}

/** Forgets every organization's filter, so the next user of this phone starts unfiltered. */
export function clearPullRequestFilters(): void {
  filters.clear();
  notify();
}

/** The organization's filter, re-read whenever it changes. */
export function usePullRequestFilter(orgId: string | null): PullRequestChannelFilter {
  const read = (): PullRequestChannelFilter => pullRequestFilterOf(orgId);
  return useSyncExternalStore(subscribe, read, read);
}

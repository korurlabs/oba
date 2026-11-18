import { useSyncExternalStore } from "react";

import type { PullRequestShareReviewerResponse } from "@/lib/schemas/pullRequests";

/*
 * The Share pull request form and its reviewer picker are separate routes,
 * so the picker cannot take the chosen reviewers as a prop. The form
 * publishes them with the function that writes back into its state; the
 * picker reads them here. One share form is open at a time, because nothing
 * on it opens another.
 */

/** The open share form's reviewers and the way to replace them. */
export interface ShareReviewersSession {
  reviewerIds: readonly string[];
  /** The edited share's reviewers as stored, so one who left can still be shown by name. */
  storedReviewers: readonly PullRequestShareReviewerResponse[];
  change: (reviewerIds: string[]) => void;
}

let session: ShareReviewersSession | null = null;
const listeners = new Set<() => void>();

function notify(): void {
  for (const listener of listeners) listener();
}

/** Publishes the open form's reviewers, or null once the form closes. */
export function publishShareReviewers(next: ShareReviewersSession | null): void {
  session = next;
  notify();
}

/** Clears the session only when it is still `owned`, so a newer form's session survives. */
export function withdrawShareReviewers(owned: ShareReviewersSession | null): void {
  if (owned && session === owned) publishShareReviewers(null);
}

/** The open share form's reviewers, or null without one. */
export function getShareReviewers(): ShareReviewersSession | null {
  return session;
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The open share form's reviewers, re-rendering when they change. */
export function useShareReviewers(): ShareReviewersSession | null {
  return useSyncExternalStore(subscribe, getShareReviewers, getShareReviewers);
}

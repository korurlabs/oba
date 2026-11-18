import { isApiError } from "@/lib/api";

/** Attempts after the first one for a query that may still succeed; TanStack Query's own default. */
const QUERY_RETRIES = 3;

/**
 * Statuses a second attempt cannot change: the session is over (401 after
 * the refresh already failed), the user may not see it (403) or it does not
 * exist (404). Retrying them only delays the error state by seconds.
 */
const FINAL_STATUSES: ReadonlySet<number> = new Set([401, 403, 404]);

/** The `retry` of every query: up to three more attempts, none for a final status. */
export function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  if (isApiError(error) && error.status !== undefined && FINAL_STATUSES.has(error.status)) {
    return false;
  }
  return failureCount < QUERY_RETRIES;
}

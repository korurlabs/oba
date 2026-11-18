/**
 * The URL state of the pull requests channel's filters: the share statuses
 * and the person who shared.
 *
 * Not marked "use client": the pull requests page resolves the statuses on
 * the server to prefetch the channel under the key the client builds, and
 * the chat routes drop these keys on a redirect to another channel. Inside a
 * "use client" module these would become client-reference proxies on the
 * server.
 */
import { PullRequestShareStatusSchema, type PullRequestShareStatus } from "../schemas";

/** The URL key of the chosen share statuses, comma-separated. */
export const PULL_REQUEST_STATUSES_PARAM = "prStatuses";

/** The URL keys of the pull requests channel's filters; a redirect to another channel drops them. */
export const PULL_REQUEST_FILTER_KEYS = [PULL_REQUEST_STATUSES_PARAM, "person"] as const;

const STATUS_ORDER: readonly PullRequestShareStatus[] = PullRequestShareStatusSchema.options;

/**
 * The statuses a URL value names: its comma-separated known values, once
 * each, in the order of the status enum. An unknown value is dropped rather
 * than sent, since the server refuses it; a repeated parameter counts every
 * value. The fixed order makes the server page and the client view build the
 * same query key from the same choice, however the URL lists it.
 */
export function resolvePullRequestStatuses(raw: string | string[] | null | undefined): PullRequestShareStatus[] {
  if (!raw) return [];
  const values = new Set((Array.isArray(raw) ? raw : [raw]).flatMap((value) => value.split(",")));
  return STATUS_ORDER.filter((status) => values.has(status));
}

"use client";

import { parseAsArrayOf, parseAsString, parseAsStringEnum, useQueryStates } from "nuqs";
import type { Values } from "nuqs";
import { PullRequestShareStatus } from "../schemas";

/**
 * The URL keys of the pull requests channel's filters. `prStatuses` holds the
 * chosen share statuses, comma-separated; the prefix keeps it apart from the
 * `statuses` of the ticket pickers. `person` is the sharer's user ID, the same
 * key as in the daily updates channel, since both narrow the channel to one
 * author.
 */
export const pullRequestChannelFilterParams = {
  prStatuses: parseAsArrayOf(parseAsStringEnum(Object.values(PullRequestShareStatus))),
  person: parseAsString,
};

export type PullRequestChannelFilterParams = Values<typeof pullRequestChannelFilterParams>;

export function usePullRequestChannelFilterParams() {
  return useQueryStates(pullRequestChannelFilterParams);
}

/** Filters that narrow the channel: Status when any is chosen, Person when set. */
export function countPullRequestChannelFilters(filters: PullRequestChannelFilterParams): number {
  const statusCount = filters.prStatuses && filters.prStatuses.length > 0 ? 1 : 0;
  const personCount = filters.person ? 1 : 0;
  return statusCount + personCount;
}

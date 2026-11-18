"use client";

import { parseAsArrayOf, parseAsString, parseAsStringEnum, useQueryStates } from "nuqs";
import type { Values } from "nuqs";
import { GithubPullRequestState } from "../schemas";

/**
 * The URL keys of the pull request picker's filters. The related ticket picker
 * on the same screen uses `statuses` and `search`, so these carry a prefix.
 */
export const pullRequestPickerFilterParams = {
  prStates: parseAsArrayOf(parseAsStringEnum(Object.values(GithubPullRequestState))),
  prAuthors: parseAsArrayOf(parseAsString),
};

export type PullRequestPickerFilters = Values<typeof pullRequestPickerFilterParams>;

export function usePullRequestPickerFilterParams() {
  return useQueryStates(pullRequestPickerFilterParams);
}

"use client";

import { useCallback, useMemo, useState } from "react";
import { useDebounce } from "@oba/shared/hooks/use-debounce";
import { useBoardPreferences } from "@/hooks/use-board-preferences";
import {
  GITHUB_LOGIN_PATTERN,
  GithubPullRequestStateSchema,
  PULL_REQUEST_AUTHORS_MAX,
  type GithubPullRequestState,
} from "../schemas";
import {
  usePullRequestPickerFilterParams,
  type PullRequestPickerFilters,
} from "./use-pull-request-picker-filter-params";

/** Key of the saved set in the reader's preferences; one set for every repository. */
export const PULL_REQUEST_PICKER_PREFERENCES_KEY = "pullRequestPicker";

/**
 * A filter change becomes a GitHub search, and a burst of searches trips
 * GitHub's secondary rate limit, so ticking three boxes must cost one request.
 */
const PICKER_FILTER_DEBOUNCE_MS = 500;

function isGithubPullRequestState(value: string): value is GithubPullRequestState {
  return GithubPullRequestStateSchema.safeParse(value).success;
}

/**
 * Keeps only what the server accepts, so a stale saved set or a hand-edited
 * URL cannot turn the list into a 422. An empty category becomes null, the
 * form the URL state and the saved set use for "no filter".
 */
function sanitizePickerFilters(filters: {
  prStates?: string[] | null;
  prAuthors?: string[] | null;
}): PullRequestPickerFilters {
  const prStates = (filters.prStates ?? []).filter(isGithubPullRequestState);
  const prAuthors = (filters.prAuthors ?? [])
    .filter((login) => GITHUB_LOGIN_PATTERN.test(login))
    .slice(0, PULL_REQUEST_AUTHORS_MAX);
  return {
    prStates: prStates.length > 0 ? prStates : null,
    prAuthors: prAuthors.length > 0 ? prAuthors : null,
  };
}

function countActivePickerFilters(filters: PullRequestPickerFilters): number {
  return (filters.prStates ? 1 : 0) + (filters.prAuthors ? 1 : 0);
}

/**
 * State of the pull request picker's filters: URL-synced, and saved per reader
 * through `useBoardPreferences`. Unless the URL holds a valid filter, the
 * picker starts on the saved set.
 *
 * The starting set is derived, not written into the URL on mount: nuqs drops a
 * URL write queued in the same task as a client navigation's history push, so
 * a link into this screen would lose it. The first change writes the whole
 * starting set plus that change to the URL; from then on, and after clearing,
 * the URL alone decides.
 *
 * `requestFilters` is what the list query takes. It stays undefined until
 * "me" has been fetched, so the list never loads unfiltered first. The debounce
 * spaces out the reader's changes only: before the first change the value goes
 * out directly, because a debounced value would still hold the set from before
 * the saved filters were known.
 */
export function usePullRequestPickerFilters() {
  const prefs = useBoardPreferences(PULL_REQUEST_PICKER_PREFERENCES_KEY);
  const [rawUrlFilters, setUrlFilters] = usePullRequestPickerFilterParams();
  const [filtersTouched, setFiltersTouched] = useState(false);

  const urlFilters = useMemo(() => sanitizePickerFilters(rawUrlFilters), [rawUrlFilters]);
  const seed = useMemo(() => sanitizePickerFilters(prefs.savedFilters ?? {}), [prefs.savedFilters]);

  const usesSeed = !filtersTouched && countActivePickerFilters(urlFilters) === 0;
  const filters = usesSeed ? seed : urlFilters;
  const activeFilterCount = countActivePickerFilters(filters);

  const setFilters = useCallback(
    (changes: Partial<PullRequestPickerFilters>) => {
      setFiltersTouched(true);
      return setUrlFilters(usesSeed ? { ...seed, ...changes } : changes);
    },
    [usesSeed, seed, setUrlFilters],
  );

  const clearFilters = useCallback(() => {
    setFiltersTouched(true);
    return setUrlFilters({ prStates: null, prAuthors: null });
  }, [setUrlFilters]);

  const knownFilters = prefs.isMeFetched ? filters : undefined;
  const { debouncedValue } = useDebounce(knownFilters, PICKER_FILTER_DEBOUNCE_MS);
  const requestFilters = filtersTouched ? debouncedValue : knownFilters;

  return {
    prefs,
    filters,
    setFilters,
    clearFilters,
    activeFilterCount,
    requestFilters,
  };
}

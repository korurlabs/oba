"use client";

import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { useChatOrganizationId } from "@/features/chat/hooks/use-chat-messages";
import {
  getGithubPullRequest,
  listGithubContributors,
  listGithubPullRequests,
  listGithubRepositories,
  requirePullRequestData,
} from "../api";
import { PULL_REQUEST_FIRST_PAGE, type GithubPullRequestPageResponse } from "../schemas";
import { PULL_REQUEST_KEYS } from "./query-keys";
import type { PullRequestPickerFilters } from "./use-pull-request-picker-filter-params";

/**
 * Picker results stay fresh for 30 seconds, so going back and forth between
 * repositories or search terms does not spend GitHub's search rate limit of
 * 30 requests per minute.
 */
const GITHUB_PULL_REQUESTS_STALE_TIME = 30_000;

/**
 * Keys live in the boundary-safe `./query-keys` module so the share pages
 * can prefetch under them on the server. Re-exported for client consumers.
 */
export { PULL_REQUEST_KEYS };

/** The GitHub App installation's repositories; 404 `KAI_NOT_016` when GitHub is not set up. */
export function useGithubRepositories() {
  const orgId = useChatOrganizationId();
  return useQuery({
    queryKey: PULL_REQUEST_KEYS.repositories(orgId ?? ""),
    queryFn: listGithubRepositories,
    enabled: !!orgId,
  });
}

/**
 * The repository's contributors for the Author filter, as an envelope like
 * `useGithubRepositories`. The default stale time applies, because the server
 * answers repeats from its own cache. A failure leaves no data, so a later
 * mount tries again; the caller keeps working on the logins it has.
 */
export function useGithubContributors(githubRepositoryId: number | undefined) {
  const orgId = useChatOrganizationId();
  return useQuery({
    queryKey: PULL_REQUEST_KEYS.githubContributors(orgId ?? "", githubRepositoryId ?? 0),
    queryFn: () => listGithubContributors(githubRepositoryId as number),
    enabled: !!orgId && githubRepositoryId !== undefined,
  });
}

/**
 * The picker list of one repository, 30 rows per page, last updated first,
 * with "Load more" as the next page. `search` is the caller's debounced text;
 * blank lists without a search. Disabled until a repository is selected and,
 * so that the list never loads unfiltered first, while `filters` is undefined.
 */
export function useGithubPullRequests(
  githubRepositoryId: number | undefined,
  search: string,
  filters: PullRequestPickerFilters | undefined,
) {
  const orgId = useChatOrganizationId();
  const query = search.trim();
  const states = [...(filters?.prStates ?? [])].sort();
  const authors = [...(filters?.prAuthors ?? [])].sort();
  return useInfiniteQuery({
    queryKey: PULL_REQUEST_KEYS.githubPullRequests(
      orgId ?? "",
      githubRepositoryId ?? 0,
      query,
      states,
      authors,
    ),
    queryFn: async ({ pageParam }: { pageParam: number }) =>
      requirePullRequestData(
        await listGithubPullRequests(githubRepositoryId as number, {
          search: query || undefined,
          page: pageParam,
          states,
          authors,
        }),
      ),
    initialPageParam: PULL_REQUEST_FIRST_PAGE,
    getNextPageParam: (lastPage: GithubPullRequestPageResponse) =>
      lastPage.hasMore ? lastPage.page + 1 : undefined,
    enabled: !!orgId && githubRepositoryId !== undefined && filters !== undefined,
    staleTime: GITHUB_PULL_REQUESTS_STALE_TIME,
  });
}

/**
 * The selected pull request with branches and ticket key, for the preview.
 * Disabled until both a repository and a row are selected.
 */
export function useGithubPullRequest(githubRepositoryId: number | undefined, number: number | undefined) {
  const orgId = useChatOrganizationId();
  return useQuery({
    queryKey: PULL_REQUEST_KEYS.githubPullRequest(orgId ?? "", githubRepositoryId ?? 0, number ?? 0),
    queryFn: () => getGithubPullRequest(githubRepositoryId as number, number as number),
    enabled: !!orgId && githubRepositoryId !== undefined && number !== undefined,
  });
}

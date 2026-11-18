/**
 * Query-key factory for the GitHub picker and pull request share queries.
 *
 * Not marked "use client": the share pages import these on the server to
 * prefetch under the exact keys the client hooks use. Inside a "use client"
 * module the functions would become client-reference proxies and could not be
 * called during the server prefetch.
 *
 * Every key holds the organization ID right after the prefix, as the chat
 * keys do: the Axios client sends the session's default organization as
 * `X-Organization-ID`, and GitHub answers only for the one configured
 * organization, so a switch of organization must read other keys.
 */
export const PULL_REQUEST_KEYS = {
  all: (orgId: string) => ["pull-requests", orgId] as const,
  /** The GitHub App installation's repositories. */
  repositories: (orgId: string) => [...PULL_REQUEST_KEYS.all(orgId), "repositories"] as const,
  /** Every picker list and detail of one repository. */
  repository: (orgId: string, githubRepositoryId: number) =>
    [...PULL_REQUEST_KEYS.all(orgId), "repository", githubRepositoryId] as const,
  /** Every picker list of one repository, whatever its search and filters. */
  repositoryLists: (orgId: string, githubRepositoryId: number) =>
    [...PULL_REQUEST_KEYS.repository(orgId, githubRepositoryId), "list"] as const,
  /**
   * The picker's `InfiniteData<GithubPullRequestPageResponse>` for one search
   * and filter; `""` lists without a search, empty arrays do not narrow.
   * `states` and `authors` come sorted, so the order of ticking reads one key.
   */
  githubPullRequests: (
    orgId: string,
    githubRepositoryId: number,
    search: string,
    states: readonly string[],
    authors: readonly string[],
  ) => [...PULL_REQUEST_KEYS.repositoryLists(orgId, githubRepositoryId), search, states, authors] as const,
  /** The repository's contributors; not a list, so it stays out of `repositoryLists`. */
  githubContributors: (orgId: string, githubRepositoryId: number) =>
    [...PULL_REQUEST_KEYS.repository(orgId, githubRepositoryId), "contributors"] as const,
  /** One pull request as GitHub holds it now, for the preview. */
  githubPullRequest: (orgId: string, githubRepositoryId: number, number: number) =>
    [...PULL_REQUEST_KEYS.repository(orgId, githubRepositoryId), "detail", number] as const,
  /** Every share. */
  sharesAll: (orgId: string) => [...PULL_REQUEST_KEYS.all(orgId), "shares"] as const,
  share: (orgId: string, shareId: string) => [...PULL_REQUEST_KEYS.sharesAll(orgId), shareId] as const,
};

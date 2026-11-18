import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import { filterEmptyValues } from "@/lib/filter-utils";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  GithubContributorResponse,
  GithubPullRequestPageResponse,
  GithubPullRequestQueryParams,
  GithubPullRequestResponse,
  GithubRepositoryResponse,
  PullRequestShareCreateRequest,
  PullRequestShareResponse,
  PullRequestShareUpdateRequest,
} from "../schemas";

/**
 * The pull request share response always carries `data`; an envelope without
 * it is a server fault, thrown so the query or mutation shows its error state
 * instead of rendering an empty page as if GitHub had returned no rows.
 */
export function requirePullRequestData<T>(response: ApiResponse<T>): T {
  if (response.data === undefined || response.data === null) {
    throw new Error(`Pull request response without data: ${response.message}`);
  }
  return response.data;
}

// ── GitHub picker ───────────────────────────────────────────────────
// 404 KAI_NOT_016 when GitHub is not set up for the organization; 502
// GHB_UPS_001/002 and 503 GHB_UPS_003 when GitHub fails or rate limits.

/** The GitHub App installation's repositories, by name. */
export async function listGithubRepositories() {
  const res = await api.get<ApiResponse<GithubRepositoryResponse[]>>(API_ROUTES.github.repositories);
  return res.data;
}

/**
 * One page of a repository's pull requests, last updated first; 30 per page.
 * Without `states` every state is listed. `states` and `authors` go out
 * comma-separated, which is how the server binds its lists.
 */
export async function listGithubPullRequests(
  githubRepositoryId: number,
  params: GithubPullRequestQueryParams,
) {
  const serialized = Object.fromEntries(
    Object.entries(filterEmptyValues(params) ?? {}).map(([key, value]) => [
      key,
      Array.isArray(value) ? value.join(",") : value,
    ]),
  );
  const res = await api.get<ApiResponse<GithubPullRequestPageResponse>>(
    API_ROUTES.github.pullRequests(githubRepositoryId),
    { params: serialized },
  );
  return res.data;
}

/**
 * Logins of a repository's GitHub contributors, at most 100, by login; empty
 * for an empty repository. The list holds commit authors, not every pull
 * request author, and the server caches it for an hour.
 */
export async function listGithubContributors(githubRepositoryId: number) {
  const res = await api.get<ApiResponse<GithubContributorResponse[]>>(
    API_ROUTES.github.contributors(githubRepositoryId),
  );
  return res.data;
}

/** One pull request with its branches and ticket key; 404 KAI_NOT_018 when GitHub has none. */
export async function getGithubPullRequest(githubRepositoryId: number, number: number) {
  const res = await api.get<ApiResponse<GithubPullRequestResponse>>(
    API_ROUTES.github.pullRequest(githubRepositoryId, number),
  );
  return res.data;
}

// ── Shares ──────────────────────────────────────────────────────────

export async function getPullRequestShareById(shareId: string) {
  const res = await api.get<ApiResponse<PullRequestShareResponse>>(
    API_ROUTES.pullRequestShares.detail(shareId),
  );
  return res.data;
}

/** Shares the pull request and posts its card in the PR channel; 201 with the share. */
export async function createPullRequestShare(data: PullRequestShareCreateRequest) {
  const res = await api.post<ApiResponse<PullRequestShareResponse>>(
    API_ROUTES.pullRequestShares.list,
    data,
  );
  return res.data;
}

/** The author's edit of status, note and reviewers; 403 KAI_SEC_009 for anyone else. */
export async function editPullRequestShare(shareId: string, data: PullRequestShareUpdateRequest) {
  const res = await api.put<ApiResponse<PullRequestShareResponse>>(
    API_ROUTES.pullRequestShares.detail(shareId),
    data,
  );
  return res.data;
}

/** Removes the share and its card; the author only. The server answers 200 with an empty envelope. */
export async function deletePullRequestShare(shareId: string): Promise<void> {
  await api.delete(API_ROUTES.pullRequestShares.detail(shareId));
}

/** The reader's approval as a reviewer; idempotent. 403 KAI_SEC_010 for a non-reviewer. */
export async function approvePullRequestShare(shareId: string) {
  const res = await api.put<ApiResponse<PullRequestShareResponse>>(
    API_ROUTES.pullRequestShares.approval(shareId),
  );
  return res.data;
}

/** Withdraws the reader's approval; idempotent, as `approvePullRequestShare`. */
export async function withdrawPullRequestShareApproval(shareId: string) {
  const res = await api.delete<ApiResponse<PullRequestShareResponse>>(
    API_ROUTES.pullRequestShares.approval(shareId),
  );
  return res.data;
}

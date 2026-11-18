import { isApiError } from "@oba/shared/api/axios";
import { PULL_REQUEST_ERROR_CODES } from "../schemas";

/** A key under `pullRequests.errors`. */
export type PullRequestErrorKey =
  | "notConnected"
  | "repositoryNotFound"
  | "pullRequestNotFound"
  | "shareNotFound"
  | "rateLimited"
  | "unreachable"
  | "credentialsRefused";

/**
 * The refusals the share screens explain in their own words, because the
 * reader's next step differs: wait for GitHub's rate limit, retry an
 * unreachable GitHub, or ask an administrator.
 */
const ERROR_KEYS: Record<string, PullRequestErrorKey> = {
  [PULL_REQUEST_ERROR_CODES.githubNotConfigured]: "notConnected",
  [PULL_REQUEST_ERROR_CODES.repositoryNotFound]: "repositoryNotFound",
  [PULL_REQUEST_ERROR_CODES.pullRequestNotFound]: "pullRequestNotFound",
  [PULL_REQUEST_ERROR_CODES.shareNotFound]: "shareNotFound",
  [PULL_REQUEST_ERROR_CODES.githubRateLimited]: "rateLimited",
  [PULL_REQUEST_ERROR_CODES.githubUnreachable]: "unreachable",
  [PULL_REQUEST_ERROR_CODES.githubCredentialsRefused]: "credentialsRefused",
};

/** The `pullRequests.errors` key of a failed read; null for any other failure, shown with the server's text. */
export function pullRequestErrorKey(error: Error | null): PullRequestErrorKey | null {
  if (!isApiError(error) || !error.code) return null;
  return ERROR_KEYS[error.code] ?? null;
}

/** True when GitHub is not set up for the reader's organization. */
export function isGithubNotConnected(error: Error | null): boolean {
  return isApiError(error, PULL_REQUEST_ERROR_CODES.githubNotConfigured);
}

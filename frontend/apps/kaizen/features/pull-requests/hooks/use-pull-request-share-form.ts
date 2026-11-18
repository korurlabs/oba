"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  GithubPullRequestState,
  PullRequestShareStatus,
  pullRequestShareFormSchema,
  type PullRequestShareCreateRequest,
  type PullRequestShareFormValues,
  type PullRequestShareUpdateRequest,
} from "../schemas";

/** A new share starts without status, note, reviewers or related tickets. */
export const EMPTY_SHARE_FORM_VALUES: PullRequestShareFormValues = {
  status: "",
  note: "",
  reviewerIds: [],
  relatedTicketIds: [],
};

// ── Mapping ─────────────────────────────────────────────────────────

/** The GitHub state presets the team's status; a closed pull request presets none. */
const STATUS_FROM_STATE: Record<GithubPullRequestState, PullRequestShareFormValues["status"]> = {
  [GithubPullRequestState.DRAFT]: PullRequestShareStatus.DRAFT,
  [GithubPullRequestState.OPEN]: PullRequestShareStatus.OPEN,
  [GithubPullRequestState.MERGED]: PullRequestShareStatus.MERGED,
  [GithubPullRequestState.CLOSED]: "",
};

export function statusFromGithubState(state: GithubPullRequestState): PullRequestShareFormValues["status"] {
  return STATUS_FROM_STATE[state];
}

/**
 * The edit body. Called on validated values only, where the status is set; a
 * blank note is sent as null, so the card shows none.
 */
export function toShareUpdateRequest(values: PullRequestShareFormValues): PullRequestShareUpdateRequest {
  if (values.status === "") {
    throw new Error("A pull request share was submitted without a status");
  }
  return {
    status: values.status,
    note: values.note.trim() ? values.note : null,
    reviewerIds: values.reviewerIds,
    relatedTicketIds: values.relatedTicketIds,
  };
}

/** The create body: the edit body plus the pull request's identity. */
export function toShareCreateRequest(
  values: PullRequestShareFormValues,
  pullRequest: { githubRepositoryId: number; number: number },
): PullRequestShareCreateRequest {
  return { ...toShareUpdateRequest(values), ...pullRequest };
}

// ── Hooks ───────────────────────────────────────────────────────────

/** Validates on change, so a too long note is flagged while typing. */
export function usePullRequestShareForm(defaultValues: PullRequestShareFormValues) {
  return useForm<PullRequestShareFormValues>({
    resolver: zodResolver(pullRequestShareFormSchema),
    mode: "onChange",
    defaultValues,
  });
}

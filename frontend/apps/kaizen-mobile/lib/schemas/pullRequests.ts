import { z } from "zod";

import { chatLinkedTicketResponseSchema } from "@/lib/schemas/chat";

/*
 * Wire shapes of pull request shares (`/kaizen/pull-request-shares`) and of
 * the GitHub proxy (`/kaizen/github`), the same as the web app reads.
 * Instants are ISO strings with `Z`.
 */

// ── Limits (mirror the share requests on the server) ────────────────

export const PULL_REQUEST_NOTE_MAX = 500;
export const PULL_REQUEST_REVIEWERS_MAX = 10;
export const PULL_REQUEST_RELATED_TICKETS_MAX = 10;
/** Longest picker search the server accepts; longer text is refused, not cut. */
export const PULL_REQUEST_SEARCH_MAX = 100;
/** Most picker authors the server accepts; a sixth is refused, so the client never sends one. */
export const PULL_REQUEST_AUTHORS_MAX = 5;
/** Picker pages start at 1, as GitHub's do. */
export const PULL_REQUEST_FIRST_PAGE = 1;

// ── Error codes the app reacts to ───────────────────────────────────

export const PULL_REQUEST_ERROR_CODES = {
  /** A size or required-field limit of a request (422); `errorValues` maps each field to its message. */
  validation: "GEN_VAL_001",
  /** A reviewer who is a duplicate, the author or not an active member, or a ticket named twice (422). */
  shareInvalid: "KAI_VAL_055",
  /** An added related ticket is deleted or on a board the author is not a member of (404). */
  ticketNotFound: "KAI_NOT_004",
  /** The organization has no pull requests channel to post in (422). */
  channelAbsent: "KAI_VAL_056",
  /** GitHub is not set up for the reader's organization (404). */
  githubNotConfigured: "KAI_NOT_016",
  /** The repository is not one of the GitHub App installation's (404). */
  repositoryNotFound: "KAI_NOT_017",
  /** The pull request does not exist on GitHub (404). */
  pullRequestNotFound: "KAI_NOT_018",
  /** The share is absent, deleted or of another organization (404). */
  shareNotFound: "KAI_NOT_019",
  /** Only the author edits or deletes a share (403). */
  notShareAuthor: "KAI_SEC_009",
  /** Only a reviewer approves or withdraws (403). */
  notReviewer: "KAI_SEC_010",
  /** GitHub unreachable, timed out or answered 5xx (502). */
  githubUnreachable: "GHB_UPS_001",
  /** GitHub refused the App's credentials (502). */
  githubCredentialsRefused: "GHB_UPS_002",
  /** GitHub's rate limit is reached; the user waits and retries (503). */
  githubRateLimited: "GHB_UPS_003",
} as const;

// ── Enums ───────────────────────────────────────────────────────────

/** The team's review status of a share, chosen by its author; not GitHub's state. */
export const PullRequestShareStatusSchema = z.enum([
  "DRAFT",
  "OPEN",
  "IN_REVIEW",
  "MERGED",
  "DISCUSS",
]);
export type PullRequestShareStatus = z.infer<typeof PullRequestShareStatusSchema>;
// eslint-disable-next-line @typescript-eslint/no-redeclare -- reason: the enum's type and its value map share one name, so callers write `PullRequestShareStatus.OPEN` and annotate with the same identifier.
export const PullRequestShareStatus = PullRequestShareStatusSchema.enum;

/** The pull request's state on GitHub: MERGED when merged, DRAFT when an open draft. */
export const GithubPullRequestStateSchema = z.enum(["OPEN", "DRAFT", "MERGED", "CLOSED"]);
export type GithubPullRequestState = z.infer<typeof GithubPullRequestStateSchema>;
// eslint-disable-next-line @typescript-eslint/no-redeclare -- reason: the enum's type and its value map share one name, as for the share status.
export const GithubPullRequestState = GithubPullRequestStateSchema.enum;

// ── Requests ────────────────────────────────────────────────────────

/**
 * Body of a share. The card's facts are read from GitHub by the server, so
 * only the pull request's identity is sent. A blank note is sent as null;
 * `reviewerIds` and `relatedTicketIds` are required, empty for none, in
 * display order.
 */
export const pullRequestShareCreateRequestSchema = z.object({
  githubRepositoryId: z.number().int().positive(),
  number: z.number().int().positive(),
  status: PullRequestShareStatusSchema,
  note: z.string().max(PULL_REQUEST_NOTE_MAX).nullable(),
  reviewerIds: z.array(z.string()).max(PULL_REQUEST_REVIEWERS_MAX),
  relatedTicketIds: z.array(z.string()).max(PULL_REQUEST_RELATED_TICKETS_MAX),
});

/**
 * Body of the author's edit: the full new status, note, reviewers and
 * related tickets. The pull request itself cannot change. A kept reviewer
 * keeps their approval; a removed one loses it.
 */
export const pullRequestShareUpdateRequestSchema = pullRequestShareCreateRequestSchema.pick({
  status: true,
  note: true,
  reviewerIds: true,
  relatedTicketIds: true,
});

// ── Responses ───────────────────────────────────────────────────────

/** A repository of the GitHub App installation. */
export const githubRepositoryResponseSchema = z.object({
  githubRepositoryId: z.number(),
  /** `owner/name`. */
  fullName: z.string(),
  privateRepository: z.boolean(),
});

/** One picker row. */
export const githubPullRequestSummaryResponseSchema = z.object({
  number: z.number(),
  title: z.string(),
  state: GithubPullRequestStateSchema,
  authorLogin: z.string(),
  updatedAt: z.string(),
  url: z.string(),
});

/** A picker page, last updated first. */
export const githubPullRequestPageResponseSchema = z.object({
  items: z.array(githubPullRequestSummaryResponseSchema),
  page: z.number(),
  /** True when the page was full, so a next page may hold more rows. */
  hasMore: z.boolean(),
  /** True when GitHub's search found more than the 1000 results it shows. */
  resultsLimited: z.boolean(),
});

/** A GitHub contributor of a repository, an option of the picker's Author filter. */
export const githubContributorResponseSchema = z.object({
  login: z.string(),
});

/** The picked pull request with its branches and ticket key, for the preview. */
export const githubPullRequestResponseSchema = githubPullRequestSummaryResponseSchema.extend({
  headRef: z.string(),
  baseRef: z.string(),
  /** Upper-case ticket key in the head branch, else in the title. */
  ticketKey: z.string().nullable(),
  /** The ticket of `ticketKey`, only when the reader can open it. */
  linkedTicket: chatLinkedTicketResponseSchema.nullable(),
});

export const pullRequestShareReviewerResponseSchema = z.object({
  userId: z.string(),
  firstName: z.string().nullable(),
  lastName: z.string().nullable(),
  /** Null while the reviewer has not approved. */
  approvedAt: z.string().nullable(),
});

/**
 * A related ticket of a share. Board name, key and title are stored when the
 * ticket is added, so every reader sees the same row. `canOpen` is computed
 * for the reader: the ticket is live and on one of their member boards.
 */
export const pullRequestShareTicketResponseSchema = z.object({
  ticketId: z.string(),
  boardId: z.string(),
  boardName: z.string(),
  ticketKey: z.string(),
  ticketTitle: z.string(),
  canOpen: z.boolean(),
});

/**
 * A share. Repository, number, title, URL, GitHub author and branches are
 * the facts GitHub gave at share time; status, note and reviewers are the
 * team's. `linkedTicket` and each related ticket's `canOpen` are computed
 * for the reader.
 */
export const pullRequestShareResponseSchema = z.object({
  id: z.string(),
  authorId: z.string(),
  repositoryFullName: z.string(),
  number: z.number(),
  title: z.string(),
  url: z.string(),
  githubAuthorLogin: z.string(),
  headRef: z.string(),
  baseRef: z.string(),
  ticketKey: z.string().nullable(),
  linkedTicket: chatLinkedTicketResponseSchema.nullable(),
  status: PullRequestShareStatusSchema,
  note: z.string().nullable(),
  /** In display order. */
  reviewers: z.array(pullRequestShareReviewerResponseSchema),
  /** In display order. */
  relatedTickets: z.array(pullRequestShareTicketResponseSchema),
  createdAt: z.string(),
  /** Time of the author's last edit; null when never edited. */
  editedAt: z.string().nullable(),
  /** The plain text as the team posts it, the same for every reader; what Copy copies. */
  summaryText: z.string(),
});

// ── Types ───────────────────────────────────────────────────────────

export type PullRequestShareCreateRequest = z.infer<typeof pullRequestShareCreateRequestSchema>;
export type PullRequestShareUpdateRequest = z.infer<typeof pullRequestShareUpdateRequestSchema>;
export type GithubRepositoryResponse = z.infer<typeof githubRepositoryResponseSchema>;
export type GithubPullRequestSummaryResponse = z.infer<
  typeof githubPullRequestSummaryResponseSchema
>;
export type GithubPullRequestPageResponse = z.infer<typeof githubPullRequestPageResponseSchema>;
export type GithubContributorResponse = z.infer<typeof githubContributorResponseSchema>;
export type GithubPullRequestResponse = z.infer<typeof githubPullRequestResponseSchema>;
export type PullRequestShareReviewerResponse = z.infer<
  typeof pullRequestShareReviewerResponseSchema
>;
export type PullRequestShareTicketResponse = z.infer<typeof pullRequestShareTicketResponseSchema>;
export type PullRequestShareResponse = z.infer<typeof pullRequestShareResponseSchema>;

/**
 * What the pull requests channel is narrowed to: share statuses (any of
 * them, in the order of the status enum) and the person who wrote. Empty
 * statuses and a null person narrow nothing.
 */
export interface PullRequestChannelFilter {
  statuses: PullRequestShareStatus[];
  personId: string | null;
}

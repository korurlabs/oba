import { z } from "zod";
// Type-only: the chat schemas embed the share response, so a value import
// would make the two schema modules import each other.
import type { ChatLinkedTicketResponse } from "@/features/chat/schemas";

// ── Limits (mirror the pull request share requests on the server) ───

export const PULL_REQUEST_NOTE_MAX = 500;
export const PULL_REQUEST_REVIEWERS_MAX = 10;
export const PULL_REQUEST_RELATED_TICKETS_MAX = 10;
/** Longest picker search the server accepts; longer text is refused, not cut. */
export const PULL_REQUEST_SEARCH_MAX = 100;
/** Most picker authors the server accepts; a sixth is refused, so the client never sends one. */
export const PULL_REQUEST_AUTHORS_MAX = 5;
/** The server's check of a picker author, so a login it would refuse is never sent. */
export const GITHUB_LOGIN_PATTERN = /^[A-Za-z0-9][A-Za-z0-9-]{0,38}(\[bot\])?$/;
/** Picker pages start at 1, as GitHub's do. */
export const PULL_REQUEST_FIRST_PAGE = 1;
/** Rows GitHub returns per picker page; `hasMore` is true when a page is full. */
export const PULL_REQUEST_PAGE_SIZE = 30;

// ── Error codes the client reacts to ────────────────────────────────

export const PULL_REQUEST_ERROR_CODES = {
  /**
   * A size or required-field limit of a request (422): note over 500, more
   * than 10 reviewers or related tickets, a search over 100 characters, a
   * page below 1, an unknown state, more than 5 authors or an invalid login.
   * `errorValues` maps each field to its message.
   */
  validation: "GEN_VAL_001",
  /**
   * A reviewer rule that needs the organization's members (duplicate, the
   * author, or not an active OWNER, ADMIN or MEMBER), or a related ticket
   * named twice (422).
   */
  shareInvalid: "KAZ_VAL_055",
  /** An added related ticket is deleted or on a board the author is not a member of (404). */
  ticketNotFound: "KAZ_NOT_004",
  /** The organization has no pull requests channel to post in (422). */
  channelAbsent: "KAZ_VAL_056",
  /** GitHub is not set up for the reader's organization (404). */
  githubNotConfigured: "KAZ_NOT_016",
  /** The repository is not one of the GitHub App installation's (404). */
  repositoryNotFound: "KAZ_NOT_017",
  /** The pull request does not exist on GitHub (404). */
  pullRequestNotFound: "KAZ_NOT_018",
  /** The share is absent, deleted or of another organization (404). */
  shareNotFound: "KAZ_NOT_019",
  /** Only the author edits or deletes a share (403). */
  notShareAuthor: "KAZ_SEC_009",
  /** Only a reviewer approves or withdraws (403). */
  notReviewer: "KAZ_SEC_010",
  /** GitHub unreachable, timed out or answered 5xx (502). */
  githubUnreachable: "GHB_UPS_001",
  /** GitHub refused the App's credentials (502). */
  githubCredentialsRefused: "GHB_UPS_002",
  /** GitHub's rate limit is reached; the user waits and retries (503). */
  githubRateLimited: "GHB_UPS_003",
} as const;

// ── Enum Schemas ────────────────────────────────────────────────────

/** The team's review status of a share, chosen by its author. */
export const PullRequestShareStatusSchema = z.enum(["DRAFT", "OPEN", "IN_REVIEW", "MERGED", "DISCUSS"]);
export type PullRequestShareStatus = z.infer<typeof PullRequestShareStatusSchema>;
export const PullRequestShareStatus = PullRequestShareStatusSchema.enum;

/** The pull request's state on GitHub: MERGED when merged, DRAFT when an open draft. */
export const GithubPullRequestStateSchema = z.enum(["OPEN", "DRAFT", "MERGED", "CLOSED"]);
export type GithubPullRequestState = z.infer<typeof GithubPullRequestStateSchema>;
export const GithubPullRequestState = GithubPullRequestStateSchema.enum;

// ── Request Schemas ─────────────────────────────────────────────────

/**
 * One picker page. `search` "#379" or "379" finds that number and ignores
 * `states` and `authors`; other text searches title and body of the selected
 * repository. A row must be in one of `states` and by one of `authors`; an
 * empty list does not narrow.
 */
export const githubPullRequestQueryParamsSchema = z.object({
  search: z.string().max(PULL_REQUEST_SEARCH_MAX).optional(),
  page: z.number().int().min(PULL_REQUEST_FIRST_PAGE).optional(),
  states: z.array(GithubPullRequestStateSchema).optional(),
  authors: z.array(z.string().regex(GITHUB_LOGIN_PATTERN)).max(PULL_REQUEST_AUTHORS_MAX).optional(),
});

/**
 * Body of a share. The card's facts are read from GitHub by the server, so
 * only the pull request's identity is sent. A blank note is stored as null;
 * `reviewerIds` and `relatedTicketIds` are required, empty for none, in
 * display order.
 */
export const pullRequestShareCreateRequestSchema = z.object({
  githubRepositoryId: z.number().int().positive(),
  number: z.number().int().positive(),
  status: PullRequestShareStatusSchema,
  note: z.string().max(PULL_REQUEST_NOTE_MAX).nullable().optional(),
  reviewerIds: z.array(z.string()).max(PULL_REQUEST_REVIEWERS_MAX),
  relatedTicketIds: z.array(z.string()).max(PULL_REQUEST_RELATED_TICKETS_MAX),
});

/**
 * Body of the author's edit: the full new status, note, reviewers and
 * related tickets. The pull request itself cannot change. A kept reviewer
 * keeps their approval; a removed one loses it. A kept ticket keeps the
 * board name, key and title stored when it was added.
 */
export const pullRequestShareUpdateRequestSchema = pullRequestShareCreateRequestSchema.pick({
  status: true,
  note: true,
  reviewerIds: true,
  relatedTicketIds: true,
});

// ── Response Schemas ────────────────────────────────────────────────

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

/** The selected pull request with its branches and ticket key, for the preview. */
export const githubPullRequestResponseSchema = githubPullRequestSummaryResponseSchema.extend({
  headRef: z.string(),
  baseRef: z.string(),
  /** Upper-case ticket key in the head branch, else in the title. */
  ticketKey: z.string().nullable(),
  /** The ticket of `ticketKey`, only when the reader can open it. */
  linkedTicket: z.custom<ChatLinkedTicketResponse>().nullable(),
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
 * ticket is added, so every reader sees the same row and a later rename does
 * not change it. `canOpen` is computed for the reader: the ticket is live and
 * on one of their member boards.
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
  linkedTicket: z.custom<ChatLinkedTicketResponse>().nullable(),
  status: PullRequestShareStatusSchema,
  note: z.string().nullable(),
  /** In display order. */
  reviewers: z.array(pullRequestShareReviewerResponseSchema),
  /** In display order. */
  relatedTickets: z.array(pullRequestShareTicketResponseSchema),
  createdAt: z.string(),
  /** Time of the author's last edit; null when never edited. */
  editedAt: z.string().nullable(),
  /** The plain text as the team posts it, the same for every reader; what "Copy text" copies. */
  summaryText: z.string(),
});

// ── Form Schema ─────────────────────────────────────────────────────
// The messages are i18n keys, resolved by `FormMessage`. The limits mirror
// the server, which answers 422 for the same cases.

/**
 * The share form. `status` is empty until one is chosen, because a closed
 * pull request presets none; the form cannot be sent without one.
 */
export const pullRequestShareFormSchema = z.object({
  status: z
    .union([PullRequestShareStatusSchema, z.literal("")])
    // Not written as `status !== ""`: TypeScript would read that as a type
    // predicate and drop the empty value from the form's own type.
    .refine((status) => status.length > 0, "pullRequests.form.statusRequired"),
  note: z.string().max(PULL_REQUEST_NOTE_MAX, "pullRequests.form.noteTooLong"),
  reviewerIds: z.array(z.string()).max(PULL_REQUEST_REVIEWERS_MAX, "pullRequests.form.reviewersTooMany"),
  relatedTicketIds: z
    .array(z.string())
    .max(PULL_REQUEST_RELATED_TICKETS_MAX, "pullRequests.form.relatedTicketsTooMany"),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type GithubPullRequestQueryParams = z.infer<typeof githubPullRequestQueryParamsSchema>;
export type PullRequestShareCreateRequest = z.infer<typeof pullRequestShareCreateRequestSchema>;
export type PullRequestShareUpdateRequest = z.infer<typeof pullRequestShareUpdateRequestSchema>;
export type GithubRepositoryResponse = z.infer<typeof githubRepositoryResponseSchema>;
export type GithubPullRequestSummaryResponse = z.infer<typeof githubPullRequestSummaryResponseSchema>;
export type GithubPullRequestPageResponse = z.infer<typeof githubPullRequestPageResponseSchema>;
export type GithubContributorResponse = z.infer<typeof githubContributorResponseSchema>;
export type GithubPullRequestResponse = z.infer<typeof githubPullRequestResponseSchema>;
export type PullRequestShareReviewerResponse = z.infer<typeof pullRequestShareReviewerResponseSchema>;
export type PullRequestShareTicketResponse = z.infer<typeof pullRequestShareTicketResponseSchema>;
export type PullRequestShareResponse = z.infer<typeof pullRequestShareResponseSchema>;
export type PullRequestShareFormValues = z.infer<typeof pullRequestShareFormSchema>;

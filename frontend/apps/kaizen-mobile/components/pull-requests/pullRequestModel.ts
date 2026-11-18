import type { TranslationKey } from "@/i18n";
import { hasKaizenAccess } from "@/lib/kaizenAccess";
import type { ChatMemberResponse, ChatMessageResponse } from "@/lib/schemas/chat";
import {
  GithubPullRequestState,
  PULL_REQUEST_ERROR_CODES,
  PULL_REQUEST_NOTE_MAX,
  PULL_REQUEST_RELATED_TICKETS_MAX,
  PULL_REQUEST_REVIEWERS_MAX,
  PULL_REQUEST_SEARCH_MAX,
  PullRequestShareStatus,
  PullRequestShareStatusSchema,
  pullRequestShareResponseSchema,
  type PullRequestChannelFilter,
  type PullRequestShareCreateRequest,
  type PullRequestShareResponse,
  type PullRequestShareReviewerResponse,
  type PullRequestShareUpdateRequest,
} from "@/lib/schemas/pullRequests";

/*
 * The rules of the pull requests channel and the share form without React
 * and without the network, so they can be tested alone.
 */

/** Every share status, in the order the filters and the form list them. */
export const PULL_REQUEST_STATUSES: readonly PullRequestShareStatus[] =
  PullRequestShareStatusSchema.options;

/** Every GitHub state, in the order the picker lists them. */
export const GITHUB_PULL_REQUEST_STATES: readonly GithubPullRequestState[] = [
  GithubPullRequestState.OPEN,
  GithubPullRequestState.DRAFT,
  GithubPullRequestState.MERGED,
  GithubPullRequestState.CLOSED,
];

/** The picker starts on the pull requests still under work. */
export const DEFAULT_PICKER_STATES: readonly GithubPullRequestState[] = [
  GithubPullRequestState.OPEN,
  GithubPullRequestState.DRAFT,
];

/** The filter that narrows nothing. */
export const NO_PULL_REQUEST_FILTER: PullRequestChannelFilter = { statuses: [], personId: null };

/**
 * A pill's label and colours: the tint on the pill and the tone of its dot.
 * The label itself is always `foreground`, because tone colours read below
 * 4.5:1 as text on their own tints.
 */
export interface PullRequestPillStyle {
  labelKey: TranslationKey;
  className: string;
  dotClassName: string;
}

/** The team's statuses: Draft grey, Open blue, In Review amber, Merged purple, Discuss red. */
export const SHARE_STATUS_STYLE: Record<PullRequestShareStatus, PullRequestPillStyle> = {
  DRAFT: {
    labelKey: "prStatusDRAFT",
    className: "bg-muted",
    dotClassName: "bg-muted-foreground",
  },
  OPEN: { labelKey: "prStatusOPEN", className: "bg-info-light", dotClassName: "bg-info" },
  IN_REVIEW: {
    labelKey: "prStatusIN_REVIEW",
    className: "bg-warning-light",
    dotClassName: "bg-warning",
  },
  MERGED: {
    labelKey: "prStatusMERGED",
    className: "bg-purple-light",
    dotClassName: "bg-purple",
  },
  DISCUSS: {
    labelKey: "prStatusDISCUSS",
    className: "bg-error-light",
    dotClassName: "bg-error",
  },
};

/** GitHub's states: Open green, Draft grey, Merged purple, Closed red. */
export const GITHUB_STATE_STYLE: Record<GithubPullRequestState, PullRequestPillStyle> = {
  OPEN: { labelKey: "prStateOPEN", className: "bg-success-light", dotClassName: "bg-success" },
  DRAFT: {
    labelKey: "prStateDRAFT",
    className: "bg-muted",
    dotClassName: "bg-muted-foreground",
  },
  MERGED: { labelKey: "prStateMERGED", className: "bg-purple-light", dotClassName: "bg-purple" },
  CLOSED: { labelKey: "prStateCLOSED", className: "bg-error-light", dotClassName: "bg-error" },
};

// ── Filters ─────────────────────────────────────────────────────────

/**
 * The known statuses among `values`, once each, in enum order. The fixed
 * order makes one choice read one cache entry however it was ticked.
 */
export function orderPullRequestStatuses(values: Iterable<string>): PullRequestShareStatus[] {
  const chosen = new Set<string>(values);
  return PULL_REQUEST_STATUSES.filter((status) => chosen.has(status));
}

/** Ticks or unticks one status. */
export function togglePullRequestStatus(
  statuses: readonly PullRequestShareStatus[],
  status: PullRequestShareStatus
): PullRequestShareStatus[] {
  return statuses.includes(status)
    ? statuses.filter((value) => value !== status)
    : orderPullRequestStatuses([...statuses, status]);
}

/** The number of chosen filter values, the badge on the filter button. */
export function pullRequestFilterCount(filter: PullRequestChannelFilter): number {
  return filter.statuses.length + (filter.personId ? 1 : 0);
}

/** The people the Person filter offers, the chosen one first even when they wrote nothing live any more. */
export function pullRequestPersonOptions(
  authors: readonly ChatMemberResponse[],
  chosen: ChatMemberResponse | string | null,
  search: string
): ChatMemberResponse[] {
  const chosenId: string | null = typeof chosen === "string" ? chosen : (chosen?.userId ?? null);
  const listed: boolean = !chosenId || authors.some((author) => author.userId === chosenId);
  const people: ChatMemberResponse[] =
    listed || !chosen || typeof chosen === "string" ? [...authors] : [chosen, ...authors];
  const needle: string = search.trim().toLowerCase();
  if (!needle) return people;
  return people.filter(
    (person) =>
      person.userId === chosenId ||
      memberLabel(person).toLowerCase().includes(needle) ||
      (person.email ?? "").toLowerCase().includes(needle)
  );
}

/** A member's first and last name, else the email, else empty. */
export function memberLabel(
  person: Pick<ChatMemberResponse, "firstName" | "lastName" | "email">
): string {
  const name: string = [person.firstName, person.lastName].filter(Boolean).join(" ").trim();
  return name || (person.email ?? "");
}

// ── Cards ───────────────────────────────────────────────────────────

/**
 * The share a chat entry carries, checked against the share schema. Null for
 * a plain message, and for an entry whose share does not match, which is
 * logged and then shown as the chat's one-line entry.
 */
export function readChatPullRequestShare(
  message: Pick<ChatMessageResponse, "id" | "pullRequestShareId" | "pullRequestShare" | "deleted">
): PullRequestShareResponse | null {
  if (message.deleted || message.pullRequestShareId == null || !message.pullRequestShare)
    return null;
  const parsed = pullRequestShareResponseSchema.safeParse(message.pullRequestShare);
  if (parsed.success) return parsed.data;
  console.error(`[pull-requests] unreadable share on message ${message.id}`, parsed.error.message);
  return null;
}

/** What the reader may do with a card. */
export interface PullRequestCardRights {
  /** "approve" or "withdraw" for a chosen reviewer; null for anyone else. */
  review: "approve" | "withdraw" | null;
  /** Edit and Delete, for the author only. */
  manage: boolean;
}

export function pullRequestCardRights(
  share: Pick<PullRequestShareResponse, "authorId" | "reviewers">,
  meId: string | undefined
): PullRequestCardRights {
  if (!meId) return { review: null, manage: false };
  const mine: PullRequestShareReviewerResponse | undefined = share.reviewers.find(
    (reviewer) => reviewer.userId === meId
  );
  return {
    review: mine ? (mine.approvedAt ? "withdraw" : "approve") : null,
    manage: share.authorId === meId,
  };
}

/** True when the share waits on the reader's review: the card is highlighted. */
export function pullRequestAwaitsMyReview(
  share: Pick<PullRequestShareResponse, "status" | "reviewers">,
  meId: string | undefined
): boolean {
  return (
    !!meId &&
    share.status !== PullRequestShareStatus.MERGED &&
    share.reviewers.some((reviewer) => reviewer.userId === meId && reviewer.approvedAt === null)
  );
}

/** A reviewer's name, or `fallback` when the account has none. */
export function reviewerName(
  reviewer: Pick<PullRequestShareReviewerResponse, "firstName" | "lastName">,
  fallback: string
): string {
  return [reviewer.firstName, reviewer.lastName].filter(Boolean).join(" ").trim() || fallback;
}

// ── Errors ──────────────────────────────────────────────────────────

const ERROR_KEYS: Record<string, TranslationKey> = {
  [PULL_REQUEST_ERROR_CODES.githubNotConfigured]: "prErrorNotConnected",
  [PULL_REQUEST_ERROR_CODES.repositoryNotFound]: "prErrorRepositoryNotFound",
  [PULL_REQUEST_ERROR_CODES.pullRequestNotFound]: "prErrorPullRequestNotFound",
  [PULL_REQUEST_ERROR_CODES.shareNotFound]: "prErrorShareNotFound",
  [PULL_REQUEST_ERROR_CODES.githubRateLimited]: "prErrorRateLimited",
  [PULL_REQUEST_ERROR_CODES.githubUnreachable]: "prErrorUnreachable",
  [PULL_REQUEST_ERROR_CODES.githubCredentialsRefused]: "prErrorCredentialsRefused",
};

/**
 * The text of a failed GitHub or share read, by the server's error code,
 * which the screens explain in their own words because the next step
 * differs: wait for the rate limit, retry an unreachable GitHub, or ask an
 * administrator. Null for any other failure.
 */
export function pullRequestErrorKey(code: string | undefined): TranslationKey | null {
  if (!code) return null;
  return ERROR_KEYS[code] ?? null;
}

// ── Share form ──────────────────────────────────────────────────────

/** The share form's values; `status` is null until one is chosen. */
export interface PullRequestShareDraft {
  status: PullRequestShareStatus | null;
  note: string;
  reviewerIds: string[];
  relatedTicketIds: string[];
}

export const EMPTY_SHARE_DRAFT: PullRequestShareDraft = {
  status: null,
  note: "",
  reviewerIds: [],
  relatedTicketIds: [],
};

/** The form of an existing share, for the author's edit. */
export function draftFromShare(share: PullRequestShareResponse): PullRequestShareDraft {
  return {
    status: share.status,
    note: share.note ?? "",
    reviewerIds: share.reviewers.map((reviewer) => reviewer.userId),
    relatedTicketIds: share.relatedTickets.map((ticket) => ticket.ticketId),
  };
}

const STATUS_FROM_STATE: Record<GithubPullRequestState, PullRequestShareStatus | null> = {
  OPEN: PullRequestShareStatus.OPEN,
  DRAFT: PullRequestShareStatus.DRAFT,
  MERGED: PullRequestShareStatus.MERGED,
  CLOSED: null,
};

/** The status a picked pull request presets: its GitHub state, none for a closed one. */
export function statusFromGithubState(
  state: GithubPullRequestState
): PullRequestShareStatus | null {
  return STATUS_FROM_STATE[state];
}

/** The fields of the share form that stop Send, with the key of the message under each. */
export type PullRequestShareProblems = Partial<
  Record<"pullRequest" | "status" | "note" | "reviewers" | "relatedTickets", TranslationKey>
>;

/** The same checks the server makes, so Send stays off until the form would pass. */
export function pullRequestShareProblems(
  draft: PullRequestShareDraft,
  pullRequestPicked: boolean
): PullRequestShareProblems {
  const problems: PullRequestShareProblems = {};
  if (!pullRequestPicked) problems.pullRequest = "prFormPullRequestRequired";
  if (draft.status === null) problems.status = "prFormStatusRequired";
  if (draft.note.length > PULL_REQUEST_NOTE_MAX) problems.note = "prFormNoteTooLong";
  if (draft.reviewerIds.length > PULL_REQUEST_REVIEWERS_MAX)
    problems.reviewers = "prFormReviewersTooMany";
  if (draft.relatedTicketIds.length > PULL_REQUEST_RELATED_TICKETS_MAX) {
    problems.relatedTickets = "prFormRelatedTicketsTooMany";
  }
  return problems;
}

/** The edit body; a blank note is sent as null, so the card shows none. */
export function toShareUpdateRequest(draft: PullRequestShareDraft): PullRequestShareUpdateRequest {
  if (draft.status === null) throw new Error("A pull request share was submitted without a status");
  return {
    status: draft.status,
    note: draft.note.trim() ? draft.note : null,
    reviewerIds: draft.reviewerIds,
    relatedTicketIds: draft.relatedTicketIds,
  };
}

/** The create body: the edit body plus the pull request's identity. */
export function toShareCreateRequest(
  draft: PullRequestShareDraft,
  pullRequest: { githubRepositoryId: number; number: number }
): PullRequestShareCreateRequest {
  return { ...toShareUpdateRequest(draft), ...pullRequest };
}

/** The picker's search as the server accepts it: trimmed and at most 100 characters. */
export function pullRequestPickerSearch(text: string): string {
  return text.trim().slice(0, PULL_REQUEST_SEARCH_MAX);
}

/**
 * Why a reviewer picker row cannot be ticked: the member has no Kaizen
 * access, which the server refuses for a reviewer, or the row is a stored
 * reviewer no longer among the members offered.
 */
export type ReviewerRestriction = "noKaizenAccess" | "former";

/** A row of the reviewer picker. */
export interface ReviewerCandidate {
  userId: string;
  name: string;
  /** Null for a former reviewer, since the share keeps only the name. */
  email: string | null;
  /** Null when the row can be ticked. */
  restriction: ReviewerRestriction | null;
}

/**
 * The rows of the reviewer picker, matched on name and email: the
 * organization's members but the author, who cannot review their own share,
 * and guests, who cannot write in the channel. Members without Kaizen are
 * listed with the reason they cannot be picked. Then every stored reviewer
 * missing from those members, such as one who left the organization, by name
 * or else by id: listed so the author can see and remove them, since the
 * server refuses to keep them, and not pickable again once removed.
 */
export function reviewerCandidates(
  members: readonly ChatMemberResponse[],
  meId: string | undefined,
  storedReviewers: readonly PullRequestShareReviewerResponse[],
  search: string
): ReviewerCandidate[] {
  const offered: ReviewerCandidate[] = members
    .filter((member) => member.userId !== meId && member.role !== "GUEST")
    .map((member) => ({
      userId: member.userId,
      name: memberLabel(member),
      email: member.email,
      restriction: hasKaizenAccess(member) ? null : "noKaizenAccess",
    }));
  const listed = new Set<string>(offered.map((candidate) => candidate.userId));
  const former: ReviewerCandidate[] = storedReviewers
    .filter((reviewer) => !listed.has(reviewer.userId))
    .map((reviewer) => ({
      userId: reviewer.userId,
      name: reviewerName(reviewer, reviewer.userId),
      email: null,
      restriction: "former",
    }));
  const needle: string = search.trim().toLowerCase();
  return [...offered, ...former].filter(
    (candidate) =>
      !needle ||
      candidate.name.toLowerCase().includes(needle) ||
      (candidate.email ?? "").toLowerCase().includes(needle)
  );
}

/**
 * The names of the chosen reviewers, in the order they were picked, for the
 * share form's reviewer row. A member is named as in the picker; a stored
 * reviewer no longer among the members, such as one who left the
 * organization, by the name the share keeps or else by id, so the row names
 * everyone the share still lists, as the web form's chips do.
 */
export function reviewerSummaryNames(
  reviewerIds: readonly string[],
  members: readonly ChatMemberResponse[],
  storedReviewers: readonly PullRequestShareReviewerResponse[]
): string[] {
  const memberById = new Map<string, ChatMemberResponse>(
    members.map((member) => [member.userId, member])
  );
  const storedById = new Map<string, PullRequestShareReviewerResponse>(
    storedReviewers.map((reviewer) => [reviewer.userId, reviewer])
  );
  return reviewerIds.flatMap((id) => {
    const member: ChatMemberResponse | undefined = memberById.get(id);
    if (member) return [memberLabel(member)];
    const stored: PullRequestShareReviewerResponse | undefined = storedById.get(id);
    return stored ? [reviewerName(stored, stored.userId)] : [];
  });
}

/** True when an unticked row cannot be ticked; a ticked row stays removable. */
export function reviewerPickBlocked(candidate: ReviewerCandidate, checked: boolean): boolean {
  return candidate.restriction !== null && !checked;
}

/** Ticks or unticks an id in a list that holds at most `max`; a tick past the limit is refused. */
export function toggleLimited(ids: readonly string[], id: string, max: number): string[] {
  if (ids.includes(id)) return ids.filter((value) => value !== id);
  if (ids.length >= max) return [...ids];
  return [...ids, id];
}

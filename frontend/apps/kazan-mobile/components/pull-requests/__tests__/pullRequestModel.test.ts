import { describe, expect, it, spyOn } from "bun:test";

import { ALICE, ME, chatMessage } from "@/components/chat/__tests__/chatFixtures";
import {
  EMPTY_SHARE_DRAFT,
  NO_PULL_REQUEST_FILTER,
  draftFromShare,
  memberLabel,
  orderPullRequestStatuses,
  pullRequestAwaitsMyReview,
  pullRequestCardRights,
  pullRequestErrorKey,
  pullRequestFilterCount,
  pullRequestPersonOptions,
  pullRequestPickerSearch,
  pullRequestShareProblems,
  readChatPullRequestShare,
  reviewerCandidates,
  reviewerName,
  reviewerPickBlocked,
  reviewerSummaryNames,
  statusFromGithubState,
  togglePullRequestStatus,
  toggleLimited,
  toShareCreateRequest,
  toShareUpdateRequest,
} from "@/components/pull-requests/pullRequestModel";
import type { ChatMemberResponse } from "@/lib/schemas/chat";
import {
  PULL_REQUEST_ERROR_CODES,
  type PullRequestShareResponse,
  type PullRequestShareReviewerResponse,
} from "@/lib/schemas/pullRequests";

const BOB = "44444444-4444-4444-8444-444444444444";

function share(overrides: Partial<PullRequestShareResponse> = {}): PullRequestShareResponse {
  return {
    id: "share-1",
    authorId: ALICE,
    repositoryFullName: "senseylabs/village",
    number: 702,
    title: "Add pull request cards",
    url: "https://github.com/senseylabs/village/pull/702",
    githubAuthorLogin: "alice",
    headRef: "feature/KAI-12-cards",
    baseRef: "main",
    ticketKey: "KAI-12",
    linkedTicket: null,
    status: "IN_REVIEW",
    note: null,
    reviewers: [],
    relatedTickets: [],
    createdAt: "2026-11-09T09:00:00Z",
    editedAt: null,
    summaryText: "KAI-12: Add pull request cards",
    ...overrides,
  };
}

function member(userId: string, firstName: string, email: string): ChatMemberResponse {
  return { userId, firstName, lastName: null, email, role: "MEMBER", online: false };
}

describe("status filter", () => {
  it("keeps the enum order and drops unknown values", () => {
    expect(orderPullRequestStatuses(["MERGED", "DRAFT", "BOGUS", "DRAFT"])).toEqual([
      "DRAFT",
      "MERGED",
    ]);
  });

  it("toggles a status in and out in enum order", () => {
    expect(togglePullRequestStatus(["MERGED"], "OPEN")).toEqual(["OPEN", "MERGED"]);
    expect(togglePullRequestStatus(["OPEN", "MERGED"], "OPEN")).toEqual(["MERGED"]);
  });

  it("counts statuses and the person", () => {
    expect(pullRequestFilterCount(NO_PULL_REQUEST_FILTER)).toBe(0);
    expect(pullRequestFilterCount({ statuses: ["OPEN", "DRAFT"], personId: ALICE })).toBe(3);
  });
});

describe("person options", () => {
  const authors: ChatMemberResponse[] = [
    member(ALICE, "Alice", "alice@example.com"),
    member(BOB, "Bob", "bob@example.com"),
  ];

  it("searches name and email", () => {
    expect(pullRequestPersonOptions(authors, null, "BOB").map((p) => p.userId)).toEqual([BOB]);
    expect(pullRequestPersonOptions(authors, null, "alice@").map((p) => p.userId)).toEqual([ALICE]);
  });

  it("keeps the chosen person first when they are no longer an author", () => {
    const chosen: ChatMemberResponse = member(ME, "Me", "me@example.com");
    expect(pullRequestPersonOptions(authors, chosen, "").map((p) => p.userId)).toEqual([
      ME,
      ALICE,
      BOB,
    ]);
    expect(pullRequestPersonOptions(authors, chosen, "bob").map((p) => p.userId)).toEqual([
      ME,
      BOB,
    ]);
  });

  it("labels a member by name, else by email", () => {
    expect(memberLabel({ firstName: null, lastName: null, email: "x@example.com" })).toBe(
      "x@example.com"
    );
  });
});

describe("readChatPullRequestShare", () => {
  it("reads the share of a share entry", () => {
    const message = chatMessage({ pullRequestShareId: "share-1", pullRequestShare: share() });
    expect(readChatPullRequestShare(message)?.number).toBe(702);
  });

  it("returns null for a plain or deleted message", () => {
    expect(readChatPullRequestShare(chatMessage())).toBeNull();
    expect(
      readChatPullRequestShare(
        chatMessage({ deleted: true, pullRequestShareId: "share-1", pullRequestShare: share() })
      )
    ).toBeNull();
  });

  it("logs and returns null for a share that does not match its schema", () => {
    const errorSpy = spyOn(console, "error").mockImplementation(() => undefined);
    const message = chatMessage({
      pullRequestShareId: "share-1",
      pullRequestShare: { summaryText: "only a summary" },
    });
    expect(readChatPullRequestShare(message)).toBeNull();
    expect(errorSpy).toHaveBeenCalled();
    errorSpy.mockRestore();
  });
});

describe("card rights", () => {
  const reviewed = share({
    reviewers: [
      { userId: ME, firstName: "Me", lastName: null, approvedAt: null },
      { userId: BOB, firstName: "Bob", lastName: null, approvedAt: "2026-11-09T10:00:00Z" },
    ],
  });

  it("offers approve to a reviewer who has not approved, withdraw to one who has", () => {
    expect(pullRequestCardRights(reviewed, ME)).toEqual({ review: "approve", manage: false });
    expect(pullRequestCardRights(reviewed, BOB)).toEqual({ review: "withdraw", manage: false });
  });

  it("offers edit and delete to the author only", () => {
    expect(pullRequestCardRights(reviewed, ALICE)).toEqual({ review: null, manage: true });
    expect(pullRequestCardRights(reviewed, undefined)).toEqual({ review: null, manage: false });
  });

  it("highlights a share waiting on the reader, not a merged one", () => {
    expect(pullRequestAwaitsMyReview(reviewed, ME)).toBe(true);
    expect(pullRequestAwaitsMyReview(reviewed, BOB)).toBe(false);
    expect(pullRequestAwaitsMyReview({ ...reviewed, status: "MERGED" }, ME)).toBe(false);
  });

  it("names a reviewer, with a fallback", () => {
    expect(reviewerName({ firstName: "Ada", lastName: "Lovelace" }, "?")).toBe("Ada Lovelace");
    expect(reviewerName({ firstName: null, lastName: null }, "?")).toBe("?");
  });
});

describe("pullRequestErrorKey", () => {
  it("maps the GitHub and share codes", () => {
    expect(pullRequestErrorKey(PULL_REQUEST_ERROR_CODES.githubRateLimited)).toBe(
      "prErrorRateLimited"
    );
    expect(pullRequestErrorKey(PULL_REQUEST_ERROR_CODES.githubNotConfigured)).toBe(
      "prErrorNotConnected"
    );
    expect(pullRequestErrorKey(PULL_REQUEST_ERROR_CODES.shareNotFound)).toBe(
      "prErrorShareNotFound"
    );
  });

  it("returns null for any other failure", () => {
    expect(pullRequestErrorKey(undefined)).toBeNull();
    expect(pullRequestErrorKey("GEN_ERR")).toBeNull();
  });
});

describe("share form", () => {
  it("presets the status from the GitHub state, none for a closed pull request", () => {
    expect(statusFromGithubState("OPEN")).toBe("OPEN");
    expect(statusFromGithubState("DRAFT")).toBe("DRAFT");
    expect(statusFromGithubState("MERGED")).toBe("MERGED");
    expect(statusFromGithubState("CLOSED")).toBeNull();
  });

  it("blocks Send until a pull request and a status are chosen", () => {
    expect(pullRequestShareProblems(EMPTY_SHARE_DRAFT, false)).toEqual({
      pullRequest: "prFormPullRequestRequired",
      status: "prFormStatusRequired",
    });
    expect(pullRequestShareProblems({ ...EMPTY_SHARE_DRAFT, status: "OPEN" }, true)).toEqual({});
  });

  it("blocks a note over 500 characters and lists over 10", () => {
    const ids: string[] = Array.from({ length: 11 }, (_, index) => `id-${index}`);
    expect(
      pullRequestShareProblems(
        { status: "OPEN", note: "x".repeat(501), reviewerIds: ids, relatedTicketIds: ids },
        true
      )
    ).toEqual({
      note: "prFormNoteTooLong",
      reviewers: "prFormReviewersTooMany",
      relatedTickets: "prFormRelatedTicketsTooMany",
    });
  });

  it("sends a blank note as null and adds the pull request on create", () => {
    const draft = {
      status: "IN_REVIEW" as const,
      note: "   ",
      reviewerIds: [BOB],
      relatedTicketIds: [],
    };
    expect(toShareUpdateRequest(draft)).toEqual({
      status: "IN_REVIEW",
      note: null,
      reviewerIds: [BOB],
      relatedTicketIds: [],
    });
    expect(
      toShareCreateRequest({ ...draft, note: "ready" }, { githubRepositoryId: 9, number: 702 })
    ).toEqual({
      status: "IN_REVIEW",
      note: "ready",
      reviewerIds: [BOB],
      relatedTicketIds: [],
      githubRepositoryId: 9,
      number: 702,
    });
  });

  it("refuses a request without a status", () => {
    expect(() => toShareUpdateRequest(EMPTY_SHARE_DRAFT)).toThrow();
  });

  it("starts the edit form from the share", () => {
    const edited = share({
      note: "ready",
      reviewers: [{ userId: BOB, firstName: "Bob", lastName: null, approvedAt: null }],
      relatedTickets: [
        {
          ticketId: "t-1",
          boardId: "b-1",
          boardName: "Kazan",
          ticketKey: "KAI-1",
          ticketTitle: "One",
          canOpen: true,
        },
      ],
    });
    expect(draftFromShare(edited)).toEqual({
      status: "IN_REVIEW",
      note: "ready",
      reviewerIds: [BOB],
      relatedTicketIds: ["t-1"],
    });
  });

  it("trims and caps the picker search at 100 characters", () => {
    expect(pullRequestPickerSearch("  #702 ")).toBe("#702");
    expect(pullRequestPickerSearch("y".repeat(150))).toHaveLength(100);
  });

  it("refuses a tick past the limit and unticks freely", () => {
    expect(toggleLimited(["a", "b"], "c", 2)).toEqual(["a", "b"]);
    expect(toggleLimited(["a", "b"], "a", 2)).toEqual(["b"]);
    expect(toggleLimited(["a"], "b", 2)).toEqual(["a", "b"]);
  });
});

describe("reviewerCandidates", () => {
  const CAROL = "55555555-5555-4555-8555-555555555555";
  const DAVE = "66666666-6666-4666-8666-666666666666";
  const members: ChatMemberResponse[] = [
    member(ME, "Me", "me@example.com"),
    { ...member(ALICE, "Alice", "alice@example.com"), kazanAccess: true },
    { ...member(BOB, "Bob", "bob@example.com"), kazanAccess: true },
  ];

  function stored(
    userId: string,
    firstName: string | null,
    lastName: string | null = null
  ): PullRequestShareReviewerResponse {
    return { userId, firstName, lastName, approvedAt: null };
  }

  it("leaves out the author, who cannot review their own share", () => {
    expect(reviewerCandidates(members, ME, [], "").map((m) => m.userId)).toEqual([ALICE, BOB]);
  });

  it("leaves out guests, as the web picker does", () => {
    const rows = reviewerCandidates(
      [...members, { ...member(CAROL, "Carol", "carol@example.com"), role: "GUEST" }],
      ME,
      [],
      ""
    );
    expect(rows.map((m) => m.userId)).toEqual([ALICE, BOB]);
  });

  it("matches the search on name and email", () => {
    expect(reviewerCandidates(members, ME, [], " bob ").map((m) => m.userId)).toEqual([BOB]);
    expect(reviewerCandidates(members, ME, [], "alice@").map((m) => m.userId)).toEqual([ALICE]);
    expect(reviewerCandidates(members, ME, [], "me@")).toEqual([]);
  });

  it("marks a member without Kazan, so the picker shows why", () => {
    const rows = reviewerCandidates(
      [
        { ...member(ALICE, "Alice", "alice@example.com"), kazanAccess: true },
        { ...member(BOB, "Bob", "bob@example.com"), kazanAccess: false },
      ],
      ME,
      [],
      ""
    );
    expect(rows.map((m) => [m.userId, m.restriction])).toEqual([
      [ALICE, null],
      [BOB, "noKazanAccess"],
    ]);
  });

  it("appends a stored reviewer who left, by name or else by id", () => {
    const rows = reviewerCandidates(
      members,
      ME,
      [stored(ALICE, "Alice"), stored(CAROL, "Carol", "Reyes"), stored(DAVE, null)],
      ""
    );
    expect(rows).toEqual([
      { userId: ALICE, name: "Alice", email: "alice@example.com", restriction: null },
      { userId: BOB, name: "Bob", email: "bob@example.com", restriction: null },
      { userId: CAROL, name: "Carol Reyes", email: null, restriction: "former" },
      { userId: DAVE, name: DAVE, email: null, restriction: "former" },
    ]);
  });

  it("appends a stored reviewer who became a guest as a former reviewer", () => {
    const rows = reviewerCandidates(
      [...members, { ...member(CAROL, "Carol", "carol@example.com"), role: "GUEST" }],
      ME,
      [stored(CAROL, "Carol")],
      ""
    );
    expect(rows.map((m) => [m.userId, m.restriction])).toEqual([
      [ALICE, null],
      [BOB, null],
      [CAROL, "former"],
    ]);
  });

  it("matches a former reviewer on name", () => {
    const rows = reviewerCandidates(members, ME, [stored(CAROL, "Carol")], "car");
    expect(rows.map((m) => m.userId)).toEqual([CAROL]);
  });
});

describe("reviewerSummaryNames", () => {
  const CAROL = "55555555-5555-4555-8555-555555555555";
  const DAVE = "66666666-6666-4666-8666-666666666666";
  const members: ChatMemberResponse[] = [
    member(ALICE, "Alice", "alice@example.com"),
    member(BOB, "Bob", "bob@example.com"),
  ];
  const storedReviewers: PullRequestShareReviewerResponse[] = [
    { userId: ALICE, firstName: "Alicia", lastName: null, approvedAt: null },
    { userId: CAROL, firstName: "Carol", lastName: "Reyes", approvedAt: null },
    { userId: DAVE, firstName: null, lastName: null, approvedAt: null },
  ];

  it("names members as the picker does, in the order picked", () => {
    expect(reviewerSummaryNames([BOB, ALICE], members, storedReviewers)).toEqual(["Bob", "Alice"]);
  });

  it("names a stored reviewer who left by the stored name, else by id", () => {
    expect(reviewerSummaryNames([ALICE, CAROL, DAVE], members, storedReviewers)).toEqual([
      "Alice",
      "Carol Reyes",
      DAVE,
    ]);
  });

  it("names stored reviewers while the members are still loading", () => {
    expect(reviewerSummaryNames([ALICE, CAROL], [], storedReviewers)).toEqual([
      "Alicia",
      "Carol Reyes",
    ]);
  });

  it("skips an id that is neither a member nor stored", () => {
    expect(reviewerSummaryNames([CAROL], members, [])).toEqual([]);
  });
});

describe("reviewerPickBlocked", () => {
  const open = { userId: ALICE, name: "Alice", email: null, restriction: null };
  const noAccess = { ...open, restriction: "noKazanAccess" as const };
  const former = { ...open, restriction: "former" as const };

  it("lets any ticked row be removed", () => {
    expect(reviewerPickBlocked(open, true)).toBe(false);
    expect(reviewerPickBlocked(noAccess, true)).toBe(false);
    expect(reviewerPickBlocked(former, true)).toBe(false);
  });

  it("refuses to tick a member without Kazan or a former reviewer again", () => {
    expect(reviewerPickBlocked(open, false)).toBe(false);
    expect(reviewerPickBlocked(noAccess, false)).toBe(true);
    expect(reviewerPickBlocked(former, false)).toBe(true);
  });
});

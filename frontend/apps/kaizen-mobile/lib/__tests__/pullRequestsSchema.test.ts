import { describe, expect, it } from "bun:test";

import {
  githubPullRequestPageResponseSchema,
  pullRequestShareCreateRequestSchema,
  pullRequestShareResponseSchema,
} from "@/lib/schemas/pullRequests";

const SHARE = {
  id: "share-1",
  authorId: "user-1",
  repositoryFullName: "senseylabs/village",
  number: 702,
  title: "Add pull request cards",
  url: "https://github.com/senseylabs/village/pull/702",
  githubAuthorLogin: "alice",
  headRef: "feature/KAI-12-cards",
  baseRef: "main",
  ticketKey: "KAI-12",
  linkedTicket: { ticketKey: "KAI-12", ticketId: "t-12", boardId: "b-1" },
  status: "IN_REVIEW",
  note: null,
  reviewers: [
    { userId: "user-2", firstName: "Bob", lastName: null, approvedAt: "2026-11-09T10:00:00Z" },
  ],
  relatedTickets: [],
  createdAt: "2026-11-09T09:00:00Z",
  editedAt: null,
  summaryText: "KAI-12: Add pull request cards",
};

describe("pull request schemas", () => {
  it("reads a share as the server sends it", () => {
    expect(pullRequestShareResponseSchema.parse(SHARE).reviewers[0].approvedAt).toBe(
      "2026-11-09T10:00:00Z"
    );
  });

  it("refuses an unknown share status", () => {
    expect(pullRequestShareResponseSchema.safeParse({ ...SHARE, status: "CLOSED" }).success).toBe(
      false
    );
  });

  it("requires the note key on a create body, null for none", () => {
    const body = {
      githubRepositoryId: 9,
      number: 702,
      status: "OPEN",
      reviewerIds: [],
      relatedTicketIds: [],
    };
    expect(pullRequestShareCreateRequestSchema.safeParse(body).success).toBe(false);
    expect(pullRequestShareCreateRequestSchema.safeParse({ ...body, note: null }).success).toBe(
      true
    );
    expect(
      pullRequestShareCreateRequestSchema.safeParse({ ...body, note: "x".repeat(501) }).success
    ).toBe(false);
  });

  it("reads a picker page", () => {
    const page = githubPullRequestPageResponseSchema.parse({
      items: [
        {
          number: 702,
          title: "Add pull request cards",
          state: "DRAFT",
          authorLogin: "alice",
          updatedAt: "2026-11-09T09:00:00Z",
          url: "https://github.com/senseylabs/village/pull/702",
        },
      ],
      page: 1,
      hasMore: false,
      resultsLimited: false,
    });
    expect(page.items[0].state).toBe("DRAFT");
  });
});

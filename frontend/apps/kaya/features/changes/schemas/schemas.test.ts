import { describe, expect, test } from "bun:test";

import {
  CHANGE_LIMITS,
  changeDetailResponseSchema,
  changeSubmitRequestSchema,
  reviewRequestSchema,
  reviewResultResponseSchema,
} from ".";

const VERSION = {
  id: "v-7",
  packId: "p-1",
  number: 7,
  status: "IN_REVIEW",
  baseVersionId: "v-6",
  proposedBy: "user-ada",
  source: "CLI",
  reason: "Allow the kaizen CLI in every repository",
  executableChange: true,
  manifestSha256: null,
  submittedAt: "2026-10-24T09:00:00Z",
  decidedAt: null,
  createdAt: "2026-10-24T08:00:00Z",
  updatedAt: "2026-10-24T09:00:00Z",
};

describe("review request", () => {
  test("an approval needs no comment; an empty one is left out", () => {
    expect(reviewRequestSchema.parse({ decision: "APPROVE" })).toEqual({
      decision: "APPROVE",
    });
    expect(reviewRequestSchema.parse({ decision: "APPROVE", comment: "  " })).toEqual({
      decision: "APPROVE",
    });
  });

  test("a rejection and a comment need the reviewer's words, trimmed", () => {
    expect(reviewRequestSchema.safeParse({ decision: "REJECT", comment: " " }).success).toBe(false);
    expect(reviewRequestSchema.safeParse({ decision: "COMMENT" }).success).toBe(false);
    expect(reviewRequestSchema.parse({ decision: "REJECT", comment: " too broad " })).toEqual({
      decision: "REJECT",
      comment: "too broad",
    });
  });

  test("a comment has at most 2000 characters", () => {
    const comment = "x".repeat(CHANGE_LIMITS.commentMax + 1);
    expect(reviewRequestSchema.safeParse({ decision: "COMMENT", comment }).success).toBe(false);
  });
});

describe("submit", () => {
  test("a reason has 10 to 1000 characters", () => {
    expect(changeSubmitRequestSchema.safeParse({ reason: "too short" }).success).toBe(false);
    expect(changeSubmitRequestSchema.parse({ reason: " long enough now " })).toEqual({
      reason: "long enough now",
    });
  });
});

describe("responses as built", () => {
  test("a change detail with violations and the caller's permissions", () => {
    const detail = changeDetailResponseSchema.parse({
      version: VERSION,
      requiredRole: "OWNER",
      reviews: [
        {
          id: "r-1",
          versionId: "v-7",
          reviewerId: "user-bob",
          decision: "COMMENT",
          comment: "Looks fine",
          createdAt: "2026-10-24T10:00:00Z",
        },
      ],
      violations: [
        {
          path: ".agents/rules/a.md",
          rule: "PATH",
          repositoryId: "repo-1",
        },
      ],
      canApprove: false,
      canReview: true,
    });
    expect(detail.violations?.[0]?.rule).toBe("PATH");
    expect(detail.reviews[0]?.decision).toBe("COMMENT");
  });

  test("a version not in review has null violations", () => {
    const parsed = changeDetailResponseSchema.safeParse({
      version: { ...VERSION, status: "APPROVED" },
      requiredRole: "ADMIN",
      reviews: [],
      violations: null,
      canApprove: false,
      canReview: false,
    });
    expect(parsed.success).toBe(true);
  });

  test("a review result with its releases", () => {
    const parsed = reviewResultResponseSchema.safeParse({
      review: {
        id: "r-2",
        versionId: "v-7",
        reviewerId: "user-bob",
        decision: "APPROVE",
        comment: null,
        createdAt: "2026-10-24T10:00:00Z",
      },
      version: { ...VERSION, status: "APPROVED" },
      releases: [
        {
          id: "rel-1",
          repositoryId: "repo-1",
          number: 3,
          causeVersionId: "v-7",
          manifestSha256: "ab",
          keyId: "k1",
          fileCount: 40,
          createdAt: "2026-10-24T10:00:01Z",
        },
      ],
    });
    expect(parsed.success).toBe(true);
  });
});

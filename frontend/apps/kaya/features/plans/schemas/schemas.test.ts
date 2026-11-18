import { describe, expect, test } from "bun:test";

import {
  PLAN_LIMITS,
  planDetailResponseSchema,
  planFeedbackRequestSchema,
  planListResponseSchema,
  planQueryRequestSchema,
} from ".";

const LIST_ROW = {
  id: "p-1",
  kind: "SPEC",
  title: "04 Kaya plans",
  path: "docs/private/plans/kaya/04_KAYA_PLANS.md",
  repository: { id: "r-1", name: "village" },
  pack: { id: "s-1", name: "kaya" },
  user: { id: "a-1", name: "Ada" },
  reviewState: "OPEN",
  latestVersionNumber: 2,
  openFeedbackCount: 1,
  lastChangedAt: "2026-10-25T09:00:00Z",
};

describe("feedback request", () => {
  test("an approval needs no comment; an empty one is left out", () => {
    expect(planFeedbackRequestSchema.parse({ versionNumber: 2, decision: "APPROVE" })).toEqual({
      versionNumber: 2,
      decision: "APPROVE",
    });
    expect(
      planFeedbackRequestSchema.parse({ versionNumber: 2, decision: "APPROVE", comment: "  " })
    ).toEqual({ versionNumber: 2, decision: "APPROVE" });
  });

  test("a change request and a comment need 1 to 4000 characters, trimmed", () => {
    for (const decision of ["REQUEST_CHANGES", "COMMENT"] as const) {
      expect(planFeedbackRequestSchema.safeParse({ versionNumber: 1, decision }).success).toBe(
        false
      );
      expect(
        planFeedbackRequestSchema.safeParse({ versionNumber: 1, decision, comment: " " }).success
      ).toBe(false);
      expect(
        planFeedbackRequestSchema.safeParse({
          versionNumber: 1,
          decision,
          comment: "x".repeat(PLAN_LIMITS.commentMax + 1),
        }).success
      ).toBe(false);
      expect(
        planFeedbackRequestSchema.parse({ versionNumber: 1, decision, comment: " Split it " })
          .comment
      ).toBe("Split it");
    }
  });

  test("a line range has both bounds or none, ending at or after its start", () => {
    const base = { versionNumber: 1, decision: "COMMENT", comment: "Here" } as const;
    expect(planFeedbackRequestSchema.safeParse({ ...base, lineStart: 40 }).success).toBe(false);
    expect(planFeedbackRequestSchema.safeParse({ ...base, lineEnd: 44 }).success).toBe(false);
    expect(
      planFeedbackRequestSchema.safeParse({ ...base, lineStart: 44, lineEnd: 40 }).success
    ).toBe(false);
    expect(planFeedbackRequestSchema.safeParse({ ...base, lineStart: 0, lineEnd: 1 }).success).toBe(
      false
    );
    expect(
      planFeedbackRequestSchema.safeParse({ ...base, lineStart: 40, lineEnd: 40 }).success
    ).toBe(true);
  });

  test("a quote has at most 500 characters", () => {
    const base = { versionNumber: 1, decision: "COMMENT", comment: "Here" } as const;
    expect(
      planFeedbackRequestSchema.safeParse({ ...base, quote: "x".repeat(PLAN_LIMITS.quoteMax) })
        .success
    ).toBe(true);
    expect(
      planFeedbackRequestSchema.safeParse({ ...base, quote: "x".repeat(PLAN_LIMITS.quoteMax + 1) })
        .success
    ).toBe(false);
  });
});

describe("list query", () => {
  test("pages of at most 100 and a search of at most 200", () => {
    expect(planQueryRequestSchema.safeParse({ amount: 100 }).success).toBe(true);
    expect(planQueryRequestSchema.safeParse({ amount: 101 }).success).toBe(false);
    expect(planQueryRequestSchema.safeParse({ search: "x".repeat(201) }).success).toBe(false);
  });
});

describe("responses as built", () => {
  test("a session plan has no path and no pack", () => {
    const row = planListResponseSchema.parse({
      ...LIST_ROW,
      kind: "SESSION",
      path: null,
      pack: null,
    });
    expect(row.path).toBeNull();
    expect(row.pack).toBeNull();
  });

  test("a reference whose name is no longer known", () => {
    expect(
      planListResponseSchema.parse({ ...LIST_ROW, user: { id: "a-1", name: null } }).user.name
    ).toBeNull();
  });

  test("a plan detail with versions, feedback, handoffs and the caller's permission", () => {
    const detail = planDetailResponseSchema.parse({
      ...LIST_ROW,
      feature: "kaya-plans",
      latestSession: { sessionId: "s-1", title: "Plan it", ended: false, branch: "main" },
      versions: [
        {
          number: 1,
          source: "FILE",
          sessionId: "s-1",
          capturedAt: LIST_ROW.lastChangedAt,
          lineCount: 10,
        },
        {
          number: 2,
          source: "FILE",
          sessionId: null,
          capturedAt: LIST_ROW.lastChangedAt,
          lineCount: 12,
        },
      ],
      feedback: [
        {
          id: "f-1",
          versionNumber: 1,
          reviewer: { id: "u-1", name: "Bob" },
          decision: "REQUEST_CHANGES",
          comment: "Split step 3",
          lineStart: 4,
          lineEnd: 5,
          quote: "3. Add",
          createdAt: LIST_ROW.lastChangedAt,
          deliveredAt: null,
          deliveredSessionId: null,
        },
      ],
      handoffs: [
        { path: "kaya-plans/planning.md", status: "DONE", unitNumber: null, content: "# Handoff" },
      ],
      planningHandoffPath: "kaya-plans/planning.md",
      canApprove: true,
    });
    expect(detail.versions[1]?.sessionId).toBeNull();
    expect(detail.feedback[0]?.deliveredAt).toBeNull();
    expect(detail.canApprove).toBe(true);
  });

  test("an unknown review state is refused", () => {
    expect(planListResponseSchema.safeParse({ ...LIST_ROW, reviewState: "REJECTED" }).success).toBe(
      false
    );
  });
});

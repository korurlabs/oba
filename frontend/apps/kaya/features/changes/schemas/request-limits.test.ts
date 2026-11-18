import { describe, expect, test } from "bun:test";

import { ReviewDecisionSchema, changeQueryRequestSchema } from ".";

describe("the change list query", () => {
  test("pages start at 1 and hold at most 50", () => {
    expect(changeQueryRequestSchema.safeParse({}).success).toBe(true);
    expect(changeQueryRequestSchema.safeParse({ page: 0 }).success).toBe(false);
    expect(changeQueryRequestSchema.safeParse({ amount: 50 }).success).toBe(true);
    expect(changeQueryRequestSchema.safeParse({ amount: 51 }).success).toBe(false);
    expect(changeQueryRequestSchema.safeParse({ page: 1.5 }).success).toBe(false);
  });

  test("a status the backend does not know is refused", () => {
    expect(changeQueryRequestSchema.safeParse({ status: "IN_REVIEW" }).success).toBe(true);
    expect(changeQueryRequestSchema.safeParse({ status: "PENDING" }).success).toBe(false);
  });

  test("a pack narrows the list", () => {
    expect(changeQueryRequestSchema.safeParse({ packId: "k-1" }).success).toBe(true);
  });
});

describe("the review decisions", () => {
  test("are the backend's values", () => {
    expect(ReviewDecisionSchema.options).toEqual(["APPROVE", "REJECT", "COMMENT"]);
  });
});

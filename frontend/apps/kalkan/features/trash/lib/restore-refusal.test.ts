import { describe, expect, test } from "bun:test";
import { ApiError } from "@oba/shared/api/axios";
import { needsParentRestoredFirst } from "./restore-refusal";

describe("needsParentRestoredFirst", () => {
  test("is true for the parent-deleted refusal only", () => {
    expect(
      needsParentRestoredFirst(new ApiError("conflict", { status: 409, code: "KLK_STA_003" }))
    ).toBe(true);
    expect(
      needsParentRestoredFirst(new ApiError("live", { status: 422, code: "KLK_VAL_008" }))
    ).toBe(false);
    expect(needsParentRestoredFirst(new Error("network"))).toBe(false);
  });
});

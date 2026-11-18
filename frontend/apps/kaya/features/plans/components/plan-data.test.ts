import { describe, expect, test } from "bun:test";

import type { PlanFilterParams } from "../hooks/use-plan-filter-params";
import {
  EMPTY_PLANS_TEXT,
  emptyPlansText,
  hasActiveFilter,
  referenceName,
  toPlanQuery,
} from "./plan-data";

const DEFAULTS: PlanFilterParams = {
  search: null,
  repositoryId: null,
  packId: null,
  kind: null,
  reviewState: "OPEN",
  userId: null,
  page: 1,
};

describe("toPlanQuery", () => {
  test("the default list asks for open plans, 25 a page", () => {
    expect(toPlanQuery(DEFAULTS)).toEqual({ page: 1, amount: 25, reviewState: "OPEN" });
  });

  test("All sends no review state; every other filter is passed on, the search trimmed", () => {
    expect(
      toPlanQuery({
        search: " kaya ",
        repositoryId: "r-1",
        packId: "s-1",
        kind: "SPEC",
        reviewState: "ALL",
        userId: "a-1",
        page: 3,
      })
    ).toEqual({
      page: 3,
      amount: 25,
      kind: "SPEC",
      repositoryId: "r-1",
      packId: "s-1",
      userId: "a-1",
      search: "kaya",
    });
  });

  test("a page below 1 falls back to the first", () => {
    expect(toPlanQuery({ ...DEFAULTS, page: 0 }).page).toBe(1);
  });
});

describe("empty list text", () => {
  test("an organization without plans says how plans appear", () => {
    expect(emptyPlansText(DEFAULTS, false)).toBe(EMPTY_PLANS_TEXT);
    expect(emptyPlansText({ ...DEFAULTS, kind: "SPEC" }, false)).toBe(EMPTY_PLANS_TEXT);
  });

  test("with plans, the default list says none waits; a filtered one says none matches", () => {
    expect(emptyPlansText(DEFAULTS, true)).toBe("No plan waits for review.");
    expect(emptyPlansText(DEFAULTS, undefined)).toBe("No plan waits for review.");
    expect(emptyPlansText({ ...DEFAULTS, reviewState: "ALL" }, true)).toBe(
      "No plan matches these filters."
    );
    expect(emptyPlansText({ ...DEFAULTS, search: "x" }, true)).toBe(
      "No plan matches these filters."
    );
  });
});

describe("filters and names", () => {
  test("only a filter beyond the default of open plans is active", () => {
    expect(hasActiveFilter(DEFAULTS)).toBe(false);
    expect(hasActiveFilter({ ...DEFAULTS, page: 4 })).toBe(false);
    expect(hasActiveFilter({ ...DEFAULTS, reviewState: "APPROVED" })).toBe(true);
    expect(hasActiveFilter({ ...DEFAULTS, userId: "a-1" })).toBe(true);
  });

  test("a reference without a name shows the start of its id", () => {
    expect(referenceName({ id: "0b8e5f4a-6c2d", name: "village" })).toBe("village");
    expect(referenceName({ id: "0b8e5f4a-6c2d", name: null })).toBe("0b8e5f4a");
  });
});

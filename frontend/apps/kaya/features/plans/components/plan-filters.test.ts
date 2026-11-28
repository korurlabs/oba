import { describe, expect, test } from "bun:test";
import { categoryActiveCount, nextSingleValue } from "@oba/ui/sky/filter-popover";

import type { PlanFilterParams } from "../hooks/use-plan-filter-params";
import { DEFAULT_REVIEW_STATE, NO_PLAN_FILTERS, planFilterCategories } from "./plan-filters";

const DEFAULTS: PlanFilterParams = {
  search: null,
  repositoryId: null,
  packId: null,
  kind: null,
  reviewState: "OPEN",
  userId: null,
  page: 1,
};

function review(filters: PlanFilterParams, changes: Partial<PlanFilterParams>[]) {
  const category = planFilterCategories(filters, (change) => changes.push(change), undefined)[0];
  if (category?.mode !== "single") throw new Error("review is a single select");
  return category;
}

describe("the plan list's Review filter", () => {
  test("defaults to Open, which counts as no filter", () => {
    const category = review(DEFAULTS, []);
    expect(category.key).toBe("reviewState");
    expect(category.defaultValue).toBe("OPEN");
    expect(categoryActiveCount(category)).toBe(0);
    expect(categoryActiveCount(review({ ...DEFAULTS, reviewState: "ALL" }, []))).toBe(1);
    expect(category.options.map((option) => option.label)).toEqual([
      "Open",
      "Changes requested",
      "Approved",
      "All",
    ]);
  });

  test("clicking the chosen state again returns to Open", () => {
    const changes: Partial<PlanFilterParams>[] = [];
    const category = review({ ...DEFAULTS, reviewState: "APPROVED" }, changes);
    category.onChange(nextSingleValue("APPROVED", "APPROVED", category.defaultValue));
    category.onChange("ALL");
    expect(changes).toEqual([{ reviewState: "OPEN" }, { reviewState: "ALL" }]);
  });

  test("clearing every filter keeps the review on its default", () => {
    expect(NO_PLAN_FILTERS.reviewState).toBe(DEFAULT_REVIEW_STATE);
  });
});

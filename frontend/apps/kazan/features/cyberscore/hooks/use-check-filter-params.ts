"use client";

import { parseAsString, parseAsStringEnum, useQueryStates } from "nuqs";
import type { Values } from "nuqs";
import { CHECK_CATEGORIES, CHECK_STATUSES, type CheckCategory, type CheckStatus } from "../schemas";

/** The category filter's value for checks without a category. */
export const UNCATEGORIZED = "NONE";

export type CheckCategoryFilter = CheckCategory | typeof UNCATEGORIZED;

/** The ways the check list can be shown, in the toggle's order; cards come first. */
export const CHECK_VIEWS = ["cards", "list", "kanban"] as const;
export type CheckView = (typeof CHECK_VIEWS)[number];

/**
 * The URL keys of the check list's filters; a missing key does not narrow the
 * list. `status` is sent to the server, and the checks page parses the same
 * key to prefetch the filtered list. `category` and `search` narrow the
 * loaded list in the browser, since an organization holds at most a few
 * hundred checks; the overview's category rows link here with `category` set.
 * `view` picks cards, list or kanban; every filter applies to each of them,
 * and a shared link opens the same view.
 */
export const checkFilterParams = {
  status: parseAsStringEnum<CheckStatus>([...CHECK_STATUSES]),
  category: parseAsStringEnum<CheckCategoryFilter>([...CHECK_CATEGORIES, UNCATEGORIZED]),
  search: parseAsString,
  view: parseAsStringEnum<CheckView>([...CHECK_VIEWS]).withDefault("cards"),
};

export type CheckFilterParams = Values<typeof checkFilterParams>;

export function useCheckFilterParams() {
  return useQueryStates(checkFilterParams);
}

/** The filters behind the filter button that narrow the list: category and status, one each when set. */
export function countCheckFilters(filters: Pick<CheckFilterParams, "category" | "status">): number {
  return (filters.category ? 1 : 0) + (filters.status ? 1 : 0);
}

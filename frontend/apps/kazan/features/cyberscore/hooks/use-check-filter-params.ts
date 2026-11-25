"use client";

import { parseAsString, parseAsStringEnum, useQueryStates } from "nuqs";
import type { Values } from "nuqs";
import { CHECK_CATEGORIES, CHECK_STATUSES, type CheckCategory, type CheckStatus } from "../schemas";

/** The category filter's value for checks without a category. */
export const UNCATEGORIZED = "NONE";

export type CheckCategoryFilter = CheckCategory | typeof UNCATEGORIZED;

/**
 * The URL keys of the check list's filters; a missing key does not narrow the
 * list. `status` is sent to the server, and the checks page parses the same
 * key to prefetch the filtered list. `category` and `search` narrow the
 * loaded list in the browser, since an organization holds at most a few
 * hundred checks; the overview's category rows link here with `category` set.
 */
export const checkFilterParams = {
  status: parseAsStringEnum<CheckStatus>([...CHECK_STATUSES]),
  category: parseAsStringEnum<CheckCategoryFilter>([...CHECK_CATEGORIES, UNCATEGORIZED]),
  search: parseAsString,
};

export type CheckFilterParams = Values<typeof checkFilterParams>;

export function useCheckFilterParams() {
  return useQueryStates(checkFilterParams);
}

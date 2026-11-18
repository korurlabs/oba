"use client";

import { parseAsString, useQueryStates } from "nuqs";
import type { Values } from "nuqs";
import { resolveUpdateDay, updateToday } from "../lib/update-day";

/**
 * The URL keys of the daily updates channel's filters. `day` absent means
 * today; `person` is the author's user ID. The key is not `author`, because
 * the chat search panel uses that one.
 */
export const updateFilterParams = {
  day: parseAsString,
  person: parseAsString,
};

export type UpdateFilterParams = Values<typeof updateFilterParams>;

export function useUpdateFilterParams() {
  return useQueryStates(updateFilterParams);
}

/** Filters that narrow the channel: Day when not today, Person when set. */
export function countUpdateFilters(filters: UpdateFilterParams): number {
  const dayCount = resolveUpdateDay(filters.day) === updateToday() ? 0 : 1;
  const personCount = filters.person ? 1 : 0;
  return dayCount + personCount;
}

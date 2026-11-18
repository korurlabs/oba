"use client";

import { useQueryStates } from "nuqs";
import { relationFilterParams } from "../lib/relation-filters";

export { relationFilterParams };
export type { RelationFilterParams } from "../lib/relation-filters";

/** The relations list's tab, search, filters and page, synced with the URL. */
export function useRelationFilterParams() {
  return useQueryStates(relationFilterParams);
}

"use client";

import { useQueryStates } from "nuqs";
import { groupFilterParams } from "../lib/group-filters";

/** The Groups list's search, synced with the URL. */
export function useGroupFilterParams() {
  return useQueryStates(groupFilterParams);
}

"use client";

import { useQueryStates } from "nuqs";
import { memberFilterParams } from "../lib/member-filters";

/** The Humans list's search, role and status, synced with the URL. */
export function useMemberFilterParams() {
  return useQueryStates(memberFilterParams);
}

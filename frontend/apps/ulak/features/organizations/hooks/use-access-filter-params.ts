"use client";

import { useQueryStates } from "nuqs";
import { accessFilterParams } from "../lib/access-filters";

/** The Access page's tab and search, synced with the URL. */
export function useAccessFilterParams() {
  return useQueryStates(accessFilterParams);
}

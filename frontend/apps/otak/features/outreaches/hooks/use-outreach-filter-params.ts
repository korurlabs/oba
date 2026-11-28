"use client";

import { useQueryStates } from "nuqs";
import { outreachFilterParams } from "../lib/outreach-filters";

export type { OutreachFilterParams } from "../lib/outreach-filters";

/** The outreach list's search, filters and page, synced with the URL. */
export function useOutreachFilterParams() {
  return useQueryStates(outreachFilterParams);
}

"use client";

import { useQueryStates } from "nuqs";
import { icpFilterParams } from "../lib/icp-filters";

export type { IcpFilterParams } from "../lib/icp-filters";

/** The ICP list's search and page, synced with the URL. */
export function useIcpFilterParams() {
  return useQueryStates(icpFilterParams);
}

"use client";

import { useQueryStates } from "nuqs";
import { outreachPageTabParams } from "../lib/outreach-tabs";

/** The Outreaches page's tab, synced with the URL. */
export function useOutreachPageTab() {
  return useQueryStates(outreachPageTabParams);
}

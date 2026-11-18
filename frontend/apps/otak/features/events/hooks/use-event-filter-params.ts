"use client";

import { useQueryStates } from "nuqs";
import { eventFilterParams } from "../lib/event-filters";

export type { EventFilterParams } from "../lib/event-filters";

/** The event list's tab, search and page, synced with the URL. */
export function useEventFilterParams() {
  return useQueryStates(eventFilterParams);
}

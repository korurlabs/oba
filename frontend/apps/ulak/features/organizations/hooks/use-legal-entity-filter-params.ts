"use client";

import { useQueryStates } from "nuqs";
import { legalEntityFilterParams } from "../lib/legal-entity-filters";

/** The Legal entities list's search, synced with the URL. */
export function useLegalEntityFilterParams() {
  return useQueryStates(legalEntityFilterParams);
}

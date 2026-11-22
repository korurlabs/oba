"use client";

import { useQueryStates } from "nuqs";
import { documentFilterParams } from "../lib/document-filters";

export type { DocumentFilterParams } from "../lib/document-filters";

/** The document list's search, relation, legal entity, language, type and page, synced with the URL. */
export function useDocumentFilterParams() {
  return useQueryStates(documentFilterParams);
}

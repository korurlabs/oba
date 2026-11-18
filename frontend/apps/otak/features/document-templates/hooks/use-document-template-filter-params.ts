"use client";

import { useQueryStates } from "nuqs";
import { documentTemplateFilterParams } from "../lib/document-template-filters";

export type { DocumentTemplateFilterParams } from "../lib/document-template-filters";

/** The template list's search, type, language and page, synced with the URL. */
export function useDocumentTemplateFilterParams() {
  return useQueryStates(documentTemplateFilterParams);
}

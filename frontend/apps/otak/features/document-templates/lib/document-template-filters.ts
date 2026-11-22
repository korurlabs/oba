import { createLoader, parseAsInteger, parseAsString, parseAsStringEnum } from "nuqs/server";
import type { Values } from "nuqs";
import {
  DOCUMENT_TEMPLATE_LIMITS,
  DOCUMENT_TEMPLATES_PAGE_SIZE,
  DocumentLanguageSchema,
  DocumentTypeSchema,
  type DocumentTemplateQueryRequest,
} from "../schemas";

/**
 * The template list's search, type, language, legal entity and page in the URL, so a
 * filtered list can be reloaded and shared. Server-safe: the list page
 * parses the same parsers to prefetch under the key the client builds.
 */
export const documentTemplateFilterParams = {
  search: parseAsString,
  type: parseAsStringEnum(DocumentTypeSchema.options),
  language: parseAsStringEnum(DocumentLanguageSchema.options),
  legalEntity: parseAsString,
  page: parseAsInteger.withDefault(1),
};

export type DocumentTemplateFilterParams = Values<typeof documentTemplateFilterParams>;

export const loadDocumentTemplateFilterParams = createLoader(documentTemplateFilterParams);

/**
 * The query the list sends for its URL state. Absent filters are left out,
 * so the client and the server prefetch build the same key. A search longer
 * than the server takes is cut to its limit rather than refused.
 */
export function toDocumentTemplateQuery(
  filters: DocumentTemplateFilterParams
): DocumentTemplateQueryRequest {
  const query: DocumentTemplateQueryRequest = {
    page: Math.max(1, filters.page),
    amount: DOCUMENT_TEMPLATES_PAGE_SIZE,
  };
  const search = filters.search?.trim().slice(0, DOCUMENT_TEMPLATE_LIMITS.searchMax);
  if (search) query.search = search;
  if (filters.type) query.type = filters.type;
  if (filters.language) query.language = filters.language;
  if (filters.legalEntity) query.legalEntityId = filters.legalEntity;
  return query;
}

/** True when the list is narrowed by anything other than its page. */
export function hasActiveDocumentTemplateFilter(filters: DocumentTemplateFilterParams): boolean {
  return !!filters.search?.trim() || !!filters.type || !!filters.language || !!filters.legalEntity;
}

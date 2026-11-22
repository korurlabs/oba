import { createLoader, parseAsInteger, parseAsString, parseAsStringEnum } from "nuqs/server";
import type { Values } from "nuqs";
import { DocumentLanguageSchema, DocumentTypeSchema } from "@/features/document-templates/schemas";
import { DOCUMENT_LIMITS, DOCUMENTS_PAGE_SIZE, type DocumentQueryRequest } from "../schemas";

/**
 * The document list's search, relation, legal entity, language, type and
 * page in the URL, so a filtered list can be reloaded and shared, and a
 * relation can link to its own documents with `?relation=<id>`. Server-safe:
 * the list page parses the same parsers to prefetch under the key the
 * client builds.
 */
export const documentFilterParams = {
  search: parseAsString,
  relation: parseAsString,
  legalEntity: parseAsString,
  language: parseAsStringEnum(DocumentLanguageSchema.options),
  type: parseAsStringEnum(DocumentTypeSchema.options),
  page: parseAsInteger.withDefault(1),
};

export type DocumentFilterParams = Values<typeof documentFilterParams>;

export const loadDocumentFilterParams = createLoader(documentFilterParams);

/**
 * The query the list sends for its URL state. Absent filters are left out,
 * so the client and the server prefetch build the same key. A search longer
 * than the server takes is cut to its limit rather than refused.
 */
export function toDocumentQuery(filters: DocumentFilterParams): DocumentQueryRequest {
  const query: DocumentQueryRequest = {
    page: Math.max(1, filters.page),
    amount: DOCUMENTS_PAGE_SIZE,
  };
  const search = filters.search?.trim().slice(0, DOCUMENT_LIMITS.searchMax);
  if (search) query.search = search;
  if (filters.relation) query.relationId = filters.relation;
  if (filters.legalEntity) query.legalEntityId = filters.legalEntity;
  if (filters.language) query.language = filters.language;
  if (filters.type) query.type = filters.type;
  return query;
}

/** True when the list is narrowed by anything other than its page. */
export function hasActiveDocumentFilter(filters: DocumentFilterParams): boolean {
  return (
    !!filters.search?.trim() ||
    !!filters.relation ||
    !!filters.legalEntity ||
    !!filters.language ||
    !!filters.type
  );
}

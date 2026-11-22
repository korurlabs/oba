import {
  DOCUMENT_TEMPLATE_LIMITS,
  type DocumentTemplateQueryRequest,
} from "@/features/document-templates/schemas";
import { RELATION_LIMITS, type RelationQueryRequest } from "@/features/relations/schemas";
import { PICKER_SIZE } from "../schemas";

/*
 * The queries of the searchable selects of the document pages. Server-safe,
 * so the list page prefetches the first relations under the key the select
 * builds.
 */

/** The relation query of a select's search: the first rows of every type. */
export function relationPickerQuery(search: string): RelationQueryRequest {
  const trimmed = search.trim().slice(0, RELATION_LIMITS.searchMax);
  return { ...(trimmed ? { search: trimmed } : {}), page: 1, amount: PICKER_SIZE };
}

/**
 * The template query of a select's search. With a legal entity it lists that
 * legal entity's own templates and those every legal entity may use.
 */
export function templatePickerQuery(
  search: string,
  legalEntityId: string | null
): DocumentTemplateQueryRequest {
  const query: DocumentTemplateQueryRequest = { page: 1, amount: PICKER_SIZE };
  const trimmed = search.trim().slice(0, DOCUMENT_TEMPLATE_LIMITS.searchMax);
  if (trimmed) query.search = trimmed;
  if (legalEntityId) query.legalEntityId = legalEntityId;
  return query;
}

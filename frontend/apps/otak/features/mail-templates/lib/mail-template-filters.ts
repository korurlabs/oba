import { createLoader, parseAsInteger, parseAsString, parseAsStringEnum } from "nuqs/server";
import type { Values } from "nuqs";
import { DocumentLanguageSchema } from "@/features/document-templates/schemas";
import {
  MAIL_TEMPLATE_LIMITS,
  MAIL_TEMPLATES_PAGE_SIZE,
  type MailTemplateQueryRequest,
} from "../schemas";

/**
 * The mail template list's search, language and page in the URL, so a
 * filtered list can be reloaded and shared. Server-safe: the templates page
 * parses the same parsers to prefetch under the key the client builds.
 */
export const mailTemplateFilterParams = {
  search: parseAsString,
  language: parseAsStringEnum(DocumentLanguageSchema.options),
  page: parseAsInteger.withDefault(1),
};

export type MailTemplateFilterParams = Values<typeof mailTemplateFilterParams>;

export const loadMailTemplateFilterParams = createLoader(mailTemplateFilterParams);

/**
 * The query the list sends for its URL state. Absent filters are left out,
 * so the client and the server prefetch build the same key. A search longer
 * than the server takes is cut to its limit rather than refused.
 */
export function toMailTemplateQuery(filters: MailTemplateFilterParams): MailTemplateQueryRequest {
  const query: MailTemplateQueryRequest = {
    page: Math.max(1, filters.page),
    amount: MAIL_TEMPLATES_PAGE_SIZE,
  };
  const search = filters.search?.trim().slice(0, MAIL_TEMPLATE_LIMITS.searchMax);
  if (search) query.search = search;
  if (filters.language) query.language = filters.language;
  return query;
}

/** True when the list is narrowed by anything other than its page. */
export function hasActiveMailTemplateFilter(filters: MailTemplateFilterParams): boolean {
  return !!filters.search?.trim() || !!filters.language;
}

import type { DocumentTemplateQueryRequest } from "../schemas";

/**
 * Query keys of the document templates. Every key starts with the
 * organization, so the templates of one organization never show under
 * another. Kept out of the "use client" hook file so a server page can
 * prefetch under the exact keys the hooks read.
 */
export const DOCUMENT_TEMPLATE_KEYS = {
  all: (organizationId: string) => ["document-templates", organizationId] as const,
  lists: (organizationId: string) =>
    [...DOCUMENT_TEMPLATE_KEYS.all(organizationId), "list"] as const,
  list: (organizationId: string, params: DocumentTemplateQueryRequest) =>
    [...DOCUMENT_TEMPLATE_KEYS.lists(organizationId), params] as const,
  detail: (organizationId: string, templateId: string) =>
    [...DOCUMENT_TEMPLATE_KEYS.all(organizationId), "detail", templateId] as const,
};

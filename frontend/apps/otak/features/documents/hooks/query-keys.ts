import type { DocumentQueryRequest } from "../schemas";

/**
 * Query keys of the documents and the legal entities that issue them. Every
 * key starts with the organization, so the data of one organization never
 * shows under another. Kept out of the "use client" hook file so a server
 * page can prefetch under the exact keys the hooks read.
 */
export const DOCUMENT_KEYS = {
  all: (organizationId: string) => ["documents", organizationId] as const,
  lists: (organizationId: string) => [...DOCUMENT_KEYS.all(organizationId), "list"] as const,
  list: (organizationId: string, params: DocumentQueryRequest) =>
    [...DOCUMENT_KEYS.lists(organizationId), params] as const,
  detail: (organizationId: string, documentId: string) =>
    [...DOCUMENT_KEYS.all(organizationId), "detail", documentId] as const,
};

export const LEGAL_ENTITY_KEYS = {
  all: (organizationId: string) => ["legal-entities", organizationId] as const,
  list: (organizationId: string) => [...LEGAL_ENTITY_KEYS.all(organizationId), "list"] as const,
};

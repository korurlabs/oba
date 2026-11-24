/**
 * Query keys of the pages. Every key starts with the organization, so the
 * pages of one organization never show under another. Kept out of the
 * "use client" hook files so a server page can prefetch under the exact keys
 * the hooks read.
 */
export const PAGE_KEYS = {
  all: (organizationId: string) => ["kalkan-pages", organizationId] as const,
  tree: (organizationId: string) => [...PAGE_KEYS.all(organizationId), "tree"] as const,
  details: (organizationId: string) => [...PAGE_KEYS.all(organizationId), "detail"] as const,
  detail: (organizationId: string, pageId: string) =>
    [...PAGE_KEYS.details(organizationId), pageId] as const,
  revisions: (organizationId: string, pageId: string) =>
    [...PAGE_KEYS.all(organizationId), "revisions", pageId] as const,
  revision: (organizationId: string, pageId: string, revisionId: string) =>
    [...PAGE_KEYS.revisions(organizationId, pageId), revisionId] as const,
  trash: (organizationId: string, locale: string) =>
    [...PAGE_KEYS.all(organizationId), "trash", locale] as const,
  recent: (organizationId: string, limit: number, locale: string) =>
    [...PAGE_KEYS.all(organizationId), "recent", limit, locale] as const,
  permissions: (organizationId: string, pageId: string) =>
    [...PAGE_KEYS.all(organizationId), "permissions", pageId] as const,
};

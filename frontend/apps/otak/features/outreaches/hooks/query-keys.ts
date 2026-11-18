import type { OutreachQueryRequest } from "../schemas";

/**
 * Query keys of the outreaches. Every key starts with the organization, so
 * the outreaches of one organization never show under another. Kept out of
 * the "use client" hook file so a server page can prefetch under the exact
 * keys the hooks read.
 */
export const OUTREACH_KEYS = {
  all: (organizationId: string) => ["outreaches", organizationId] as const,
  lists: (organizationId: string) => [...OUTREACH_KEYS.all(organizationId), "list"] as const,
  list: (organizationId: string, params: OutreachQueryRequest) =>
    [...OUTREACH_KEYS.lists(organizationId), params] as const,
  detail: (organizationId: string, outreachId: string) =>
    [...OUTREACH_KEYS.all(organizationId), "detail", outreachId] as const,
};

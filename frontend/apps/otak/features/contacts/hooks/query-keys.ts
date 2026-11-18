/**
 * Query keys of the contacts. Every key starts with the organization, so the
 * contacts of one organization never show under another. Kept out of the
 * "use client" hook file so a server page can prefetch under the same keys.
 */
export const CONTACT_KEYS = {
  all: (organizationId: string) => ["contacts", organizationId] as const,
  list: (organizationId: string, relationId: string) =>
    [...CONTACT_KEYS.all(organizationId), "list", relationId] as const,
};

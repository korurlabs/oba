/**
 * Query-key factories for the agents of an organization.
 *
 * This module is intentionally NOT marked "use client": the page's server
 * prefetch imports it to fill the cache under the exact keys the client
 * hooks use (see `features/organizations/hooks/query-keys.ts`).
 *
 * Keys start with the organization id, so that `organization(orgId)` matches
 * the list and the options of that organization.
 */
export const AGENT_KEYS = {
  all: ["agents"] as const,
  organization: (orgId: string) => [...AGENT_KEYS.all, orgId] as const,
  list: (orgId: string) => [...AGENT_KEYS.organization(orgId), "list"] as const,
  options: (orgId: string) => [...AGENT_KEYS.organization(orgId), "options"] as const,
};

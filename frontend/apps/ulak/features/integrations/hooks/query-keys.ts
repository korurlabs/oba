/**
 * Query-key factories for the Azure connections of an organization.
 *
 * This module is intentionally NOT marked "use client": the page's server
 * prefetch imports it to fill the cache under the exact keys the client
 * hooks use (see `features/organizations/hooks/query-keys.ts`).
 *
 * Keys start with the organization id, so that `organization(orgId)` matches
 * the list and every detail of that organization.
 */
export const AZURE_CONNECTION_KEYS = {
  all: ["azureConnections"] as const,
  organization: (orgId: string) => [...AZURE_CONNECTION_KEYS.all, orgId] as const,
  list: (orgId: string) => [...AZURE_CONNECTION_KEYS.organization(orgId), "list"] as const,
  detail: (orgId: string, connectionId: string) =>
    [...AZURE_CONNECTION_KEYS.organization(orgId), "detail", connectionId] as const,
};

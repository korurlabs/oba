import type { IcpQueryRequest } from "../schemas";

/**
 * Query keys of the ICPs. Every key starts with the organization, so the
 * ICPs of one organization never show under another. Kept out of the
 * "use client" hook file so a server page can prefetch under the exact keys
 * the hooks read.
 */
export const ICP_KEYS = {
  all: (organizationId: string) => ["icps", organizationId] as const,
  lists: (organizationId: string) => [...ICP_KEYS.all(organizationId), "list"] as const,
  list: (organizationId: string, params: IcpQueryRequest) =>
    [...ICP_KEYS.lists(organizationId), params] as const,
  detail: (organizationId: string, icpId: string) =>
    [...ICP_KEYS.all(organizationId), "detail", icpId] as const,
};

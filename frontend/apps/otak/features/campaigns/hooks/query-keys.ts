import type { CampaignQueryRequest } from "../schemas";

/**
 * Query keys of the campaigns and their contents. Every key starts with the
 * organization, so the campaigns of one organization never show under
 * another. Kept out of the "use client" hook file so a server page can
 * prefetch under the exact keys the hooks read.
 */
export const CAMPAIGN_KEYS = {
  all: (organizationId: string) => ["campaigns", organizationId] as const,
  lists: (organizationId: string) => [...CAMPAIGN_KEYS.all(organizationId), "list"] as const,
  list: (organizationId: string, params: CampaignQueryRequest) =>
    [...CAMPAIGN_KEYS.lists(organizationId), params] as const,
  detail: (organizationId: string, campaignId: string) =>
    [...CAMPAIGN_KEYS.all(organizationId), "detail", campaignId] as const,
  contents: (organizationId: string, campaignId: string) =>
    [...CAMPAIGN_KEYS.all(organizationId), "contents", campaignId] as const,
};

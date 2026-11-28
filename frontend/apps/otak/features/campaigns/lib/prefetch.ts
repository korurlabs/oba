import type { QueryClient } from "@tanstack/react-query";
import { getCampaign, listCampaignContents, listCampaigns } from "../api";
import { CAMPAIGN_KEYS } from "../hooks/query-keys";
import type { CampaignQueryRequest } from "../schemas";

/*
 * Server-side prefetches of the campaign pages, under the keys and with the
 * api functions the client hooks use. Each is best-effort: on failure the
 * error is logged and the client hook fetches on mount.
 */

export async function prefetchCampaigns(
  queryClient: QueryClient,
  organizationId: string,
  query: CampaignQueryRequest
): Promise<void> {
  try {
    queryClient.setQueryData(CAMPAIGN_KEYS.list(organizationId, query), await listCampaigns(query));
  } catch (error) {
    console.error("[campaigns-page] list prefetch failed, the client will refetch:", error);
  }
}

export async function prefetchCampaign(
  queryClient: QueryClient,
  organizationId: string,
  campaignId: string
): Promise<void> {
  try {
    queryClient.setQueryData(
      CAMPAIGN_KEYS.detail(organizationId, campaignId),
      await getCampaign(campaignId)
    );
  } catch (error) {
    // A missing campaign is answered by the client with its not-found state.
    console.error("[campaign-page] detail prefetch failed, the client will refetch:", error);
  }
}

export async function prefetchCampaignContents(
  queryClient: QueryClient,
  organizationId: string,
  campaignId: string
): Promise<void> {
  try {
    queryClient.setQueryData(
      CAMPAIGN_KEYS.contents(organizationId, campaignId),
      await listCampaignContents(campaignId)
    );
  } catch (error) {
    console.error("[campaign-page] contents prefetch failed, the client will refetch:", error);
  }
}

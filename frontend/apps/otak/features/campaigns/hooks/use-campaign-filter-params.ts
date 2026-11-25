"use client";

import { useQueryStates } from "nuqs";
import { campaignFilterParams } from "../lib/campaign-filters";

export type { CampaignFilterParams } from "../lib/campaign-filters";

/** The campaign list's tab, channel and search, synced with the URL. */
export function useCampaignFilterParams() {
  return useQueryStates(campaignFilterParams);
}

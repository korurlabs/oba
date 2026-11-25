import { createLoader, parseAsString, parseAsStringEnum, parseAsStringLiteral } from "nuqs/server";
import type { Values } from "nuqs";
import {
  CAMPAIGN_LIMITS,
  CampaignChannelSchema,
  CampaignStatus,
  type CampaignQueryRequest,
} from "../schemas";

/** The list's tabs, in the order they are shown: every status, then one per status. */
export const CAMPAIGN_TABS = ["all", "draft", "scheduled", "published", "archived"] as const;
export type CampaignTab = (typeof CAMPAIGN_TABS)[number];

/** The status each tab asks the server for; "all" asks for none. */
export const CAMPAIGN_TAB_STATUSES: Record<CampaignTab, CampaignStatus | null> = {
  all: null,
  draft: CampaignStatus.DRAFT,
  scheduled: CampaignStatus.SCHEDULED,
  published: CampaignStatus.PUBLISHED,
  archived: CampaignStatus.ARCHIVED,
};

export function isCampaignTab(value: string): value is CampaignTab {
  return (CAMPAIGN_TABS as readonly string[]).includes(value);
}

/**
 * The campaign list's tab, channel and search in the URL, so a view can be
 * reloaded and shared. Server-safe: the list page parses the same parsers to
 * prefetch under the key the client builds.
 */
export const campaignFilterParams = {
  tab: parseAsStringLiteral(CAMPAIGN_TABS).withDefault("all"),
  channel: parseAsStringEnum(CampaignChannelSchema.options),
  search: parseAsString,
};

export type CampaignFilterParams = Values<typeof campaignFilterParams>;

export const loadCampaignFilterParams = createLoader(campaignFilterParams);

/**
 * The query the list sends for its URL state. Absent filters are left out,
 * so the client and the server prefetch build the same key; a search longer
 * than the server takes is cut to its limit.
 */
export function toCampaignQuery(filters: CampaignFilterParams): CampaignQueryRequest {
  const query: CampaignQueryRequest = {};
  const status = CAMPAIGN_TAB_STATUSES[filters.tab];
  if (status !== null) query.status = status;
  if (filters.channel) query.channel = filters.channel;
  const search = filters.search?.trim().slice(0, CAMPAIGN_LIMITS.searchMax);
  if (search) query.search = search;
  return query;
}

/** True when the list is narrowed by anything other than its tab. */
export function hasActiveCampaignFilter(filters: CampaignFilterParams): boolean {
  return !!filters.channel || !!filters.search?.trim();
}

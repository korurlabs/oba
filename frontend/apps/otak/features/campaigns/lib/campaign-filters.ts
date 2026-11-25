import { createLoader, parseAsString } from "nuqs/server";
import type { Values } from "nuqs";
import type { CampaignQueryRequest } from "../schemas";

/**
 * The campaign list's search in the URL, so a view can be reloaded and
 * shared. Server-safe: the list page parses the same parsers to prefetch
 * under the key the client builds.
 */
export const campaignFilterParams = {
  search: parseAsString,
};

export type CampaignFilterParams = Values<typeof campaignFilterParams>;

export const loadCampaignFilterParams = createLoader(campaignFilterParams);

/**
 * The query the list sends for its URL state. A blank search is left out,
 * so the client and the server prefetch build the same key.
 */
export function toCampaignQuery(filters: CampaignFilterParams): CampaignQueryRequest {
  const query: CampaignQueryRequest = {};
  const search = filters.search?.trim();
  if (search) query.search = search;
  return query;
}

/** True when the list is narrowed by a search. */
export function hasActiveCampaignFilter(filters: CampaignFilterParams): boolean {
  return !!filters.search?.trim();
}

import { createLoader, parseAsInteger, parseAsString, parseAsStringEnum } from "nuqs/server";
import type { Values } from "nuqs";
import {
  OUTREACH_LIMITS,
  OUTREACHES_PAGE_SIZE,
  OutreachStatusSchema,
  OutreachTypeSchema,
  type OutreachQueryRequest,
} from "../schemas";

/**
 * The outreach list's search, filters and page in the URL, so a filtered
 * list can be reloaded and shared. Server-safe: the list page parses the
 * same parsers to prefetch under the key the client builds.
 */
export const outreachFilterParams = {
  search: parseAsString,
  channel: parseAsStringEnum(OutreachTypeSchema.options),
  status: parseAsStringEnum(OutreachStatusSchema.options),
  /** The id of the mail sender the outreaches were sent from. */
  sender: parseAsString,
  /** The id of the campaign the outreaches belong to. */
  campaign: parseAsString,
  page: parseAsInteger.withDefault(1),
};

export type OutreachFilterParams = Values<typeof outreachFilterParams>;

export const loadOutreachFilterParams = createLoader(outreachFilterParams);

/** Every filter and the search cleared; the list goes back to its first page. */
export const CLEARED_OUTREACH_FILTERS = {
  search: null,
  channel: null,
  status: null,
  sender: null,
  campaign: null,
  page: 1,
};

/** The Filter popover's categories cleared; the search stays. */
export const CLEARED_OUTREACH_POPOVER_FILTERS = {
  channel: null,
  status: null,
  sender: null,
  campaign: null,
  page: 1,
};

/**
 * The query the organization's list sends for its URL state. Absent filters
 * are left out, so the client and the server prefetch build the same key. A
 * search longer than the server takes is cut to its limit rather than
 * refused.
 */
export function toOutreachQuery(filters: OutreachFilterParams): OutreachQueryRequest {
  const query: OutreachQueryRequest = {
    page: Math.max(1, filters.page),
    amount: OUTREACHES_PAGE_SIZE,
  };
  const search = filters.search?.trim().slice(0, OUTREACH_LIMITS.searchMax);
  if (search) query.search = search;
  if (filters.channel) query.channel = filters.channel;
  if (filters.status) query.status = filters.status;
  if (filters.sender) query.mailSenderId = filters.sender;
  if (filters.campaign) query.campaignId = filters.campaign;
  return query;
}

/** True when the list is narrowed by anything other than its page. */
export function hasActiveOutreachFilter(filters: OutreachFilterParams): boolean {
  return (
    !!filters.search?.trim() ||
    !!filters.channel ||
    !!filters.status ||
    !!filters.sender ||
    !!filters.campaign
  );
}

import { createLoader, parseAsInteger, parseAsStringEnum } from "nuqs/server";
import type { Values } from "nuqs";
import { OUTREACHES_PAGE_SIZE, OutreachStatusSchema, type OutreachQueryRequest } from "../schemas";

/**
 * The outreach list's status filter and page in the URL, so a filtered list
 * can be reloaded and shared. Server-safe: the list page parses the same
 * parsers to prefetch under the key the client builds.
 */
export const outreachFilterParams = {
  status: parseAsStringEnum(OutreachStatusSchema.options),
  page: parseAsInteger.withDefault(1),
};

export type OutreachFilterParams = Values<typeof outreachFilterParams>;

export const loadOutreachFilterParams = createLoader(outreachFilterParams);

/**
 * The query the organization's list sends for its URL state. An absent
 * status is left out, so the client and the server prefetch build the same
 * key.
 */
export function toOutreachQuery(filters: OutreachFilterParams): OutreachQueryRequest {
  const query: OutreachQueryRequest = {
    page: Math.max(1, filters.page),
    amount: OUTREACHES_PAGE_SIZE,
  };
  if (filters.status) query.status = filters.status;
  return query;
}

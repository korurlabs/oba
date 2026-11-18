import { createLoader, parseAsInteger, parseAsString } from "nuqs/server";
import type { Values } from "nuqs";
import { ICP_LIMITS, ICPS_PAGE_SIZE, type IcpQueryRequest } from "../schemas";

/**
 * The ICP list's search and page in the URL, so a search can be reloaded
 * and shared. Server-safe: the list page parses the same parsers to
 * prefetch under the key the client builds.
 */
export const icpFilterParams = {
  search: parseAsString,
  page: parseAsInteger.withDefault(1),
};

export type IcpFilterParams = Values<typeof icpFilterParams>;

export const loadIcpFilterParams = createLoader(icpFilterParams);

/**
 * The query the list sends for its URL state. An absent search is left out,
 * so the client and the server prefetch build the same key. A search longer
 * than the server takes is cut to its limit rather than refused.
 */
export function toIcpQuery(filters: IcpFilterParams): IcpQueryRequest {
  const query: IcpQueryRequest = { page: Math.max(1, filters.page), amount: ICPS_PAGE_SIZE };
  const search = filters.search?.trim().slice(0, ICP_LIMITS.searchMax);
  if (search) query.search = search;
  return query;
}

import type { QueryClient } from "@tanstack/react-query";
import { listIcps } from "../api";
import { ICP_KEYS } from "../hooks/query-keys";
import type { IcpQueryRequest } from "../schemas";

/**
 * Server-side prefetch of a page of ICPs, under the key and with the api
 * function the client hook uses. Best-effort: on failure the error is logged
 * and the client hook fetches on mount.
 */
export async function prefetchIcps(
  queryClient: QueryClient,
  organizationId: string,
  query: IcpQueryRequest
): Promise<void> {
  try {
    queryClient.setQueryData(ICP_KEYS.list(organizationId, query), await listIcps(query));
  } catch (error) {
    console.error("[icps-page] list prefetch failed — client will refetch:", error);
  }
}

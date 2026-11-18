import type { QueryClient } from "@tanstack/react-query";
import { listOutreaches } from "../api";
import { OUTREACH_KEYS } from "../hooks/query-keys";
import { firstRelationOutreachesPage, type OutreachQueryRequest } from "../schemas";

/**
 * Server-side prefetch of a page of outreaches, under the key and with the
 * api function the client hook uses. Best-effort: on failure the error is
 * logged and the client hook fetches on mount.
 */
export async function prefetchOutreaches(
  queryClient: QueryClient,
  organizationId: string,
  query: OutreachQueryRequest
): Promise<void> {
  try {
    queryClient.setQueryData(
      OUTREACH_KEYS.list(organizationId, query),
      await listOutreaches(query)
    );
  } catch (error) {
    console.error("[outreaches] list prefetch failed, the client will refetch:", error);
  }
}

/** The first page of a relation's outreaches, as the relation page's card reads it first. */
export function prefetchRelationOutreaches(
  queryClient: QueryClient,
  organizationId: string,
  relationId: string
): Promise<void> {
  return prefetchOutreaches(queryClient, organizationId, firstRelationOutreachesPage(relationId));
}

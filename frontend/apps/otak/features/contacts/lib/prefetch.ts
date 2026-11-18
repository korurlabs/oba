import type { QueryClient } from "@tanstack/react-query";
import { listContacts } from "../api";
import { CONTACT_KEYS } from "../hooks/query-keys";

/**
 * Server-side prefetch of a relation's contacts under the key the client hook
 * reads. Best-effort: on failure the error is logged and the hook fetches on mount.
 */
export async function prefetchContacts(
  queryClient: QueryClient,
  organizationId: string,
  relationId: string
): Promise<void> {
  try {
    queryClient.setQueryData(
      CONTACT_KEYS.list(organizationId, relationId),
      await listContacts(relationId)
    );
  } catch (error) {
    console.error("[relation-page] contacts prefetch failed — client will refetch:", error);
  }
}

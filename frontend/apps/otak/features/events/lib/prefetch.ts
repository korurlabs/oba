import type { QueryClient } from "@tanstack/react-query";
import { getEvent } from "../api";
import { EVENT_KEYS } from "../hooks/query-keys";

/**
 * Server-side prefetch of one event, under the key and with the api function
 * the client hook uses. Best-effort: on failure the error is logged and the
 * client hook fetches on mount. The list is not prefetched: its tabs depend
 * on the viewer's calendar day, which the server does not know.
 */
export async function prefetchEvent(
  queryClient: QueryClient,
  organizationId: string,
  eventId: string
): Promise<void> {
  try {
    queryClient.setQueryData(EVENT_KEYS.detail(organizationId, eventId), await getEvent(eventId));
  } catch (error) {
    // A missing event is answered by the client with its not-found state.
    console.error("[event-page] detail prefetch failed, the client will refetch:", error);
  }
}

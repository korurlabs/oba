import type { QueryClient } from "@tanstack/react-query";
import { listMailSenders } from "../api";
import { MAIL_SENDER_KEYS } from "../hooks/query-keys";

/**
 * Server-side prefetch of the mail senders, under the key and with the api
 * function the client hook uses. Best-effort: on failure the error is logged
 * and the client hook fetches on mount.
 */
export async function prefetchMailSenders(
  queryClient: QueryClient,
  organizationId: string
): Promise<void> {
  try {
    queryClient.setQueryData(MAIL_SENDER_KEYS.list(organizationId), await listMailSenders());
  } catch (error) {
    console.error("[mail-page] mail senders prefetch failed, the client will refetch:", error);
  }
}

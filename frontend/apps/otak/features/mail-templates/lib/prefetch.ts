import type { QueryClient } from "@tanstack/react-query";
import { listMailTemplates } from "../api";
import { MAIL_TEMPLATE_KEYS } from "../hooks/query-keys";
import type { MailTemplateQueryRequest } from "../schemas";

/**
 * Server-side prefetch of a page of mail templates, under the key and with
 * the api function the client hook uses. Best-effort: on failure the error
 * is logged and the client hook fetches on mount.
 */
export async function prefetchMailTemplates(
  queryClient: QueryClient,
  organizationId: string,
  query: MailTemplateQueryRequest
): Promise<void> {
  try {
    queryClient.setQueryData(
      MAIL_TEMPLATE_KEYS.list(organizationId, query),
      await listMailTemplates(query)
    );
  } catch (error) {
    console.error("[mail-page] mail template prefetch failed, the client will refetch:", error);
  }
}

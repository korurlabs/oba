import type { QueryClient } from "@tanstack/react-query";
import { listDocumentTemplates } from "../api";
import { DOCUMENT_TEMPLATE_KEYS } from "../hooks/query-keys";
import type { DocumentTemplateQueryRequest } from "../schemas";

/**
 * Server-side prefetch of a page of templates, under the key and with the
 * api function the client hook uses. Best-effort: on failure the error is
 * logged and the client hook fetches on mount.
 */
export async function prefetchDocumentTemplates(
  queryClient: QueryClient,
  organizationId: string,
  query: DocumentTemplateQueryRequest
): Promise<void> {
  try {
    queryClient.setQueryData(
      DOCUMENT_TEMPLATE_KEYS.list(organizationId, query),
      await listDocumentTemplates(query)
    );
  } catch (error) {
    console.error("[templates-page] list prefetch failed, the client will refetch:", error);
  }
}

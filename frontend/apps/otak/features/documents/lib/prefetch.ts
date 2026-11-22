import type { QueryClient } from "@tanstack/react-query";
import { getDocument, listDocuments, listLegalEntities } from "../api";
import { DOCUMENT_KEYS, LEGAL_ENTITY_KEYS } from "../hooks/query-keys";
import type { DocumentQueryRequest } from "../schemas";

/*
 * Server-side prefetches of the document pages, under the keys and with the
 * api functions the client hooks use. Each is best-effort: on failure the
 * error is logged and the client hook fetches on mount.
 */

export async function prefetchDocuments(
  queryClient: QueryClient,
  organizationId: string,
  query: DocumentQueryRequest
): Promise<void> {
  try {
    queryClient.setQueryData(DOCUMENT_KEYS.list(organizationId, query), await listDocuments(query));
  } catch (error) {
    console.error("[documents-page] list prefetch failed, the client will refetch:", error);
  }
}

export async function prefetchDocument(
  queryClient: QueryClient,
  organizationId: string,
  documentId: string
): Promise<void> {
  try {
    queryClient.setQueryData(
      DOCUMENT_KEYS.detail(organizationId, documentId),
      await getDocument(documentId)
    );
  } catch (error) {
    console.error("[document-page] prefetch failed, the client will refetch:", error);
  }
}

export async function prefetchLegalEntities(
  queryClient: QueryClient,
  organizationId: string
): Promise<void> {
  try {
    queryClient.setQueryData(LEGAL_ENTITY_KEYS.list(organizationId), await listLegalEntities());
  } catch (error) {
    console.error("[legal-entities] prefetch failed, the client will refetch:", error);
  }
}

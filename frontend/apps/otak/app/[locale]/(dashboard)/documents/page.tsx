import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { DocumentsView } from "@/features/documents/components/documents-view";
import { relationPickerQuery } from "@/features/documents/lib/picker-queries";
import {
  loadDocumentFilterParams,
  toDocumentQuery,
} from "@/features/documents/lib/document-filters";
import { prefetchDocuments, prefetchLegalEntities } from "@/features/documents/lib/prefetch";
import { prefetchRelation, prefetchRelations } from "@/features/relations/lib/prefetch";

/**
 * Document List Page
 *
 * Thin async server component. It parses the URL with the parsers the view
 * uses and server-prefetches, under the keys the client hooks build, the
 * page of documents, the legal entities and the first relations of the
 * filters, and the relation the list is narrowed to, so the table and its
 * filters render with data in the first HTML payload. The dashboard layout
 * gates auth.
 */
export default async function DocumentsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const filters = await loadDocumentFilterParams(searchParams);
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  // Mirrors the hooks' `enabled: !!organizationId` guard.
  if (organizationId) {
    await Promise.all([
      prefetchDocuments(queryClient, organizationId, toDocumentQuery(filters)),
      prefetchLegalEntities(queryClient, organizationId),
      prefetchRelations(queryClient, organizationId, relationPickerQuery("")),
      filters.relation
        ? prefetchRelation(queryClient, organizationId, filters.relation)
        : Promise.resolve(),
    ]);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <DocumentsView />
    </HydrationBoundary>
  );
}

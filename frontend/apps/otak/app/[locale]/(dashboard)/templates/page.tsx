import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { DocumentTemplatesView } from "@/features/document-templates/components/document-templates-view";
import {
  loadDocumentTemplateFilterParams,
  toDocumentTemplateQuery,
} from "@/features/document-templates/lib/document-template-filters";
import { prefetchDocumentTemplates } from "@/features/document-templates/lib/prefetch";
import { prefetchLegalEntities } from "@/features/documents/lib/prefetch";

/**
 * Document Template List Page
 *
 * Thin async server component. It parses the URL with the parsers the view
 * uses and server-prefetches the templates and the legal entities of the
 * filter under the keys the client hooks build, so the table renders with
 * data in the first HTML payload. The dashboard layout gates auth.
 */
export default async function TemplatesPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const filters = await loadDocumentTemplateFilterParams(searchParams);
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  // Mirrors the hooks' `enabled: !!organizationId` guard.
  if (organizationId) {
    await Promise.all([
      prefetchDocumentTemplates(queryClient, organizationId, toDocumentTemplateQuery(filters)),
      prefetchLegalEntities(queryClient, organizationId),
    ]);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <DocumentTemplatesView />
    </HydrationBoundary>
  );
}

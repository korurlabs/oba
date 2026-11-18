import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { TemplatesPageView } from "@/features/document-templates/components/templates-page-view";
import {
  loadDocumentTemplateFilterParams,
  toDocumentTemplateQuery,
} from "@/features/document-templates/lib/document-template-filters";
import { prefetchDocumentTemplates } from "@/features/document-templates/lib/prefetch";
import { loadTemplatePageTabParams } from "@/features/document-templates/lib/template-tabs";
import {
  loadMailTemplateFilterParams,
  toMailTemplateQuery,
} from "@/features/mail-templates/lib/mail-template-filters";
import { prefetchMailTemplates } from "@/features/mail-templates/lib/prefetch";

/**
 * Template List Page
 *
 * Thin async server component. It parses the URL with the parsers the views
 * use and server-prefetches the list of the open tab, document templates or
 * mail templates, under the key the client hook builds, so the table renders
 * with data in the first HTML payload. The other tab is not mounted, so it
 * is not prefetched. The dashboard layout gates auth.
 */
export default async function TemplatesPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { tab } = await loadTemplatePageTabParams(searchParams);
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  // Mirrors the hooks' `enabled: !!organizationId` guard.
  if (organizationId) {
    if (tab === "mail") {
      const filters = await loadMailTemplateFilterParams(searchParams);
      await prefetchMailTemplates(queryClient, organizationId, toMailTemplateQuery(filters));
    } else {
      const filters = await loadDocumentTemplateFilterParams(searchParams);
      await prefetchDocumentTemplates(
        queryClient,
        organizationId,
        toDocumentTemplateQuery(filters)
      );
    }
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <TemplatesPageView />
    </HydrationBoundary>
  );
}

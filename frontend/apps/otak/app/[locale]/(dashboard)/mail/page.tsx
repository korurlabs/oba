import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { MailTemplatesView } from "@/features/mail-templates/components/mail-templates-view";
import {
  loadMailTemplateFilterParams,
  toMailTemplateQuery,
} from "@/features/mail-templates/lib/mail-template-filters";
import { prefetchMailTemplates } from "@/features/mail-templates/lib/prefetch";

/**
 * Mail Template List Page
 *
 * Thin async server component. It parses the URL with the parsers the view
 * uses and server-prefetches the mail templates under the key the client
 * hook builds, so the table renders with data in the first HTML payload.
 * The dashboard layout gates auth.
 */
export default async function MailPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const filters = await loadMailTemplateFilterParams(searchParams);
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  // Mirrors the hook's `enabled: !!organizationId` guard.
  if (organizationId) {
    await prefetchMailTemplates(queryClient, organizationId, toMailTemplateQuery(filters));
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <MailTemplatesView />
    </HydrationBoundary>
  );
}

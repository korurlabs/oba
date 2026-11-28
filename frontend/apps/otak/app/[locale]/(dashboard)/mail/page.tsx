import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { prefetchMailSenders } from "@/features/mail-senders/lib/prefetch";
import { MailPageView } from "@/features/mail-templates/components/mail-page-view";
import { loadMailPageTabParams } from "@/features/mail-templates/lib/mail-page-tabs";
import {
  loadMailTemplateFilterParams,
  toMailTemplateQuery,
} from "@/features/mail-templates/lib/mail-template-filters";
import { prefetchMailTemplates } from "@/features/mail-templates/lib/prefetch";

/**
 * Mail Page
 *
 * Thin async server component. It parses the URL with the parsers the view
 * uses and server-prefetches the list of the open tab under the key the
 * client hook builds, so the table renders with data in the first HTML
 * payload. The other tab is not mounted, so it is not prefetched. The
 * dashboard layout gates auth.
 */
export default async function MailPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { tab } = await loadMailPageTabParams(searchParams);
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  // Mirrors the hooks' `enabled: !!organizationId` guard.
  if (organizationId) {
    if (tab === "senders") {
      await prefetchMailSenders(queryClient, organizationId);
    } else {
      const filters = await loadMailTemplateFilterParams(searchParams);
      await prefetchMailTemplates(queryClient, organizationId, toMailTemplateQuery(filters));
    }
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <MailPageView />
    </HydrationBoundary>
  );
}

import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { prefetchMailSenders } from "@/features/mail-senders/lib/prefetch";
import { OutreachesPageView } from "@/features/outreaches/components/outreaches-page-view";
import {
  loadOutreachFilterParams,
  toOutreachQuery,
} from "@/features/outreaches/lib/outreach-filters";
import { loadOutreachPageTabParams } from "@/features/outreaches/lib/outreach-tabs";
import { prefetchOutreaches } from "@/features/outreaches/lib/prefetch";

/**
 * Outreach List Page
 *
 * Thin async server component. It parses the URL with the parsers the view
 * uses and server-prefetches the list of the open tab under the key the
 * client hook builds, so the table renders with data in the first HTML
 * payload. The other tab is not mounted, so it is not prefetched. The
 * dashboard layout gates auth.
 */
export default async function OutreachesPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { tab } = await loadOutreachPageTabParams(searchParams);
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  // Mirrors the hooks' `enabled: !!organizationId` guard.
  if (organizationId) {
    if (tab === "senders") {
      await prefetchMailSenders(queryClient, organizationId);
    } else {
      const filters = await loadOutreachFilterParams(searchParams);
      await prefetchOutreaches(queryClient, organizationId, toOutreachQuery(filters));
    }
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <OutreachesPageView />
    </HydrationBoundary>
  );
}

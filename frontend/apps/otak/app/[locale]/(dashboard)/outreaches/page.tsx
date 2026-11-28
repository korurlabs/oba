import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { OutreachesView } from "@/features/outreaches/components/outreaches-view";
import {
  loadOutreachFilterParams,
  toOutreachQuery,
} from "@/features/outreaches/lib/outreach-filters";
import { prefetchOutreaches } from "@/features/outreaches/lib/prefetch";

/**
 * Outreach List Page
 *
 * Thin async server component. It parses the URL with the parsers the view
 * uses and server-prefetches the outreaches under the key the client hook
 * builds, so the table renders with data in the first HTML payload. The
 * dashboard layout gates auth.
 */
export default async function OutreachesPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const filters = await loadOutreachFilterParams(searchParams);
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  // Mirrors the hook's `enabled: !!organizationId` guard.
  if (organizationId) {
    await prefetchOutreaches(queryClient, organizationId, toOutreachQuery(filters));
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <OutreachesView />
    </HydrationBoundary>
  );
}

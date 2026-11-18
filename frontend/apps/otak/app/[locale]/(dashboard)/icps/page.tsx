import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { IcpsView } from "@/features/icps/components/icps-view";
import { loadIcpFilterParams, toIcpQuery } from "@/features/icps/lib/icp-filters";
import { prefetchIcps } from "@/features/icps/lib/prefetch";

/**
 * ICP List Page
 *
 * Thin async server component. It parses the URL with the parsers the view
 * uses and server-prefetches the page of ICPs under the key the client hook
 * builds, so the table renders with data in the first HTML payload. The
 * dashboard layout gates auth.
 */
export default async function IcpsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const filters = await loadIcpFilterParams(searchParams);
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  // Mirrors the hook's `enabled: !!organizationId` guard.
  if (organizationId) {
    await prefetchIcps(queryClient, organizationId, toIcpQuery(filters));
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <IcpsView />
    </HydrationBoundary>
  );
}

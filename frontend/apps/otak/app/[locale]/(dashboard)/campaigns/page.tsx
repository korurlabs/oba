import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { CampaignsView } from "@/features/campaigns/components/campaigns-view";
import {
  loadCampaignFilterParams,
  toCampaignQuery,
} from "@/features/campaigns/lib/campaign-filters";
import { prefetchCampaigns } from "@/features/campaigns/lib/prefetch";

/**
 * Campaign List Page
 *
 * Thin async server component. It parses the URL with the parsers the view
 * uses and server-prefetches the matching campaigns under the key the client
 * hook builds, so the table renders with data in the first HTML payload. The
 * dashboard layout gates auth.
 */
export default async function CampaignsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const filters = await loadCampaignFilterParams(searchParams);
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  // Mirrors the hooks' `enabled: !!organizationId` guard.
  if (organizationId) {
    await prefetchCampaigns(queryClient, organizationId, toCampaignQuery(filters));
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <CampaignsView />
    </HydrationBoundary>
  );
}

import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { CampaignDetailView } from "@/features/campaigns/components/campaign-detail-view";
import { prefetchCampaign } from "@/features/campaigns/lib/prefetch";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("campaigns.detail");
  return { title: t("title") };
}

/**
 * Campaign Detail Page
 *
 * Server-prefetches the campaign, so its heading, details and texts render
 * with data in the first HTML payload; the images card fetches its list on
 * mount, as attachment lists are always read fresh. Whether the user may
 * delete the campaign comes from the current user ("me"), which the
 * dashboard layout prefetches. The dashboard layout gates auth.
 */
export default async function CampaignDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  if (organizationId) {
    await prefetchCampaign(queryClient, organizationId, id);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <CampaignDetailView campaignId={id} />
    </HydrationBoundary>
  );
}

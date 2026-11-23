import type { Metadata } from "next";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { getTranslations } from "next-intl/server";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { prefetchRecentPages } from "@/features/pages/lib/prefetch";
import { RecentView } from "@/features/recent/components/recent-view";
import { RECENT_PAGE_LIMIT } from "@/features/recent/lib/recent-limits";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("recent");
  return { title: t("title") };
}

/**
 * Recent
 *
 * The pages changed most recently. Server-prefetches the list under the key
 * the view's hook builds, so the table renders with data in the first HTML
 * payload. The dashboard layout gates auth.
 */
export default async function RecentPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  // Mirrors the hook's `enabled: !!organizationId` guard.
  if (organizationId) {
    await prefetchRecentPages(
      queryClient,
      organizationId,
      RECENT_PAGE_LIMIT,
      locale,
      "recent-page"
    );
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <RecentView />
    </HydrationBoundary>
  );
}

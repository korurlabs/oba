import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { RelationBattlefieldView } from "@/features/relations/components/relation-battlefield-view";
import { prefetchRelationBattlefield } from "@/features/relations/lib/prefetch";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("relations.battlefield");
  return { title: t("title"), description: t("subtitle") };
}

/**
 * Lead Battlefield Page
 *
 * Server-prefetches every lead under the key the battlefield hook reads, so the
 * columns render with data in the first HTML payload. The "Closed" toggle
 * only shows or hides columns, so it needs no prefetch of its own. The
 * dashboard layout gates auth.
 */
export default async function BattlefieldPage() {
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  if (organizationId) {
    await prefetchRelationBattlefield(queryClient, organizationId);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <RelationBattlefieldView />
    </HydrationBoundary>
  );
}

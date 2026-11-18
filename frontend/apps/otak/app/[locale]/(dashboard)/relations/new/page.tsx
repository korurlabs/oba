import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { RelationCreateView } from "@/features/relations/components/relation-create-view";
import { prefetchRelationOwnerCandidates } from "@/features/relations/lib/prefetch";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("relations.newPage");
  return { title: t("title") };
}

/**
 * New Relation Page
 *
 * Server-prefetches the members the owner select offers. The dashboard layout
 * gates auth.
 */
export default async function NewRelationPage() {
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  if (organizationId) {
    await prefetchRelationOwnerCandidates(queryClient, organizationId);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <RelationCreateView />
    </HydrationBoundary>
  );
}

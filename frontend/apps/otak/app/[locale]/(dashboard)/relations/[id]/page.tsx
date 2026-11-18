import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { prefetchContacts } from "@/features/contacts/lib/prefetch";
import { prefetchRelationOutreaches } from "@/features/outreaches/lib/prefetch";
import { RelationDetailView } from "@/features/relations/components/relation-detail-view";
import {
  prefetchRelation,
  prefetchRelationOwnerCandidates,
} from "@/features/relations/lib/prefetch";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("relations.detail");
  return { title: t("title") };
}

/**
 * Relation Detail Page
 *
 * Server-prefetches the relation, the members the owner select offers, the
 * contacts and the first page of outreaches, so those cards render with data
 * in the first HTML payload. The attachments card reads its list on mount,
 * because that list is read again on every mount anyway. Whether the user
 * may delete the relation comes from the current user ("me"), which the
 * dashboard layout prefetches. The dashboard layout gates auth.
 */
export default async function RelationDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  if (organizationId) {
    await Promise.all([
      prefetchRelation(queryClient, organizationId, id),
      prefetchRelationOwnerCandidates(queryClient, organizationId),
      prefetchContacts(queryClient, organizationId, id),
      prefetchRelationOutreaches(queryClient, organizationId, id),
    ]);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <RelationDetailView relationId={id} />
    </HydrationBoundary>
  );
}

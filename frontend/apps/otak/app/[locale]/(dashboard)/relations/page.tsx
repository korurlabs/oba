import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { RelationsView } from "@/features/relations/components/relations-view";
import {
  loadRelationFilterParams,
  toRelationQuery,
} from "@/features/relations/lib/relation-filters";
import { prefetchMembers, prefetchRelations } from "@/features/relations/lib/prefetch";

/**
 * Relations List Page
 *
 * Thin async server component. It parses the URL with the parsers the view
 * uses and server-prefetches the page of relations and the members the owner
 * filter lists, under the keys the client hooks build, so the table renders
 * with data in the first HTML payload. The dashboard layout gates auth.
 */
export default async function RelationsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const filters = await loadRelationFilterParams(searchParams);
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  // Mirrors the hooks' `enabled: !!organizationId` guard.
  if (organizationId) {
    await Promise.all([
      prefetchRelations(queryClient, organizationId, toRelationQuery(filters)),
      prefetchMembers(queryClient, organizationId),
    ]);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <RelationsView />
    </HydrationBoundary>
  );
}

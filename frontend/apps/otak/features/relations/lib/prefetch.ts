import type { QueryClient } from "@tanstack/react-query";
import { listOrganizationMembers } from "@/features/members/api";
import { MEMBER_KEYS } from "@/features/members/hooks/query-keys";
import {
  getRelation,
  getRelationBattlefield,
  listRelationOwnerCandidates,
  listRelations,
} from "../api";
import { RELATION_KEYS } from "../hooks/query-keys";
import type { RelationQueryRequest } from "../schemas";

/*
 * Server-side prefetches of the relation pages, under the keys and with the
 * api functions the client hooks use. Each is best-effort: on failure the
 * error is logged and the client hook fetches on mount.
 */

export async function prefetchRelations(
  queryClient: QueryClient,
  organizationId: string,
  query: RelationQueryRequest
): Promise<void> {
  try {
    queryClient.setQueryData(RELATION_KEYS.list(organizationId, query), await listRelations(query));
  } catch (error) {
    console.error("[relations-page] list prefetch failed — client will refetch:", error);
  }
}

export async function prefetchRelation(
  queryClient: QueryClient,
  organizationId: string,
  relationId: string
): Promise<void> {
  try {
    queryClient.setQueryData(
      RELATION_KEYS.detail(organizationId, relationId),
      await getRelation(relationId)
    );
  } catch (error) {
    // A missing relation is answered by the client with its not-found state.
    console.error("[relation-page] detail prefetch failed — client will refetch:", error);
  }
}

export async function prefetchRelationBattlefield(
  queryClient: QueryClient,
  organizationId: string
): Promise<void> {
  try {
    queryClient.setQueryData(
      RELATION_KEYS.battlefield(organizationId),
      await getRelationBattlefield()
    );
  } catch (error) {
    console.error("[battlefield-page] battlefield prefetch failed — client will refetch:", error);
  }
}

/** The members the owner select of the relation form offers. */
export async function prefetchRelationOwnerCandidates(
  queryClient: QueryClient,
  organizationId: string
): Promise<void> {
  try {
    queryClient.setQueryData(
      RELATION_KEYS.ownerCandidates(organizationId),
      await listRelationOwnerCandidates()
    );
  } catch (error) {
    console.error("[relation-form] owner candidates prefetch failed — client will refetch:", error);
  }
}

/** Every member of the organization, for the owner filter of the relations list. */
export async function prefetchMembers(
  queryClient: QueryClient,
  organizationId: string
): Promise<void> {
  try {
    queryClient.setQueryData(
      MEMBER_KEYS.list(organizationId),
      await listOrganizationMembers(organizationId)
    );
  } catch (error) {
    console.error("[relations] members prefetch failed — client will refetch:", error);
  }
}

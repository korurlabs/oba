import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary, type QueryClient } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { getOrganization } from "@/features/organizations/api";
import { ORGANIZATION_KEYS } from "@/features/organizations/hooks/query-keys";
import { OrganizationDetailView } from "@/features/organizations/components/organization-detail-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("metadata");
  return {
    title: t("organization"),
  };
}

/**
 * Best-effort prefetch of the organization detail query
 * (useGetOrganization(id)). On failure the client hook fetches by itself.
 */
async function prefetchOrganization(queryClient: QueryClient, id: string): Promise<void> {
  // Mirror the useGetOrganization `enabled: !!organizationId` guard.
  if (!id) return;

  try {
    const data = await getOrganization(id);
    queryClient.setQueryData(ORGANIZATION_KEYS.detail(id), data);
  } catch (error) {
    console.error(
      "[organization-detail-page] prefetch failed — client will refetch:",
      error,
    );
  }
}

/**
 * Organization Detail Page
 *
 * Thin async server component. It server-prefetches the organization
 * (useGetOrganization(id)), which OrganizationDetailView fires on mount
 * whatever the tab. The view derives whether the user may edit from the
 * current user ("me", useGetMe), which the dashboard layout prefetches and
 * hydrates for the whole shell. With both in the first HTML payload an owner
 * or admin gets the editable form on first paint.
 *
 * The Members and Invitations tab queries stay client-side, also on a
 * `?tab=` deep link.
 *
 * The dashboard layout gates auth; this page adds none. See
 * server-prefetch-convention.
 */
export default async function OrganizationDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const queryClient = makeQueryClient();

  await prefetchOrganization(queryClient, id);

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <OrganizationDetailView id={id} />
    </HydrationBoundary>
  );
}

import type { Metadata } from "next";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { getTranslations } from "next-intl/server";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { PageEditView } from "@/features/pages/components/page-edit-view";
import { prefetchPage } from "@/features/pages/lib/prefetch";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("pageForm");
  return { title: t("metaEdit") };
}

/**
 * Edit Page
 *
 * Server-prefetches the page, so the editor opens with the stored version in
 * the first HTML payload. The dashboard layout gates auth.
 */
export default async function PageEditPage({ params }: { params: Promise<{ pageId: string }> }) {
  const { pageId } = await params;
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  if (organizationId) {
    await prefetchPage(queryClient, organizationId, pageId);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <PageEditView pageId={pageId} />
    </HydrationBoundary>
  );
}

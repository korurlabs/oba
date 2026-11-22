import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { DocumentDetailView } from "@/features/documents/components/document-detail-view";
import { prefetchDocument } from "@/features/documents/lib/prefetch";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("documents.detail");
  return { title: t("title") };
}

/**
 * Document Detail Page
 *
 * Server-prefetches the document with its snapshots, so its heading, PDF and
 * details render with data in the first HTML payload. The source template's
 * name loads on mount, as its signed detail is fetched again whenever shown.
 * The dashboard layout gates auth.
 */
export default async function DocumentDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  if (organizationId) {
    await prefetchDocument(queryClient, organizationId, id);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <DocumentDetailView documentId={id} />
    </HydrationBoundary>
  );
}

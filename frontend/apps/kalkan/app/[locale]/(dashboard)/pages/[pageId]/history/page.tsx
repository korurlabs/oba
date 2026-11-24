import type { Metadata } from "next";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { HistoryView } from "@/features/history/components/history-view";
import { prefetchPage, requirePageId } from "@/features/pages/lib/prefetch";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("history");
  return { title: t("title") };
}

/**
 * Page History
 *
 * Server-prefetches the page, so the heading names it and the compare has
 * the current text without a second round trip. The revisions load on the
 * client. The dashboard layout gates auth.
 */
export default async function PageHistoryPage({
  params,
}: {
  params: Promise<{ locale: string; pageId: string }>;
}) {
  const { locale, pageId } = await params;
  requirePageId(pageId);
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  if (organizationId && !(await prefetchPage(queryClient, organizationId, pageId, locale))) {
    notFound();
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <HistoryView pageId={pageId} />
    </HydrationBoundary>
  );
}

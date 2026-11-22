import type { Metadata } from "next";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { PageView } from "@/features/pages/components/page-view";
import { prefetchPage } from "@/features/pages/lib/prefetch";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("pages.view");
  return { title: t("title") };
}

/**
 * Page Detail
 *
 * Server-prefetches the page, so its title and body render with data in the
 * first HTML payload; a page the server does not show answers 404 unless the
 * caller can restore it from the trash. The dashboard layout gates auth.
 */
export default async function PageDetailPage({
  params,
}: {
  params: Promise<{ locale: string; pageId: string }>;
}) {
  const { locale, pageId } = await params;
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  if (organizationId && !(await prefetchPage(queryClient, organizationId, pageId, locale))) {
    notFound();
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <PageView pageId={pageId} />
    </HydrationBoundary>
  );
}

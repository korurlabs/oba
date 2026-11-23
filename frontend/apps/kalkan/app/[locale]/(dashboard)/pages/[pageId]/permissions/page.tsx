import type { Metadata } from "next";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { PagePermissionsView } from "@/features/pages/components/page-permissions-view";
import { prefetchPage, requirePageId } from "@/features/pages/lib/prefetch";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("permissions");
  return { title: t("meta") };
}

/**
 * Page Access
 *
 * Server-prefetches the page, whose level decides whether the access list is
 * shown at all. The grants load on the client, only for a MANAGE holder. The
 * dashboard layout gates auth.
 */
export default async function PagePermissionsPage({
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
      <PagePermissionsView pageId={pageId} />
    </HydrationBoundary>
  );
}

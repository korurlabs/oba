import type { Metadata } from "next";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { getTranslations } from "next-intl/server";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { prefetchTrash } from "@/features/pages/lib/prefetch";
import { TrashView } from "@/features/trash/components/trash-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("trash");
  return { title: t("title") };
}

/**
 * Trash
 *
 * The deleted pages a member can restore. Server-prefetches the trash under
 * the key the view's hook builds, so the table renders with data in the
 * first HTML payload; a guest is refused the trash and gets nothing cached.
 * The dashboard layout gates auth.
 */
export default async function TrashPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  // Mirrors the hook's `enabled: !!organizationId` guard.
  if (organizationId) {
    await prefetchTrash(queryClient, organizationId, locale, "trash-page");
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <TrashView />
    </HydrationBoundary>
  );
}

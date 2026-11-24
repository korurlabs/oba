import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { auth } from "@oba/shared/libs/auth";
import { prefetchMe } from "@oba/shared/features/users/api/prefetch-me";
import { ColumnDefaultCard } from "@/features/settings/components/column-default-card";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("columnSettings");
  return {
    title: t("title"),
    description: t("subtitle"),
  };
}

/**
 * Table Columns Page
 *
 * Where the user manages the default column layout of their ticket tables.
 * The layout lives in the user's preferences, so the "me" query is
 * server-prefetched and the card renders the saved default in the first HTML
 * payload. The dashboard layout already gates auth.
 */
export default async function TableColumnsPage() {
  const t = await getTranslations("columnSettings");
  const queryClient = makeQueryClient();

  // Best-effort: on a failed prefetch the client fetches through useGetMe.
  const session = await auth();
  await prefetchMe(queryClient, session?.accessToken, "table-columns-page");

  return (
    <div className="border-border bg-card mx-auto min-h-full max-w-3xl border-x px-8 py-10">
      <div className="mb-8">
        <h1 className="text-foreground text-3xl font-bold tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground mt-2 text-base">{t("subtitle")}</p>
      </div>
      <HydrationBoundary state={dehydrate(queryClient)}>
        <ColumnDefaultCard />
      </HydrationBoundary>
    </div>
  );
}

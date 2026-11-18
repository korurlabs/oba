import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { auth } from "@oba/shared/libs/auth";
import { prefetchMe } from "@oba/shared/features/users/api/prefetch-me";
import { listAlertRoutes } from "@/features/alert-routes/api";
import { isKaizenClosedOnServer } from "@/components/layout/kaizen-access-server";
import { ALERT_ROUTE_KEYS } from "@/features/alert-routes/hooks/query-keys";
import { AlertRoutesView } from "@/features/alert-routes/components/alert-routes-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("alertRoutes");
  return {
    title: t("title"),
    description: t("subtitle"),
  };
}

/**
 * Alerts Page
 *
 * The system-to-board map of incoming alerts. Server-prefetches the "me"
 * query, which decides whether the manage controls show, and the map itself,
 * so the table renders with data in the first HTML payload (see
 * server-prefetch-convention). The dashboard layout already gates auth.
 */
export default async function AlertsPage() {
  const queryClient = makeQueryClient();

  // Best-effort: on a failed prefetch the client fetches through useGetMe.
  const session = await auth();
  await prefetchMe(queryClient, session?.accessToken, "alerts-page");

  // Best-effort as well: on a failure the client fetches through useAlertRoutes.
  // Skipped without Kaizen in the organization: the API would refuse it, and
  // the no-access panel takes the place of the page.
  if (!(await isKaizenClosedOnServer())) {
    try {
      queryClient.setQueryData(ALERT_ROUTE_KEYS.list(), await listAlertRoutes());
    } catch (error) {
      const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
      console.error("[alerts-page] prefetch failed, client will refetch:", message);
    }
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <AlertRoutesView />
    </HydrationBoundary>
  );
}

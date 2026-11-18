import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { auth } from "@oba/shared/libs/auth";
import { prefetchMe } from "@oba/shared/features/users/api/prefetch-me";
import { AllTicketsView } from "@/features/all-tickets/components/all-tickets-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("sidebar");
  return { title: t("incidents") };
}

/**
 * Incidents dashboard page: the INCIDENT tickets of every board the user is
 * a member of.
 *
 * Server-prefetches "me" for the same reason as the Tickets page: the first
 * ticket params derive from the user's saved "incidents" filter preferences,
 * which arrive in the me response. The incidents query itself is not
 * prefetched, because its key embeds those client-side params merged with the
 * URL state and could not be reproduced on the server.
 */
export default async function IncidentsPage() {
  const queryClient = makeQueryClient();

  // Best-effort: on a failed prefetch the client fetches through useGetMe.
  const session = await auth();
  await prefetchMe(queryClient, session?.accessToken, "incidents-page");

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <AllTicketsView variant="incidents" />
    </HydrationBoundary>
  );
}

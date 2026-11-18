import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { auth } from "@oba/shared/libs/auth";
import { prefetchMe } from "@oba/shared/features/users/api/prefetch-me";
import { AllTicketsView } from "@/features/all-tickets/components/all-tickets-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("sidebar");
  return { title: t("tickets") };
}

/**
 * All-tickets dashboard page.
 *
 * Server-prefetches the current-user ("me") query, which is the single gate
 * for this page's data: `useAllTicketsPage` derives the first-mount ticket
 * params from `useGetMe()` (user id) and `useBoardPreferences("main")` — and
 * the latter also reads from the me response (`userPreferences`). With "me"
 * hydrated, the tickets query fires immediately after hydration instead of
 * waiting on a client round trip.
 *
 * The tickets query itself is intentionally NOT prefetched: its key embeds
 * params derived client-side (saved filter preferences merged with nuqs URL
 * state, falling back to `{ assigneeIds: [meUserId] }`), which are not
 * reproducible byte-identically on the server. A mismatched key would be
 * dead weight (see server-prefetch-convention).
 */
export default async function AllTicketsPage() {
  const queryClient = makeQueryClient();

  // Best-effort: on a failed prefetch the client fetches through useGetMe.
  const session = await auth();
  await prefetchMe(queryClient, session?.accessToken, "all-tickets-page");

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <AllTicketsView />
    </HydrationBoundary>
  );
}

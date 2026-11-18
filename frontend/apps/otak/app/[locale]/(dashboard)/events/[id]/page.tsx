import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { auth } from "@oba/shared/libs/auth";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { EventDetailView } from "@/features/events/components/event-detail-view";
import { prefetchEvent } from "@/features/events/lib/prefetch";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("events.detail");
  return { title: t("title") };
}

/**
 * Event Detail Page
 *
 * Server-prefetches the event, so its heading and details render with data
 * in the first HTML payload; the relations card fetches its page on mount.
 * Whether the user may delete the event comes from the current user ("me"),
 * which the dashboard layout prefetches. The dashboard layout gates auth.
 */
export default async function EventDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const session = await auth();
  const organizationId = session?.user?.defaultOrganizationId;
  const queryClient = makeQueryClient();

  if (organizationId) {
    await prefetchEvent(queryClient, organizationId, id);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <EventDetailView eventId={id} />
    </HydrationBoundary>
  );
}

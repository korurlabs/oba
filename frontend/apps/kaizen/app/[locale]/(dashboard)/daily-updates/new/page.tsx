import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary, type QueryClient } from "@tanstack/react-query";
import type { SearchParams } from "nuqs/server";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { auth } from "@oba/shared/libs/auth";
import { prefetchMe } from "@oba/shared/features/users/api/prefetch-me";
import { getTicketByKey } from "@/features/all-tickets/api";
import { isKaizenClosedOnServer } from "@/components/layout/kaizen-access-server";
import { UPDATE_KEYS } from "@/features/updates/hooks/query-keys";
import {
  parseTicketKeysParam,
  UPDATE_TICKET_KEYS_PARAM,
} from "@/features/updates/lib/create-update-path";
import { UPDATE_TICKETS_MAX } from "@/features/updates/schemas";
import { CreateUpdateView } from "@/features/updates/components/create-update-view";

interface CreateUpdatePageProps {
  searchParams: Promise<SearchParams>;
}

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("updates.form");
  return { title: t("createTitle") };
}

/**
 * Create-update page.
 *
 * Opened plainly, the form starts with an empty card and nothing but the
 * current user ("me") is prefetched. Opened from a ticket selection
 * (`?ticketKeys=`), it also prefetches those tickets, which the form waits
 * for before it mounts with them selected. "Me" carries the saved picker
 * filters and the default assignee filter the ticket list starts on. The
 * ticket list itself is not prefetched: its key embeds the URL filters
 * merged with that starting set on the client. Every prefetch is
 * best-effort: on a failure the client fetches the data itself and shows any
 * error there.
 */
export default async function CreateUpdatePage({ searchParams }: CreateUpdatePageProps) {
  const queryClient = makeQueryClient();
  const ticketKeys = parseTicketKeysParam((await searchParams)[UPDATE_TICKET_KEYS_PARAM]);

  const session = await auth();
  // Without a token the dashboard layout sends the user to sign in; a
  // request would only fail. Without Kaizen in the organization the API would
  // refuse the tickets, and the no-access panel takes the place of the page.
  if (session?.accessToken) {
    const kaizenClosed = await isKaizenClosedOnServer();
    await Promise.all([
      prefetchMe(queryClient, session.accessToken, "create-update-page"),
      ...(kaizenClosed ? [] : (ticketKeys ?? []))
        .slice(0, UPDATE_TICKETS_MAX)
        .map((ticketKey) => prefetchSeedTicket(queryClient, ticketKey)),
    ]);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <CreateUpdateView ticketKeys={ticketKeys} />
    </HydrationBoundary>
  );
}

function prefetchSeedTicket(queryClient: QueryClient, ticketKey: string): Promise<void> {
  return getTicketByKey(ticketKey)
    .then((response) => {
      queryClient.setQueryData(UPDATE_KEYS.seedTicket(ticketKey), response);
    })
    .catch((error: unknown) => {
      console.error(
        `[create-update-page] ticket ${ticketKey} prefetch failed — client will refetch:`,
        describeError(error),
      );
    });
}

function describeError(error: unknown): string {
  return error instanceof Error ? `${error.name}: ${error.message}` : String(error);
}

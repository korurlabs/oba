import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary, type QueryClient } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { auth } from "@oba/shared/libs/auth";
import { prefetchMe } from "@oba/shared/features/users/api/prefetch-me";
import { getUpdateById } from "@/features/updates/api";
import { isKaizenClosedOnServer } from "@/components/layout/kaizen-access-server";
import { UPDATE_KEYS } from "@/features/updates/hooks/query-keys";
import { CreateUpdateView } from "@/features/updates/components/create-update-view";

interface EditUpdatePageProps {
  params: Promise<{ updateId: string }>;
}

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("updates.form");
  return { title: t("editTitle") };
}

/**
 * Edit-update page: the create screen seeded with the stored update.
 *
 * Prefetches the update and the current user ("me"); the view compares the
 * two and offers the form only to the author. Both are best-effort: on a
 * failure, including an update the reader cannot see, the client fetches
 * again and shows the server's answer.
 */
export default async function EditUpdatePage({ params }: EditUpdatePageProps) {
  const { updateId } = await params;
  const queryClient = makeQueryClient();

  const session = await auth();
  // Without a token the dashboard layout sends the user to sign in; a
  // request would only fail. Without Kaizen in the organization the API would
  // refuse the update, and the no-access panel takes the place of the page.
  if (session?.accessToken) {
    const kaizenClosed = await isKaizenClosedOnServer();
    await Promise.all([
      prefetchMe(queryClient, session.accessToken, "edit-update-page"),
      kaizenClosed ? undefined : prefetchUpdate(queryClient, updateId),
    ]);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <CreateUpdateView mode="edit" updateId={updateId} />
    </HydrationBoundary>
  );
}

function prefetchUpdate(queryClient: QueryClient, updateId: string): Promise<void> {
  return getUpdateById(updateId)
    .then((response) => {
      queryClient.setQueryData(UPDATE_KEYS.detail(updateId), response);
    })
    .catch((error: unknown) => {
      const message =
        error instanceof Error ? `${error.name}: ${error.message}` : String(error);
      console.error("[edit-update-page] update prefetch failed — client will refetch:", message);
    });
}

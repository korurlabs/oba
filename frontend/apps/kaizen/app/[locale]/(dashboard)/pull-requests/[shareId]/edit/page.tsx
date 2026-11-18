import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { auth } from "@oba/shared/libs/auth";
import { prefetchMe } from "@oba/shared/features/users/api/prefetch-me";
import { getPullRequestShareById } from "@/features/pull-requests/api";
import { isKaizenClosedOnServer } from "@/components/layout/kaizen-access-server";
import { PULL_REQUEST_KEYS } from "@/features/pull-requests/hooks/query-keys";
import { SharePullRequestView } from "@/features/pull-requests/components/share-pull-request-view";

interface EditPullRequestSharePageProps {
  params: Promise<{ shareId: string }>;
}

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("pullRequests.form");
  return { title: t("editTitle") };
}

/**
 * Edit-pull-request-share page: the share screen seeded with the stored
 * share, without the picker.
 *
 * Prefetches the share of the session's default organization and the
 * current user ("me"); the view compares the two and offers the form only to
 * the author. Both are best-effort: on a failure, including a share the
 * reader cannot see, the client fetches again and shows the server's answer.
 */
export default async function EditPullRequestSharePage({ params }: EditPullRequestSharePageProps) {
  const { shareId } = await params;
  const queryClient = makeQueryClient();

  const session = await auth();
  const orgId = session?.user?.defaultOrganizationId || undefined;
  // Without a token the dashboard layout sends the user to sign in; a
  // request would only fail. Without Kaizen in the organization the API would
  // refuse the share, and the no-access panel takes the place of the page.
  if (session?.accessToken) {
    const kaizenClosed = await isKaizenClosedOnServer();
    await Promise.all([
      prefetchMe(queryClient, session.accessToken, "edit-pull-request-share-page"),
      orgId && !kaizenClosed
        ? getPullRequestShareById(shareId)
            .then((response) => queryClient.setQueryData(PULL_REQUEST_KEYS.share(orgId, shareId), response))
            .catch((error: unknown) => {
              const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
              console.error(
                "[edit-pull-request-share-page] share prefetch failed — client will refetch:",
                message,
              );
            })
        : undefined,
    ]);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <SharePullRequestView mode="edit" shareId={shareId} />
    </HydrationBoundary>
  );
}

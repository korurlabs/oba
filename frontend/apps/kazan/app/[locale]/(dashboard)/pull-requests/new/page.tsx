import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { auth } from "@oba/shared/libs/auth";
import { prefetchMe } from "@oba/shared/features/users/api/prefetch-me";
import { listGithubRepositories } from "@/features/pull-requests/api";
import { isKazanClosedOnServer } from "@/components/layout/kazan-access-server";
import { PULL_REQUEST_KEYS } from "@/features/pull-requests/hooks/query-keys";
import { SharePullRequestView } from "@/features/pull-requests/components/share-pull-request-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("pullRequests.form");
  return { title: t("createTitle") };
}

/**
 * Share-pull-request page.
 *
 * Prefetches the current user ("me") and the GitHub App installation's
 * repositories of the session's default organization, the organization
 * every request goes to. The pull request list is not prefetched: its key
 * names the repository last chosen in this browser, which only the client
 * knows. Every prefetch is best-effort: on a failure, including GitHub not
 * set up for the organization, the client fetches again and shows the
 * server's answer.
 */
export default async function SharePullRequestPage() {
  const queryClient = makeQueryClient();

  const session = await auth();
  const orgId = session?.user?.defaultOrganizationId || undefined;
  // Without a token the dashboard layout sends the user to sign in; a
  // request would only fail. Without Kazan in the organization the API would
  // refuse the repositories, and the no-access panel takes the place of the page.
  if (session?.accessToken) {
    const kazanClosed = await isKazanClosedOnServer();
    await Promise.all([
      prefetchMe(queryClient, session.accessToken, "share-pull-request-page"),
      orgId && !kazanClosed
        ? listGithubRepositories()
            .then((response) => queryClient.setQueryData(PULL_REQUEST_KEYS.repositories(orgId), response))
            .catch((error: unknown) => {
              console.error(
                "[share-pull-request-page] repositories prefetch failed — client will refetch:",
                describeError(error),
              );
            })
        : undefined,
    ]);
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <SharePullRequestView />
    </HydrationBoundary>
  );
}

function describeError(error: unknown): string {
  return error instanceof Error ? `${error.name}: ${error.message}` : String(error);
}

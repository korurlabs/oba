import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { auth } from "@oba/shared/libs/auth";
import { prefetchMe } from "@oba/shared/features/users/api/prefetch-me";
import { isKazanClosedOnServer } from "@/components/layout/kazan-access-server";
import { listChecks } from "@/features/cyberscore/api";
import { CHECK_KEYS } from "@/features/cyberscore/hooks/query-keys";
import { CheckStatusSchema } from "@/features/cyberscore/schemas";
import { CheckList } from "@/features/cyberscore/components/check-list";
import { CyberscoreAccessGate } from "@/features/cyberscore/components/cyberscore-access-gate";
import { isCyberscoreClosedOnServer } from "@/features/cyberscore/lib/cyberscore-access-server";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("cyberscore");
  return {
    title: t("checks.title"),
    description: t("checks.description"),
  };
}

interface ChecksPageProps {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}

/**
 * Checks Page
 *
 * The organization's cyberscore checks. Server-prefetches the "me" query,
 * which decides whether the write controls show, and the check list under
 * the status filter in the URL, so the table renders with data in the first
 * HTML payload. An unknown status lists every check, as the client's filter
 * parser reads it. The dashboard layout already gates auth; a guest of the
 * organization, whom the API refuses the cyberscore, gets the gate's
 * no-access state instead.
 */
export default async function ChecksPage({ searchParams }: ChecksPageProps) {
  const queryClient = makeQueryClient();

  // Best-effort: on a failed prefetch the client fetches through useGetMe.
  const session = await auth();
  await prefetchMe(queryClient, session?.accessToken, "cyberscore-checks-page");

  // Best-effort as well: on a failure the client fetches through useChecks.
  // Skipped without Kazan in the organization: the API would refuse it, and
  // the no-access panel takes the place of the page. Skipped for a guest as
  // well, for the same reason.
  if (!(await isKazanClosedOnServer()) && !(await isCyberscoreClosedOnServer())) {
    const parsed = CheckStatusSchema.safeParse((await searchParams).status);
    const status = parsed.success ? parsed.data : undefined;
    try {
      queryClient.setQueryData(
        CHECK_KEYS.list(status),
        await listChecks(status ? { status } : undefined)
      );
    } catch (error) {
      const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
      console.error("[cyberscore-checks-page] prefetch failed, client will refetch:", message);
    }
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <CyberscoreAccessGate>
        <CheckList />
      </CyberscoreAccessGate>
    </HydrationBoundary>
  );
}

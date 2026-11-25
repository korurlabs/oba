import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { isKazanClosedOnServer } from "@/components/layout/kazan-access-server";
import { getCyberscore, getCyberscoreTrend } from "@/features/cyberscore/api";
import { CYBERSCORE_KEYS } from "@/features/cyberscore/hooks/query-keys";
import { CYBERSCORE_TREND_DAYS } from "@/features/cyberscore/schemas";
import { CyberscoreAccessGate } from "@/features/cyberscore/components/cyberscore-access-gate";
import { CyberscoreOverview } from "@/features/cyberscore/components/cyberscore-overview";
import { isCyberscoreClosedOnServer } from "@/features/cyberscore/lib/cyberscore-access-server";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("cyberscore");
  return { title: t("title"), description: t("description") };
}

/**
 * Cyberscore overview: one rating of the organization's security posture,
 * computed from its own checks, with its breakdown by category and its trend.
 * Server-prefetches the score and the trend under the keys the client hooks
 * read, so the page renders with data in the first HTML payload. The
 * dashboard layout already gates auth; a guest of the organization, whom the
 * API refuses the cyberscore, gets the gate's no-access state instead.
 */
export default async function CyberscorePage() {
  const queryClient = makeQueryClient();

  // Best-effort: on a failure the client fetches through useCyberscore and
  // useCyberscoreTrend. Skipped without Kazan in the organization: the API
  // would refuse it, and the no-access panel takes the place of the page.
  // Skipped for a guest as well, for the same reason.
  if (!(await isKazanClosedOnServer()) && !(await isCyberscoreClosedOnServer())) {
    const [score, trend] = await Promise.allSettled([
      getCyberscore(),
      getCyberscoreTrend(CYBERSCORE_TREND_DAYS),
    ]);
    if (score.status === "fulfilled") {
      queryClient.setQueryData(CYBERSCORE_KEYS.current(), score.value);
    } else {
      logPrefetchFailure("score", score.reason);
    }
    if (trend.status === "fulfilled") {
      queryClient.setQueryData(CYBERSCORE_KEYS.trend(CYBERSCORE_TREND_DAYS), trend.value);
    } else {
      logPrefetchFailure("trend", trend.reason);
    }
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <CyberscoreAccessGate>
        <CyberscoreOverview />
      </CyberscoreAccessGate>
    </HydrationBoundary>
  );
}

function logPrefetchFailure(query: string, error: unknown) {
  const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
  console.error(`[cyberscore-page] ${query} prefetch failed, client will refetch:`, message);
}

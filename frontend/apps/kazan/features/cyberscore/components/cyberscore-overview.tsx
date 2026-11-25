"use client";

import type { ReactNode } from "react";
import { AlertCircle, ListChecks, RotateCw, ShieldCheck } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { useCyberscore, useCyberscoreTrend } from "../hooks/use-cyberscore";
import { CyberscoreLevel } from "../schemas";
import { CyberscoreBreakdown } from "./cyberscore-breakdown";
import { CyberscoreGauge } from "./cyberscore-gauge";
import { CyberscoreTrend } from "./cyberscore-trend";

const PANEL_CLASS = "kazan-grid-frame bg-card text-card-foreground flex flex-col gap-4 p-4 sm:p-5";

/** The route of the check list, which the overview links to. */
const CHECKS_HREF = "/cyberscore/checks";

function Panel({
  title,
  description,
  className,
  children,
}: {
  title: string;
  description: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <section className={cn(PANEL_CLASS, className)}>
      <div className="flex flex-col gap-1">
        <h2 className="text-foreground text-base font-semibold">{title}</h2>
        <p className="text-muted-foreground text-sm">{description}</p>
      </div>
      {children}
    </section>
  );
}

function LoadError({ onRetry, isRetrying }: { onRetry: () => void; isRetrying: boolean }) {
  const t = useTranslations("cyberscore");
  const tErrors = useTranslations("errors");
  return (
    <div
      role="alert"
      className="border-destructive/40 bg-destructive/5 text-card-foreground flex items-center gap-4 border p-4 sm:p-5"
    >
      <AlertCircle aria-hidden className="text-destructive size-5 shrink-0" />
      <p className="text-muted-foreground flex-1 text-sm">{t("loadError")}</p>
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="gap-2"
        disabled={isRetrying}
        onClick={onRetry}
      >
        <RotateCw aria-hidden className={cn("size-4", isRetrying && "animate-spin")} />
        {tErrors("tryAgain")}
      </Button>
    </div>
  );
}

/**
 * The cyberscore overview: the current score as a gauge, its breakdown by
 * category and its trend. Every member reads it; the checks it is built from
 * are managed on the check list it links to. Before any check has a scorable
 * outcome the score is `UNKNOWN`, and the page shows an empty state instead
 * of a gauge at zero, which would read as the worst possible score.
 *
 * Score and trend load on their own, so a failed trend still leaves the
 * score readable and is retried alone.
 */
export function CyberscoreOverview() {
  const t = useTranslations("cyberscore");
  const scoreQuery = useCyberscore();
  const trendQuery = useCyberscoreTrend();
  const score = scoreQuery.data?.data;

  let body: ReactNode;
  if (scoreQuery.isPending) {
    body = (
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
        <Skeleton className="h-64 rounded-none" />
        <Skeleton className="h-64 rounded-none" />
        <Skeleton className="h-56 rounded-none lg:col-span-2" />
      </div>
    );
  } else if (scoreQuery.isError || !score) {
    // A response without data is a failure too: the server always sends a score.
    body = (
      <LoadError onRetry={() => void scoreQuery.refetch()} isRetrying={scoreQuery.isFetching} />
    );
  } else if (score.scoreLevel === CyberscoreLevel.UNKNOWN) {
    body = (
      <div className="kazan-grid-frame bg-card py-10">
        <EmptyState
          Icon={ShieldCheck}
          title={t("emptyTitle")}
          description={t("emptyDescription")}
        />
      </div>
    );
  } else {
    const trendPoints = trendQuery.data?.data;
    body = (
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
        <Panel title={t("gauge.title")} description={t("gauge.description")}>
          <CyberscoreGauge score={score} />
        </Panel>
        <Panel title={t("breakdown.title")} description={t("breakdown.description")}>
          <CyberscoreBreakdown breakdown={score.breakdownByCategory} />
        </Panel>
        <Panel
          title={t("trend.title")}
          description={t("trend.description")}
          className="lg:col-span-2"
        >
          {trendQuery.isPending ? (
            <Skeleton className="h-44 rounded-none" />
          ) : trendQuery.isError || !trendPoints ? (
            <LoadError
              onRetry={() => void trendQuery.refetch()}
              isRetrying={trendQuery.isFetching}
            />
          ) : (
            <CyberscoreTrend points={trendPoints} />
          )}
        </Panel>
      </div>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 sm:py-8">
      <PageHeader title={t("title")} description={t("description")}>
        <Button asChild variant="outline" size="sm" className="gap-2">
          <Link href={CHECKS_HREF}>
            <ListChecks aria-hidden className="size-4" />
            {t("viewChecks")}
          </Link>
        </Button>
      </PageHeader>
      {body}
    </div>
  );
}

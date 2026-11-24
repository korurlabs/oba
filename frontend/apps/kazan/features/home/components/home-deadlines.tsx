"use client";

import { useLocale, useTranslations } from "next-intl";
import { formatDateOnly, utcTodayDateOnly } from "@oba/shared/lib/date-only";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Skeleton } from "@/components/ui/skeleton";
import { BOARD_TAB, boardPath } from "@/features/boards/lib/board-route";
import { MilestoneHealthBadge } from "@/features/milestones/components/milestone-badge";
import { cn } from "@/lib/utils";
import { useHomeDeadlines } from "../hooks/use-home-deadlines";
import type { HomeDeadlineResponse } from "../schemas";
import { HomeSectionError } from "./home-section-error";

const SECTION_CLASS = "kazan-grid-frame bg-card text-card-foreground flex flex-col gap-4 p-4 sm:p-5";
const DAY_MS = 86_400_000;

function daysUntil(targetDate: string, today: string): number {
  return Math.round((Date.parse(`${targetDate}T00:00:00Z`) - Date.parse(`${today}T00:00:00Z`)) / DAY_MS);
}

function DeadlineRow({ deadline, today }: { deadline: HomeDeadlineResponse; today: string }) {
  const t = useTranslations("home.deadlines");
  const locale = useLocale();
  const days = daysUntil(deadline.targetDate, today);

  return (
    <li>
      <Link
        href={boardPath(deadline.boardSlug, BOARD_TAB.roadmap)}
        className={cn(
          "flex flex-col gap-1 border-l-2 border-transparent px-2 py-2 transition-colors sm:flex-row sm:items-center sm:gap-3",
          "hover:border-primary hover:bg-primary/5 dark:hover:bg-korur-slate",
          "focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-none",
        )}
      >
        <time
          dateTime={deadline.targetDate}
          className="text-muted-foreground w-24 shrink-0 font-mono text-xs tabular-nums"
        >
          {formatDateOnly(deadline.targetDate, locale)}
        </time>
        <span className="flex min-w-0 flex-1 flex-col">
          <span className="text-foreground flex items-center gap-2 text-sm font-medium">
            {deadline.color && (
              <span
                aria-hidden
                className="size-2 shrink-0"
                style={{ backgroundColor: deadline.color }}
              />
            )}
            <span className="truncate">{deadline.name}</span>
          </span>
          <span className="text-muted-foreground truncate text-xs">
            {deadline.boardName}
            {" · "}
            {t("progress", { done: deadline.doneCount, total: deadline.totalCount })}
          </span>
        </span>
        <span className="flex shrink-0 items-center gap-2">
          <MilestoneHealthBadge health={deadline.health} />
          <span
            className={cn(
              "w-24 text-right text-xs tabular-nums",
              days < 0 ? "text-destructive" : "text-muted-foreground",
            )}
          >
            {days < 0 ? t("overdue", { count: -days }) : t("dueIn", { count: days })}
          </span>
        </span>
      </Link>
    </li>
  );
}

/**
 * The OPEN roadmap milestones on the user's boards that are overdue or due
 * within 60 days, soonest first, each linking to its board's roadmap. The
 * server sends at most eight, so the list never grows past the leaderboard
 * beside it; "View roadmap" opens the roadmap of the first one.
 */
export function HomeDeadlines() {
  const t = useTranslations("home.deadlines");
  const { data, isPending, isError, refetch, isRefetching } = useHomeDeadlines();
  const deadlines = data?.data;
  // Today as a UTC calendar date. Milestone target dates and their health are
  // computed on the UTC calendar, so "overdue" here matches the health badge.
  const today = utcTodayDateOnly();

  return (
    <section aria-labelledby="home-deadlines-heading" className={SECTION_CLASS}>
      <h2 id="home-deadlines-heading" className="kazan-grid-heading">
        {t("title")}
      </h2>

      {isError || (!isPending && !deadlines) ? (
        <HomeSectionError
          message={t("error")}
          isRetrying={isRefetching}
          onRetry={() => void refetch()}
        />
      ) : isPending || !deadlines ? (
        <div aria-busy className="flex flex-col gap-2">
          {Array.from({ length: 4 }, (_, index) => (
            <Skeleton key={index} className="h-10 w-full" />
          ))}
        </div>
      ) : deadlines.length === 0 ? (
        <p className="text-muted-foreground py-6 text-center text-sm">{t("empty")}</p>
      ) : (
        <div className="flex flex-col gap-2">
          <ul className="flex flex-col gap-1">
            {deadlines.map((deadline) => (
              <DeadlineRow key={deadline.id} deadline={deadline} today={today} />
            ))}
          </ul>
          {deadlines[0] && (
            <Link
              href={boardPath(deadlines[0].boardSlug, BOARD_TAB.roadmap)}
              className="text-korur-blue-strong dark:text-korur-blue self-start px-2 text-sm font-medium hover:underline"
            >
              {t("viewRoadmap")}
            </Link>
          )}
        </div>
      )}
    </section>
  );
}

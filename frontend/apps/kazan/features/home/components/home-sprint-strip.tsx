"use client";

import { parseAsString, useQueryState } from "nuqs";
import { useLocale, useTranslations } from "next-intl";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { useBoards } from "@/features/boards/hooks/use-boards";
import { BOARD_TAB, boardPath } from "@/features/boards/lib/board-route";
import { formatDateOnly } from "@oba/shared/lib/date-only";
import { updateDayOf, updateToday } from "@/features/updates/lib/update-day";
import { cn } from "@/lib/utils";
import { useHomeSprint } from "../hooks/use-home-sprint";
import type { HomeSprintResponse } from "../schemas";
import { BurndownChart } from "./burndown-chart";
import { HomeSectionError } from "./home-section-error";

/** The URL parameter that names the focus board by slug. */
export const HOME_BOARD_PARAM = "board";

const STRIP_CLASS = "kazan-grid-frame bg-card text-card-foreground p-4 sm:p-5";
const DAY_MS = 86_400_000;

/**
 * Whole days from `from` to `to`, both `YYYY-MM-DD`. Counted on the calendar
 * dates in UTC, so no zone offset or summer time shifts the result.
 */
function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / DAY_MS);
}

/**
 * The focus board's ACTIVE sprint: the days left, its dates and a bar of the
 * time passed, with the burndown beside it (below it on a phone).
 *
 * Days are dates in the updates channel's zone, the same calendar the server
 * builds the burndown on, so the countdown, the chart and the daily updates
 * agree on what "today" is for every reader.
 *
 * The focus board lives in `?board=<slug>`. The server's default focus answer
 * lists every candidate board, which turns the slug into the id the sprint
 * endpoint takes; an unknown slug falls back to that default.
 */
export function HomeSprintStrip() {
  const t = useTranslations("home.sprint");
  const [boardSlug, setBoardSlug] = useQueryState(
    HOME_BOARD_PARAM,
    parseAsString.withOptions({ history: "replace", scroll: false }),
  );

  const defaultQuery = useHomeSprint();
  const focusBoardId = boardSlug
    ? defaultQuery.data?.data?.boards.find((board) => board.slug === boardSlug)?.id
    : undefined;
  // Without a resolved hint this is the same query as the default one.
  const focusQuery = useHomeSprint(focusBoardId);
  const { data, isPending, isError, refetch, isRefetching } = focusQuery;
  const sprint = data?.data;
  const defaultSlug = defaultQuery.data?.data?.boardSlug;

  if (isError || defaultQuery.isError || (!isPending && !sprint)) {
    return (
      <HomeSectionError
        message={t("error")}
        isRetrying={isRefetching || defaultQuery.isRefetching}
        onRetry={() => {
          void defaultQuery.refetch();
          void refetch();
        }}
      />
    );
  }

  if (isPending || !sprint) {
    return (
      <section aria-busy className={cn(STRIP_CLASS, "flex flex-col gap-4 md:flex-row")}>
        <div className="flex flex-1 flex-col gap-3">
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-9 w-40" />
          <Skeleton className="h-2 w-full" />
        </div>
        <Skeleton className="h-44 flex-[2]" />
      </section>
    );
  }

  if (!sprint.sprintId) {
    return <NoActiveSprint />;
  }

  return (
    <section aria-label={sprint.sprintName ?? t("label")} className={cn(STRIP_CLASS, "flex flex-col gap-6 md:flex-row")}>
      <Countdown
        sprint={sprint}
        // The default focus writes no parameter, so a shared link stays clean.
        onBoardChange={(slug) => void setBoardSlug(slug === defaultSlug ? null : slug)}
      />
      {sprint.burndown.length > 0 && (
        <div className="min-w-0 md:flex-[2]">
          <BurndownChart
            sprintName={sprint.sprintName ?? t("label")}
            points={sprint.burndown}
            totalTickets={sprint.totalTickets}
            openTickets={sprint.openTickets}
            today={updateToday()}
          />
        </div>
      )}
    </section>
  );
}

function Countdown({
  sprint,
  onBoardChange,
}: {
  sprint: HomeSprintResponse;
  onBoardChange: (slug: string) => void;
}) {
  const t = useTranslations("home.sprint");
  const locale = useLocale();
  const today = updateToday();
  const startDay = sprint.startDate ? updateDayOf(sprint.startDate) : null;
  const endDay = sprint.endDate ? updateDayOf(sprint.endDate) : null;
  const daysLeft = endDay ? daysBetween(today, endDay) : null;

  // Share of the sprint's days that have passed, today included.
  const elapsedPercent =
    startDay && endDay
      ? Math.round(
          Math.min(
            1,
            Math.max(0, (daysBetween(startDay, today) + 1) / Math.max(1, daysBetween(startDay, endDay) + 1)),
          ) * 100,
        )
      : null;

  return (
    <div className="flex min-w-0 flex-1 flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-foreground truncate text-base font-semibold">{sprint.sprintName}</h2>
        {sprint.boards.length > 1 && sprint.boardSlug && (
          <Select value={sprint.boardSlug} onValueChange={onBoardChange}>
            <SelectTrigger size="sm" className="w-auto max-w-48" aria-label={t("focusBoard")}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {sprint.boards.map((board) => (
                <SelectItem key={board.id} value={board.slug}>
                  {board.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      </div>

      {daysLeft === null ? (
        <p className="text-muted-foreground text-2xl font-semibold">{t("noEndDate")}</p>
      ) : daysLeft >= 0 ? (
        <p className="text-foreground text-3xl font-semibold">{t("daysLeft", { count: daysLeft })}</p>
      ) : (
        <p className="text-destructive text-3xl font-semibold">{t("ended", { count: -daysLeft })}</p>
      )}

      {startDay && endDay && (
        <p className="text-muted-foreground text-sm">
          {t("dateRange", {
            start: formatDateOnly(startDay, locale),
            end: formatDateOnly(endDay, locale),
          })}
        </p>
      )}

      {elapsedPercent !== null && (
        <div
          role="meter"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={elapsedPercent}
          aria-label={t("elapsed", { percent: elapsedPercent })}
          className="bg-primary/10 dark:bg-korur-slate h-2 w-full overflow-hidden"
        >
          <div
            className={cn(
              "h-full",
              daysLeft !== null && daysLeft < 0 ? "bg-destructive" : "bg-korur-blue-hover dark:bg-korur-blue",
            )}
            style={{ width: `${elapsedPercent}%` }}
          />
        </div>
      )}
    </div>
  );
}

/**
 * No board of the user runs an ACTIVE sprint. The link goes to the sprint
 * tab of the first board, where a sprint can be started. Without boards the
 * page's create-board card already covers the empty state, so no link shows.
 */
function NoActiveSprint() {
  const t = useTranslations("home.sprint");
  const { data } = useBoards();
  const firstBoard = data?.data?.[0];

  return (
    <section className={cn(STRIP_CLASS, "flex flex-wrap items-center justify-between gap-3")}>
      <p className="text-foreground text-base font-semibold">{t("noActive")}</p>
      {firstBoard && (
        <Link
          href={boardPath(firstBoard.slug, BOARD_TAB.sprint)}
          className="text-korur-blue-strong dark:text-korur-blue text-sm font-medium hover:underline"
        >
          {t("openSprintPage")}
        </Link>
      )}
    </section>
  );
}

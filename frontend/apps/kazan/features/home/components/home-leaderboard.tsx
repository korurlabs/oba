"use client";

import { useState } from "react";
import { Medal } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { InfoTip } from "@/components/ui/info-tip";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { UserAvatar } from "@/components/ui/user-avatar";
import { cn } from "@/lib/utils";
import { useHomeLeaderboard } from "../hooks/use-home-leaderboard";
import {
  HomeLeaderboardPeriod,
  HomeLeaderboardPeriodSchema,
  type HomeLeaderboardEntryResponse,
} from "../schemas";
import { HomeSectionError } from "./home-section-error";

/** Rows shown before the list is cut; the current user's row follows when it ranks lower. */
const LEADERBOARD_ROWS = 10;

/** Medal tints of the first three ranks, decoration beside the rank number that stays readable on its own. */
const MEDAL_CLASS: Record<number, string> = {
  1: "text-amber-500",
  2: "text-slate-400",
  3: "text-orange-700 dark:text-orange-400",
};

const SECTION_CLASS = "kazan-grid-frame bg-card text-card-foreground flex flex-col gap-4 p-4 sm:p-5";

function LeaderboardRow({
  entry,
  rank,
  isCurrentUser,
  pointsFormat,
}: {
  entry: HomeLeaderboardEntryResponse;
  rank: number;
  isCurrentUser: boolean;
  pointsFormat: Intl.NumberFormat;
}) {
  const t = useTranslations("home.leaderboard");
  const medalClass = MEDAL_CLASS[rank];

  return (
    <li
      aria-current={isCurrentUser ? "true" : undefined}
      className={cn(
        "flex items-center gap-3 border-l-2 border-transparent px-2 py-1.5",
        isCurrentUser && "border-primary bg-primary/5 dark:bg-korur-slate",
      )}
    >
      <span className="text-muted-foreground flex w-8 shrink-0 items-center gap-0.5 font-mono text-sm tabular-nums">
        <span className="sr-only">{t("rank", { rank })}</span>
        <span aria-hidden>{rank}</span>
        {medalClass && <Medal aria-hidden className={cn("size-4", medalClass)} />}
      </span>
      <UserAvatar
        user={{
          firstName: entry.firstName ?? undefined,
          lastName: entry.lastName ?? undefined,
          email: entry.email,
        }}
        size="sm"
        className="min-w-0 flex-1 overflow-hidden whitespace-nowrap"
      />
      {isCurrentUser && <span className="text-muted-foreground text-xs">{t("you")}</span>}
      <span className="text-muted-foreground w-20 shrink-0 text-right text-xs tabular-nums">
        {t("tickets", { count: entry.tickets })}
      </span>
      <span className="text-foreground w-16 shrink-0 text-right text-sm font-semibold tabular-nums">
        {t("points", { points: pointsFormat.format(entry.points) })}
      </span>
    </li>
  );
}

/**
 * Who finished the most work, weighted by size, for this sprint or the last
 * 30 days. The full calculation sits in the title's InfoTip. The server ranks
 * every user; the list shows the top ten and, when the current user ranks
 * lower, their own row below it, so everyone sees where they stand.
 *
 * Without an ACTIVE sprint on the user's boards the server answers with the
 * last 30 days even when this sprint was asked, and the select then offers
 * only that period.
 */
export function HomeLeaderboard() {
  const t = useTranslations("home.leaderboard");
  const locale = useLocale();
  const [period, setPeriod] = useState<HomeLeaderboardPeriod>(HomeLeaderboardPeriod.SPRINT);
  const { data, isPending, isError, refetch, isRefetching } = useHomeLeaderboard(period);
  const { data: me } = useGetMe();
  const currentUserId = me?.data?.id;

  const leaderboard = data?.data;
  const sprintUnavailable =
    period === HomeLeaderboardPeriod.SPRINT && leaderboard?.period === HomeLeaderboardPeriod.LAST_30_DAYS;
  const periods = sprintUnavailable
    ? [HomeLeaderboardPeriod.LAST_30_DAYS]
    : HomeLeaderboardPeriodSchema.options;
  const shownPeriod = leaderboard?.period ?? period;
  const pointsFormat = new Intl.NumberFormat(locale, { maximumFractionDigits: 1 });

  const entries = leaderboard?.entries ?? [];
  const top = entries.slice(0, LEADERBOARD_ROWS);
  const currentIndex = currentUserId ? entries.findIndex((entry) => entry.userId === currentUserId) : -1;
  const currentEntry = currentIndex >= LEADERBOARD_ROWS ? entries[currentIndex] : undefined;

  return (
    <section aria-labelledby="home-leaderboard-heading" className={SECTION_CLASS}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h2 id="home-leaderboard-heading" className="kazan-grid-heading">
            {t("title")}
          </h2>
          <InfoTip label={t("infoLabel")}>{t("info")}</InfoTip>
        </div>
        <Select
          value={shownPeriod}
          onValueChange={(value) => setPeriod(HomeLeaderboardPeriodSchema.parse(value))}
          disabled={periods.length < 2}
        >
          <SelectTrigger size="sm" className="w-auto" aria-label={t("period")}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {periods.map((option) => (
              <SelectItem key={option} value={option}>
                {t(`periods.${option}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {isError || (!isPending && !leaderboard) ? (
        <HomeSectionError
          message={t("error")}
          isRetrying={isRefetching}
          onRetry={() => void refetch()}
        />
      ) : isPending ? (
        <div aria-busy className="flex flex-col gap-2">
          {Array.from({ length: 5 }, (_, index) => (
            <Skeleton key={index} className="h-9 w-full" />
          ))}
        </div>
      ) : entries.length === 0 ? (
        <p className="text-muted-foreground py-6 text-center text-sm">{t("empty")}</p>
      ) : (
        <div className="flex flex-col gap-1">
          <ol className="flex flex-col gap-1">
            {top.map((entry, index) => (
              <LeaderboardRow
                key={entry.userId}
                entry={entry}
                rank={index + 1}
                isCurrentUser={entry.userId === currentUserId}
                pointsFormat={pointsFormat}
              />
            ))}
          </ol>
          {currentEntry && (
            <ol start={currentIndex + 1} className="border-border border-t pt-1">
              <LeaderboardRow
                entry={currentEntry}
                rank={currentIndex + 1}
                isCurrentUser
                pointsFormat={pointsFormat}
              />
            </ol>
          )}
        </div>
      )}
    </section>
  );
}

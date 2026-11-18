import * as React from "react";

import { ListRow, ListSection } from "@/components/kaizen/List";
import { nextSprintName, seriesRunMessage } from "@/components/sprints/sprintModel";
import { useDates } from "@/hooks/useDates";
import { useTranslation, type TranslationKey } from "@/i18n";
import type { SprintSeries } from "@/lib/schemas/sprints";

const RUN_STATUS: Record<string, TranslationKey> = {
  OK: "sprintSeriesRunOk",
  BLOCKED: "sprintSeriesRunBlocked",
  FAILED: "sprintSeriesRunFailed",
};

type SeriesStatusProps = {
  series: SprintSeries | null;
  /** The sprint's own name, from which a series that is not saved yet takes its name. */
  sprintName: string;
  /** Repeat as the form holds it, saved or not. */
  switchOn: boolean;
};

/**
 * Where the series stands: the sprint it creates next, when the current one
 * rolls over (local time with the UTC time beside it, since rollover is at
 * 00:00 UTC) and how its last run went, with the reason a series was switched
 * off in the viewer's language. Renders nothing while nothing repeats.
 */
function SeriesStatus({ series, sprintName, switchOn }: SeriesStatusProps) {
  const { t } = useTranslation();
  const dates = useDates();
  const next: string | null = nextSprintName(series, sprintName, switchOn);
  if (!next && !series) return null;

  const enabled: boolean = series?.enabled ?? false;
  const rollover: string | null =
    enabled && series?.nextRolloverAt ? dates.utcRollover(series.nextRolloverAt) : null;
  const lastRun: string | null = series?.lastRunAt ? dates.timestamp(series.lastRunAt) : null;
  const statusKey: TranslationKey | undefined = series?.lastRunStatus
    ? RUN_STATUS[series.lastRunStatus]
    : undefined;

  return (
    <ListSection header={t("sprintSeriesStatus")}>
      <ListRow title={t("sprintSeriesNext")} value={next ?? t("sprintSeriesOff")} />
      {rollover ? <ListRow title={t("sprintSeriesRollover")} value={rollover} /> : null}
      <ListRow title={t("sprintSeriesLastRun")} value={lastRun ?? t("sprintSeriesNeverRun")} />
      {statusKey ? (
        <ListRow
          title={t("sprintSeriesResult")}
          value={t(statusKey)}
          subtitle={series?.lastRunMessage ? seriesRunMessage(series.lastRunMessage, t) : undefined}
          subtitleTone={series?.lastRunStatus === "OK" ? "success" : "destructive"}
          wrap
        />
      ) : null}
    </ListSection>
  );
}

export { SeriesStatus };

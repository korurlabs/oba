/**
 * SprintRecurrenceTab Component
 *
 * The edit sprint dialog's Automation tab: the settings of the sprint's
 * series, saved on their own as a full replace, the name of the next sprint
 * and the status of the last run. Switching repeat on for a sprint outside
 * any series starts a series named after the sprint.
 *
 * The settings belong to the series, so they are the same on every board and
 * every sprint of the series. The series name is edited in the General tab,
 * so there is no name field here. The tab is read-only for a completed
 * sprint (completed sprints do not repeat) and for members who cannot manage
 * the board.
 */

"use client";

import { useMemo } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useFormContext } from "react-hook-form";
import { AlertCircle, Globe, Info, Loader2 } from "lucide-react";
import { ObaForm } from "@oba/shared/features/form";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { DialogFooter } from "@/components/ui/dialog";
import { InlineAlert } from "@/components/ui/inline-alert";
import { useOptionalBoardRoleContext } from "@/features/boards/providers/board-role-provider";
import { useSprint, useUpdateSprintSeries } from "@/features/sprints/hooks/use-sprints";
import {
  buildSprintRecurrenceFormSchema,
  DEFAULT_SPRINT_RECURRENCE_FIELDS,
  SPRINT_RECURRENCE_MAX_LENGTH_DAYS,
  SPRINT_RECURRENCE_MIN_LENGTH_DAYS,
  type SprintRecurrenceFormValues,
  type SprintSeriesResponse,
  type SprintRecurrenceRunStatus,
} from "@/features/sprints/schemas";
import {
  calendarDaysBetween,
  formatUtcDate,
  fromUtcCalendarDate,
  isUtcMidnight,
  splitSeriesName,
  toSprintSeriesRequest,
  toUtcCalendarDate,
} from "@/features/sprints/lib/sprint-recurrence";
import { SprintRecurrenceFields } from "./sprint-recurrence-fields";
import { seriesRunMessage } from "@/features/sprints/lib/series-run-message";

interface SprintRecurrenceTabProps {
  boardId: string;
  sprintId: string;
  onCancel: () => void;
}

export function SprintRecurrenceTab({ boardId, sprintId, onCancel }: SprintRecurrenceTabProps) {
  const t = useTranslations("sprint");
  const tCommon = useTranslations("common");
  const roleContext = useOptionalBoardRoleContext();
  const { data: sprintResponse, isLoading, isError } = useSprint(boardId, sprintId);
  const updateSeries = useUpdateSprintSeries();

  const sprint = sprintResponse?.data;

  /**
   * The dates automation runs on. Dates saved before automation was switched
   * on carry the time of day they were picked at; they are moved to 00:00 UTC
   * of the same calendar day when automation is switched on, since the server
   * accepts no other time.
   */
  const schedule = useMemo(() => {
    if (!sprint?.startDate || !sprint?.endDate) {
      return { start: null, end: null, needsNormalizing: false };
    }
    const needsNormalizing = !isUtcMidnight(sprint.startDate) || !isUtcMidnight(sprint.endDate);
    return {
      start: needsNormalizing
        ? toUtcCalendarDate(new Date(sprint.startDate))
        : new Date(sprint.startDate),
      end: needsNormalizing
        ? toUtcCalendarDate(new Date(sprint.endDate))
        : new Date(sprint.endDate),
      needsNormalizing,
    };
  }, [sprint?.startDate, sprint?.endDate]);

  const lengthDays =
    schedule.start && schedule.end
      ? calendarDaysBetween(fromUtcCalendarDate(schedule.start), fromUtcCalendarDate(schedule.end))
      : null;

  const schema = useMemo(() => buildSprintRecurrenceFormSchema(lengthDays), [lengthDays]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-8">
        <Loader2 className="text-muted-foreground h-5 w-5 animate-spin" />
      </div>
    );
  }

  if (isError || !sprint) {
    return (
      <InlineAlert
        variant="danger"
        icon={<AlertCircle />}
        description={t("recurrence.loadError")}
        className="my-4"
      />
    );
  }

  const isCompleted = sprint.status === "COMPLETED";
  const canManage = roleContext?.canManage ?? false;
  const readOnly = isCompleted || !canManage;
  const series = sprint.series ?? null;
  // A new series starts with the length of this sprint's dates.
  const defaultLengthDays =
    series?.lengthDays ??
    (lengthDays === null
      ? null
      : Math.min(
          Math.max(lengthDays, SPRINT_RECURRENCE_MIN_LENGTH_DAYS),
          SPRINT_RECURRENCE_MAX_LENGTH_DAYS
        ));

  const handleSubmit = (values: SprintRecurrenceFormValues) => {
    const enabling = values.recurrence.enabled;
    updateSeries.mutate({
      boardId,
      sprintId,
      data: toSprintSeriesRequest(
        values.recurrence,
        defaultLengthDays ?? SPRINT_RECURRENCE_MIN_LENGTH_DAYS
      ),
      dates:
        enabling && schedule.needsNormalizing && schedule.start && schedule.end
          ? { startDate: schedule.start.toISOString(), endDate: schedule.end.toISOString() }
          : undefined,
    });
  };

  return (
    <ObaForm
      schema={schema}
      onSubmit={handleSubmit}
      defaultValues={{
        recurrence: series
          ? {
              enabled: series.enabled,
              lengthDays: series.lengthDays,
              unfinishedTicketAction: series.unfinishedTicketAction,
              creationMode: series.creationMode,
              createDaysBefore: series.createDaysBefore ?? null,
            }
          : { ...DEFAULT_SPRINT_RECURRENCE_FIELDS, lengthDays: defaultLengthDays },
      }}
      className="flex flex-col gap-4 py-4"
    >
      {isCompleted && (
        <InlineAlert
          variant="info"
          icon={<Info />}
          description={t("recurrence.completedReadOnly")}
        />
      )}
      {!isCompleted && !canManage && (
        <InlineAlert variant="info" icon={<Info />} description={t("recurrence.noPermission")} />
      )}

      <SprintRecurrenceFields
        lengthDays={lengthDays}
        rolloverAt={schedule.end}
        disabled={readOnly || updateSeries.isPending}
      />

      <NormalizeDatesNote
        visible={schedule.needsNormalizing && !readOnly}
        start={schedule.start}
        end={schedule.end}
      />

      <SprintRecurrenceStatus series={series} sprintName={sprint.name} readOnly={readOnly} />

      {!readOnly && (
        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={onCancel}
            disabled={updateSeries.isPending}
          >
            {tCommon("cancel")}
          </Button>
          <Button type="submit" disabled={updateSeries.isPending}>
            {updateSeries.isPending ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                {t("saving")}
              </>
            ) : (
              t("save")
            )}
          </Button>
        </DialogFooter>
      )}
    </ObaForm>
  );
}

// ── Date note ────────────────────────────────────────────────────────

/** Says which UTC dates the sprint gets when automation is switched on. */
function NormalizeDatesNote({
  visible,
  start,
  end,
}: {
  visible: boolean;
  start: Date | null;
  end: Date | null;
}) {
  const t = useTranslations("sprint.recurrence");
  const locale = useLocale();
  const { watch } = useFormContext<SprintRecurrenceFormValues>();
  const enabled = watch("recurrence.enabled");

  if (!visible || !enabled || !start || !end) return null;

  return (
    <p className="text-muted-foreground flex items-start gap-2 text-xs">
      <Globe className="mt-0.5 h-3.5 w-3.5 shrink-0" />
      <span>
        {t("normalizeDatesNote", {
          start: formatUtcDate(locale, start),
          end: formatUtcDate(locale, end),
        })}
      </span>
    </p>
  );
}

// ── Run status ───────────────────────────────────────────────────────

const RUN_STATUS_VARIANT: Record<
  SprintRecurrenceRunStatus,
  "secondary" | "outline" | "destructive"
> = {
  OK: "secondary",
  BLOCKED: "outline",
  FAILED: "destructive",
};

/**
 * The name the next sprint gets: the series' own while it repeats. While the
 * switch is on but not yet saved, the server will take the series name from
 * this sprint's name with a trailing number split off, and a new series
 * counts on from that number; an existing series keeps its counter.
 */
function nextSprintName(
  series: SprintSeriesResponse | null,
  sprintName: string,
  switchOn: boolean
): string | null {
  if (series?.enabled) return series.nextSprintName;
  if (!switchOn) return null;
  const { seriesName, round } = splitSeriesName(sprintName);
  return `${seriesName} ${series ? series.nextRound : round + 1}`;
}

function SprintRecurrenceStatus({
  series,
  sprintName,
  readOnly,
}: {
  series: SprintSeriesResponse | null;
  sprintName: string;
  readOnly: boolean;
}) {
  const t = useTranslations("sprint.recurrence.status");
  const tReasons = useTranslations("sprint.recurrence.reasons");
  const { formatDateTime } = useDateFormat();
  const { watch } = useFormContext<SprintRecurrenceFormValues>();
  const switchOn = watch("recurrence.enabled");
  const nextName = nextSprintName(series, sprintName, switchOn && !readOnly);

  if (!series && !nextName) return null;

  // Rollover and creation happen at 00:00 UTC; the viewer's own time is shown
  // first because that is when they will see the change.
  const formatScheduled = (value: string | null | undefined) =>
    value ? t("scheduledTime", { time: formatDateTime(value) }) : t("none");

  const rows: Array<{ label: string; value: React.ReactNode }> = [];
  if (nextName) {
    rows.push({ label: t("nextSprint"), value: nextName });
  }
  if (series) {
    rows.push({ label: t("nextRollover"), value: formatScheduled(series.nextRolloverAt) });
    if (series.creationMode === "DAYS_BEFORE") {
      rows.push({ label: t("nextCreation"), value: formatScheduled(series.nextCreationAt) });
    }
    rows.push({
      label: t("lastRun"),
      value: series.lastRunAt ? formatDateTime(series.lastRunAt) : t("never"),
    });
    if (series.lastRunStatus) {
      rows.push({
        label: t("lastStatus"),
        value: (
          <Badge variant={RUN_STATUS_VARIANT[series.lastRunStatus]} className="text-xs">
            {t(`runStatus.${series.lastRunStatus}`)}
          </Badge>
        ),
      });
    }
    if (series.lastRunMessage) {
      rows.push({ label: t("message"), value: seriesRunMessage(series.lastRunMessage, tReasons) });
    }
  }

  return (
    <div className="rounded-lg border">
      <div className="border-b px-3 py-2 text-sm font-semibold">{t("title")}</div>
      <dl className="divide-y">
        {rows.map((row) => (
          <div
            key={row.label}
            className="grid grid-cols-[10rem_minmax(0,1fr)] items-center gap-3 px-3 py-2 text-sm"
          >
            <dt className="text-muted-foreground">{row.label}</dt>
            <dd className="break-words">{row.value}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

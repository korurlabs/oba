/**
 * SprintRecurrenceFields Component
 *
 * The "Repeat automatically" switch and, while it is on, the series'
 * settings: how long each new sprint runs, where unfinished tickets go and
 * when the next sprint is created. The series takes its name from the sprint
 * name, so there is no name input here. Used inside the create sprint form
 * and the edit dialog's Automation tab; both forms keep these values under
 * `recurrence`.
 */

"use client";

import { useLocale, useTranslations } from "next-intl";
import { useFormContext } from "react-hook-form";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Clock } from "lucide-react";
import { TextField } from "@/components/ui/form-fields";
import { FormField, FormItem, FormControl, FormLabel, FormMessage } from "@/components/ui/form";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";
import {
  SPRINT_RECURRENCE_MAX_LENGTH_DAYS,
  SPRINT_RECURRENCE_MIN_LENGTH_DAYS,
  type SprintRecurrenceFieldsValues,
} from "@/features/sprints/schemas";
import { formatUtcDate } from "@/features/sprints/lib/sprint-recurrence";

type RecurrenceFormShape = { recurrence: SprintRecurrenceFieldsValues };

interface SprintRecurrenceFieldsProps {
  /** Whole days from this sprint's start to its end, or null when a date is missing. */
  lengthDays: number | null;
  /** The end date at 00:00 UTC, which is the rollover time. */
  rolloverAt: Date | null;
  disabled?: boolean;
}

export function SprintRecurrenceFields({
  lengthDays,
  rolloverAt,
  disabled = false,
}: SprintRecurrenceFieldsProps) {
  const t = useTranslations("sprint.recurrence");
  const locale = useLocale();
  const { formatDateTime } = useDateFormat();
  const { control, watch } = useFormContext<RecurrenceFormShape>();

  const enabled = watch("recurrence.enabled");
  const creationMode = watch("recurrence.creationMode");
  const seriesLength = Number(watch("recurrence.lengthDays"));

  const hasValidLength =
    lengthDays !== null && lengthDays >= 1 && lengthDays <= SPRINT_RECURRENCE_MAX_LENGTH_DAYS;
  const hasValidSeriesLength =
    Number.isInteger(seriesLength) &&
    seriesLength >= 2 &&
    seriesLength <= SPRINT_RECURRENCE_MAX_LENGTH_DAYS;

  return (
    <div className="flex flex-col gap-4">
      <RecurrenceSwitchField
        label={t("repeat")}
        description={t("repeatDescription")}
        disabled={disabled}
      />

      {enabled && (
        <>
          <TextField
            control={control}
            name="recurrence.lengthDays"
            label={t("lengthDaysLabel")}
            tooltipText={t("lengthDaysTooltip")}
            type="number"
            min={SPRINT_RECURRENCE_MIN_LENGTH_DAYS}
            max={SPRINT_RECURRENCE_MAX_LENGTH_DAYS}
            required
            disabled={disabled}
          />

          <RecurrenceRadioField
            name="recurrence.unfinishedTicketAction"
            label={t("unfinishedTickets")}
            disabled={disabled}
            options={[
              { value: "MOVE_TO_NEXT_SPRINT", label: t("moveToNextSprint") },
              { value: "MOVE_TO_BACKLOG", label: t("moveToBacklog") },
            ]}
          />

          <RecurrenceRadioField
            name="recurrence.creationMode"
            label={t("createNext")}
            disabled={disabled}
            options={[
              { value: "AT_END", label: t("atEnd") },
              { value: "DAYS_BEFORE", label: t("daysBefore") },
            ]}
          />

          {creationMode === "DAYS_BEFORE" && (
            <TextField
              control={control}
              name="recurrence.createDaysBefore"
              label={t("daysBeforeLabel")}
              description={
                hasValidSeriesLength ? t("daysBeforeHint", { max: seriesLength - 1 }) : undefined
              }
              type="number"
              min={1}
              max={hasValidSeriesLength ? seriesLength - 1 : undefined}
              required
              disabled={disabled}
            />
          )}

          {hasValidLength && rolloverAt && (
            <p className="text-muted-foreground flex items-start gap-2 text-xs">
              <Clock className="mt-0.5 h-3.5 w-3.5 shrink-0" />
              <span>
                {t("lengthLine", {
                  days: lengthDays,
                  date: formatUtcDate(locale, rolloverAt),
                  localTime: formatDateTime(rolloverAt),
                })}
              </span>
            </p>
          )}
        </>
      )}
    </div>
  );
}

// ── Switch ───────────────────────────────────────────────────────────

interface RecurrenceSwitchFieldProps {
  label: string;
  description: string;
  disabled: boolean;
}

/**
 * The "Repeat automatically" switch, laid out like the shared ToggleField.
 * The Automation tab reports a sprint length outside 1-365 days on this
 * field, and the shared field's message shows the i18n key as is; the app's
 * FormMessage translates it.
 */
function RecurrenceSwitchField({ label, description, disabled }: RecurrenceSwitchFieldProps) {
  const { control } = useFormContext<RecurrenceFormShape>();

  return (
    <FormField
      control={control}
      name="recurrence.enabled"
      render={({ field: { value, onChange, ...field }, fieldState: { error } }) => (
        <FormItem>
          <FormLabel className="text-sm">{label}</FormLabel>
          <FormControl>
            <div
              className={cn(
                "border-input flex min-h-11 w-full flex-row-reverse items-center rounded-lg border-[1.5px] bg-transparent px-4 py-2 shadow-xs",
                error && "border-destructive"
              )}
            >
              <Switch checked={value} onCheckedChange={onChange} {...field} disabled={disabled} />
              <span className="text-muted-foreground mr-3 flex-1 text-sm">{description}</span>
            </div>
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  );
}

// ── Radio group ──────────────────────────────────────────────────────

interface RecurrenceRadioFieldProps {
  name: "recurrence.unfinishedTicketAction" | "recurrence.creationMode";
  label: string;
  disabled: boolean;
  options: Array<{ value: string; label: string }>;
}

/**
 * The shared form package has no radio field, so the app's RadioGroup is
 * wrapped in a FormField here, laid out like the sprint dialogs' other
 * option groups.
 */
function RecurrenceRadioField({ name, label, disabled, options }: RecurrenceRadioFieldProps) {
  const { control } = useFormContext<RecurrenceFormShape>();

  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel className="text-sm">{label}</FormLabel>
          <FormControl>
            <RadioGroup
              value={field.value}
              onValueChange={field.onChange}
              disabled={disabled}
              className="grid grid-cols-2 gap-2"
            >
              {options.map((option) => {
                const id = `${name}-${option.value}`;
                return (
                  <Label
                    key={option.value}
                    htmlFor={id}
                    className={cn(
                      "border-border flex items-center gap-2 rounded-lg border p-3 text-sm font-normal transition-colors",
                      disabled ? "cursor-not-allowed opacity-60" : "hover:bg-accent cursor-pointer"
                    )}
                  >
                    <RadioGroupItem value={option.value} id={id} />
                    {option.label}
                  </Label>
                );
              })}
            </RadioGroup>
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  );
}

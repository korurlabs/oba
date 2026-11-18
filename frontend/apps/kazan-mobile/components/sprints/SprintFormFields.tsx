import { Globe, TriangleAlert } from "lucide-react-native";
import * as React from "react";
import { View } from "react-native";

import { ListRow, ListSection } from "@/components/kazan/List";
import type { SprintFieldProblems, SprintFields } from "@/components/sprints/sprintModel";
import { useDueDatePicker } from "@/components/ticket/useDueDatePicker";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { TextField } from "@/components/ui/text-field";
import { useDates } from "@/hooks/useDates";
import { useTranslation, type TranslationKey } from "@/i18n";
import { SPRINT_GOAL_MAX, SPRINT_NAME_MAX, SPRINT_SERIES_NAME_MAX } from "@/lib/schemas/sprints";

const NAME_PROBLEM: Record<NonNullable<SprintFieldProblems["name"]>, TranslationKey> = {
  required: "sprintNameRequired",
  "too-long": "sprintNameTooLong",
  "series-too-long": "sprintSeriesNameTooLong",
};

type SprintFormFieldsProps = {
  fields: SprintFields;
  onChange: (patch: Partial<SprintFields>) => void;
  /** Problems found before sending; each shows under its field. */
  problems: SprintFieldProblems;
  /** Field errors the server returned, by field name; they win over `problems`. */
  serverErrors: Record<string, string>;
  /** The round shown after the name while the name is a series name, such as 1 or 7. */
  round: number | null;
  /**
   * What the helper under a series name says: on Create, that sprints get
   * round numbers; on Edit, which round this sprint is and that a rename
   * renames the series' unfinished sprints. Default `create`.
   */
  roundHelper?: "create" | "edit";
  /** The days are UTC calendar days, because the sprint is automated. */
  utcDays: boolean;
  /** A dates problem the automation found, such as a sprint longer than 365 days. */
  datesError?: string | null;
  disabled?: boolean;
};

/**
 * The name, goal and dates of a sprint, shared by Create and Edit. The
 * dates are calendar days picked in the platform's own date picker; while
 * the sprint is automated they are UTC days, which a note under them says.
 */
function SprintFormFields({
  fields,
  onChange,
  problems,
  serverErrors,
  round,
  roundHelper = "create",
  utcDays,
  datesError,
  disabled = false,
}: SprintFormFieldsProps) {
  const { t } = useTranslation();
  const dates = useDates();
  const seriesName: boolean = round !== null;

  const startPicker = useDueDatePicker({
    value: fields.startDay || null,
    title: t("sprintStartDate"),
    onPick: (startDay: string) => onChange({ startDay }),
  });
  const endPicker = useDueDatePicker({
    value: fields.endDay || null,
    title: t("sprintEndDate"),
    onPick: (endDay: string) => onChange({ endDay }),
  });

  const nameError: string | null =
    serverErrors.name ??
    serverErrors.seriesName ??
    (problems.name
      ? t(NAME_PROBLEM[problems.name], {
          max: problems.name === "series-too-long" ? SPRINT_SERIES_NAME_MAX : SPRINT_NAME_MAX,
        })
      : null);
  const goalError: string | null =
    serverErrors.goal ?? (problems.goal ? t("sprintGoalTooLong", { max: SPRINT_GOAL_MAX }) : null);
  const endError: string | null =
    serverErrors.endDate ??
    serverErrors.startDate ??
    (problems.endDay ? t("sprintEndAfterStart") : null) ??
    datesError ??
    null;

  return (
    <View>
      <View className="gap-4 px-4 pt-4">
        <TextField
          label={t("sprintName")}
          placeholder={seriesName ? t("sprintSeriesNamePlaceholder") : t("sprintNamePlaceholder")}
          value={fields.name}
          onChangeText={(name) => onChange({ name })}
          suffix={round !== null ? ` ${round}` : undefined}
          helper={
            round === null
              ? undefined
              : roundHelper === "edit"
                ? t("sprintSeriesRoundHelper", { round })
                : t("sprintSeriesNameHelper", {
                    name: fields.name.trim() || t("sprintSeriesNamePlaceholder"),
                    round,
                  })
          }
          error={nameError}
          maxLength={seriesName ? SPRINT_SERIES_NAME_MAX : SPRINT_NAME_MAX}
          editable={!disabled}
          returnKeyType="next"
        />
        <TextField
          label={t("sprintGoal")}
          placeholder={t("sprintGoalPlaceholder")}
          helper={t("sprintGoalHelper")}
          value={fields.goal}
          onChangeText={(goal) => onChange({ goal })}
          multiline
          rows={3}
          error={goalError}
          maxLength={SPRINT_GOAL_MAX}
          editable={!disabled}
        />
      </View>
      <ListSection header={t("sprintDates")}>
        <ListRow
          title={t("sprintStartDate")}
          value={(fields.startDay && dates.date(fields.startDay)) || t("sprintPickDate")}
          onPress={disabled ? undefined : startPicker.open}
          disabled={disabled}
        />
        <ListRow
          title={t("sprintEndDate")}
          value={(fields.endDay && dates.date(fields.endDay)) || t("sprintPickDate")}
          onPress={disabled ? undefined : endPicker.open}
          disabled={disabled}
        />
      </ListSection>
      {startPicker.calendar ? <View className="pt-2">{startPicker.calendar}</View> : null}
      {endPicker.calendar ? <View className="pt-2">{endPicker.calendar}</View> : null}
      {endError ? (
        <View className="flex-row items-start gap-1.5 px-6 pt-2">
          <Icon as={TriangleAlert} size={14} className="mt-0.5 text-destructive" />
          <Text variant="footnote" className="flex-1 text-destructive">
            {endError}
          </Text>
        </View>
      ) : null}
      {utcDays ? (
        <View className="flex-row items-start gap-2 px-6 pt-2">
          <Icon as={Globe} size={14} className="mt-0.5 text-muted-foreground" />
          <Text variant="footnote" className="flex-1 text-muted-foreground">
            {t("sprintUtcDatesNote")}
          </Text>
        </View>
      ) : null}
    </View>
  );
}

export { SprintFormFields };
export type { SprintFormFieldsProps };

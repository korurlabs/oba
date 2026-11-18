import { Clock } from "lucide-react-native";
import * as React from "react";
import { View } from "react-native";

import { ListRow, ListSection } from "@/components/kazan/List";
import {
  parseCount,
  rolloverInstant,
  type RecurrenceForm,
  type RecurrenceProblems,
} from "@/components/sprints/sprintModel";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { TextField } from "@/components/ui/text-field";
import { useDates } from "@/hooks/useDates";
import { useTranslation, type TranslationKey } from "@/i18n";
import { SPRINT_LENGTH_MAX_DAYS, SPRINT_LENGTH_MIN_DAYS } from "@/lib/schemas/sprints";

const LENGTH_PROBLEM: Record<NonNullable<RecurrenceProblems["lengthDays"]>, TranslationKey> = {
  range: "sprintRepeatLengthRange",
};

const DAYS_BEFORE_PROBLEM: Record<
  NonNullable<RecurrenceProblems["createDaysBefore"]>,
  TranslationKey
> = {
  required: "sprintRepeatDaysBeforeRequired",
  range: "sprintRepeatDaysBeforeRange",
};

type RecurrenceFieldsProps = {
  value: RecurrenceForm;
  onChange: (patch: Partial<RecurrenceForm>) => void;
  /** The sprint's own length from its dates; null while a date is missing. */
  datesLength: number | null;
  /** The sprint's end day, `YYYY-MM-DD`: the rollover is that day at 00:00 UTC. */
  endDay: string | null;
  problems: RecurrenceProblems;
  /** Read-only, for a completed sprint or a user who may not manage it. */
  disabled?: boolean;
};

/**
 * The automation of a sprint series: repeat, the length of each new sprint,
 * where unfinished tickets go and when the next sprint is created. An
 * automated sprint rolls over at 00:00 UTC on its end date; the line under
 * the fields says when that is in the device's time.
 */
function RecurrenceFields({
  value,
  onChange,
  datesLength,
  endDay,
  problems,
  disabled = false,
}: RecurrenceFieldsProps) {
  const { t } = useTranslation();
  const dates = useDates();
  const seriesLength: number | null = parseCount(value.lengthDays);
  const lengthValid: boolean =
    seriesLength !== null &&
    seriesLength >= SPRINT_LENGTH_MIN_DAYS &&
    seriesLength <= SPRINT_LENGTH_MAX_DAYS;
  const rollover: string | null = endDay ? rolloverInstant(endDay) : null;
  const datesValid: boolean = datesLength !== null && !problems.dates && datesLength > 0;

  return (
    <View>
      <ListSection header={t("sprintAutomation")}>
        <ListRow
          title={t("sprintRepeat")}
          subtitle={t("sprintRepeatBody")}
          wrap
          accessory="switch"
          checked={value.enabled}
          onCheckedChange={(enabled) => onChange({ enabled })}
          disabled={disabled}
        />
      </ListSection>
      {value.enabled ? (
        <>
          <View className="px-4 pt-4">
            <TextField
              label={t("sprintRepeatLength")}
              helper={t("sprintRepeatLengthHelper")}
              value={value.lengthDays}
              onChangeText={(lengthDays) => onChange({ lengthDays })}
              keyboardType="number-pad"
              error={problems.lengthDays ? t(LENGTH_PROBLEM[problems.lengthDays]) : null}
              editable={!disabled}
              maxLength={3}
            />
          </View>
          <ListSection header={t("sprintRepeatUnfinished")}>
            <ListRow
              title={t("sprintRepeatToNextSprint")}
              accessory="radio"
              checked={value.unfinishedTicketAction === "MOVE_TO_NEXT_SPRINT"}
              onPress={() => onChange({ unfinishedTicketAction: "MOVE_TO_NEXT_SPRINT" })}
              disabled={disabled}
            />
            <ListRow
              title={t("sprintRepeatToBacklog")}
              accessory="radio"
              checked={value.unfinishedTicketAction === "MOVE_TO_BACKLOG"}
              onPress={() => onChange({ unfinishedTicketAction: "MOVE_TO_BACKLOG" })}
              disabled={disabled}
            />
          </ListSection>
          <ListSection header={t("sprintRepeatCreateNext")}>
            <ListRow
              title={t("sprintRepeatAtEnd")}
              accessory="radio"
              checked={value.creationMode === "AT_END"}
              onPress={() => onChange({ creationMode: "AT_END" })}
              disabled={disabled}
            />
            <ListRow
              title={t("sprintRepeatDaysBefore")}
              accessory="radio"
              checked={value.creationMode === "DAYS_BEFORE"}
              onPress={() => onChange({ creationMode: "DAYS_BEFORE" })}
              disabled={disabled}
            />
          </ListSection>
          {value.creationMode === "DAYS_BEFORE" ? (
            <View className="px-4 pt-4">
              <TextField
                label={t("sprintRepeatDaysBefore")}
                helper={
                  lengthValid && seriesLength !== null
                    ? t("sprintRepeatDaysBeforeHelper", { max: seriesLength - 1 })
                    : undefined
                }
                value={value.createDaysBefore}
                onChangeText={(createDaysBefore) => onChange({ createDaysBefore })}
                keyboardType="number-pad"
                error={
                  problems.createDaysBefore
                    ? t(DAYS_BEFORE_PROBLEM[problems.createDaysBefore])
                    : null
                }
                editable={!disabled}
                maxLength={3}
              />
            </View>
          ) : null}
          {datesValid && rollover ? (
            <View className="flex-row items-start gap-2 px-6 pt-3">
              <Icon as={Clock} size={14} className="mt-0.5 text-muted-foreground" />
              <Text variant="footnote" className="flex-1 text-muted-foreground">
                {t("sprintRepeatRollover", {
                  count: datesLength,
                  date: dates.instantDate(rollover, "UTC") ?? "",
                  localTime: dates.time(rollover) ?? "",
                })}
              </Text>
            </View>
          ) : null}
        </>
      ) : null}
    </View>
  );
}

export { RecurrenceFields };
export type { RecurrenceFieldsProps };

import DateTimePicker, {
  DateTimePickerAndroid,
  type DateTimePickerEvent,
} from "@react-native-community/datetimepicker";
import { useColorScheme } from "nativewind";
import * as React from "react";
import { Platform } from "react-native";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTranslation } from "@/i18n";
import { parseDateOnly, toDateOnly } from "@/lib/dates";

/** Monday, the first day of the app's weeks. */
const MONDAY = 1;

type DueDatePickerInput = {
  /** The day the picker starts on, `YYYY-MM-DD`; today without one. */
  value: string | null;
  /** The picker's title, such as "Due date". */
  title: string;
  /** Receives the picked day as `YYYY-MM-DD`. */
  onPick: (day: string) => void;
  /** Takes the due date off; without it the picker offers no way to clear. */
  onClear?: () => void;
  /** The first day that can be picked, `YYYY-MM-DD`; any day without one. */
  minimumDate?: string | null;
};

/**
 * A due-day picker in the platform's own form. Android opens the system date
 * dialog, with "Remove" as its third button when the day can be cleared. iOS
 * unfolds an inline calendar under the row, as Reminders does: a tapped day
 * is the pick and folds it again. A due date is a calendar day, so the pick
 * leaves here as `YYYY-MM-DD` from the local date fields.
 *
 * Returns `open` for the row's press and `calendar`, the iOS calendar to
 * render under the row while it is open (always null on Android).
 */
export function useDueDatePicker({
  value,
  title,
  onPick,
  onClear,
  minimumDate,
}: DueDatePickerInput) {
  const { t, language } = useTranslation();
  const colors = useThemeColors();
  const { colorScheme } = useColorScheme();
  const [expanded, setExpanded] = React.useState<boolean>(false);
  const start: Date = parseDateOnly(value) ?? new Date();
  const earliest: Date | undefined = parseDateOnly(minimumDate) ?? undefined;

  const open = React.useCallback(() => {
    if (Platform.OS !== "android") {
      setExpanded((current) => !current);
      return;
    }
    DateTimePickerAndroid.open({
      value: parseDateOnly(value) ?? new Date(),
      mode: "date",
      title,
      minimumDate: parseDateOnly(minimumDate) ?? undefined,
      firstDayOfWeek: MONDAY,
      neutralButton: onClear ? { label: t("ticketDueDateRemove") } : undefined,
      onChange: (event: DateTimePickerEvent, date?: Date) => {
        if (event.type === "set" && date) onPick(toDateOnly(date));
        else if (event.type === "neutralButtonPressed") onClear?.();
      },
      onError: (error: Error) => {
        console.warn("[ticket] the date dialog failed to open", error.message);
        toast.error(t("ticketDueDateUnavailable"));
      },
    });
  }, [minimumDate, onClear, onPick, t, title, value]);

  const calendar: React.ReactNode =
    Platform.OS === "ios" && expanded ? (
      <Card className="mx-4 gap-1">
        <DateTimePicker
          value={start}
          mode="date"
          display="inline"
          minimumDate={earliest}
          locale={language}
          accentColor={colors.primaryFill}
          themeVariant={colorScheme === "dark" ? "dark" : "light"}
          accessibilityLabel={title}
          onChange={(event: DateTimePickerEvent, date?: Date) => {
            if (event.type !== "set" || !date) return;
            setExpanded(false);
            onPick(toDateOnly(date));
          }}
        />
        {onClear && value ? (
          <Button
            variant="ghost"
            size="sm"
            onPress={() => {
              setExpanded(false);
              onClear();
            }}
          >
            {t("ticketDueDateRemove")}
          </Button>
        ) : null}
      </Card>
    ) : null;

  return { open, calendar, expanded };
}

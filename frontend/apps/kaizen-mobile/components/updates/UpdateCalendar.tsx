import { addDays, format, startOfWeek, type Locale } from "date-fns";
import { enUS, nl, tr } from "date-fns/locale";
import { ChevronLeft, ChevronRight } from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable, View } from "react-native";

import {
  calendarWeeks,
  monthOf,
  shiftMonth,
  type CalendarCell,
} from "@/components/updates/updateModel";
import { IconButton } from "@/components/ui/icon-button";
import { Text } from "@/components/ui/text";
import { useDates } from "@/hooks/useDates";
import { useTranslation, type SupportedLanguage } from "@/i18n";
import { parseDateOnly } from "@/lib/dates";
import { cn } from "@/lib/utils";

/** A day cell is the platform's touch minimum tall: 44 pt on iOS, 48 dp on Android. */
const CELL_HEIGHT: string = Platform.OS === "ios" ? "h-hit-ios" : "h-hit-android";

// Month and weekday names follow the app language, as the rest of the app's dates do.
const LOCALES: Record<SupportedLanguage, Locale> = { en: enUS, nl, tr };

function localeOf(language: string): Locale {
  return LOCALES[language as SupportedLanguage] ?? enUS;
}

export interface UpdateCalendarProps {
  /** The picked business day, `YYYY-MM-DD`. */
  value: string;
  /** Today in the business zone; later days cannot be picked. */
  today: string;
  onChange: (day: string) => void;
}

/**
 * A Monday-first month of business days. Days after today are greyed and
 * cannot be picked, and the month cannot move past today's month.
 */
export function UpdateCalendar({ value, today, onChange }: UpdateCalendarProps) {
  const { t, language } = useTranslation();
  const dates = useDates();
  const locale: Locale = localeOf(language);
  const [month, setMonth] = React.useState<string>(() => monthOf(value));
  const weeks: CalendarCell[][] = React.useMemo(() => calendarWeeks(month, today), [month, today]);
  const monthDate: Date | null = parseDateOnly(month);
  const canGoForward: boolean = month < monthOf(today);
  const weekdays: string[] = React.useMemo(() => {
    const monday: Date = startOfWeek(new Date(), { weekStartsOn: 1 });
    return Array.from({ length: 7 }, (_, index) =>
      format(addDays(monday, index), "EEEEEE", { locale })
    );
  }, [locale]);

  return (
    <View className="gap-2 px-4 pt-2">
      <View className="flex-row items-center justify-between">
        <IconButton
          icon={ChevronLeft}
          label={t("updCalendarPrevious")}
          onPress={() => setMonth(shiftMonth(month, -1))}
        />
        <Text variant="subhead" accessibilityRole="header" className="font-sans-semibold">
          {monthDate ? format(monthDate, "LLLL yyyy", { locale }) : ""}
        </Text>
        <IconButton
          icon={ChevronRight}
          label={t("updCalendarNext")}
          disabled={!canGoForward}
          onPress={() => setMonth(shiftMonth(month, 1))}
        />
      </View>
      <View className="flex-row">
        {weekdays.map((weekday, index) => (
          <Text
            key={index}
            variant="caption"
            importantForAccessibility="no"
            className="flex-1 text-center text-muted-foreground"
          >
            {weekday}
          </Text>
        ))}
      </View>
      {weeks.map((week, weekIndex) => (
        <View key={weekIndex} className="flex-row">
          {week.map((cell, cellIndex) => {
            if (!cell.day) return <View key={cellIndex} className={cn(CELL_HEIGHT, "flex-1")} />;
            const day: string = cell.day;
            const selected: boolean = day === value;
            const isToday: boolean = day === today;
            const dayNumber: number = parseDateOnly(day)?.getDate() ?? 0;
            return (
              <Pressable
                key={cellIndex}
                disabled={cell.disabled}
                onPress={() => onChange(day)}
                accessibilityRole="button"
                accessibilityLabel={dates.longDate(day) ?? day}
                accessibilityState={{ selected, disabled: cell.disabled }}
                className={cn(CELL_HEIGHT, "flex-1 items-center justify-center")}
              >
                <View
                  className={cn(
                    "h-9 w-9 items-center justify-center rounded-full",
                    selected ? "bg-primary-fill" : isToday ? "border border-link" : ""
                  )}
                >
                  <Text
                    variant="footnote"
                    className={cn(
                      selected
                        ? "font-sans-semibold text-primary-foreground"
                        : cell.disabled
                          ? "text-muted-foreground opacity-40"
                          : "text-foreground"
                    )}
                  >
                    {String(dayNumber)}
                  </Text>
                </View>
              </Pressable>
            );
          })}
        </View>
      ))}
    </View>
  );
}

import { Check } from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable, StyleSheet, View } from "react-native";

import { BottomSheet } from "@/components/kazan/BottomSheet";
import {
  resolvePriority,
  resolveStatus,
  resolveTicketType,
} from "@/components/kazan/ticketMetaModel";
import { DismissWhen } from "@/components/ticket/DismissWhen";
import { clearTicketChoice, useTicketChoice } from "@/components/ticket/ticketChoiceStore";
import { ChoiceChip } from "@/components/ticket/TicketChips";
import {
  choiceToApply,
  disabledReasons,
  type ChoiceKind,
  type ChoiceOption,
  type ChoicePicker,
} from "@/components/ticket/ticketPickers";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTranslation, type TranslationKey } from "@/i18n";
import { cn } from "@/lib/utils";

const IS_IOS = Platform.OS === "ios";

/** The spoken name of a value, since its row shows only the chip. */
function choiceLabel(t: (key: TranslationKey) => string, kind: ChoiceKind, value: string): string {
  switch (kind) {
    case "type": {
      const meta = resolveTicketType(value);
      return meta.rawLabel ?? t(meta.labelKey);
    }
    case "status":
      return t(resolveStatus(value).labelKey);
    case "priority": {
      const meta = resolvePriority(value);
      return meta ? t(meta.labelKey) : value;
    }
    case "rank":
      return value;
  }
}

type ChoiceListProps = {
  kind: ChoiceKind;
  options: readonly ChoiceOption[];
  /** The value that carries the check. */
  selected: string | null;
  onSelect: (value: string) => void;
};

/**
 * The options of a ticket value, each on one row: its chip with any detail
 * beside it, what it is worth at the end, and a check on the selected one. A
 * disabled option is dimmed and not pickable; the reasons of the disabled
 * options follow the list once each. iOS groups the rows in an inset card;
 * Android draws full-bleed rows.
 */
export function ChoiceList({ kind, options, selected, onSelect }: ChoiceListProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const notes: string[] = disabledReasons(options);
  // With values at the row ends, every row keeps room for the check so those values line up.
  const hasTrailing: boolean = options.some((option) => !!option.trailing);

  return (
    <View className={IS_IOS ? "mt-2" : undefined}>
      <View className={cn(IS_IOS && "mx-4 overflow-hidden rounded-lg bg-card")}>
        {options.map((option, index) => {
          const checked: boolean = option.value === selected;
          const disabled: boolean = !!option.disabled;
          const label: string = [
            choiceLabel(t, kind, option.value),
            option.detail,
            option.trailing,
            option.reason,
          ]
            .filter(Boolean)
            .join(", ");
          return (
            <Pressable
              key={option.value}
              accessibilityRole="button"
              accessibilityLabel={label}
              accessibilityState={{ selected: checked, disabled }}
              disabled={disabled}
              onPress={() => onSelect(option.value)}
              android_ripple={{ color: colors.accent }}
              className={cn(
                "flex-row items-center gap-3",
                IS_IOS
                  ? "min-h-row-ios px-4 py-2.5 active:bg-accent"
                  : "min-h-row-android px-6 py-2"
              )}
            >
              <View
                className={cn(
                  "flex-1 flex-row flex-wrap items-center gap-x-2.5 gap-y-1",
                  disabled && "opacity-40"
                )}
              >
                {/* A floor under the chips' widths so the details beside them start in one column. */}
                <View className={cn("items-start", option.detail && "min-w-16")}>
                  <ChoiceChip kind={kind} value={option.value} />
                </View>
                {option.detail ? (
                  <Text className="font-sans text-subhead text-muted-foreground">
                    {option.detail}
                  </Text>
                ) : null}
              </View>
              {option.trailing ? (
                <Text
                  className={cn(
                    "font-sans-medium text-subhead text-foreground",
                    disabled && "opacity-40"
                  )}
                >
                  {option.trailing}
                </Text>
              ) : null}
              {checked ? (
                <Icon as={Check} size={20} className={IS_IOS ? "text-link" : "text-primary-fill"} />
              ) : hasTrailing ? (
                <View className="w-5" />
              ) : null}
              {IS_IOS && index < options.length - 1 ? (
                <View
                  className="absolute bottom-0 left-4 right-0 bg-border"
                  style={{ height: StyleSheet.hairlineWidth }}
                />
              ) : null}
            </Pressable>
          );
        })}
      </View>
      {notes.map((note) => (
        <Text
          key={note}
          className={cn(
            "pt-1.5 font-sans text-footnote text-muted-foreground",
            IS_IOS ? "px-8" : "px-6"
          )}
        >
          {note}
        </Text>
      ))}
    </View>
  );
}

/**
 * The choice sheet of a ticket value (type, status, priority or mission
 * rank), for a saved ticket and for New ticket alike: the screen beneath
 * hands over the picker through the choice store, and a tap on an option
 * hands the pick back and closes the sheet. Without a picker, such as after
 * a reload, it closes.
 */
export function ChoicePickerSheet() {
  const latest: ChoicePicker | null = useTicketChoice();
  // The picker this sheet opened with; a later one belongs to the next sheet.
  const [picker] = React.useState<ChoicePicker | null>(latest);
  const [picked, setPicked] = React.useState<boolean>(false);

  React.useEffect(() => () => clearTicketChoice(picker), [picker]);

  const select = (value: string) => {
    if (!picker) return;
    const next: string | null = choiceToApply(picker, value);
    if (next !== null) picker.onPick(next);
    setPicked(true);
  };

  return (
    <BottomSheet title={picker?.title ?? ""} detent="medium" scroll>
      <DismissWhen when={!picker || picked} />
      {picker ? (
        <ChoiceList
          kind={picker.kind}
          options={picker.options}
          selected={picker.current}
          onSelect={select}
        />
      ) : null}
    </BottomSheet>
  );
}

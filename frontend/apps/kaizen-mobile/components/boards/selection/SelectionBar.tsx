import { ArrowRightLeft } from "lucide-react-native";
import * as React from "react";
import { Pressable, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import {
  allPicked,
  selectableIn,
  type BoardSelection,
  type SelectionGroup,
} from "@/components/boards/selection/useTicketSelection";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

type SelectionBarProps = {
  count: number;
  onMove: () => void;
};

/**
 * The bar under a board list in selection mode: Move selected tickets, off
 * while nothing is picked. It keeps to the bottom safe area, which inside a
 * tab includes the iOS tab bar floating over the list.
 */
function SelectionBar({ count, onMove }: SelectionBarProps) {
  const { t } = useTranslation();
  return (
    <SafeAreaView edges={["bottom"]} className="border-t border-border bg-screen">
      <View className="px-4 py-3">
        <Button block icon={ArrowRightLeft} onPress={onMove} disabled={count === 0}>
          {t("bulkMoveTitle")}
        </Button>
      </View>
    </SafeAreaView>
  );
}

type SelectionTextButtonProps = {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  /** Bold for the nav bar's Cancel, which ends the mode. */
  bold?: boolean;
  className?: string;
};

/**
 * A text action of selection mode, 44 pt tall as a touch target: the nav
 * bar's Cancel and a list header's Select all or Select none.
 */
function SelectionTextButton({
  label,
  onPress,
  disabled = false,
  bold = false,
  className,
}: SelectionTextButtonProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      hitSlop={{ left: 10, right: 10 }}
      onPress={onPress}
      className={cn(
        "min-h-11 justify-center px-2 active:opacity-60",
        disabled && "opacity-50",
        className
      )}
    >
      <Text
        numberOfLines={1}
        className={cn("text-body text-link", bold ? "font-sans-semibold" : "font-sans")}
      >
        {label}
      </Text>
    </Pressable>
  );
}

type SelectAllToggleProps = {
  selection: BoardSelection;
  group: SelectionGroup;
  /** The ids of the rows the group lists. */
  rowIds: readonly string[];
};

/**
 * A list header's Select all, or Select none once every row of the group is
 * picked. Off while the other group holds the selection or the list is empty.
 */
function SelectAllToggle({ selection, group, rowIds }: SelectAllToggleProps) {
  const { t } = useTranslation();
  const all: boolean = allPicked(selection.state, group, rowIds);
  return (
    <SelectionTextButton
      label={all ? t("boardSelectNone") : t("boardSelectAll")}
      onPress={() => selection.toggleAll(group, rowIds)}
      disabled={rowIds.length === 0 || !selectableIn(selection.state, group)}
    />
  );
}

export { SelectAllToggle, SelectionBar, SelectionTextButton };

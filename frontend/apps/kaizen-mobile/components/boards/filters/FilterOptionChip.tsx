import * as React from "react";
import { Platform, Pressable, View } from "react-native";

import { CHIP_SIZES } from "@/components/ui/chip";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { hapticSelection } from "@/lib/haptics";
import { cn } from "@/lib/utils";

type FilterOptionChipProps = {
  label: string;
  selected: boolean;
  onPress: () => void;
  /** The option's mark before the label: an avatar, a type swatch, a priority icon, a dot. */
  leading?: React.ReactNode;
  /** Read instead of the label, such as the member's name behind "Me". */
  accessibilityLabel?: string;
};

/**
 * One option of a multi-select filter in the Filters sheet, the same outlined
 * pill in every section so the sheet reads as one list of choices. Selected is
 * a `primary-light` ground in a `primary-medium` outline with a heavier label,
 * so a chosen option stands out without colour alone. The size is the `md`
 * Chip's, a step up from the dense filter row, since the sheet has the room. A
 * long name ends in an ellipsis at the sheet width instead of running past the
 * edge.
 */
function FilterOptionChip({
  label,
  selected,
  onPress,
  leading,
  accessibilityLabel,
}: FilterOptionChipProps) {
  const colors = useThemeColors();
  const sizing = CHIP_SIZES.md;
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={{ checked: selected }}
      hitSlop={sizing.hitSlop}
      android_ripple={{ color: colors.accent, foreground: true }}
      onPress={() => {
        hapticSelection();
        onPress();
      }}
      className={cn(
        "max-w-full flex-row items-center overflow-hidden rounded-full border",
        sizing.className,
        selected ? "border-primary-medium bg-primary-light" : "border-border bg-card",
        Platform.select({ ios: "active:opacity-60" })
      )}
    >
      {leading ? <View className="shrink-0">{leading}</View> : null}
      <Text
        numberOfLines={1}
        className={cn(
          "shrink text-foreground",
          sizing.textClassName,
          selected ? "font-sans-semibold" : "font-sans-medium"
        )}
      >
        {label}
      </Text>
    </Pressable>
  );
}

export { FilterOptionChip };
export type { FilterOptionChipProps };

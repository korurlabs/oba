import { Check, ChevronDown, X } from "lucide-react-native";
import type { LucideIcon } from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable, View, type PressableProps } from "react-native";

import { formatBadgeCount } from "@/components/ui/badge-count";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

type ChipSize = "sm" | "md";

/**
 * The chip sizes: `sm`, 32 tall, for the dense filter row above ticket lists;
 * `md`, 36 tall, for chips in a sheet with room to read them. Each hit slop
 * grows the chip to the platform's touch minimum: 44 pt on iOS, 48 dp on Android.
 */
const CHIP_SIZES: Record<
  ChipSize,
  { className: string; textClassName: string; hitSlop: PressableProps["hitSlop"] }
> = {
  sm: {
    className: "min-h-8 gap-1.5 px-3 py-1",
    textClassName: "text-footnote",
    hitSlop: Platform.select({
      android: { top: 8, bottom: 8, left: 2, right: 2 },
      default: { top: 6, bottom: 6, left: 2, right: 2 },
    }),
  },
  md: {
    className: "min-h-9 gap-2 px-3.5 py-1.5",
    textClassName: "text-subhead",
    hitSlop: Platform.select({
      android: { top: 6, bottom: 6, left: 2, right: 2 },
      default: { top: 4, bottom: 4, left: 2, right: 2 },
    }),
  },
};

type ChipProps = Omit<PressableProps, "children" | "style"> & {
  label: string;
  selected?: boolean;
  /** `sm` by default; see {@link CHIP_SIZES}. */
  size?: ChipSize;
  /** A leading lucide icon. */
  icon?: LucideIcon;
  /** A leading mark instead of an icon, such as a PriorityIcon or TicketTypeBadge. */
  leading?: React.ReactNode;
  /** A count after the label, such as the active filters on "Filters". */
  count?: number | null;
  /** Adds a chevron: the chip opens a picker sheet. */
  dropdown?: boolean;
  /** Adds a remove button after the label. */
  onRemove?: () => void;
  className?: string;
};

/**
 * A filter toggle in the horizontal row above ticket lists. Selected is a
 * `primary-light` ground in a `primary-medium` outline; Android also leads with
 * a check, as Material filter chips do. The height is a minimum that grows
 * with the system text size.
 */
function Chip({
  label,
  selected = false,
  size = "sm",
  icon,
  leading,
  count,
  dropdown = false,
  onRemove,
  disabled,
  className,
  accessibilityLabel,
  ...props
}: ChipProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const countText = formatBadgeCount(count);
  const showCheck = selected && Platform.OS === "android";
  const sizing = CHIP_SIZES[size];

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? (countText ? `${label}, ${countText}` : label)}
      accessibilityState={{ selected, disabled: !!disabled }}
      disabled={disabled}
      hitSlop={sizing.hitSlop}
      android_ripple={{ color: colors.accent, foreground: true }}
      className={cn(
        "flex-row items-center self-start overflow-hidden rounded-full border",
        sizing.className,
        selected ? "border-primary-medium bg-primary-light" : "border-border bg-card",
        Platform.select({ ios: "active:opacity-60" }),
        disabled && "opacity-50",
        className
      )}
      {...props}
    >
      {showCheck ? <Icon as={Check} size={16} /> : null}
      {!showCheck && icon ? <Icon as={icon} size={16} className="text-muted-foreground" /> : null}
      {!showCheck && !icon ? leading : null}
      <Text className={cn("font-sans-medium text-foreground", sizing.textClassName)}>{label}</Text>
      {countText ? (
        <View className="min-h-[18px] min-w-[18px] items-center justify-center rounded-full bg-primary-fill px-1">
          <Text className="font-sans-semibold text-caption-2 text-primary-foreground">
            {countText}
          </Text>
        </View>
      ) : null}
      {dropdown ? <Icon as={ChevronDown} size={14} className="text-muted-foreground" /> : null}
      {onRemove ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`${t("kitRemoveFilter")}: ${label}`}
          hitSlop={10}
          onPress={onRemove}
        >
          <Icon as={X} size={14} className="text-muted-foreground" />
        </Pressable>
      ) : null}
    </Pressable>
  );
}

export { Chip, Chip as FilterChip, CHIP_SIZES };
export type { ChipProps, ChipSize };
